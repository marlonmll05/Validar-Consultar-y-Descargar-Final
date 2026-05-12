package com.certificadosapi.certificados.controller;

import java.util.Map;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.certificadosapi.certificados.dto.ZipResult;
import com.certificadosapi.certificados.service.R202Service;

@RestController
@RequestMapping("/r202")
public class R202Controller {


    private final R202Service R202Service;

    @Autowired
    public R202Controller(R202Service R202Service) {
        this.R202Service = R202Service;
    }


    @SuppressWarnings("null")
    @RequestMapping("/generar202")
    public ResponseEntity<?> generar202(@RequestParam Integer consultaNum, @RequestParam String tipo, @RequestParam String tipoId, @RequestParam String tipoReg, @RequestParam String consecArch) {

        ZipResult respuesta = R202Service.generar202(consultaNum, tipo, tipoId, tipoReg, consecArch);

        return ResponseEntity.ok().header("Content-Disposition", "attachment; filename=\"" + respuesta.getFileName() + "\"").contentType(MediaType.TEXT_PLAIN).body(respuesta.getZipBytes());
    }



    @RequestMapping("/consultar")
    ResponseEntity<?> consultarOCrear(@RequestParam String FechaIni, @RequestParam String FechaFin, @RequestParam Integer IdTerceroKey) {

        Map<String, Object> respuesta = R202Service.consultarOCrear(FechaIni, FechaFin, IdTerceroKey);

        return ResponseEntity.status(200).body(respuesta);
    }


    @RequestMapping("/generarPaso")
    public ResponseEntity<?> generarPaso(@RequestParam Integer idRep, @RequestParam String campo) {

        String respuesta = R202Service.generarPaso(idRep, campo);

        return ResponseEntity.ok(respuesta);
    }

    @RequestMapping("/ejecutarPasos")
    public ResponseEntity<?> ejecutarPasos(@RequestParam Integer idRep, @RequestParam String campo) {

        String respuesta = R202Service.ejecutarPasos(idRep, campo);

        return ResponseEntity.ok(respuesta);
    }

    @RequestMapping("/consultarProgreso")
    public ResponseEntity<?> consultarProgreso(@RequestParam Integer idRep) {

        Map<String, Object> respuesta = R202Service.consultarProgreso(idRep);

        return ResponseEntity.ok(respuesta);
    }
 }

