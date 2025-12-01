package com.certificadosapi.certificados.controller;

import java.sql.SQLException;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import com.certificadosapi.certificados.service.ValidadorService;

@RestController
@RequestMapping("/api/validador")
public class ValidadorController {
    
    private final ValidadorService validadorService;

    @Autowired
    public ValidadorController(ValidadorService validadorService) {
        this.validadorService = validadorService;
    }

    // ENDPOINT PARA ENVIAR FACTURA AL MINISTERIO
    @PostMapping("/subir")
    public ResponseEntity<String> subirArchivoJson(@RequestBody String jsonContenido, 
                                                  @RequestHeader("Authorization") String bearerToken, 
                                                  @RequestParam String nFact) {
        String resultado = validadorService.subirArchivoJson(jsonContenido, bearerToken, nFact);
        return ResponseEntity.ok(resultado);
    }

    // Método para exportar documento XML en Base64
    @GetMapping("/base64/{idMovDoc}")
    public ResponseEntity<String> exportDocXmlBase64(@PathVariable int idMovDoc) {
        String resultado = validadorService.exportDocXmlBase64(idMovDoc);
        return ResponseEntity.ok(resultado);
    }

    // Método para el login
    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody String jsonBody) {
        String resultado = validadorService.login(jsonBody);
        return ResponseEntity.ok(resultado);
    }

    @RequestMapping(value = "/login", method = RequestMethod.OPTIONS)
    public ResponseEntity<?> handleOptions() {
    return ResponseEntity.ok().build();
    }

    // Método para guardar la respuesta de la API (El CUV se guarda en una tabla y luego al descargar la factura aparece el TXT con el CUV)
    @PostMapping("/guardarrespuesta")
    public ResponseEntity<?> guardarRespuestaApi(@RequestBody Map<String, Object> payload) throws SQLException {
        
        String nFact = (String) payload.get("nFact");
        String mensajeRespuesta = (String) payload.get("mensajeRespuesta");
        
        String resultado = validadorService.guardarRespuestaApi(nFact, mensajeRespuesta);
        return ResponseEntity.ok(resultado);
    }
}
