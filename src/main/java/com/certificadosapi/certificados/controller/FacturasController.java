package com.certificadosapi.certificados.controller;


import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.certificadosapi.certificados.dto.ZipResult;
import com.certificadosapi.certificados.service.FacturasService;
import com.fasterxml.jackson.core.JsonProcessingException;


@RestController
@RequestMapping("/facturas")
public class FacturasController {

    private final FacturasService facturasService;

    @Autowired
    public FacturasController(FacturasService facturasService) {
        this.facturasService = facturasService;
    }


    // Endpoint para exportar el XML
    @GetMapping("/{idMovDoc}")
    public ResponseEntity<ByteArrayResource> exportarXml(@PathVariable int idMovDoc) throws SQLException {

        byte[] xmlBytes = facturasService.exportDocXml(idMovDoc);

        ByteArrayResource resource = new ByteArrayResource(xmlBytes);

        return ResponseEntity.ok(resource);
    }

    // Endpoint para generar json de facturas
    @GetMapping("/generarjson/{idMovDoc}")
    public ResponseEntity<ByteArrayResource> generarjson(@PathVariable int idMovDoc) throws SQLException, JsonProcessingException {
        
        byte[] jsonBytes = facturasService.generarjson(idMovDoc);

        ByteArrayResource jsonResponse = new ByteArrayResource(jsonBytes);

        return ResponseEntity.ok(jsonResponse);
    }

    // Endpoint para generar txt de facturas
    @GetMapping("/generartxt/{idMovDoc}")
    public ResponseEntity<Map<String, ByteArrayResource>> generarTxt(@PathVariable int idMovDoc) throws SQLException {
        
        Map<String, byte[]> txtFiles = facturasService.generarTxt(idMovDoc);

        Map<String, ByteArrayResource> resourceMap = new HashMap<>();
        for(Map.Entry<String, byte[]> entry: txtFiles.entrySet()){
            
            resourceMap.put(entry.getKey(), new ByteArrayResource(entry.getValue()));
        }

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(resourceMap);
    }

    // Endpoint para generar zip de los 3 anteriores
    @GetMapping("/generarzip/{idMovDoc}/{tipoArchivo}/{incluirXml}")
    public ResponseEntity<ByteArrayResource> generarZip(
        @PathVariable int idMovDoc,
        @PathVariable String tipoArchivo,
        @PathVariable boolean incluirXml) throws IOException, SQLException {

        ZipResult zipResult = facturasService.generarzip(idMovDoc, tipoArchivo, incluirXml);
        byte[] zipBytes = zipResult.getZipBytes();
        String fileName = zipResult.getFileName();

        ByteArrayResource resource = new ByteArrayResource(zipBytes);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(zipBytes.length)
                .body(resource);
    }

    //Endpoint para mostrar las admisiones
    @GetMapping("/admision")
    public ResponseEntity<?> generarAdmision(
        @RequestParam int idMovDoc,
        @RequestParam int idDoc
    ) throws SQLException {
        List<Map<String, Object>> resultados = facturasService.generarAdmision(idMovDoc, idDoc);

        return ResponseEntity.ok(resultados);
    }

}



