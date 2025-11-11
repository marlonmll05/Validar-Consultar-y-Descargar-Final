package com.certificadosapi.certificados.service.atenciones;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

public class GenerarService {

    //ENDPOINT PARA GENERAR APOYO DIAGNOSTICO (IMAGENES A PDF)
    @GetMapping("/generar-apoyo-diagnostico")
    public ResponseEntity<?> generarPdfAnexos(
            @RequestParam int idAdmision,
            @RequestParam int idPacienteKey
    ) {
        System.out.println("Generando Apoyo diagnostico para idAdmision=" + idAdmision + ", idPacienteKey=" + idPacienteKey);

        String servidor;
        try {
            servidor = getServerFromRegistry();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al obtener servidor: " + e.getMessage());
        }

        String connectionUrl = String.format(
                "jdbc:sqlserver://%s;databaseName=IPSoft100_ST;user=ConexionApi;password=ApiConexion.77;encrypt=true;trustServerCertificate=true;sslProtocol=TLSv1.2;",
                servidor
        );

        String sql = "EXEC dbo.pa_HC_ResultadosAnexosPrintUnif ?, ?, '-1'";

        try (Connection conn = DriverManager.getConnection(connectionUrl);
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
                    return ResponseEntity
                            .status(HttpStatus.NO_CONTENT)
                            .body("No hay anexos disponibles para generar PDF");
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                doc.save(baos);
                doc.close();

                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_PDF)
                        .body(baos.toByteArray());
            }

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al generar PDF: " + e.getMessage());
        }
    }


    //ENDPOINT PARA DESCARGAR LA FACTURA DE VENTA
    @GetMapping("/descargar-factura-venta")
    public ResponseEntity<?> descargarPdfFactura(
            @RequestParam Long idAdmision,
            @RequestParam Long idPacienteKey,
            @RequestParam Long idSoporteKey,
            @RequestParam String tipoDocumento
    ) {
        System.out.println("========== DESCARGA PDF FACTURA (IdMovDoc) ==========");
        System.out.println("idAdmision: " + idAdmision);
        System.out.println("idPacienteKey: " + idPacienteKey);
        System.out.println("idSoporteKey: " + idSoporteKey);
        System.out.println("tipoDocumento: " + tipoDocumento);
        System.out.println("====================================================");

        String servidor = null;
        String urlBase = null;

        try {
            System.out.println("Obteniendo servidor del registro...");
            servidor = getServerFromRegistry();
            System.out.println("Servidor obtenido: " + servidor);
            
            String connectionUrl = String.format(
                "jdbc:sqlserver://%s;databaseName=IPSoft100_ST;user=ConexionApi;password=ApiConexion.77;encrypt=true;trustServerCertificate=true;sslProtocol=TLSv1.2;",
                servidor
            );

            System.out.println("Conectando a BD para obtener URLReportServerWS...");
            try (Connection conn = DriverManager.getConnection(connectionUrl)) {
                String sql = "SELECT ValorParametro FROM ParametrosServidor WHERE NomParametro = 'URLReportServerWS'";
                try (PreparedStatement stmt = conn.prepareStatement(sql);
                    ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        urlBase = rs.getString("ValorParametro");
                        System.out.println("URL Base obtenida: " + urlBase);
                    } else {
                        System.err.println("ERROR: No se encontró URLReportServerWS");
                        return ResponseEntity.internalServerError().body("No se encontró la URL del servidor de reportes.");
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("ERROR al obtener URL del servidor: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Error al obtener la URL del servidor: " + e.getMessage());
        }

        if (urlBase == null || urlBase.trim().isEmpty()) {
            System.err.println("ERROR: urlBase vacía");
            return ResponseEntity.internalServerError().body("No se encontró la URL del servidor de reportes.");
        }

        // ====== EJECUTAR PROCEDIMIENTO ALMACENADO ======
        String rutaReporte = null;
        Long idMovDoc = null;

        try {
            System.out.println("Ejecutando pa_Net_Facturas_Tablas...");
            String connectionUrl = String.format(
                "jdbc:sqlserver://%s;databaseName=IPSoft100_ST;user=ConexionApi;password=ApiConexion.77;encrypt=true;trustServerCertificate=true;sslProtocol=TLSv1.2;",
                servidor
            );

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
                            return ResponseEntity.internalServerError()
                                    .body("No se obtuvieron resultados del procedimiento almacenado.");
                        }
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("ERROR al ejecutar proc: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body("Error al ejecutar el procedimiento almacenado: " + e.getMessage());
        }

        if (rutaReporte == null || idMovDoc == null) {
            System.err.println("ERROR: rutaReporte o idMovDoc null");
            return ResponseEntity.internalServerError()
                    .body("No se pudo obtener RutaReporte o IdMovDoc del procedimiento almacenado.");
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

                    // ====== INSERTAR EN BD ======
                    String connectionUrl = String.format(
                        "jdbc:sqlserver://%s;databaseName=IPSoft100_ST;user=ConexionApi;password=ApiConexion.77;encrypt=true;trustServerCertificate=true;sslProtocol=TLSv1.2;",
                        servidor
                    );

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
                                return ResponseEntity.ok("PDF factura insertado correctamente con ID: " + idGenerado);
                            }
                        }

                    } catch (SQLException ex) {
                        System.err.println("ERROR al insertar: " + ex.getMessage());
                        ex.printStackTrace();
                        return ResponseEntity
                                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body("Error al insertar en la base de datos: " + ex.getMessage());
                    }

                    return ResponseEntity.ok("PDF descargado correctamente");

                } else {
                    System.err.println("ERROR al descargar. Código: " + statusCode);
                    String errorContent = "";
                    if (response.getEntity() != null) {
                        try {
                            errorContent = EntityUtils.toString(response.getEntity(), "UTF-8");
                            System.err.println("Detalle: " + errorContent);
                        } catch (Exception e) {
                            errorContent = "No se pudo leer error";
                        }
                    }

                    return ResponseEntity.status(statusCode)
                            .body("Error al descargar el informe. Código: " + statusCode +
                                    " - " + response.getStatusLine().getReasonPhrase() +
                                    "\nDetalle: " + errorContent);
                }
            }

        } catch (IOException e) {
            System.err.println("ERROR GENERAL: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body("Excepción al conectar o procesar el PDF: " + e.getMessage());
        }
    }

        //ENDPOINT QUE DESCARGA PDFS DE REPORTING SERVICE Y LOS INSERTA
    @GetMapping("/insertar-soportes")
    public ResponseEntity<?> descargarEInsertarPdf(
            @RequestParam Long idAdmision,
            @RequestParam Long idPacienteKey,
            @RequestParam Long idSoporteKey,
            @RequestParam String tipoDocumento,
            @RequestParam String nombreSoporte,
            @RequestParam(required = false, defaultValue = "anexo") String nombreArchivo
    ) {
        System.out.println("========== DATOS RECIBIDOS ==========");
        System.out.println("idAdmision: " + idAdmision);
        System.out.println("idPacienteKey: " + idPacienteKey);
        System.out.println("idSoporteKey: " + idSoporteKey);
        System.out.println("tipoDocumento: " + tipoDocumento);
        System.out.println("nombreSoporte: " + nombreSoporte);
        System.out.println("======================================");

        if (nombreSoporte == null || nombreSoporte.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("El nombre del soporte es requerido.");
        }

        String servidor = null;
        String urlBase = null;

        try {
            servidor = getServerFromRegistry();
            String connectionUrl = String.format(
                "jdbc:sqlserver://%s;databaseName=IPSoft100_ST;user=ConexionApi;password=ApiConexion.77;encrypt=true;trustServerCertificate=true;sslProtocol=TLSv1.2;",
                servidor
            );

            try (Connection conn = DriverManager.getConnection(connectionUrl)) {
                String sql = "SELECT ValorParametro FROM ParametrosServidor WHERE NomParametro = 'URLReportServerWS'";
                try (PreparedStatement stmt = conn.prepareStatement(sql);
                    ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        urlBase = rs.getString("ValorParametro");
                    } else {
                        return ResponseEntity.internalServerError().body("No se encontró la URL del servidor de reportes.");
                    }
                }
            }

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error al obtener la URL del servidor: " + e.getMessage());
        }

        if (urlBase == null || urlBase.trim().isEmpty()) {
            return ResponseEntity.internalServerError().body("No se encontró la URL del servidor de reportes.");
        }

        try (CloseableHttpClient httpClient = servidorUtil.crearHttpClientConNTLM()) {

            String reportUrl = urlBase + "?" + nombreSoporte + "&IdAdmision=" + idAdmision + "&rs:Format=PDF";
            System.out.println("URL: " + reportUrl);

            HttpGet request = new HttpGet(reportUrl);

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                int statusCode = response.getStatusLine().getStatusCode();

                if (statusCode == 200) {
                    byte[] pdfBytes = EntityUtils.toByteArray(response.getEntity());

                    String connectionUrl = String.format(
                        "jdbc:sqlserver://%s;databaseName=IPSoft100_ST;user=ConexionApi;password=ApiConexion.77;encrypt=true;trustServerCertificate=true;sslProtocol=TLSv1.2;",
                        servidor
                    );

                    try (Connection conn = DriverManager.getConnection(connectionUrl)) {
                        
                        String sql = "EXEC dbo.pa_Net_Insertar_DocumentoPdf ?, ?, ?, ?, ?, ?, ?, ?";
                        
                        try (PreparedStatement ps = conn.prepareStatement(sql)) {
                            ps.setLong(1, idAdmision);           // @IdAdmision
                            ps.setLong(2, idPacienteKey);        // @IdPacienteKey  
                            ps.setLong(3, idSoporteKey);         // @IdSoporteKey
                            ps.setBoolean(4, false);             // @Inactivar (0)
                            ps.setString(5, tipoDocumento);      // @TipoDocumento
                            ps.setBinaryStream(6, new ByteArrayInputStream(pdfBytes)); // @NameFilePdf
                            ps.setBoolean(7, false);      // @EliminarSiNo
                            ps.setBoolean(8, true); 
                            
                            ResultSet rs = ps.executeQuery();
                            if (rs.next()) {
                                long idGenerado = rs.getLong("IdpdfKey");
                                System.out.println("PDF insertado con ID: " + idGenerado);
                            }
                        }

                    } catch (SQLException ex) {
                        ex.printStackTrace();
                        return ResponseEntity
                                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body("Error al insertar en la base de datos: " + ex.getMessage());
                    }

                    return ResponseEntity.ok("PDF insertado correctamente en la base de datos");

                } else {
                    String errorContent = "";
                    if (response.getEntity() != null) {
                        try {
                            errorContent = EntityUtils.toString(response.getEntity(), "UTF-8");
                        } catch (Exception e) {
                            errorContent = "No se pudo leer el contenido del error: " + e.getMessage();
                        }
                    }

                    return ResponseEntity.status(statusCode)
                            .body("Error al descargar el informe. Código: " + statusCode +
                                    " - " + response.getStatusLine().getReasonPhrase() +
                                    "\nDetalle: " + errorContent);
                }
            }

        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body("Excepción al conectar o procesar el PDF: " + e.getMessage());
        }
    }

}
