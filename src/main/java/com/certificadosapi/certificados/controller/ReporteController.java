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
