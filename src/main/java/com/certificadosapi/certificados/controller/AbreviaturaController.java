package com.certificadosapi.certificados.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.certificadosapi.certificados.service.AbreviaturaService;

@RestController
@RequestMapping("/abreviatura")
public class AbreviaturaController {

    private final AbreviaturaService abreviaturaService;

    public AbreviaturaController(AbreviaturaService abreviaturaService){
        this.abreviaturaService = abreviaturaService;
    }

    // Endpoint para consultar documentos soporte filtrando por nombre y estado.
    @GetMapping("/doc-soporte")
    public ResponseEntity<?> consultarDocSoporte(
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false, defaultValue = "false") Boolean inactivo) {
        try {
            List<Map<String, Object>> resultado = abreviaturaService.consultarDocSoporte(nombre, inactivo);
            return ResponseEntity.ok(resultado);
        } catch (RuntimeException e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }
    
    // Endpoint para consultar terceros asociados a un documento soporte.
    @GetMapping("/doc-soporte-tercero")
    public ResponseEntity<?> consultarDocSoporteTercero(
            @RequestParam Integer idDocSoporte) {
        try {
            List<Map<String, Object>> resultado = abreviaturaService.consultarDocSoporteTercero(idDocSoporte);
            return ResponseEntity.ok(resultado);
        } catch (RuntimeException e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    // Endpoint para insertar una nueva relación entre documento soporte y tercero.
    @PostMapping("/doc-soporte-tercero")
    public ResponseEntity<?> insertarDocSoporteTercero(
            @RequestParam Integer idDocSoporte,
            @RequestParam Integer idTerceroKey,
            @RequestParam String prefijo,
            @RequestParam(required = false, defaultValue = "true") Boolean obligatorio,
            @RequestParam(required = false, defaultValue = "false") Boolean inactivo) {
        try {
            abreviaturaService.insertarDocSoporteTercero(idDocSoporte, idTerceroKey, prefijo, obligatorio, inactivo);
            return ResponseEntity.ok("Registro insertado correctamente");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    // Endpoint para actualizar una relación existente entre documento soporte y tercero.
    @PutMapping("/doc-soporte-tercero")
    public ResponseEntity<?> actualizarDocSoporteTercero(
            @RequestParam Integer id,
            @RequestParam String prefijo,
            @RequestParam Integer IdTerceroKey,
            @RequestParam(required = false, defaultValue = "true") Boolean obligatorio,
            @RequestParam(required = false, defaultValue = "false") Boolean inactivo) {
        try {
            abreviaturaService.actualizarDocSoporteTercero(id, prefijo, IdTerceroKey, obligatorio, inactivo);
            return ResponseEntity.ok("Registro actualizado correctamente");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    // Endpoint para eliminar una relación entre documento soporte y tercero.
    @DeleteMapping("/doc-soporte-tercero/{id}")
    public ResponseEntity<?> eliminarDocSoporteTercero(@PathVariable Integer id) {
        try {
            abreviaturaService.eliminarDocSoporteTercero(id);
            return ResponseEntity.ok("Registro eliminado correctamente");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }
    
}
