package com.certificadosapi.certificados.controller.firma;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


import com.certificadosapi.certificados.service.atenciones.GenerarService;
import com.certificadosapi.certificados.service.firma.FirmaService;


@RestController
@RequestMapping("/firma")
public class FirmaController {

    private final FirmaService firmaService;
    private static final Logger log = LoggerFactory.getLogger(GenerarService.class);

    public FirmaController(FirmaService firmaService) {
        this.firmaService = firmaService;
    }

    @GetMapping("/consultar-firma")
    public ResponseEntity<?> consultarAdmisionFirmas(
            @RequestParam String TipoDoc,
            @RequestParam String Identificacion,
            @RequestParam Integer IdAtencion) {
        try {
            List<Map<String, Object>> resultados = firmaService.consultarAdmisionFirmas(TipoDoc, Identificacion, IdAtencion);
            return ResponseEntity.ok(resultados);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/insertar-firma")
    public ResponseEntity<?> insertarAdmisionFirma(@RequestBody Map<String, Object> datos) {
        Map<String, Object> respuesta = new HashMap<>();
        try {
            firmaService.insertarAdmisionFirma(datos);
            respuesta.put("success", true);
            respuesta.put("message", "Firma registrada exitosamente");
        } catch (Exception e) {
            e.printStackTrace();
            respuesta.put("success", false);
            respuesta.put("message", "Error: " + e.getMessage());
        }
        return ResponseEntity.ok(respuesta);
    }

    @GetMapping("/ver-firma/{id}")
    public ResponseEntity<byte[]> verFirma(@PathVariable int id) {
        try {
            byte[] imagen = firmaService.verFirma(id);
            if (imagen != null) {
                return ResponseEntity.ok()
                        .header("Content-Type", "image/jpeg")
                        .body(imagen);
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/contar-pdf")
    public ResponseEntity<?> contarPdf(
            @RequestParam Long idAdmision,
            @RequestParam Long idSoporteKey) {
        try {
            int count = firmaService.contarPdf(idAdmision, idSoporteKey);
            return ResponseEntity.ok(Map.of("count", count));
        } catch (Exception e) {
            log.error("Error en contarPdf: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    @PostMapping("/descargar-insertar-pdf")
    public ResponseEntity<?> descargarEInsertarPdf(
            @RequestParam Long idAdmision,
            @RequestParam Long idPacienteKey,
            @RequestParam Long idSoporteKey,
            @RequestParam String tipoDocumento,
            @RequestParam(defaultValue = "false") boolean anexar) {
        try {
            Long idPdfKey = firmaService.descargarEInsertarPdf(idAdmision, idPacienteKey, idSoporteKey, tipoDocumento, anexar);
            return ResponseEntity.ok(Map.of(
                    "mensaje", anexar ? "PDF anexado exitosamente" : "PDF insertado exitosamente",
                    "idPdfKey", idPdfKey
            ));
        } catch (Exception e) {
            log.error("Error en descargarEInsertarPdf: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    @GetMapping("/obtener-tipos-identificacion")
    public ResponseEntity<?> obtenerTiposIdentificacion() {
        try {
            List<Map<String, Object>> tipos = firmaService.obtenerTiposIdentificacion();
            return ResponseEntity.ok(tipos);
        } catch (SQLException e) {
            log.error("Error en obtenerTiposIdentificacion: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }
}