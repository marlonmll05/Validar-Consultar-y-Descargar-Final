package com.certificadosapi.certificados.controller;


import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.http.HttpHeaders;
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

import com.certificadosapi.certificados.service.atenciones.AnexarService;
import com.certificadosapi.certificados.service.atenciones.ConsultaService;
import com.certificadosapi.certificados.service.atenciones.ExportarService;
import com.certificadosapi.certificados.service.atenciones.GenerarService;
import com.certificadosapi.certificados.service.atenciones.VerService;
import com.certificadosapi.certificados.dto.PdfDocumento;
import com.certificadosapi.certificados.dto.XmlDocumento;
import com.certificadosapi.certificados.util.ServidorUtil;

@RestController
@RequestMapping("/api")
public class AtencionesController {

    private final ExportarService exportarService;
    private final VerService verService;
    private final GenerarService generarService;
    private final ConsultaService consultaService;
    private final AnexarService anexarService;

    @Autowired
    public AtencionesController(ServidorUtil servidorUtil, ExportarService exportarService, VerService verService, GenerarService generarService, ConsultaService consultaService, AnexarService anexarService){

        this.generarService = generarService;
        this.exportarService = exportarService;
        this.verService = verService;
        this.consultaService = consultaService;
        this.anexarService = anexarService;
    }

    //ENDPOINT PARA VALIDAR SI UNA CUENTA DE COBRO ESTA BIEN
    @GetMapping("/validar-cuenta")
    public ResponseEntity<String> validarCuentacobro (Integer cuentaCobro) throws SQLException {

        String resultados = consultaService.validarCuentacobro(cuentaCobro);

        return ResponseEntity.ok(resultados);
    } 

    //ENDPOINT PARA VER TODAS LAS TIPIFICACIONES DE ANEXOS
    @GetMapping("/soportes-anexos-completo")
    public ResponseEntity<List<Map<String, Object>>> obtenerDocumentosSoporteSinFiltros() throws SQLException {
        List<Map<String, Object>> resultados = anexarService.obtenerDocumentosSoporteSinFiltros();
        return ResponseEntity.ok(resultados);
    }

    //ENDPOINT PARA VER LOS SOPORTES DE ANEXOS
    @GetMapping("/soportes-anexos")
    public ResponseEntity<List<Map<String, Object>>> obtenerDocumentosSoporte() throws SQLException {
        List<Map<String, Object>> resultados = anexarService.obtenerDocumentosSoporte();
        return ResponseEntity.ok(resultados);
    }

    //ENDPOINT PARA INSERTAR PDFS
    @PostMapping("/insertar-pdf")
    public ResponseEntity<Map<String, Object>> insertarListaPdf(
            @RequestParam Long idAdmision,
            @RequestParam Long idPacienteKey,
            @RequestParam Long idSoporteKey,
            @RequestParam String tipoDocumento,
            @RequestParam("nameFilePdf") List<MultipartFile> files,
            @RequestParam(defaultValue = "true") boolean eliminarSiNo,
            @RequestParam(defaultValue = "false") boolean automatico
    ) throws SQLException, IOException {

        if (files == null || files.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "No se enviaron archivos."));
        }

        Long idGenerado = anexarService.insertarListaPdf(
                idAdmision,
                idPacienteKey,
                idSoporteKey,
                tipoDocumento,
                files,
                eliminarSiNo,
                automatico
        );

        return ResponseEntity.ok(Map.of(
                "mensaje", "PDF insertado exitosamente",
                "idPdfKey", idGenerado
        ));
    }

    //ENDPOINT PARA LLENAR LOS SELECTS DE LOS FILTROS DE BUSQUEDA
    @GetMapping("/selects-filtro")
    public ResponseEntity<List<Map<String, Object>>> obtenerTablas(
            @RequestParam int idTabla,
            @RequestParam int id) throws SQLException {
        
        List<Map<String, Object>> resultados = consultaService.obtenerTablas(idTabla, id);
        return ResponseEntity.ok(resultados);
    }

    //ENDPOINT PARA VERIFICAR SI HAY FACTURA DE VENTA
    @GetMapping("/verificar-factura-venta")
    public ResponseEntity<Map<String, Integer>> countSoporte18(@RequestParam Long idAdmision) throws SQLException {
        
        Map<String, Integer> cantidad = consultaService.countSoporte18(idAdmision);
        return ResponseEntity.ok(cantidad);
    }

    //ENDPOINT PARA VERIFICAR SI HAY SOPORTE INSERTADO PARA UN IDSOPORTE EN ESPECIFICO
    @GetMapping("/soportes-por-anexos")
    public ResponseEntity<List<Long>> obtenerAnexosPorAdmision(@RequestParam Long idAdmision) throws SQLException {
        
        List<Long> anexos = consultaService.obtenerAnexosPorAdmision(idAdmision);
        return ResponseEntity.ok(anexos);
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
            @RequestParam("numeroCuentaCobro") String numeroCuentaCobro,
            @RequestParam("incluirArchivos") boolean incluirArchivos,
            @RequestParam MultiValueMap<String, MultipartFile> fileParts
    ) throws IOException {

        byte[] zipBytes = exportarService.exportarCuentaCobro(numeroCuentaCobro, incluirArchivos, fileParts);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(zipBytes);
    }

}
