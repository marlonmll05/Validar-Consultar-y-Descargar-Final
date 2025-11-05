package com.certificadosapi.certificados.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.certificadosapi.certificados.config.DatabaseConfig;
import com.certificadosapi.certificados.util.ServidorUtil;


@Service 
public class ReporteService {

    private DatabaseConfig databaseConfig;
    private ServidorUtil servidorUtil;

    @Autowired
    public ReporteService(DatabaseConfig databaseConfig, ServidorUtil servidorUtil){
        this.databaseConfig = databaseConfig;
        this.servidorUtil = servidorUtil;
    }

    public byte[] descargarPdf(String idAdmision, String nombreArchivo, String nombreSoporte) {
        if (nombreSoporte == null || nombreSoporte.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre del soporte es requerido.");
        }

        String urlBase = null;
        try {
            try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoft100_ST"))) {
                String sql = "SELECT ValorParametro FROM ParametrosServidor WHERE NomParametro = 'URLReportServerWS'";
                try (PreparedStatement stmt = conn.prepareStatement(sql); ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        urlBase = rs.getString("ValorParametro");
                    } else {
                        throw new IllegalStateException("No se encontró la URL del servidor de reportes.");
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error al obtener la URL del servidor: " + e.getMessage(), e);
        }

        if (urlBase == null || urlBase.trim().isEmpty()) {
            throw new IllegalArgumentException("No se encontró la URL del servidor de reportes.");
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
                HttpGet request = new HttpGet(reportUrl);

                try (CloseableHttpResponse response = httpClient.execute(request)) {
                    int statusCode = response.getStatusLine().getStatusCode();

                    if (statusCode == 200) {
                        byte[] pdfBytes = EntityUtils.toByteArray(response.getEntity());
                        merger.addSource(new ByteArrayInputStream(pdfBytes));
                        hayAlMenosUnPdf = true;
                    } else {
                        String errorContent = EntityUtils.toString(response.getEntity(), "UTF-8");
                        throw new RuntimeException("Error al descargar el informe. Código: " + statusCode + " - " + errorContent);
                    }
                }
            }

            if (!hayAlMenosUnPdf) {
                throw new IllegalArgumentException("No se pudo procesar ninguna admisión.");
            }

            merger.mergeDocuments(null);
            return mergedOutput.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Excepción al conectar o procesar los PDFs: " + e.getMessage(), e);
        }
    }

    public List<Map<String, Object>> obtenerDocumentosSoporte() {
        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoft100_ST"))) {
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

                return resultados;
            }
        }catch (SQLException e) {
            throw new RuntimeException("Error al obtener los documentos de soporte: " + e.getMessage(), e);
        }
    }

    public Map<String, Boolean> existeFactura(
            String idAdmision,
            Integer idDoc
    ) {

        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoft100_ST"))) {
            String sql = "SELECT COUNT(*) FROM dbo.tbl_Net_Facturas_ListaPdf WHERE IdSoporteKey = ? AND IdAdmision = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, idDoc);
                ps.setString(2, idAdmision);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        return Map.of("existe", true);
                    }
                }
            }
            return Map.of("existe", false);
            
        }catch (Exception e) {
            throw new RuntimeException("Error: " + e.getMessage(), e);
        }
    }
}


