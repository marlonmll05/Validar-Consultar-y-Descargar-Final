package com.certificadosapi.certificados.service.atenciones;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import javax.imageio.ImageIO;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.certificadosapi.certificados.config.DatabaseConfig;
import com.certificadosapi.certificados.util.ServidorUtil;

@Service
public class GenerarService {

    private static final Logger log = LoggerFactory.getLogger(GenerarService.class);

    private final DatabaseConfig databaseConfig;
    private final ServidorUtil servidorUtil;

    @Autowired
    public GenerarService(DatabaseConfig databaseConfig, ServidorUtil servidorUtil){
        this.databaseConfig = databaseConfig;
        this.servidorUtil = servidorUtil;
    }

    // ENDPOINT PARA GENERAR APOYO DIAGNOSTICO (IMAGENES A PDF)
    public byte[] generarPdfApoyoDiagnostico(int idAdmision, int idPacienteKey) 
            throws SQLException, IOException {
        
        log.info("Iniciando generarPdfApoyoDiagnostico(idAdmision={}, idPacienteKey={})", idAdmision, idPacienteKey);

        String sql = "EXEC dbo.pa_HC_ResultadosAnexosPrintUnif ?, ?, '-1'";

        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoft100_ST"));
             PreparedStatement ps = conn.prepareStatement(sql)) {

            log.debug("Conexión abierta a IPSoft100_ST para generarPdfApoyoDiagnostico");
            ps.setInt(1, idPacienteKey);
            ps.setInt(2, idAdmision);

            try (ResultSet rs = ps.executeQuery()) {
                boolean hayResultados = false;
                PDDocument doc = new PDDocument();

                try {
                    while (rs.next()) {
                        String ruta = rs.getString("RutaArchivoLocal");
                        log.debug("RutaArchivoLocal obtenida desde SQL: {}", ruta);

                        if (ruta == null || ruta.isBlank()) {
                            log.warn("Registro con RutaArchivoLocal nula o vacía. Se omite.");
                            continue;
                        }

                        File imgFile = new File(ruta);
                        if (!imgFile.exists()) {
                            log.warn("Archivo de imagen no existe en ruta: {}", ruta);
                            continue;
                        }

                        BufferedImage bimg = ImageIO.read(imgFile);
                        if (bimg == null) {
                            log.warn("No se pudo leer archivo como imagen: {}", ruta);
                            continue;
                        }

                        PDPage page = new PDPage(PDRectangle.A4);
                        doc.addPage(page);

                        PDImageXObject pdImage = PDImageXObject.createFromFileByContent(imgFile, doc);
                        try (PDPageContentStream contentStream = new PDPageContentStream(doc, page)) {
                            float pageWidth = page.getMediaBox().getWidth();
                            float pageHeight = page.getMediaBox().getHeight();
                            contentStream.drawImage(pdImage, 0, 0, pageWidth, pageHeight);
                        }

                        hayResultados = true;
                    }

                    if (!hayResultados) {
                        log.warn("No se encontraron anexos para generar PDF en generarPdfApoyoDiagnostico(idAdmision={}, idPacienteKey={})",
                                 idAdmision, idPacienteKey);
                        throw new NoSuchElementException("No hay anexos disponibles para generar PDF");
                    }

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    doc.save(baos);
                    byte[] pdfBytes = baos.toByteArray();
                    log.info("PDF de apoyo diagnóstico generado correctamente. Tamaño={} bytes", pdfBytes.length);
                    return pdfBytes;
                } finally {
                    doc.close();
                }
            }
        }
    }

    // ENDPOINT PARA DESCARGAR LA FACTURA DE VENTA
    public Long descargarFacturaVenta(Long idAdmision, Long idPacienteKey, 
                                      Long idSoporteKey, String tipoDocumento) 
            throws SQLException, IOException {
        
        log.info("Iniciando descargarFacturaVenta(idAdmision={}, idPacienteKey={}, idSoporteKey={}, tipoDocumento={})",
                 idAdmision, idPacienteKey, idSoporteKey, tipoDocumento);

        String urlBase = null;
        String connectionUrl = databaseConfig.getConnectionUrl("IPSoft100_ST");

        // Obtener URL del servidor de reportes
        try (Connection conn = DriverManager.getConnection(connectionUrl)) {
            String sql = "SELECT ValorParametro FROM ParametrosServidor WHERE NomParametro = 'URLReportServerWS'";
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    urlBase = rs.getString("ValorParametro");
                    log.debug("URL Base obtenida desde ParametrosServidor: {}", urlBase);
                } else {
                    log.error("No se encontró el parámetro URLReportServerWS en ParametrosServidor");
                    throw new IllegalStateException("No se encontró la URL del servidor de reportes.");
                }
            }
        }

        if (urlBase == null || urlBase.trim().isEmpty()) {
            log.error("Valor de urlBase nulo o vacío después de consultar ParametrosServidor");
            throw new IllegalStateException("No se encontró la URL del servidor de reportes.");
        }

        // Ejecutar procedimiento para obtener rutaReporte e IdMovDoc
        String rutaReporte = null;
        Long idMovDoc = null;

        try (Connection conn = DriverManager.getConnection(connectionUrl)) {
            String sqlProc = "EXEC pa_Net_Facturas_Tablas 12, ?";
            
            try (PreparedStatement psProc = conn.prepareStatement(sqlProc)) {
                psProc.setLong(1, idAdmision);
                log.debug("Ejecutando pa_Net_Facturas_Tablas 12, {} para obtener RutaReporte e IdMovDoc", idAdmision);
                
                try (ResultSet rsProc = psProc.executeQuery()) {
                    if (rsProc.next()) {
                        rutaReporte = rsProc.getString("RutaReporte");
                        idMovDoc = rsProc.getLong("IdMovDoc");
                        
                        log.info("Resultado pa_Net_Facturas_Tablas: RutaReporte='{}', IdMovDoc={}", rutaReporte, idMovDoc);
                    } else {
                        log.error("El procedimiento pa_Net_Facturas_Tablas 12, {} no retornó resultados", idAdmision);
                        throw new IllegalStateException("No se obtuvieron resultados del procedimiento almacenado.");
                    }
                }
            }
        }

        if (rutaReporte == null || idMovDoc == null) {
            log.error("rutaReporte o idMovDoc nulos después de pa_Net_Facturas_Tablas para idAdmision={}", idAdmision);
            throw new IllegalStateException("No se pudo obtener RutaReporte o IdMovDoc del procedimiento almacenado.");
        }

        // Descarga del PDF desde Reporting Services
        try (CloseableHttpClient httpClient = servidorUtil.crearHttpClientConNTLM()) {

            String reportUrl = urlBase + "?" + rutaReporte + "&IdMovDoc=" + idMovDoc + "&rs:Format=PDF";
            log.debug("URL original para descarga de factura: {}", reportUrl);

            try {
                URI uri = new URI(reportUrl.replace(" ", "%20"));
                reportUrl = uri.toString();
                log.debug("URL codificada para descarga de factura: {}", reportUrl);
            } catch (Exception ex) {
                log.warn("Error al codificar URL de descarga de factura: {}", ex.getMessage());
            }

            HttpGet request = new HttpGet(reportUrl);

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                int statusCode = response.getStatusLine().getStatusCode();
                log.info("Respuesta HTTP al descargar factura: statusCode={}", statusCode);

                if (statusCode == 200) {
                    byte[] pdfBytes = EntityUtils.toByteArray(response.getEntity());
                    log.info("Factura descargada correctamente. Tamaño={} bytes", pdfBytes.length);

                    try (Connection conn = DriverManager.getConnection(connectionUrl)) {
                        String sql = "EXEC dbo.pa_Net_Insertar_DocumentoPdf ?, ?, ?, ?, ?, ?, ?, ?";
                        
                        try (PreparedStatement ps = conn.prepareStatement(sql)) {
                            ps.setLong(1, idAdmision);
                            ps.setLong(2, idPacienteKey);
                            ps.setLong(3, idSoporteKey != null ? idSoporteKey : 18L);
                            ps.setBoolean(4, false);
                            ps.setString(5, tipoDocumento);
                            ps.setBinaryStream(6, new ByteArrayInputStream(pdfBytes));
                            ps.setBoolean(7, true);
                            ps.setBoolean(8, true);
                            
                            log.debug("Insertando PDF de factura en BD para idAdmision={}, idPacienteKey={}", idAdmision, idPacienteKey);
                            try (ResultSet rs = ps.executeQuery()) {
                                if (rs.next()) {
                                    long idGenerado = rs.getLong("IdpdfKey");
                                    log.info("PDF de factura insertado correctamente con IdpdfKey={}", idGenerado);
                                    return idGenerado;
                                } else {
                                    log.error("El procedimiento pa_Net_Insertar_DocumentoPdf no retornó IdpdfKey");
                                }
                            }
                        }
                    }

                    throw new SQLException("No se pudo obtener el ID generado del PDF");

                } else {
                    String errorContent = "";
                    if (response.getEntity() != null) {
                        errorContent = EntityUtils.toString(response.getEntity(), "UTF-8");
                    }

                    log.error("Error HTTP al descargar reporte de factura. Código={}, detalle={}", statusCode, errorContent);
                    throw new IOException("Error al descargar el informe. Código: " + statusCode +
                            " - " + response.getStatusLine().getReasonPhrase() +
                            "\nDetalle: " + errorContent);
                }
            }
        }
    }

    // ENDPOINT PARA DESCARGAR LOS SOPORTES DISPONIBLES PARA UNA ADMISION
    public List<Map<String, Object>> obtenerSoportesDisponibles(Long idAdmision) throws SQLException {

        log.info("Iniciando obtenerSoportesDisponibles(idAdmision={})", idAdmision);

        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoft100_ST"))) {
            String sql = "EXEC pa_Net_Facturas_Tablas ?, ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, 10);
                stmt.setLong(2, idAdmision);

                try (ResultSet rs = stmt.executeQuery()) {
                    List<Map<String, Object>> resultados = new ArrayList<>();
                    while (rs.next()) {
                        Map<String, Object> fila = new LinkedHashMap<>();
                        fila.put("Id", rs.getInt("Id"));
                        fila.put("nombreRptService", rs.getString("NombreRptService"));
                        fila.put("TipoDocumento", rs.getInt("TipoDocumento"));
                        resultados.add(fila);
                    }

                    log.info("obtenerSoportesDisponibles(idAdmision={}) devolvió {} registros", idAdmision, resultados.size());
                    return resultados;
                }
            }
        }
    }

    // ENDPOINT QUE DESCARGA PDFS DE REPORTING SERVICE Y LOS INSERTA
    public Long insertarSoporte(Long idAdmision, Long idPacienteKey, Long idSoporteKey,
                                String tipoDocumento, String nombreSoporte) 
            throws SQLException, IOException {
        
        log.info("Iniciando insertarSoporte(idAdmision={}, idPacienteKey={}, idSoporteKey={}, tipoDocumento={}, nombreSoporte={})",
                 idAdmision, idPacienteKey, idSoporteKey, tipoDocumento, nombreSoporte);

        if (nombreSoporte == null || nombreSoporte.trim().isEmpty()) {
            log.warn("insertarSoporte llamado sin nombreSoporte válido");
            throw new IllegalArgumentException("El nombre del soporte es requerido.");
        }

        String urlBase = null;

        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoft100_ST"))) {
            String sql = "SELECT ValorParametro FROM ParametrosServidor WHERE NomParametro = 'URLReportServerWS'";
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    urlBase = rs.getString("ValorParametro");
                    log.debug("URL Base obtenida desde ParametrosServidor: {}", urlBase);
                } else {
                    log.error("No se encontró el parámetro URLReportServerWS en ParametrosServidor");
                    throw new IllegalStateException("No se encontró la URL del servidor de reportes.");
                }
            }
        }

        if (urlBase == null || urlBase.trim().isEmpty()) {
            log.error("Valor de urlBase nulo o vacío en insertarSoporte");
            throw new IllegalStateException("No se encontró la URL del servidor de reportes.");
        }

        try (CloseableHttpClient httpClient = servidorUtil.crearHttpClientConNTLM()) {

            String reportUrl = urlBase + "?" + nombreSoporte + "&IdAdmision=" + idAdmision + "&rs:Format=PDF";
            log.debug("URL para descarga de soporte: {}", reportUrl);

            HttpGet request = new HttpGet(reportUrl);

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                int statusCode = response.getStatusLine().getStatusCode();
                log.info("Respuesta HTTP al descargar soporte: statusCode={}", statusCode);

                if (statusCode == 200) {
                    byte[] pdfBytes = EntityUtils.toByteArray(response.getEntity());
                    log.info("Soporte descargado correctamente. Tamaño={} bytes", pdfBytes.length);

                    String connectionUrl = databaseConfig.getConnectionUrl("IPSoft100_ST");

                    try (Connection conn = DriverManager.getConnection(connectionUrl)) {
                        String sql = "EXEC dbo.pa_Net_Insertar_DocumentoPdf ?, ?, ?, ?, ?, ?, ?, ?";
                        
                        try (PreparedStatement ps = conn.prepareStatement(sql)) {
                            ps.setLong(1, idAdmision);
                            ps.setLong(2, idPacienteKey);
                            ps.setLong(3, idSoporteKey);
                            ps.setBoolean(4, false);
                            ps.setString(5, tipoDocumento);
                            ps.setBinaryStream(6, new ByteArrayInputStream(pdfBytes));
                            ps.setBoolean(7, false);
                            ps.setBoolean(8, true);
                            
                            log.debug("Insertando soporte PDF en BD para idAdmision={}, idPacienteKey={}", idAdmision, idPacienteKey);
                            try (ResultSet rs = ps.executeQuery()) {
                                if (rs.next()) {
                                    long idGenerado = rs.getLong("IdpdfKey");
                                    log.info("Soporte PDF insertado correctamente con IdpdfKey={}", idGenerado);
                                    return idGenerado;
                                } else {
                                    log.error("El procedimiento pa_Net_Insertar_DocumentoPdf no retornó IdpdfKey en insertarSoporte");
                                }
                            }
                        }
                    }

                    throw new SQLException("No se pudo obtener el ID generado del PDF");

                } else {
                    String errorContent = "";
                    if (response.getEntity() != null) {
                        errorContent = EntityUtils.toString(response.getEntity(), "UTF-8");
                    }

                    log.error("Error HTTP al descargar reporte de soporte. Código={}, detalle={}", statusCode, errorContent);
                    throw new IOException("Error al descargar el informe. Código: " + statusCode +
                            " - " + response.getStatusLine().getReasonPhrase() +
                            "\nDetalle: " + errorContent);
                }
            }
        }
    }
}
