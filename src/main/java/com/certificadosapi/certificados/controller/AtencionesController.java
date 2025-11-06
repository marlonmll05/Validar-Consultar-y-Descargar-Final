package com.certificadosapi.certificados.controller;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;

import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;



import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.certificadosapi.certificados.service.atenciones.ExportarService;
import com.certificadosapi.certificados.service.atenciones.VerService;
import com.certificadosapi.certificados.dto.PdfDocumento;
import com.certificadosapi.certificados.dto.XmlDocumento;
import com.certificadosapi.certificados.util.ServidorUtil;

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;

@RestController
@RequestMapping("/api")
public class AtencionesController {

    private ServidorUtil servidorUtil;
    private ExportarService exportarService;
    private VerService verService;

    @Autowired
    public AtencionesController(ServidorUtil servidorUtil, ExportarService exportarService, VerService verService){
        this.servidorUtil = servidorUtil;
        this.exportarService = exportarService;
        this.verService = verService;
    }


    private String getServerFromRegistry() throws Exception {
        String registryPath = "SOFTWARE\\VB and VBA Program Settings\\Asclepius\\Administrativo";
        String valueName = "Servidor";
        
        try {
            return Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, registryPath, valueName);
        } catch (Exception e) {
            throw new Exception("Error al leer el servidor desde el registro", e);
        }
    }

    //ENDPOINT PARA LLENAR LOS SELECTS DE LOS FILTROS DE BUSQUEDA
    @GetMapping("/selects-filtro")
    public ResponseEntity<?> obtenerTablas(
            @RequestParam int idTabla,
            @RequestParam(defaultValue = "-1") int id
    ) {
        try {
            String servidor = getServerFromRegistry();
            String connectionUrl = String.format(
                "jdbc:sqlserver://%s;databaseName=IPSoft100_ST;user=ConexionApi;password=ApiConexion.77;encrypt=true;trustServerCertificate=true;sslProtocol=TLSv1;",
                servidor
            );

            try (Connection conn = DriverManager.getConnection(connectionUrl)) {
                String sql = "EXEC dbo.pa_Net_Facturas_Tablas ?, ?";

                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, idTabla);
                    ps.setInt(2, id);

                    try (ResultSet rs = ps.executeQuery()) {
                        List<Map<String, Object>> resultados = new ArrayList<>();
                        ResultSetMetaData meta = rs.getMetaData();
                        int colCount = meta.getColumnCount();

                        while (rs.next()) {
                            Map<String, Object> fila = new LinkedHashMap<>();
                            for (int i = 1; i <= colCount; i++) {
                                String colName = meta.getColumnName(i);
                                Object value = rs.getObject(i);
                                fila.put(colName, (value instanceof String) ? value.toString().trim() : value);
                            }
                            resultados.add(fila);
                        }

                        return ResponseEntity.ok(resultados);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al ejecutar pa_Net_Facturas_Tablas: " + e.getMessage());
        }
    }

    //ENDPOINT PARA VER TODAS LAS TIPIFICACIONES DE ANEXOS
    @GetMapping("/soportes-anexos-completo")
    public ResponseEntity<?> obtenerDocumentosSoporteSinFiltros() {
        try {
            String servidor = getServerFromRegistry();
            String connectionUrl = String.format(
                "jdbc:sqlserver://%s;databaseName=IPSoft100_ST;user=ConexionApi;password=ApiConexion.77;encrypt=true;trustServerCertificate=true;sslProtocol=TLSv1;",
                servidor
            );

            try (Connection conn = DriverManager.getConnection(connectionUrl)) {
                String sql = "EXEC pa_Net_Facturas_Tablas ?, ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, 8);  
                    stmt.setInt(2, -1);  

                    try (ResultSet rs = stmt.executeQuery()) {
                        List<Map<String, Object>> resultados = new ArrayList<>();
                        while (rs.next()) {
                            Map<String, Object> fila = new LinkedHashMap<>();
                            fila.put("Id", rs.getInt("Id"));
                            fila.put("nombreDocSoporte", rs.getString("NombreDocSoporte"));
                            fila.put("nombreRptService", rs.getString("NombreRptService"));
                            fila.put("TipoDocumento", rs.getInt("TipoDocumento"));
                            resultados.add(fila);
                        }
                        return ResponseEntity.ok(resultados);
                    }
                }
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error ejecutando procedimiento: " + e.getMessage());
        }
    }

    //ENDPOINT PARA VER LOS SOPORTES DE ANEXOS
    @GetMapping("/soportes-anexos")
    public ResponseEntity<?> obtenerDocumentosSoporte() {
        try {
            String servidor = getServerFromRegistry();
            String connectionUrl = String.format(
                "jdbc:sqlserver://%s;databaseName=IPSoft100_ST;user=ConexionApi;password=ApiConexion.77;encrypt=true;trustServerCertificate=true;sslProtocol=TLSv1;",
                servidor
            );

            try (Connection conn = DriverManager.getConnection(connectionUrl)) {
                String sql = "EXEC pa_Net_Facturas_Tablas ?, ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, 9);  
                    stmt.setInt(2, -1);  

                    try (ResultSet rs = stmt.executeQuery()) {
                        List<Map<String, Object>> resultados = new ArrayList<>();
                        while (rs.next()) {
                            Map<String, Object> fila = new LinkedHashMap<>();
                            fila.put("Id", rs.getInt("Id"));
                            fila.put("nombreDocSoporte", rs.getString("NombreDocSoporte"));
                            fila.put("nombreRptService", rs.getString("NombreRptService"));
                            fila.put("TipoDocumento", rs.getInt("TipoDocumento"));
                            resultados.add(fila);
                        }
                        return ResponseEntity.ok(resultados);
                    }
                }
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error ejecutando procedimiento: " + e.getMessage());
        }
    }

    //ENDPOINT PARA DESCARGAR LOS SOPORTES DISPONIBLES PARA UNA ADMISION
    @GetMapping("/soportes-disponibles")
    public ResponseEntity<?> obtenerSoportesFiltrados(@RequestParam Long idAdmision) {

        try {
            String servidor = getServerFromRegistry();
            String connectionUrl = String.format(
                "jdbc:sqlserver://%s;databaseName=IPSoft100_ST;user=ConexionApi;password=ApiConexion.77;encrypt=true;trustServerCertificate=true;sslProtocol=TLSv1;",
                servidor
            );

            try (Connection conn = DriverManager.getConnection(connectionUrl)) {
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
                        return ResponseEntity.ok(resultados);
                    }
                }
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error ejecutando procedimiento: " + e.getMessage());
        }
    }

    //ENDPOINT PARA VERIFICAR SI HAY FACTURA DE VENTA
    @GetMapping("/verificar-factura-venta")
    public ResponseEntity<?> countSoporte18(@RequestParam Long idAdmision) {
        try {
            String servidor = getServerFromRegistry();
            String connectionUrl = String.format(
                "jdbc:sqlserver://%s;databaseName=Asclepius_Documentos;user=ConexionApi;password=ApiConexion.77;encrypt=true;trustServerCertificate=true;sslProtocol=TLSv1.2;",
                servidor
            );

            String sql = "SELECT COUNT(*) AS Cantidad " +
                        "FROM dbo.tbl_Net_Facturas_ListaPdf " +
                        "WHERE IdAdmision = ? AND IdSoporteKey = 18";

            try (Connection conn = DriverManager.getConnection(connectionUrl);
                PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setLong(1, idAdmision);

                try (ResultSet rs = ps.executeQuery()) {
                    int cantidad = 0;
                    if (rs.next()) cantidad = rs.getInt("Cantidad");
                    return ResponseEntity.ok(Map.of("cantidad", cantidad));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Error al contar: " + e.getMessage());
        }
    }


    //ENDPOINT PARA VERIFICAR SI HAY SOPORTE INSERTADO PARA UN IDSOPORTE EN ESPECIFICO
    @GetMapping("/soportes-por-anexos")
    public ResponseEntity<?> obtenerAnexosPorAdmision(@RequestParam Long idAdmision) {
        try {
            String servidor = getServerFromRegistry();
            String connectionUrl = String.format(
                "jdbc:sqlserver://%s;databaseName=Asclepius_Documentos;user=ConexionApi;password=ApiConexion.77;encrypt=true;trustServerCertificate=true;sslProtocol=TLSv1.2;",
                servidor
            );

            String sql = "SELECT IdSoporteKey FROM tbl_Net_Facturas_ListaPdf PDF INNER JOIN IPSoft100_ST.dbo.tbl_Net_Facturas_DocSoporte DS ON PDF.IdSoporteKey = DS.Id \n" + //
                                "WHERE IdAdmision = ?\n" + //
                                "ORDER BY IdSoporteKey";

            try (Connection conn = DriverManager.getConnection(connectionUrl);
                PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setLong(1, idAdmision);

                try (ResultSet rs = ps.executeQuery()) {
                    List<Long> anexos = new ArrayList<>();
                    while (rs.next()) {
                        anexos.add(rs.getLong("IdSoporteKey"));
                    }

                    if (anexos.isEmpty()) {
                        return ResponseEntity.ok(Collections.emptyList());
                    }
                    return ResponseEntity.ok(anexos);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body("Error al obtener anexos: " + e.getMessage());
        }
    }

    //ENDPOINT PARA INSERTAR PDFS
    @PostMapping("/insertar-pdf")
    public ResponseEntity<?> insertListaPdf(
            @RequestParam Long idAdmision,
            @RequestParam Long idPacienteKey,
            @RequestParam Long idSoporteKey,
            @RequestParam String tipoDocumento,
            @RequestParam("nameFilePdf") List<MultipartFile> files,
            @RequestParam(defaultValue = "true") boolean eliminarSiNo,
            @RequestParam(defaultValue = "false") boolean automatico
    ) {
        if (files == null || files.isEmpty()) {
            return ResponseEntity.badRequest().body("No se enviaron archivos.");
        }

        try {
            for (MultipartFile f : files) {
                String ct = f.getContentType();
                if (ct == null || !ct.equalsIgnoreCase("application/pdf")) {
                    return ResponseEntity.badRequest().body("Todos los archivos deben ser PDF.");
                }
            }

            String servidor = getServerFromRegistry();
            String connectionUrl = String.format(
                "jdbc:sqlserver://%s;databaseName=Asclepius_Documentos;user=ConexionApi;password=ApiConexion.77;encrypt=true;trustServerCertificate=true;sslProtocol=TLSv1.2;",
                servidor
            );

            byte[] pdfFinal;

            try (Connection conn = DriverManager.getConnection(connectionUrl)) {
                PDFMergerUtility merger = new PDFMergerUtility();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                merger.setDestinationStream(baos);

                if (!eliminarSiNo) {
                    String sqlSelect = """
                        SELECT TOP 1 NameFilePdf 
                        FROM tbl_Net_Facturas_ListaPdf 
                        WHERE IdAdmision = ? AND IdSoporteKey = ? 
                        ORDER BY IdpdfKey DESC
                    """;
                    try (PreparedStatement psSel = conn.prepareStatement(sqlSelect)) {
                        psSel.setLong(1, idAdmision);
                        psSel.setLong(2, idSoporteKey);
                        try (ResultSet rsSel = psSel.executeQuery()) {
                            if (rsSel.next()) {
                                byte[] pdfExistente = rsSel.getBytes("NameFilePdf");
                                if (pdfExistente != null && pdfExistente.length > 0) {
                                    merger.addSource(new ByteArrayInputStream(pdfExistente));
                                }
                            }
                        }
                    }

                    eliminarSiNo = true;
                }

                for (MultipartFile f : files) {
                    merger.addSource(f.getInputStream());
                }

                merger.mergeDocuments(null);
                pdfFinal = baos.toByteArray();
            
            }

            String connectionUrlIPSoft = String.format(
            "jdbc:sqlserver://%s;databaseName=IPSoft100_ST;user=ConexionApi;password=ApiConexion.77;encrypt=true;trustServerCertificate=true;sslProtocol=TLSv1.2;",
            servidor
            );

            // Conectar a la base de datos IPSoft100_ST para ejecutar el procedimiento almacenado
            try (Connection connIPSoft = DriverManager.getConnection(connectionUrlIPSoft)) {
                String sql = "EXEC dbo.pa_Net_Insertar_DocumentoPdf ?, ?, ?, ?, ?, ?, ?, ?";
                try (PreparedStatement ps = connIPSoft.prepareStatement(sql)) {
                    ps.setLong(1, idAdmision);
                    ps.setLong(2, idPacienteKey);
                    ps.setLong(3, idSoporteKey);
                    ps.setBoolean(4, false);
                    ps.setString(5, tipoDocumento);
                    ps.setBinaryStream(6, new ByteArrayInputStream(pdfFinal), pdfFinal.length);
                    ps.setBoolean(7, eliminarSiNo);
                    ps.setBoolean(8, automatico);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            long idGenerado = rs.getLong("IdpdfKey");
                            System.out.println("PDF insertado con ID: " + idGenerado);
                        }
                    }
                }
            }

            return ResponseEntity.ok("Insert OK con PDF unificado");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al insertar: " + e.getMessage());
        }
    }

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




    //ENDPOINT PARA EXPORTAR EL CONTENIDO DE UNA ADMISION
    @GetMapping("/exportar-pdf")
    public ResponseEntity<?> exportarPdfIndividual(
            @RequestParam Long idAdmision,
            @RequestParam Long idSoporteKey) throws SQLException, IOException {
        
        PdfDocumento pdf = exportarService.exportarPdf(idAdmision, idSoporteKey);
        
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + pdf.getNombre() + "\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdf.getContenido());
    }

    //ENDPOINT PARA OBTENER EL CUV DE UNA FACTURA VALIDADA
    @GetMapping(value = "/rips/respuesta", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> obtenerRespuestaRips(@RequestParam String nFact) throws SQLException {
        String respuesta = exportarService.obtenerRespuestaRips(nFact);
        return ResponseEntity.ok(respuesta);
    }

    //ENDPOINT PARA OBTENER EL XML DE UNA FACTURA Y RENOMBRARLO
    @GetMapping("/generarxml/{nFact}")
    public ResponseEntity<byte[]> generarXml(@PathVariable String nFact) throws SQLException {
        XmlDocumento xml = exportarService.generarXml(nFact);
        
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + xml.getFileName() + "\"")
            .header("X-IdMovDoc", String.valueOf(xml.getIdMovDoc()))
            .contentType(MediaType.APPLICATION_XML)
            .body(xml.getContenido());
    }


    //ENDPOINT PARA VER LA LISTA DE PDFS INSERTADOS EN LA TABLA
    @GetMapping("/admisiones/lista-pdfs")
    public ResponseEntity<?> listaPdfs(@RequestParam Long idAdmision) throws SQLException {
        List<Map<String, Object>> lista = verService.listaPdfs(idAdmision);
        return ResponseEntity.ok(lista);
    }

    //ENDPOINT PARA VER EL CONTENIDO DE UN PDF
    @GetMapping("/admisiones/ver-pdf")
    public ResponseEntity<?> verPdf(
            @RequestParam Long idAdmision,
            @RequestParam Long idSoporteKey) throws SQLException {
        PdfDocumento pdf = verService.obtenerPdf(idAdmision, idSoporteKey);
        
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + pdf.getNombre() + "\"")
            .contentType(MediaType.parseMediaType(pdf.getContentType()))
            .body(pdf.getContenido());
    }

    //ENDPOINT PARA ELIMINAR PDFS MANUALMENTE
    @GetMapping("/eliminar-pdf")
    public ResponseEntity<?> eliminarPdf(
            @RequestParam Long idAdmision,
            @RequestParam Long idSoporteKey) throws SQLException {
        verService.eliminarPdf(idAdmision, idSoporteKey);
        return ResponseEntity.ok("Documento eliminado correctamente");
    }


    //ENDPOINT PARA ARMAR ZIP AL SELECCIONAR POR LOTE (1 ZIP POR ATENCION)
    @PostMapping(
        value = "/armar-zip/{nFact}",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
        produces = "application/zip"
    )
    public ResponseEntity<byte[]> armarZip(
            @PathVariable String nFact,
            @RequestPart("xml") MultipartFile xml,
            @RequestPart(value = "jsonFactura", required = false) MultipartFile jsonFactura,
            @RequestPart(value = "pdfs", required = false) List<MultipartFile> pdfs
    ) throws SQLException, IOException {

        byte[] zipBytes = exportarService.armarZip(nFact, xml, jsonFactura, pdfs);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(zipBytes);
    }

    //ENDPOINT PARA EXPORTAR POR CUENTA DE COBRO 
    @PostMapping(
        value = "/exportar-cuenta-cobro",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
        produces = "application/zip"
    )
    public ResponseEntity<byte[]> exportarCuentaCobro(
            @RequestParam String numeroCuentaCobro,
            @RequestParam MultiValueMap<String, MultipartFile> fileParts
    ) throws IOException {

        byte[] zipBytes = exportarService.exportarCuentaCobro(numeroCuentaCobro, fileParts);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(zipBytes);
    }

}
