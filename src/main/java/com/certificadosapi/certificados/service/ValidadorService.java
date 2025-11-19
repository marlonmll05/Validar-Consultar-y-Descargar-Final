package com.certificadosapi.certificados.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import com.certificadosapi.certificados.config.DatabaseConfig;
import com.certificadosapi.certificados.util.ServidorUtil;

import java.sql.*;

@Service
public class ValidadorService {

    private static final Logger log = LoggerFactory.getLogger(ValidadorService.class);

    private final DatabaseConfig databaseConfig;
    private final ServidorUtil servidorUtil;

    @Autowired
    public ValidadorService(DatabaseConfig databaseConfig, ServidorUtil servidorUtil){
        this.databaseConfig = databaseConfig;
        this.servidorUtil = servidorUtil;
    }

    // ENVIAR FACTURA AL MINISTERIO
    public String subirArchivoJson(String jsonContenido, String bearerToken, String nFact) {
        log.info("Iniciando envío de JSON al ministerio para NFact={}", nFact);

        Integer idTipoCapita = obtenerIdTipoCapita(nFact);

        if (idTipoCapita == null) {
            log.warn("No se encontró IdTipoCapita para NFact={}", nFact);
            throw new IllegalArgumentException("No se encontró registro con NFact: " + nFact);
        }

        log.debug("IdTipoCapita detectado para NFact {} -> {}", nFact, idTipoCapita);

        RestTemplate restTemplate = servidorUtil.crearRestTemplateInseguro();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", bearerToken);

        HttpEntity<String> entidad = new HttpEntity<>(jsonContenido, headers);

        String urlApiDocker;

        if (idTipoCapita == 1) {
            urlApiDocker = "https://localhost:9443/api/PaquetesFevRips/CargarFevRips";
            log.info("Solicitud enviada a endpoint CargarFevRips para NFact={}", nFact);
        } else if (idTipoCapita == 3) {
            urlApiDocker = "https://localhost:9443/api/PaquetesFevRips/CargarCapitaPeriodo";
            log.info("Solicitud enviada a endpoint CargarCapitaPeriodo para NFact={}", nFact);
        } else {
            log.error("IdTipoCapita no soportado para NFact={} -> {}", nFact, idTipoCapita);
            throw new IllegalArgumentException("IdTipoCapita no soportado: " + idTipoCapita);
        }

        try {
            ResponseEntity<String> respuesta = restTemplate.postForEntity(urlApiDocker, entidad, String.class);
            log.info("Respuesta recibida correctamente para NFact={} Status={}", nFact, respuesta.getStatusCode());
            return respuesta.getBody();

        } catch (HttpStatusCodeException ex) {
            log.error("Error HTTP al enviar JSON para NFact={} Detalle={}", nFact, ex.getResponseBodyAsString());
            throw new RuntimeException("Error en login: " + ex.getResponseBodyAsString(), ex);

        } catch (Exception e) {
            log.error("Error inesperado al enviar archivo JSON para NFact={} Error={}", nFact, e.getMessage());
            throw new RuntimeException("Error en la solicitud de login: " + e.getMessage(), e);
        }
    }


    // ObtenerIdTipoCapita
    private Integer obtenerIdTipoCapita(String nFact) {
        log.debug("Consultando IdTipoCapita para NFact={}", nFact);

        String sql = "SELECT IdTipoCapita FROM FacturaFinal WHERE NFact = ?";

        try (Connection connection = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoft100_ST"));
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, nFact);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    int idTipo = resultSet.getInt("IdTipoCapita");
                    log.debug("IdTipoCapita encontrado para {} -> {}", nFact, idTipo);
                    return idTipo;
                }
                log.warn("No se encontró IdTipoCapita para NFact={}", nFact);
                return null;
            }

        } catch (SQLException e) {
            log.error("Error SQL al consultar IdTipoCapita para NFact={} Error={}", nFact, e.getMessage());
            throw new RuntimeException("Error en la consulta SQL", e);
        }
    }


    // Obtener XML como Base64
    public String exportDocXmlBase64(int idMovDoc) {
        log.info("Solicitando XML Base64 para IdMovDoc={}", idMovDoc);

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
                            log.error("XML vacío o nulo para IdMovDoc={}", idMovDoc);
                            throw new IllegalStateException("El contenido XML está vacío para IdMovDoc: " + idMovDoc);
                        }

                        byte[] xmlBytes = docXmlContent.getBytes(StandardCharsets.UTF_8);
                        String base64Encoded = Base64.getEncoder().encodeToString(xmlBytes);

                        log.debug("XML convertido a Base64 correctamente para IdMovDoc={}", idMovDoc);
                        return base64Encoded;
                    }

                    log.warn("No se encontró documento XML para IdMovDoc={}", idMovDoc);
                    throw new IllegalArgumentException("No se encontró documento con IdMovDoc: " + idMovDoc);
                }
            }
        } catch (SQLException e) {
            log.error("Error al procesar XML Base64 para IdMovDoc={} Detalle={}", idMovDoc, e.getMessage());
            throw new RuntimeException("Error al procesar la solicitud: " + e.getMessage(), e);
        }
    }


    // Login API DOCKER
    public String login(String jsonBody) {
        log.info("Ingresando a la Api Docker");

        try {
            RestTemplate restTemplate = servidorUtil.crearRestTemplateInseguro();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
            String url = "https://localhost:9443/api/auth/LoginSISPRO";

            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            log.debug("Login exitoso Status={} ", response.getStatusCode());
            return response.getBody();

        } catch (HttpStatusCodeException ex) {
            log.error("Error en login externo. Detalle={}", ex.getResponseBodyAsString());
            throw new RuntimeException("Error en login: " + ex.getResponseBodyAsString(), ex);

        } catch (Exception e) {
            log.error("Error inesperado en login externo: {}", e.getMessage());
            throw new RuntimeException("Error en la solicitud de login: " + e.getMessage(), e);
        }
    }


    // Guardar respuesta API (CUV)
    public String guardarRespuestaApi(String nFact, String mensajeRespuesta) {
        log.info("Guardando respuesta API para NFact={}", nFact);

        try {
            String connectionUrl = databaseConfig.getConnectionUrl("IPSoft100_ST");

            String checkSql = "SELECT COUNT(*) FROM RIPS_RespuestaAPI WHERE Nfact = ?";
            String insertSql = "INSERT INTO RIPS_RespuestaAPI (Nfact, MensajeRespuesta) VALUES (?, ?)";

            try (Connection conn = DriverManager.getConnection(connectionUrl)) {

                try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                    checkStmt.setString(1, nFact);
                    try (ResultSet rs = checkStmt.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0) {
                            log.warn("Intento de duplicado: ya existe registro para NFact={}", nFact);
                            throw new IllegalStateException("Ya existe un registro para el Nfact: " + nFact);
                        }
                    }
                }

                try (PreparedStatement statement = conn.prepareStatement(insertSql)) {
                    statement.setString(1, nFact);
                    statement.setString(2, mensajeRespuesta);
                    statement.executeUpdate();
                }

                log.info("Respuesta API guardada exitosamente para NFact={}", nFact);
                return "Respuesta guardada correctamente";
            }
        } catch (SQLException e) {
            log.error("Error al guardar respuesta API para NFact={} Detalle={}", nFact, e.getMessage());
            throw new RuntimeException("Error al guardar respuesta: " + e.getMessage(), e);
        }
    }
}
