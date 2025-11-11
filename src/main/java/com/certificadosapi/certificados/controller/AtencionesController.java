package com.certificadosapi.certificados.controller;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Connection;

import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

import java.util.ArrayList;
import java.util.Collections;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
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
import com.certificadosapi.certificados.service.atenciones.GenerarService;
import com.certificadosapi.certificados.service.atenciones.VerService;
import com.certificadosapi.certificados.dto.PdfDocumento;
import com.certificadosapi.certificados.dto.XmlDocumento;
import com.certificadosapi.certificados.util.ServidorUtil;

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;

@RestController
@RequestMapping("/api")
public class AtencionesController {

    private ExportarService exportarService;
    private VerService verService;
    private GenerarService generarService;

    @Autowired
    public AtencionesController(ServidorUtil servidorUtil, ExportarService exportarService, VerService verService, GenerarService generarService){

        this.generarService = generarService;
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









    
    //ENDPOINT PARA DESCARGAR LOS SOPORTES DISPONIBLES PARA UNA ADMISION
    @GetMapping("/soportes-disponibles")
    public ResponseEntity<List<Map<String, Object>>> obtenerSoportesDisponibles(
            @RequestParam Long idAdmision
    ) throws SQLException {
        
        List<Map<String, Object>> soportes = generarService.obtenerSoportesDisponibles(idAdmision);
        
        return ResponseEntity.ok(soportes);
    }

    //ENDPOINT PARA GENERAR APOYO DIAGNOSTICO (IMAGENES A PDF)
    @GetMapping("/generar-apoyo-diagnostico")
    public ResponseEntity<byte[]> generarPdfApoyoDiagnostico(
            @RequestParam int idAdmision,
            @RequestParam int idPacienteKey
    ) throws SQLException, IOException {
        
        byte[] pdfBytes = generarService.generarPdfApoyoDiagnostico(idAdmision, idPacienteKey);
        
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    //ENDPOINT QUE DESCARGA PDFS DE REPORTING SERVICE Y LOS INSERTA
    @GetMapping("/insertar-soportes")
    public ResponseEntity<String> insertarSoporte(
            @RequestParam Long idAdmision,
            @RequestParam Long idPacienteKey,
            @RequestParam Long idSoporteKey,
            @RequestParam String tipoDocumento,
            @RequestParam String nombreSoporte
    ) throws SQLException, IOException {
        
        Long idGenerado = generarService.insertarSoporte(
            idAdmision,
            idPacienteKey,
            idSoporteKey,
            tipoDocumento,
            nombreSoporte
        );
        
        return ResponseEntity.ok("PDF insertado correctamente con ID: " + idGenerado);
    }

    //ENDPOINT PARA DESCARGAR LA FACTURA DE VENTA
    @GetMapping("/descargar-factura-venta")
    public ResponseEntity<String> descargarFacturaVenta(
            @RequestParam Long idAdmision,
            @RequestParam Long idPacienteKey,
            @RequestParam Long idSoporteKey,
            @RequestParam String tipoDocumento
    ) throws SQLException, IOException {
        
        Long idGenerado = generarService.descargarFacturaVenta(
            idAdmision, 
            idPacienteKey, 
            idSoporteKey, 
            tipoDocumento
        );
        
        return ResponseEntity.ok("PDF factura insertado correctamente con ID: " + idGenerado);
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
