package com.certificadosapi.certificados.controller;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.http.config.Registry;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.NTCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.impl.auth.NTLMSchemeFactory;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartFile;

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;

@RestController
@RequestMapping("/api")
public class AtencionesController {

    private String getServerFromRegistry() throws Exception {
        String registryPath = "SOFTWARE\\VB and VBA Program Settings\\Asclepius\\Administrativo";
        String valueName = "Servidor";
        
        try {
            return Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, registryPath, valueName);
        } catch (Exception e) {
            throw new Exception("Error al leer el servidor desde el registro", e);
        }
    }

    @ControllerAdvice
    public class GlobalExceptionHandler {

        @ExceptionHandler(MethodArgumentTypeMismatchException.class)
        public ResponseEntity<String> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
            if (ex.getRequiredType() == LocalDate.class) {
                return ResponseEntity
                    .badRequest()
                    .body("⚠️ Formato de fecha inválido. Usa 'yyyy-MM-dd' sin espacios ni saltos de línea. Valor recibido: " + ex.getValue());
            }
            return ResponseEntity
                .badRequest()
                .body("Parámetro inválido: " + ex.getMessage());
        }
    }

    //ENDPOINT PARA BUSCAR LAS ADMISIONES (FILTROS)
    @GetMapping("/admisiones")
    public ResponseEntity<?> buscarAdmisiones(
        @RequestParam(required = false) Long IdAtencion,
        @RequestParam(required = false) String HistClinica,
        @RequestParam(required = false) Integer Cliente,
        @RequestParam(required = false) String NoContrato,
        @RequestParam(required = false) Integer IdAreaAtencion,
        @RequestParam(required = false) Integer IdUnidadAtencion,
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyyMMdd") LocalDate FechaDesde,
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyyMMdd") LocalDate FechaHasta,
        @RequestParam(required = false) String nFact,
        @RequestParam(required = false) Integer nCuentaCobro,
        @RequestParam(required = false) Boolean soloFacturados
    ) {
        try {

            System.out.printf(
                "➡️ Filtros recibidos: IdAtencion=%s, HistClinica=%s, Cliente=%s, NoContrato=%s, " +
                "IdAreaAtencion=%s, IdUnidadAtencion=%s, FechaDesde=%s, FechaHasta=%s, nFact=%s, nCuentaCobro=%s",
                IdAtencion, HistClinica, Cliente, NoContrato, IdAreaAtencion, IdUnidadAtencion, FechaDesde, FechaHasta, nFact, nCuentaCobro
            );

            String servidor = getServerFromRegistry();
            String connectionUrl = String.format(
                "jdbc:sqlserver://%s;databaseName=IPSoft100_ST;user=ConexionApi;password=ApiConexion.77;encrypt=true;trustServerCertificate=true;sslProtocol=TLSv1;",
                servidor
            );

            try (Connection conn = DriverManager.getConnection(connectionUrl)) {
                String sql = "EXEC dbo.pa_Net_Facturas_Historico_GenSoportes ?,?,?,?,?,?,?,?,?,?, ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setObject(1, IdAtencion);
                    stmt.setObject(2, HistClinica);
                    stmt.setObject(3, Cliente);
                    stmt.setObject(4, NoContrato);
                    stmt.setObject(5, IdAreaAtencion);
                    stmt.setObject(6, IdUnidadAtencion);
                    stmt.setObject(7, FechaDesde != null ? Date.valueOf(FechaDesde) : null);
                    stmt.setObject(8, FechaHasta != null ? Date.valueOf(FechaHasta) : null);
                    stmt.setObject(9, nFact);
                    stmt.setObject(10, nCuentaCobro);
                    stmt.setObject(11, soloFacturados);

                    try (ResultSet rs = stmt.executeQuery()) {
                        List<Map<String, Object>> resultados = new ArrayList<>();
                        ResultSetMetaData meta = rs.getMetaData();
                        int colCount = meta.getColumnCount();

                        while (rs.next()) {
                            Map<String, Object> fila = new LinkedHashMap<>();
                            for (int i = 1; i <= colCount; i++) {
                                String colName = meta.getColumnName(i);
                                Object value = rs.getObject(i);
                                fila.put(colName, value);
                            }
                            resultados.add(fila);
                        }
                        return ResponseEntity.ok(resultados);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
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
                "jdbc:sqlserver://%s;databaseName=IPSoft100_ST;user=ConexionApi;password=ApiConexion.77;encrypt=true;trustServerCertificate=true;sslProtocol=TLSv1.2;",
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
                "jdbc:sqlserver://%s;databaseName=IPSoft100_ST;user=ConexionApi;password=ApiConexion.77;encrypt=true;trustServerCertificate=true;sslProtocol=TLSv1.2;",
                servidor
            );

            String sql = "SELECT IdSoporteKey FROM tbl_Net_Facturas_ListaPdf PDF INNER JOIN tbl_Net_Facturas_DocSoporte DS ON PDF.IdSoporteKey = DS.Id \n" + //
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

    //ENDPOINT PARA VER LA LISTA DE PDFS INSERTADOS EN LA TABLA
    @GetMapping("/admisiones/lista-pdfs")
    public ResponseEntity<?> listaPdfs(@RequestParam Long idAdmision) {
        try {
            String servidor = getServerFromRegistry();
            String connectionUrl = String.format(
                "jdbc:sqlserver://%s;databaseName=IPSoft100_ST;user=ConexionApi;password=ApiConexion.77;encrypt=true;trustServerCertificate=true;sslProtocol=TLSv1.2;",
                servidor
            );

            String sql = """
                SELECT l.IdSoporteKey,
                    dbo.fn_Net_DocSoporte_NameFile(?, l.IdSoporteKey) AS Nombre
                FROM tbl_Net_Facturas_ListaPdf l
                WHERE l.IdAdmision = ?
                ORDER BY l.IdSoporteKey
            """;

            try (Connection conn = DriverManager.getConnection(connectionUrl);
                PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, idAdmision);
                ps.setLong(2, idAdmision);
                try (ResultSet rs = ps.executeQuery()) {
                    List<Map<String,Object>> out = new ArrayList<>();
                    while (rs.next()) {
                        Map<String,Object> fila = new LinkedHashMap<>();
                        fila.put("idSoporteKey", rs.getLong("IdSoporteKey"));
                        fila.put("nombre", rs.getString("Nombre"));
                        out.add(fila);
                    }
                    if (out.isEmpty()) {
                        return ResponseEntity.badRequest()
                            .body("No hay documentos para la admisión " + idAdmision);
                    }
                    return ResponseEntity.ok(out);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Error al listar: " + e.getMessage());
        }
    }

    //ENDPOINT PARA VER EL CONTENIDO DE UN PDF
    @GetMapping("/admisiones/ver-pdf")
    public ResponseEntity<?> verPdf(
            @RequestParam Long idAdmision,
            @RequestParam Long idSoporteKey) {
        try {
            String servidor = getServerFromRegistry();
            String connectionUrl = String.format(
                "jdbc:sqlserver://%s;databaseName=IPSoft100_ST;user=ConexionApi;password=ApiConexion.77;encrypt=true;trustServerCertificate=true;sslProtocol=TLSv1.2;",
                servidor
            );

            String sql = """
                SELECT NameFilePdf,
                    dbo.fn_Net_DocSoporte_NameFile(?, ?) AS Nombre
                FROM tbl_Net_Facturas_ListaPdf
                WHERE IdAdmision = ? AND IdSoporteKey = ?
            """;

            try (Connection conn = DriverManager.getConnection(connectionUrl);
                PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, idAdmision);
                ps.setLong(2, idSoporteKey);
                ps.setLong(3, idAdmision);
                ps.setLong(4, idSoporteKey);

                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return ResponseEntity.badRequest().body("Documento no encontrado.");
                    }

                    byte[] data = rs.getBytes("NameFilePdf");
                    if (data == null || data.length == 0) {
                        return ResponseEntity.badRequest().body("Contenido vacío del documento.");
                    }

                    String nombre = rs.getString("Nombre");
                    if (nombre == null || nombre.isBlank()) {
                        nombre = "Documento_" + idSoporteKey + ".pdf";
                    }

                    String contentType = detectarContentType(data);

                    return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + nombre + "\"")
                        .contentType(MediaType.parseMediaType(contentType))
                        .body(data);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Error al obtener documento: " + e.getMessage());
        }
    }

    // === Helper para detectar content-type por "magic numbers" ===
    private String detectarContentType(byte[] data) {
        if (data != null && data.length >= 4) {
            // %PDF
            if ((data[0] & 0xFF) == 0x25 && data[1] == 0x50 && data[2] == 0x44 && data[3] == 0x46) {
                return "application/pdf";
            }
            // PNG
            if ((data[0] & 0xFF) == 0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47) {
                return "image/png";
            }
            // JPG
            if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8) {
                return "image/jpeg";
            }
            // GIF
            if (data[0] == 'G' && data[1] == 'I' && data[2] == 'F') {
                return "image/gif";
            }
            // WEBP (RIFF....WEBP)
            if (data.length >= 12 &&
                data[0]=='R' && data[1]=='I' && data[2]=='F' && data[3]=='F' &&
                data[8]=='W' && data[9]=='E' && data[10]=='B' && data[11]=='P') {
                return "image/webp";
            }
        }
        // Por defecto, PDF
        return "application/pdf";
    }

    //ENDPOINT PARA ELIMINAR PDFS MANUALMENTE
    @GetMapping("/eliminar-pdf")
    public ResponseEntity<?> eliminarPdf(
            @RequestParam Long idAdmision,
            @RequestParam Long idSoporteKey) {
        try {
            String servidor = getServerFromRegistry();
            String connectionUrl = String.format(
                "jdbc:sqlserver://%s;databaseName=IPSoft100_ST;user=ConexionApi;password=ApiConexion.77;encrypt=true;trustServerCertificate=true;sslProtocol=TLSv1.2;",
                servidor
            );

            String sql = "EXEC [dbo].[pa_Net_Eliminar_DocumentoPdf] @IdAdmision = ?, @IdSoporteKey = ?";

            try (Connection conn = DriverManager.getConnection(connectionUrl);
                PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setLong(1, idAdmision);
                ps.setLong(2, idSoporteKey);
                ps.execute();

                return ResponseEntity.ok("Documento eliminado correctamente");
            }

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                .body("Error al ejecutar eliminación: " + e.getMessage());
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
                "jdbc:sqlserver://%s;databaseName=IPSoft100_ST;user=ConexionApi;password=ApiConexion.77;encrypt=true;trustServerCertificate=true;sslProtocol=TLSv1.2;",
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

                String sql = "EXEC dbo.pa_Net_Insertar_DocumentoPdf ?, ?, ?, ?, ?, ?, ?, ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
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

        String dominio = "servergihos";
        String usuario = "Consulta";
        String contrasena = "Informes.01";

        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        NTCredentials ntCredentials = new NTCredentials(usuario, contrasena, null, dominio);
        credentialsProvider.setCredentials(AuthScope.ANY, ntCredentials);

        Registry<AuthSchemeProvider> authSchemeRegistry = RegistryBuilder.<AuthSchemeProvider>create()
                .register("NTLM", new NTLMSchemeFactory())
                .build();

        RequestConfig requestConfig = RequestConfig.custom()
                .setAuthenticationEnabled(true)
                .setTargetPreferredAuthSchemes(Arrays.asList("NTLM"))
                .setProxyPreferredAuthSchemes(Arrays.asList("NTLM"))
                .build();

        HttpClientBuilder clientBuilder = HttpClients.custom()
                .setDefaultCredentialsProvider(credentialsProvider)
                .setDefaultAuthSchemeRegistry(authSchemeRegistry)
                .setDefaultRequestConfig(requestConfig);

        try (CloseableHttpClient httpClient = clientBuilder.build()) {

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
            System.out.println("🔍 Obteniendo servidor del registro...");
            servidor = getServerFromRegistry();
            System.out.println("✅ Servidor obtenido: " + servidor);
            
            String connectionUrl = String.format(
                "jdbc:sqlserver://%s;databaseName=IPSoft100_ST;user=ConexionApi;password=ApiConexion.77;encrypt=true;trustServerCertificate=true;sslProtocol=TLSv1.2;",
                servidor
            );

            System.out.println("🔍 Conectando a BD para obtener URLReportServerWS...");
            try (Connection conn = DriverManager.getConnection(connectionUrl)) {
                String sql = "SELECT ValorParametro FROM ParametrosServidor WHERE NomParametro = 'URLReportServerWS'";
                try (PreparedStatement stmt = conn.prepareStatement(sql);
                    ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        urlBase = rs.getString("ValorParametro");
                        System.out.println("✅ URL Base obtenida: " + urlBase);
                    } else {
                        System.err.println("❌ ERROR: No se encontró URLReportServerWS");
                        return ResponseEntity.internalServerError().body("No se encontró la URL del servidor de reportes.");
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("❌ ERROR al obtener URL del servidor: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Error al obtener la URL del servidor: " + e.getMessage());
        }

        if (urlBase == null || urlBase.trim().isEmpty()) {
            System.err.println("❌ ERROR: urlBase vacía");
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
                            System.err.println("❌ ERROR: Proc no retornó resultados");
                            return ResponseEntity.internalServerError()
                                    .body("No se obtuvieron resultados del procedimiento almacenado.");
                        }
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("❌ ERROR al ejecutar proc: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body("Error al ejecutar el procedimiento almacenado: " + e.getMessage());
        }

        if (rutaReporte == null || idMovDoc == null) {
            System.err.println("❌ ERROR: rutaReporte o idMovDoc null");
            return ResponseEntity.internalServerError()
                    .body("No se pudo obtener RutaReporte o IdMovDoc del procedimiento almacenado.");
        }

        System.out.println("Configurando NTLM...");
        String dominio = "servergihos";
        String usuario = "Consulta";
        String contrasena = "Informes.01";

        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        NTCredentials ntCredentials = new NTCredentials(usuario, contrasena, null, dominio);
        credentialsProvider.setCredentials(AuthScope.ANY, ntCredentials);

        Registry<AuthSchemeProvider> authSchemeRegistry = RegistryBuilder.<AuthSchemeProvider>create()
                .register("NTLM", new NTLMSchemeFactory())
                .build();

        RequestConfig requestConfig = RequestConfig.custom()
                .setAuthenticationEnabled(true)
                .setTargetPreferredAuthSchemes(Arrays.asList("NTLM"))
                .setProxyPreferredAuthSchemes(Arrays.asList("NTLM"))
                .build();

        HttpClientBuilder clientBuilder = HttpClients.custom()
                .setDefaultCredentialsProvider(credentialsProvider)
                .setDefaultAuthSchemeRegistry(authSchemeRegistry)
                .setDefaultRequestConfig(requestConfig);

        try (CloseableHttpClient httpClient = clientBuilder.build()) {

            System.out.println("\n========== DESCARGANDO PDF ==========");
            String reportUrl = urlBase + "?" + rutaReporte + "&IdMovDoc=" + idMovDoc + "&rs:Format=PDF";
            System.out.println("URL: " + reportUrl);

            try {
                URI uri = new URI(reportUrl.replace(" ", "%20"));
                reportUrl = uri.toString();
                System.out.println("URL codificada: " + reportUrl);
            } catch (Exception ex) {
                System.err.println("❌ ERROR al codificar URL: " + ex.getMessage());
            }

            HttpGet request = new HttpGet(reportUrl);

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                int statusCode = response.getStatusLine().getStatusCode();
                System.out.println("📥 Status: " + statusCode);

                if (statusCode == 200) {
                    byte[] pdfBytes = EntityUtils.toByteArray(response.getEntity());
                    System.out.println("📄 Tamaño: " + pdfBytes.length + " bytes");

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
                            
                            System.out.println("💾 Insertando en BD...");
                            ResultSet rs = ps.executeQuery();
                            if (rs.next()) {
                                long idGenerado = rs.getLong("IdpdfKey");
                                System.out.println("✅ PDF insertado con ID: " + idGenerado);
                                return ResponseEntity.ok("PDF factura insertado correctamente con ID: " + idGenerado);
                            }
                        }

                    } catch (SQLException ex) {
                        System.err.println("❌ ERROR al insertar: " + ex.getMessage());
                        ex.printStackTrace();
                        return ResponseEntity
                                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body("Error al insertar en la base de datos: " + ex.getMessage());
                    }

                    return ResponseEntity.ok("PDF descargado correctamente");

                } else {
                    System.err.println("❌ ERROR al descargar. Código: " + statusCode);
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
            System.err.println("❌ ERROR GENERAL: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body("Excepción al conectar o procesar el PDF: " + e.getMessage());
        }
    }


    //ENDPOINT PARA EXPORTAR EL CONTENIDO DE UNA ADMISION
    @GetMapping("/exportar-pdf")
    public ResponseEntity<?> exportarPdfIndividual(
            @RequestParam Long idAdmision,
            @RequestParam Long idSoporteKey) {
        System.out.println("Exportando PDF para IdAdmision=" + idAdmision + ", IdSoporteKey=" + idSoporteKey);

        try {
            String servidor = getServerFromRegistry();
            String connectionUrl = String.format(
                "jdbc:sqlserver://%s;databaseName=IPSoft100_ST;user=ConexionApi;password=ApiConexion.77;encrypt=true;trustServerCertificate=true;sslProtocol=TLSv1.2;",
                servidor
            );

            String sql = "SELECT NameFilePdf FROM tbl_Net_Facturas_ListaPdf WHERE IdAdmision = ? AND IdSoporteKey = ?";

            try (Connection conn = DriverManager.getConnection(connectionUrl);
                PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setLong(1, idAdmision);
                ps.setLong(2, idSoporteKey);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        try (InputStream is = rs.getBinaryStream("NameFilePdf")) {
                            if (is == null) {
                                System.out.println("NameFilePdf NULL para soporte=" + idSoporteKey + " → se salta");
                                return ResponseEntity.noContent().build();
                            }

                            byte[] pdfBytes = is.readAllBytes();
                            if (pdfBytes.length == 0) {
                                System.out.println("NameFilePdf vacío (0 bytes) soporte=" + idSoporteKey + " → se salta");
                                return ResponseEntity.noContent().build();
                            }

                            // Obtener nombre real
                            String nombrePdf = "Documento_" + idSoporteKey + ".pdf";
                            try (PreparedStatement psName = conn.prepareStatement(
                                    "SELECT dbo.fn_Net_DocSoporte_NameFile(?, ?) AS Nombre")) {
                                psName.setLong(1, idAdmision);
                                psName.setLong(2, idSoporteKey);
                                try (ResultSet rsName = psName.executeQuery()) {
                                    if (rsName.next() && rsName.getString("Nombre") != null) {
                                        nombrePdf = rsName.getString("Nombre");
                                    }
                                }
                            }

                            System.out.println("PDF válido encontrado soporte=" + idSoporteKey);
                            return ResponseEntity.ok()
                                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombrePdf + "\"")
                                    .contentType(MediaType.APPLICATION_PDF)
                                    .body(pdfBytes);
                        }
                    } else {
                        System.out.println("No existe fila en la tabla para soporte=" + idSoporteKey);
                        return ResponseEntity.noContent().build();
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Error al exportar: " + e.getMessage());
        }
    }

    //ENDPOINT PARA OBTENER EL CUV DE UNA FACTURA VALIDADA
    @GetMapping(value = "/rips/respuesta", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> obtenerRespuestaRips(@RequestParam String nFact) throws Exception {
        
        final String servidor;
        try {
            servidor = getServerFromRegistry();
        } catch (Exception ex) {
            throw new RuntimeException("Error obteniendo servidor del registry", ex);
        }

        final String conn100 = String.format(
            "jdbc:sqlserver://%s;databaseName=IPSoft100_ST;user=ConexionApi;password=ApiConexion.77;encrypt=true;trustServerCertificate=true;sslProtocol=TLSv1.2;",
            servidor
        );

        final String sql =
            "SELECT TOP 1 MensajeRespuesta " +
            "FROM RIPS_RespuestaApi " +
            "WHERE LTRIM(RTRIM(NFact)) = LTRIM(RTRIM(?)) " +
            "ORDER BY 1 DESC";

        String respuestaValidador = null;

        try (Connection c = DriverManager.getConnection(conn100);
            PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, nFact);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    respuestaValidador = rs.getString("MensajeRespuesta");
                }
            }
        }

        if (respuestaValidador == null || respuestaValidador.isBlank()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(respuestaValidador);
    }

    //ENDPOINT PARA OBTENER EL XML DE UNA FACTURA Y RENOMBRARLO
    @GetMapping("/generarxml/{nFact}")
    public ResponseEntity<byte[]> generarXml(@PathVariable String nFact) {
        Connection conn = null;
        try {
            String servidor = getServerFromRegistry();
            String connectionUrl = String.format(
                "jdbc:sqlserver://%s;databaseName=IPSoft100_ST;user=ConexionApi;password=ApiConexion.77;encrypt=true;trustServerCertificate=true;sslProtocol=TLSv1.2;",
                servidor
            );

            String sql = "SELECT FF.NFact, CONVERT(xml, M.DocXml) AS DocXml, T.CUV, FF.IdMovDoc " +
                        "FROM IPSoft100_ST.dbo.FacturaFinal FF " +
                        "INNER JOIN IPSoft100_ST.dbo.Rips_Transaccion T ON T.IdMovDoc = FF.IdMovDoc " +
                        "INNER JOIN IPSoftFinanciero_ST.dbo.MovimientoDocumentos M ON M.IdMovDoc = FF.IdMovDoc " +
                        "WHERE FF.NFact = ?";

            conn = DriverManager.getConnection(connectionUrl);
            
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, nFact);

                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        System.out.println("No se encontró registro para nFact=" + nFact);
                        return ResponseEntity.notFound().build();
                    }

                    String docXml = rs.getString("DocXml");
                    int idMovDoc = rs.getInt("IdMovDoc");

                    if (docXml == null || docXml.trim().isEmpty()) {
                        System.out.println("No hay XML para nFact=" + nFact);
                        return ResponseEntity.noContent().build();
                    }

                    String connectionUrlFinanciero = String.format(
                        "jdbc:sqlserver://%s;databaseName=IPSoftFinanciero_ST;user=ConexionApi;password=ApiConexion.77;encrypt=true;trustServerCertificate=true;sslProtocol=TLSv1;",
                        servidor
                    );
                    Connection connFinanciero = DriverManager.getConnection(connectionUrlFinanciero);

                    String numdoc = "";
                    String docQuery = "SELECT Numdoc FROM MovimientoDocumentos WHERE IdMovDoc = ?";
                    try (PreparedStatement stmt = connFinanciero.prepareStatement(docQuery)) {
                        stmt.setInt(1, idMovDoc);
                        ResultSet rsDoc = stmt.executeQuery();
                        if (rsDoc.next()) {
                            numdoc = rsDoc.getString("Numdoc");
                        }
                    }

                    String IdEmpresaGrupo = "";
                    String empresaQuery = "SELECT IdEmpresaGrupo FROM MovimientoDocumentos as M INNER JOIN Empresas as E ON E.IdEmpresaKey = M.IdEmpresaKey WHERE IdMovDoc = ?";
                    try (PreparedStatement stmt = connFinanciero.prepareStatement(empresaQuery)) {
                        stmt.setInt(1, idMovDoc);
                        ResultSet rsEmpresa = stmt.executeQuery();
                        if (rsEmpresa.next()) {
                            IdEmpresaGrupo = rsEmpresa.getString("IdEmpresaGrupo");
                        }
                    }

                    connFinanciero.close();

                    String yearSuffix = String.valueOf(LocalDate.now().getYear()).substring(2);

                    String formattedNumdoc = String.format("%08d", Integer.parseInt(numdoc));

                    String fileName = "ad0" + IdEmpresaGrupo + "000" + yearSuffix + formattedNumdoc + ".xml";

                    byte[] xmlBytes = docXml.getBytes(StandardCharsets.UTF_8);

                    return ResponseEntity.ok()
                        .header("Content-Disposition", "attachment; filename=\"" + fileName + "\"")
                        .header("X-IdMovDoc", String.valueOf(idMovDoc))
                        .contentType(MediaType.APPLICATION_XML)
                        .body(xmlBytes);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(("Error al generar XML: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    

}
