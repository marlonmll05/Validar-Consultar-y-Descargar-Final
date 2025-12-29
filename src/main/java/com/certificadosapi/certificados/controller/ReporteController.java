package com.certificadosapi.certificados.controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.core.io.ByteArrayResource;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import com.certificadosapi.certificados.service.ReporteService;

import org.springframework.http.*;

@RestController
public class ReporteController {

    private final ReporteService reporteService;

    public ReporteController(ReporteService reporteService){
        this.reporteService = reporteService;
    }

    // ENDPOINT PARA DESCARGAR UN PDF DE SOPORTE
    @GetMapping("/descargar-pdf")
    public ResponseEntity<?> descargarPdf(
            @RequestParam String idAdmision,
            @RequestParam String nombreArchivo,
            @RequestParam String nombreSoporte) throws SQLException {

        byte[] pdfBytes = reporteService.descargarPdf(idAdmision, nombreArchivo, nombreSoporte);
        
        ByteArrayResource resource = new ByteArrayResource(pdfBytes);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=" + nombreArchivo + ".pdf")
                .body(resource);
    }

    // ENDPOINT PARA OBTENER LOS DOCUMENTOS DE SOPORTE
    @GetMapping("/soporte")
    public ResponseEntity<List<Map<String, Object>>> obtenerDocumentosSoporte() {
        List<Map<String, Object>> documentos = reporteService.obtenerDocumentosSoporte();
        return ResponseEntity.ok(documentos);
    }

    // ENDPOINT PARA VALIDAR SI EXISTE UNA FACTURA
    @GetMapping("/facturas/existe")
    public ResponseEntity<?> existeFactura(
            @RequestParam String idAdmision,
            @RequestParam Integer idDoc
    ) {

        Map<String, Boolean> existe = reporteService.existeFactura(idAdmision, idDoc);

        return ResponseEntity.ok(existe);

    }


}
