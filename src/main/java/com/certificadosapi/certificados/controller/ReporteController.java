package com.certificadosapi.certificados.controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.NTCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.util.EntityUtils;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.auth.NTLMSchemeFactory;
import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import java.sql.*;

import org.apache.pdfbox.multipdf.PDFMergerUtility;

import com.certificadosapi.certificados.service.ReporteService;
import com.certificadosapi.certificados.util.ServidorUtil;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;

import org.springframework.http.*;

@RestController
public class ReporteController {

    private ReporteService reporteService;

    public ReporteController(ReporteService reporteService){
        this.reporteService = reporteService;
    }


    @GetMapping("/descargar-pdf")
    public ResponseEntity<?> descargarPdf(
            @RequestParam String idAdmision,
            @RequestParam String nombreArchivo,
            @RequestParam String nombreSoporte) {

        byte[] pdfBytes = reporteService.descargarPdf(idAdmision, nombreArchivo, nombreSoporte);
        
        ByteArrayResource resource = new ByteArrayResource(pdfBytes);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=" + nombreArchivo + ".pdf")
                .body(resource);
    }


    @GetMapping("/soporte")
    public ResponseEntity<List<Map<String, Object>>> obtenerDocumentosSoporte() {
        List<Map<String, Object>> documentos = reporteService.obtenerDocumentosSoporte();
        return ResponseEntity.ok(documentos);
    }

    
    @GetMapping("/facturas/existe")
    public ResponseEntity<?> existeFactura(
            @RequestParam String idAdmision,
            @RequestParam Integer idDoc
    ) {

        Map<String, Boolean> existe = reporteService.existeFactura(idAdmision, idDoc);

        return ResponseEntity.ok(existe);

    }


}
