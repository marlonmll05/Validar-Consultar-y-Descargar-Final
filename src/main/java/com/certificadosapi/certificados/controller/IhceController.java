package com.certificadosapi.certificados.controller;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.certificadosapi.certificados.service.IhceService;

@RestController
@RequestMapping("/ihce")
public class IhceController {

    private final IhceService ihceService;

    public IhceController(IhceService ihceService){
        this.ihceService = ihceService;
    }

    @GetMapping("/rda-consulta")
    public ResponseEntity<List<Map<String, Object>>>  RdaConsulta(@RequestParam Integer IdAdmision, @RequestParam Integer IdSesion, @RequestParam Integer Tipo) throws SQLException {


        List<Map<String, Object>> respuesta = ihceService.RdaConsulta(IdAdmision, IdSesion, Tipo);

        return ResponseEntity.ok(respuesta);
        
        
    }

    @GetMapping("/lista-rda")
    public ResponseEntity<List<Map<String, Object>>> ObtenerListaRda(@RequestParam String tipoId, @RequestParam String numId) throws SQLException {


        List<Map<String, Object>> respuesta = ihceService.ObtenerListaRda(tipoId, numId);

        return ResponseEntity.ok(respuesta);
        
        
    }
}
