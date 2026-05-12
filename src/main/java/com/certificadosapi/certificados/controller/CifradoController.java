package com.certificadosapi.certificados.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.certificadosapi.certificados.service.CifradoService;

@RestController
@RequestMapping("/cifrado")
public class CifradoController {

    private final CifradoService CifradoService;

    @Autowired
    public CifradoController(CifradoService CifradoService) {
        this.CifradoService = CifradoService;
    }

    @SuppressWarnings("null")
    @PostMapping("/cifrar")
    public ResponseEntity<byte[]> cifrar(@RequestParam("archivo") MultipartFile archivo) {
        try {

            byte[] respuesta = CifradoService.cifrar(archivo);

            return ResponseEntity.ok().header("Content-Disposition", "attachment; filename=" + archivo.getOriginalFilename() + ".zip").contentType(MediaType.APPLICATION_OCTET_STREAM).body(respuesta);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage().getBytes());
        }
    }
    
}
