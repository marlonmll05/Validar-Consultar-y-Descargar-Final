package com.certificadosapi.certificados.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import com.certificadosapi.certificados.config.DatabaseConfig;
import com.certificadosapi.certificados.util.ServidorUtil;

import com.certificadosapi.certificados.service.ValidadorService;


import java.sql.*;

@Service
public class ValidadorService {

    private DatabaseConfig databaseConfig;
    private ServidorUtil servidorUtil;

    @Autowired
    public ValidadorService(DatabaseConfig databaseConfig, ServidorUtil servidorUtil){
        this.databaseConfig = databaseConfig;
        this.servidorUtil = servidorUtil;
    }

    //ENVIAR FACTURA AL MINISTERIO
    public String subirArchivoJson(String jsonContenido, String bearerToken, String nFact) {
        System.out.println("NFact recibido: " + nFact);

        Integer idTipoCapita = obtenerIdTipoCapita(nFact);
        
        if (idTipoCapita == null) {
            throw new IllegalArgumentException("No se encontró registro con NFact: " + nFact);
        }
        
        System.out.println("IdTipoCapita obtenido: " + idTipoCapita);

        RestTemplate restTemplate = servidorUtil.crearRestTemplateInseguro();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", bearerToken);

        HttpEntity<String> entidad = new HttpEntity<>(jsonContenido, headers);

        String urlApiDocker;

        if (idTipoCapita == 1) {
            urlApiDocker = "https://localhost:9443/api/PaquetesFevRips/CargarFevRips";
            System.out.println("Enviado a CargarFevRips");
        } else if (idTipoCapita == 3) {
            urlApiDocker = "https://localhost:9443/api/PaquetesFevRips/CargarCapitaPeriodo";
        } else {
            throw new IllegalArgumentException("IdTipoCapita no soportado: " + idTipoCapita);
        }

        ResponseEntity<String> respuesta = restTemplate.postForEntity(urlApiDocker, entidad, String.class);
        return respuesta.getBody();
    }

    /**
     * Obtiene el tipo de capita desde la BD para saber si se envía como capita o como evento
     */

    private Integer obtenerIdTipoCapita(String nFact) {
        String sql = "SELECT IdTipoCapita FROM FacturaFinal WHERE NFact = ?";
        
        try (Connection connection = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoft100_ST"));
            PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, nFact);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt("IdTipoCapita");
                }
                return null;
            }

        } catch (Exception e) {
            System.err.println("Error SQL: " + e.getMessage());
            throw new RuntimeException("Error en la consulta SQL", e);
        }
    }

    //Para enviar al ministerio se necesita enviar el XML como base64
    public String exportDocXmlBase64(int idMovDoc) {
        try {
            String connectionUrl = databaseConfig.getConnectionUrl("IPSoftFinanciero_ST");

            try (Connection conn = DriverManager.getConnection(connectionUrl);
                PreparedStatement pstmt = conn.prepareStatement(
                    "SELECT CONVERT(XML, DocXmlEnvelope) AS DocXml FROM MovimientoDocumentos WHERE IdMovDoc = ?")) {
                
                pstmt.setInt(1, idMovDoc);

                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        String docXmlContent = rs.getString("DocXml");

                        if (docXmlContent == null) {
                            throw new IllegalStateException("El contenido XML está vacío o es nulo para el documento con IdMovDoc: " + idMovDoc);
                        }

                        byte[] xmlBytes = docXmlContent.getBytes(StandardCharsets.UTF_8);
                        String base64Encoded = Base64.getEncoder().encodeToString(xmlBytes);

                        return base64Encoded;

                    } else {
                        throw new IllegalArgumentException("No se encontró un documento con el idMovDoc: " + idMovDoc);
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al procesar la solicitud: " + e.getMessage(), e);
        }
    }

    public String login(String jsonBody) {
        try {
            RestTemplate restTemplate = servidorUtil.crearRestTemplateInseguro();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
            String url = "https://localhost:9443/api/auth/LoginSISPRO";

            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            return response.getBody();
            
        } catch (HttpStatusCodeException ex) {
            throw new RuntimeException("Error en login: " + ex.getResponseBodyAsString(), ex);
        } catch (Exception e) {
            throw new RuntimeException("Error en la solicitud de login: " + e.getMessage(), e);
        }
    }

    // Método para guardar la respuesta de la API (El CUV se guarda en una tabla y luego al descargar la factura aparece el TXT con el CUV)
    public String guardarRespuestaApi(String nFact, String mensajeRespuesta) {
        try {
            String connectionUrl = databaseConfig.getConnectionUrl("IPSoft100_ST");

            String checkSql = "SELECT COUNT(*) FROM RIPS_RespuestaAPI WHERE Nfact = ?";
            String insertSql = "INSERT INTO RIPS_RespuestaAPI (Nfact, MensajeRespuesta) VALUES (?, ?)";

            try (Connection conn = DriverManager.getConnection(connectionUrl)) {

                try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                    checkStmt.setString(1, nFact);
                    try (ResultSet rs = checkStmt.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0) {
                            throw new IllegalStateException("Ya existe un registro para el Nfact: " + nFact);
                        }
                    }
                }

                try (PreparedStatement statement = conn.prepareStatement(insertSql)) {
                    statement.setString(1, nFact);
                    statement.setString(2, mensajeRespuesta);
                    statement.executeUpdate();
                }

                return "Respuesta guardada correctamente";
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al guardar respuesta: " + e.getMessage(), e);
        }
    }

}
