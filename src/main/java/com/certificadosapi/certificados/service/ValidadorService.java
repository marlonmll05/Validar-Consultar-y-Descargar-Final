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


/**
 * Servicio encargado de la validación y comunicación con API externa del Ministerio.
 * Gestiona el envío de facturas en formato JSON, obtención de documentos XML,
 * autenticación con API Docker y almacenamiento de respuestas del ministerio.
 * 
 * @author Marlon Morales Llanos
 *
 */
@Service
public class ValidadorService {

    private static final Logger log = LoggerFactory.getLogger(ValidadorService.class);

    private final DatabaseConfig databaseConfig;
    private final ServidorUtil servidorUtil;


    /**
     * Constructor de ValidadorService con inyección de dependencias.
     * 
     * @param databaseConfig Objeto de configuración para las conexiones a base de datos
     * @param servidorUtil Objeto utilitario para operaciones del servidor
     */
    @Autowired
    public ValidadorService(DatabaseConfig databaseConfig, ServidorUtil servidorUtil){
        this.databaseConfig = databaseConfig;
        this.servidorUtil = servidorUtil;
    }

    /**
     * Envía una factura en formato JSON al Ministerio a través de la API Docker.
     * Determina automáticamente el endpoint a utilizar basándose en el IdTipoCapita
     * asociado al número de factura.
     * 
     * @param jsonContenido Contenido JSON de la factura a enviar
     * @param bearerToken Token de autenticación Bearer para la API
     * @param nFact Número de factura a procesar
     * @return Respuesta del servidor en formato String
     * @throws IllegalArgumentException si no se encuentra el NFact o el IdTipoCapita no es soportado
     * @throws RuntimeException si ocurre un error HTTP o inesperado durante el envío
     */
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


    /**
     * Obtiene el IdTipoCapita asociado a un número de factura desde la base de datos.
     * Este método es privado y se utiliza internamente para determinar el endpoint correcto.
     * 
     * @param nFact Número de factura a consultar
     * @return IdTipoCapita como Integer, o null si no se encuentra el registro
     * @throws RuntimeException si ocurre un error SQL durante la consulta
     */
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


    /**
     * Exporta un documento XML desde la base de datos y lo convierte a formato Base64.
     * Obtiene el contenido del campo DocXmlEnvelope de la tabla MovimientoDocumentos.
     * 
     * @param idMovDoc ID del movimiento de documento a exportar
     * @return String con el contenido XML codificado en Base64
     * @throws IllegalArgumentException si no se encuentra el documento con el ID proporcionado
     * @throws IllegalStateException si el contenido XML está vacío o es nulo
     * @throws RuntimeException si ocurre un error SQL durante el procesamiento
     */
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


    /**
     * Realiza el login en la API Docker del sistema SISPRO.
     * Envía las credenciales en formato JSON y retorna el token de autenticación.
     * 
     * @param jsonBody Cuerpo JSON con las credenciales de autenticación
     * @return Respuesta del servidor conteniendo el token de autenticación
     * @throws RuntimeException si ocurre un error HTTP o inesperado durante el login
     */
    public String login(String jsonBody) {
        log.info("Intentando ingresar a la Api Docker");

        try {
            RestTemplate restTemplate = servidorUtil.crearRestTemplateInseguro();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
            String url = "https://localhost:9443/api/auth/LoginSISPRO";

            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            log.info("Envio de respuesta exitosa | Respuesta = {} ", response.getBody());
            return response.getBody();

        } catch (HttpStatusCodeException ex) {
            log.error("Error en login externo. Detalle={}", ex.getResponseBodyAsString());
            throw new RuntimeException("Error en login: " + ex.getResponseBodyAsString(), ex);

        } catch (Exception e) {
            log.error("Error inesperado en login externo: {}", e.getMessage());
            throw new RuntimeException("Error en la solicitud de login: " + e.getMessage(), e);
        }
    }


    /**
     * Guarda la respuesta de la API del ministerio (CUV) en la base de datos.
     * Verifica que no exista un registro duplicado antes de insertar.
     * 
     * @param nFact Número de factura asociado a la respuesta
     * @param mensajeRespuesta Mensaje de respuesta del ministerio (contiene el CUV)
     * @return String confirmando que la respuesta fue guardada correctamente
     * @throws IllegalArgumentException si alguno de los parámetros es nulo o vacío
     * @throws IllegalStateException si ya existe un registro para el número de factura proporcionado
     * @throws RuntimeException si ocurre un error SQL durante el guardado
     */
    public String guardarRespuestaApi(String nFact, String mensajeRespuesta) {
        
        if (nFact == null || nFact.isEmpty()) {
            throw new IllegalArgumentException("El parámetro 'nFact' es requerido");
        }
        if (mensajeRespuesta == null || mensajeRespuesta.isEmpty()) {
            throw new IllegalArgumentException("El parámetro 'mensajeRespuesta' es requerido");
        }
        
        log.info("Guardando respuesta API para NFact={}", nFact);
        
        try {
            String connectionUrl = databaseConfig.getConnectionUrl("IPSoft100_ST");
            String checkSql = "SELECT COUNT(*) FROM RIPS_RespuestaAPI WHERE Nfact = ?";
            String insertSql = "INSERT INTO RIPS_RespuestaAPI (Nfact, MensajeRespuesta) VALUES (?, ?)";
            
            try (Connection conn = DriverManager.getConnection(connectionUrl)) {
                
                // Verificar duplicados
                try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                    checkStmt.setString(1, nFact);
                    try (ResultSet rs = checkStmt.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0) {
                            log.warn("Intento de duplicado: ya existe registro para NFact={}", nFact);
                            throw new IllegalStateException("Ya existe un registro para el Nfact: " + nFact);
                        }
                    }
                }
                
                // Insertar registro
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
