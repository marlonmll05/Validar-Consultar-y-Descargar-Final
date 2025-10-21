package com.certificadosapi.certificados.controller;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
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


}
