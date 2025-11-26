package com.certificadosapi.certificados.controller;


import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import com.certificadosapi.certificados.service.ApisqlService;
import com.certificadosapi.certificados.service.EditarjsonService;
import com.certificadosapi.certificados.service.InicioService;
import com.certificadosapi.certificados.service.LoginsqlService;

import java.sql.SQLException;
import java.util.Map;


@RestController
@RequestMapping("/api/sql")
public class ApisqlController {

    private final ApisqlService apisqlService;
    private EditarjsonService editarJsonService;
    private LoginsqlService loginsqlService;
    private InicioService inicioService;

    @Autowired
    public ApisqlController(EditarjsonService editarjsonService, ApisqlService apisqlService, LoginsqlService loginsqlService, InicioService inicioService){
        this.editarJsonService = editarjsonService;
        this.apisqlService = apisqlService;
        this.loginsqlService = loginsqlService;
        this.inicioService = inicioService;
    }

    //Metodo para verificar acceso al modulo de atenciones
    @GetMapping("/validar-parametro")
    public ResponseEntity<String> validarParametro() throws SQLException{
        
        String respuesta = inicioService.validarApartado();

        return ResponseEntity.ok(respuesta);
    }

    //Metodo para iniciar sesion a la aplicacion web
    @PostMapping("/login")
    public ResponseEntity<?> iniciarSesion(@RequestBody Map<String, String> datos) throws SQLException {
        
        Map<String, Object> respuesta = loginsqlService.iniciarSesion(datos);
        
        return ResponseEntity.ok(respuesta);
    }

    // Método para obtener CUV
    @GetMapping("/cuv")
    public ResponseEntity<Map<String, Object>> obtenerCuv(@RequestParam String nFact) {
        Map<String, Object> resultado = apisqlService.obtenerCuv(nFact);
        return ResponseEntity.ok(resultado);
    }


    // Método para actualizar CUV de Factura Final
    @PostMapping("/agregarcuv")
    public ResponseEntity<Map<String, Object>> actualizarCuvFF(
        @RequestParam String nFact,
        @RequestParam(required = false) String ripsCuv
    ) {
        Map<String, Object> resultado = apisqlService.actualizarCuvFF(nFact, ripsCuv);
        return ResponseEntity.ok(resultado);
    }

    // Método para actualizar CUV de Rips_Transaccion
    @PostMapping("/actualizarcuvrips")
    public ResponseEntity<?> actualizarCuvRipsTransaccion(
        @RequestParam String nFact,
        @RequestParam(required = false) String cuv,
        @RequestParam Integer idEstadoValidacion
    ) {
        Map<String, Object> resultado = apisqlService.actualizarCuvTransaccion(nFact, cuv, idEstadoValidacion);

        return ResponseEntity.ok(resultado);

    }

    // Método para obtener estado de validación
    @GetMapping("/estadovalidacion")
    public ResponseEntity<Map<String, Object>> obtenerEstadoValidacion(@RequestParam String nFact) {
        Map<String, Object> resultado = apisqlService.obtenerEstadoValidacion(nFact);
        return ResponseEntity.ok(resultado);
    }

    // Método para ejecutar RIPS (Llena las tablas para cada factura)
    @GetMapping("/ejecutarRips")
    public ResponseEntity<String> ejecutarRips(@RequestParam String Nfact) throws SQLException, Exception {
        String resultado = apisqlService.ejecutarRips(Nfact);
        return ResponseEntity.ok(resultado);
    }

    // Método para actualizar campos
    @PostMapping("/actualizarCampos")
    public ResponseEntity<String> actualizarCampos(
        @RequestBody Map<String, Object> datos,
        @RequestParam int idMovDoc 
    ) throws Exception {
        editarJsonService.actualizarCampos(datos, idMovDoc);

        return ResponseEntity.ok().body("Datos Actualizados Correctamente");
    }
}





