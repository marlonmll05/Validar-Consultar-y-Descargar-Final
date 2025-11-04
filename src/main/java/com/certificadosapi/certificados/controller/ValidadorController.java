package com.certificadosapi.certificados.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import com.sun.jna.platform.win32.WinReg;
import com.certificadosapi.certificados.service.ValidadorService;
import com.sun.jna.platform.win32.Advapi32Util;
import javax.net.ssl.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Map;
import java.sql.*;



@RestController
@RequestMapping("/api/validador")
public class ValidadorController {
    
    private final ValidadorService validadorService;

    @Autowired
    public ValidadorController(ValidadorService validadorService) {
        this.validadorService = validadorService;
    }

    // ENDPOINT PARA ENVIAR FACTURA AL MINISTERIO
    @PostMapping("/subir")
    public ResponseEntity<String> subirArchivoJson(@RequestBody String jsonContenido, 
                                                  @RequestHeader("Authorization") String bearerToken, 
                                                  @RequestParam String nFact) {
        String resultado = validadorService.subirArchivoJson(jsonContenido, bearerToken, nFact);
        return ResponseEntity.ok(resultado);
    }

    @GetMapping("/base64/{idMovDoc}")
    public ResponseEntity<String> exportDocXmlBase64(@PathVariable int idMovDoc) {
        Connection conn = null;

        try {

            String servidor = getServerFromRegistry();

            String connectionUrl = String.format(
                "jdbc:sqlserver://%s;databaseName=IPSoftFinanciero_ST;user=ConexionApi;password=ApiConexion.77;encrypt=true;trustServerCertificate=true;sslProtocol=TLSv1;",
                servidor
            );

            conn = DriverManager.getConnection(connectionUrl);

            String query = "SELECT CONVERT(XML, DocXmlEnvelope) AS DocXml FROM MovimientoDocumentos WHERE IdMovDoc = ?";
            PreparedStatement pstmt = conn.prepareStatement(query);
            pstmt.setInt(1, idMovDoc);

            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                String docXmlContent = rs.getString("DocXml");


                if (docXmlContent == null) {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body("Error: El contenido XML está vacío o es nulo para el documento con IdMovDoc: " + idMovDoc);
                }

                byte[] xmlBytes = docXmlContent.getBytes(StandardCharsets.UTF_8);


                String base64Encoded = Base64.getEncoder().encodeToString(xmlBytes);

                return ResponseEntity.ok()
                        .contentType(MediaType.TEXT_PLAIN)
                        .body(base64Encoded);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("No se encontró un documento con el idMovDoc: " + idMovDoc);
            }

        } catch (Exception e) {

            String errorMsg = "Error al procesar la solicitud: " + e.getMessage();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorMsg);
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    System.err.println("Error cerrando la conexión: " + e.getMessage());
                }
            }
        }
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody String jsonBody) {
    try {
        RestTemplate restTemplate = crearRestTemplateInseguro();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
        String url = "https://localhost:9443/api/auth/LoginSISPRO";

        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

        return ResponseEntity.status(response.getStatusCode()).body(response.getBody());

    } catch (HttpStatusCodeException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(ex.getResponseBodyAsString());
    } catch (Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("{\"error\":\"Error en la solicitud de login\", \"detalle\":\"" + e.getMessage() + "\"}");
    }
    }

    @RequestMapping(value = "/login", method = RequestMethod.OPTIONS)
    public ResponseEntity<?> handleOptions() {
    return ResponseEntity.ok().build();
    }


    @PostMapping("/guardarrespuesta")
    public ResponseEntity<?> guardarRespuestaApi(@RequestBody Map<String, Object> payload){
        

        String servidor;

        try{
            servidor = getServerFromRegistry();
        }
        catch(Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al leer el servidor de registro: " + e.getMessage());
        }

        String nFact = (String) payload.get("nFact");
        String mensajeRespuesta = (String) payload.get("mensajeRespuesta");

        String connectionUrl = String.format("jdbc:sqlserver://%s;databaseName=IPSoft100_ST;user=ConexionApi;password=ApiConexion.77;encrypt=true;trustServerCertificate=true;sslProtocol=TLSv1;", servidor);

        String sql = "INSERT INTO RIPS_RespuestaAPI (Nfact, MensajeRespuesta) VALUES (?, ?)";
        String checkSql = "SELECT COUNT(*) FROM RIPS_RespuestaAPI WHERE Nfact = ?";


        try(Connection conn = DriverManager.getConnection(connectionUrl)) {

            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setString(1, nFact);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        return ResponseEntity.status(HttpStatus.CONFLICT)
                                .body("Ya existe un registro para el Nfact: " + nFact);
                    }
               }
            }
                
            try(PreparedStatement statement = conn.prepareStatement(sql)){
                statement.setString(1, nFact);
                statement.setString(2, mensajeRespuesta);
                statement.executeUpdate();
            }

            return ResponseEntity.ok("Respuesta guardada correctamente");


        } catch (SQLException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al guardar respuesta: " + e.getMessage());
        }
    }  
}
