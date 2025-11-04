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

import com.certificadosapi.certificados.util.ServidorUtil;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;

import org.springframework.http.*;

@RestController
public class ReporteController {

    private ServidorUtil servidorUtil;

    @Autowired
    public ReporteController(ServidorUtil servidorUtil){
        this.servidorUtil = servidorUtil;
    }


    @GetMapping("/descargar-pdf")
    public ResponseEntity<?> descargarPdf(
            @RequestParam String idAdmision,
            @RequestParam String nombreArchivo,
            @RequestParam String nombreSoporte 
            
    ) {

        System.out.println("========== DATOS RECIBIDOS ==========");
        System.out.println("idAdmision: " + idAdmision);
        System.out.println("nombreArchivo: " + nombreArchivo);
        System.out.println("nombreSoporte: " + nombreSoporte);
        System.out.println("======================================");


        if (nombreSoporte == null || nombreSoporte.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("El nombre del soporte es requerido.");
        }

        String urlBase = null;
        try{
            String servidor = getServerFromRegistry();
            String connectionUrl = String.format("jdbc:sqlserver://%s;databaseName=IPSoft100_ST;user=ConexionApi;password=ApiConexion.77;encrypt=true;trustServerCertificate=true;sslProtocol=TLSv1;",
            servidor);
        

            try (Connection conn = DriverManager.getConnection(connectionUrl)){
                String sql = "SELECT ValorParametro FROM ParametrosServidor WHERE NomParametro = 'URLReportServerWS'";
                try (PreparedStatement stmt = conn.prepareStatement(sql);
                     ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        urlBase = rs.getString("ValorParametro");
                    } else {
                        return ResponseEntity.internalServerError().body("No se encontró la URL del servidor de reportes.");
                    }               
                }
            }
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error al obtener la URL del servidor: " + e.getMessage());
        }


        if (urlBase == null || urlBase.trim().isEmpty()) {
            return ResponseEntity.internalServerError().body("No se encontró la URL del servidor de reportes.");
        }

        try (CloseableHttpClient httpClient = servidorUtil.crearHttpClientConNTLM()) {

            String[] ids = idAdmision.split(",");
            PDFMergerUtility merger = new PDFMergerUtility();
            ByteArrayOutputStream mergedOutput = new ByteArrayOutputStream();
            merger.setDestinationStream(mergedOutput);

            boolean hayAlMenosUnPdf = false;


            for (String id : ids) {
                id = id.trim();
                
                String reportUrl = urlBase + "?" + nombreSoporte + "&IdAdmision=" + id + "&rs:Format=PDF";
                System.out.println("URL: " + reportUrl);

                HttpGet request = new HttpGet(reportUrl);

                try (CloseableHttpResponse response = httpClient.execute(request)) {
                    int statusCode = response.getStatusLine().getStatusCode();

                    if (statusCode == 200) {
                        byte[] pdfBytes = EntityUtils.toByteArray(response.getEntity());
                        merger.addSource(new ByteArrayInputStream(pdfBytes));
                        hayAlMenosUnPdf = true;

                    } else {
                        String errorContent = "";
                        if (response.getEntity() != null) {
                            try {
                                errorContent = EntityUtils.toString(response.getEntity(), "UTF-8");
                            } catch (Exception e) {
                                errorContent = "No se pudo leer el contenido del error: " + e.getMessage();
                            }
                        }

                        System.err.println("ERROR REPORTING SERVICES:");
                        System.err.println("Status Code: " + statusCode);
                        System.err.println("Reason Phrase: " + response.getStatusLine().getReasonPhrase());
                        System.err.println("URL solicitada: " + reportUrl);
                        System.err.println("Contenido del error: " + errorContent);

                        return ResponseEntity.status(statusCode)
                                .body("Error al descargar el informe. Código: " + statusCode +
                                        " - " + response.getStatusLine().getReasonPhrase() +
                                        "\nDetalle: " + errorContent);
                    }
                }
            }
            
            if(!hayAlMenosUnPdf) {
                return ResponseEntity.badRequest().body("No se pudo procesar ninguna admisión.");
            }

            merger.mergeDocuments(null);

            ByteArrayResource resource = new ByteArrayResource(mergedOutput.toByteArray());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + nombreArchivo + ".pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(resource);


        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body("Excepción al conectar o procesar los PDFs: " + e.getMessage());
        }
    }
    

    @GetMapping("/soporte")
    public ResponseEntity<?> obtenerDocumentosSoporte() {
        try {
            String servidor = getServerFromRegistry();
            String connectionUrl = String.format(
                "jdbc:sqlserver://%s;databaseName=IPSoft100_ST;user=ConexionApi;password=ApiConexion.77;encrypt=true;trustServerCertificate=true;sslProtocol=TLSv1;",
                servidor);

            try (Connection conn = DriverManager.getConnection(connectionUrl)) {
                String sql = "SELECT Id, NombreDocSoporte, NombreRptService FROM tbl_Net_Facturas_DocSoporte WHERE Inactivo = 0";

                try (PreparedStatement stmt = conn.prepareStatement(sql);
                    ResultSet rs = stmt.executeQuery()) {

                    List<Map<String, Object>> resultados = new ArrayList<>();
                    while (rs.next()) {
                        Map<String, Object> fila = new LinkedHashMap<>();
                        fila.put("Id", rs.getInt("Id"));
                        fila.put("nombreDocSoporte", rs.getString("NombreDocSoporte"));
                        fila.put("nombreRptService", rs.getString("NombreRptService"));
                        resultados.add(fila);
                    }

                    return ResponseEntity.ok(resultados);
                }
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error en la consulta: " + e.getMessage());
        }
    }

    @GetMapping("/facturas/existe")
    public ResponseEntity<?> existeFactura(
            @RequestParam String idAdmision,
            @RequestParam Integer idDoc
    ) {
        try {
            String servidor = getServerFromRegistry();
            String cnx = String.format(
                    "jdbc:sqlserver://%s;databaseName=IPSoft100_ST;user=ConexionApi;password=ApiConexion.77;encrypt=true;trustServerCertificate=true;sslProtocol=TLSv1;",
                    servidor
            );

            try (Connection conn = DriverManager.getConnection(cnx)) {
                String sql = "SELECT COUNT(*) FROM dbo.tbl_Net_Facturas_ListaPdf WHERE IdSoporteKey = ? AND IdAdmision = ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, idDoc);
                    ps.setString(2, idAdmision);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0) {
                            return ResponseEntity.ok(Map.of("existe", true));
                        }
                    }
                }
            }

            return ResponseEntity.ok(Map.of("existe", false));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }

}
