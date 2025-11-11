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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.certificadosapi.certificados.config.DatabaseConfig;
import com.certificadosapi.certificados.util.ServidorUtil;

@Service
public class GenerarService {

    private DatabaseConfig databaseConfig;
    private ServidorUtil servidorUtil;

    @Autowired
    public GenerarService(DatabaseConfig databaseConfig, ServidorUtil servidorUtil){
        this.databaseConfig = databaseConfig;
        this.servidorUtil = servidorUtil;
    }


    //ENDPOINT PARA GENERAR APOYO DIAGNOSTICO (IMAGENES A PDF)
    public byte[] generarPdfApoyoDiagnostico(int idAdmision, int idPacienteKey) 
            throws SQLException, IOException {
        
        System.out.println("Generando Apoyo diagnostico para idAdmision=" + idAdmision + 
                         ", idPacienteKey=" + idPacienteKey);

        String sql = "EXEC dbo.pa_HC_ResultadosAnexosPrintUnif ?, ?, '-1'";

        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoft100_ST"));
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, idPacienteKey);
            ps.setInt(2, idAdmision);

            try (ResultSet rs = ps.executeQuery()) {
                boolean hayResultados = false;
                PDDocument doc = new PDDocument();

                while (rs.next()) {
                    String ruta = rs.getString("RutaArchivoLocal");
                    System.out.println("RutaArchivoLocal desde SQL: " + ruta);

                    if (ruta == null || ruta.isBlank()) {
                        continue;
                    }

                    File imgFile = new File(ruta);
                    if (!imgFile.exists()) {
                        System.err.println("No existe: " + ruta);
                        continue;
                    }

                    BufferedImage bimg = ImageIO.read(imgFile);
                    if (bimg == null) {
                        System.err.println("No se pudo leer como imagen: " + ruta);
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
                    System.out.println("No se encontraron anexos para generar PDF.");
                    doc.close();
                    throw new NoSuchElementException("No hay anexos disponibles para generar PDF");
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                doc.save(baos);
                doc.close();

                return baos.toByteArray();
            }
        }
    }

    //ENDPOINT PARA DESCARGAR LA FACTURA DE VENTA
    public Long descargarFacturaVenta(Long idAdmision, Long idPacienteKey, 
                                      Long idSoporteKey, String tipoDocumento) 
            throws SQLException, IOException {
        
        System.out.println("========== DESCARGA PDF FACTURA (IdMovDoc) ==========");
        System.out.println("idAdmision: " + idAdmision);
        System.out.println("idPacienteKey: " + idPacienteKey);
        System.out.println("idSoporteKey: " + idSoporteKey);
        System.out.println("tipoDocumento: " + tipoDocumento);
        System.out.println("====================================================");

        String urlBase = null;
        String connectionUrl = databaseConfig.getConnectionUrl("IPSoft100_ST");

        try (Connection conn = DriverManager.getConnection(connectionUrl)) {
            String sql = "SELECT ValorParametro FROM ParametrosServidor WHERE NomParametro = 'URLReportServerWS'";
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    urlBase = rs.getString("ValorParametro");
                    System.out.println("URL Base obtenida: " + urlBase);
                } else {
                    System.err.println("ERROR: No se encontró URLReportServerWS");
                    throw new IllegalStateException("No se encontró la URL del servidor de reportes.");
                }
            }
        }

        if (urlBase == null || urlBase.trim().isEmpty()) {
            System.err.println("ERROR: urlBase vacía");
            throw new IllegalStateException("No se encontró la URL del servidor de reportes.");
        }

        // EJECUTAR PROCEDIMIENTO ALMACENADO
        String rutaReporte = null;
        Long idMovDoc = null;

        try (Connection conn = DriverManager.getConnection(connectionUrl)) {
            String sqlProc = "EXEC pa_Net_Facturas_Tablas 12, ?";
            
            try (PreparedStatement psProc = conn.prepareStatement(sqlProc)) {
                psProc.setLong(1, idAdmision);
                System.out.println("Ejecutando: EXEC pa_Net_Facturas_Tablas 12, " + idAdmision);
                
                try (ResultSet rsProc = psProc.executeQuery()) {
                    if (rsProc.next()) {
                        rutaReporte = rsProc.getString("RutaReporte");
                        idMovDoc = rsProc.getLong("IdMovDoc");
                        
                        System.out.println("========== RESULTADO PROC ==========");
                        System.out.println("RutaReporte: " + rutaReporte);
                        System.out.println("IdMovDoc: " + idMovDoc);
                        System.out.println("====================================");
                    } else {
                        System.err.println("ERROR: Proc no retornó resultados");
                        throw new IllegalStateException("No se obtuvieron resultados del procedimiento almacenado.");
                    }
                }
            }
        }

        if (rutaReporte == null || idMovDoc == null) {
            System.err.println("ERROR: rutaReporte o idMovDoc null");
            throw new IllegalStateException("No se pudo obtener RutaReporte o IdMovDoc del procedimiento almacenado.");
        }

        try (CloseableHttpClient httpClient = servidorUtil.crearHttpClientConNTLM()) {

            System.out.println("\n========== DESCARGANDO PDF ==========");
            String reportUrl = urlBase + "?" + rutaReporte + "&IdMovDoc=" + idMovDoc + "&rs:Format=PDF";
            System.out.println("URL: " + reportUrl);

            try {
                URI uri = new URI(reportUrl.replace(" ", "%20"));
                reportUrl = uri.toString();
                System.out.println("URL codificada: " + reportUrl);
            } catch (Exception ex) {
                System.err.println("ERROR al codificar URL: " + ex.getMessage());
            }

            HttpGet request = new HttpGet(reportUrl);

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                int statusCode = response.getStatusLine().getStatusCode();
                System.out.println("Status: " + statusCode);

                if (statusCode == 200) {
                    byte[] pdfBytes = EntityUtils.toByteArray(response.getEntity());
                    System.out.println("Tamaño: " + pdfBytes.length + " bytes");

                    try (Connection conn = DriverManager.getConnection(connectionUrl)) {
                        String sql = "EXEC dbo.pa_Net_Insertar_DocumentoPdf ?, ?, ?, ?, ?, ?, ?, ?";
                        
                        try (PreparedStatement ps = conn.prepareStatement(sql)) {
                            ps.setLong(1, idAdmision);
                            ps.setLong(2, idPacienteKey);
                            ps.setLong(3, 18);
                            ps.setBoolean(4, false);
                            ps.setString(5, tipoDocumento);
                            ps.setBinaryStream(6, new ByteArrayInputStream(pdfBytes));
                            ps.setBoolean(7, true);
                            ps.setBoolean(8, true);
                            
                            System.out.println("Insertando en BD...");
                            ResultSet rs = ps.executeQuery();
                            if (rs.next()) {
                                long idGenerado = rs.getLong("IdpdfKey");
                                System.out.println("PDF insertado con ID: " + idGenerado);
                                return idGenerado;
                            }
                        }
                    }

                    throw new SQLException("No se pudo obtener el ID generado del PDF");

                } else {
                    System.err.println("ERROR al descargar. Código: " + statusCode);
                    String errorContent = "";
                    if (response.getEntity() != null) {
                        errorContent = EntityUtils.toString(response.getEntity(), "UTF-8");
                        System.err.println("Detalle: " + errorContent);
                    }

                    throw new IOException("Error al descargar el informe. Código: " + statusCode +
                            " - " + response.getStatusLine().getReasonPhrase() +
                            "\nDetalle: " + errorContent);
                }
            }
        }
    }

    //ENDPOINT PARA DESCARGAR LOS SOPORTES DISPONIBLES PARA UNA ADMISION
    public List<Map<String, Object>> obtenerSoportesDisponibles(Long idAdmision) throws SQLException {

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
                    return resultados;
                }
            }
        }
    }

    //ENDPOINT QUE DESCARGA PDFS DE REPORTING SERVICE Y LOS INSERTA
    public Long insertarSoporte(Long idAdmision, Long idPacienteKey, Long idSoporteKey,
                                String tipoDocumento, String nombreSoporte) 
            throws SQLException, IOException {
        
        System.out.println("========== DATOS RECIBIDOS ==========");
        System.out.println("idAdmision: " + idAdmision);
        System.out.println("idPacienteKey: " + idPacienteKey);
        System.out.println("idSoporteKey: " + idSoporteKey);
        System.out.println("tipoDocumento: " + tipoDocumento);
        System.out.println("nombreSoporte: " + nombreSoporte);
        System.out.println("======================================");

        if (nombreSoporte == null || nombreSoporte.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre del soporte es requerido.");
        }

        String urlBase = null;

        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoft100_ST"))) {
            String sql = "SELECT ValorParametro FROM ParametrosServidor WHERE NomParametro = 'URLReportServerWS'";
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    urlBase = rs.getString("ValorParametro");
                } else {
                    throw new IllegalStateException("No se encontró la URL del servidor de reportes.");
                }
            }
        }

        if (urlBase == null || urlBase.trim().isEmpty()) {
            throw new IllegalStateException("No se encontró la URL del servidor de reportes.");
        }

        try (CloseableHttpClient httpClient = servidorUtil.crearHttpClientConNTLM()) {

            String reportUrl = urlBase + "?" + nombreSoporte + "&IdAdmision=" + idAdmision + "&rs:Format=PDF";
            System.out.println("URL: " + reportUrl);

            HttpGet request = new HttpGet(reportUrl);

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                int statusCode = response.getStatusLine().getStatusCode();

                if (statusCode == 200) {
                    byte[] pdfBytes = EntityUtils.toByteArray(response.getEntity());

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
                            
                            ResultSet rs = ps.executeQuery();
                            if (rs.next()) {
                                long idGenerado = rs.getLong("IdpdfKey");
                                System.out.println("PDF insertado con ID: " + idGenerado);
                                return idGenerado;
                            }
                        }
                    }

                    throw new SQLException("No se pudo obtener el ID generado del PDF");

                } else {
                    String errorContent = "";
                    if (response.getEntity() != null) {
                        errorContent = EntityUtils.toString(response.getEntity(), "UTF-8");
                    }

                    throw new IOException("Error al descargar el informe. Código: " + statusCode +
                            " - " + response.getStatusLine().getReasonPhrase() +
                            "\nDetalle: " + errorContent);
                }
            }
        }
    }
}
