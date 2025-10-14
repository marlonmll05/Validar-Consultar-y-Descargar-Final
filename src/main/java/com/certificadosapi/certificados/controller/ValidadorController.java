package com.certificadosapi.certificados.controller;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import com.sun.jna.platform.win32.WinReg;
import com.sun.jna.platform.win32.Advapi32Util;

import javax.net.ssl.*;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import java.sql.*;



@RestController
@RequestMapping("/api/validador")
public class ValidadorController {
    
       private String getServerFromRegistry() throws Exception {
        String registryPath = "SOFTWARE\\VB and VBA Program Settings\\Asclepius\\Administrativo";
        String valueName = "Servidor"; 

        try {
            return Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, registryPath, valueName);
        } catch (Exception e) {
            throw new Exception("Error al leer el registro: " + e.getMessage());
        }
    }

        @PostMapping("/subir")
    public ResponseEntity<String> subirArchivoJson(
        @RequestBody String jsonContenido,
        @RequestHeader("Authorization") String bearerToken,
        @RequestParam String nFact
    ) {
        try {
            System.out.println("NFact recibido: " + nFact);

            Integer idTipoCapita = obtenerIdTipoCapita(nFact);
            
            if (idTipoCapita == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("{\"error\":\"No se encontró registro con NFact: " + nFact + "\"}");
            }
            
            System.out.println("IdTipoCapita obtenido: " + idTipoCapita);

            RestTemplate restTemplate = crearRestTemplateInseguro();

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
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("{\"error\":\"IdTipoCapita no soportado: " + idTipoCapita + "\"}");
            }

            ResponseEntity<String> respuesta = restTemplate.postForEntity(urlApiDocker, entidad, String.class);

            return ResponseEntity.status(respuesta.getStatusCode()).body(respuesta.getBody());

        } catch (Exception e) {
            if (e instanceof HttpStatusCodeException) {
                HttpStatusCodeException ex = (HttpStatusCodeException) e;
                return ResponseEntity.status(ex.getStatusCode()).body(ex.getResponseBodyAsString());
            }

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\":\"Error al enviar a la API Docker\", \"detalle\":\"" + e.getMessage() + "\"}");
        }
    }

    private RestTemplate crearRestTemplateInseguro() throws Exception {
        TrustManager[] trustAllCerts = new TrustManager[]{
            new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] xcs, String s) {}
                public void checkServerTrusted(X509Certificate[] xcs, String s) {}
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }
        };

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCerts, new SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
        HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);

        return new RestTemplate();
    }

    private Integer obtenerIdTipoCapita(String nFact) {
        String servidor;
        try {
            servidor = getServerFromRegistry();
        } catch (Exception e) {
            System.err.println("❌ Error al obtener el servidor del registro: " + e.getMessage());
            throw new RuntimeException("Error al obtener el servidor", e);
        }

        String connectionUrl = String.format(
            "jdbc:sqlserver://%s;databaseName=IPSoft100_ST;user=ConexionApi;password=ApiConexion.77;encrypt=true;trustServerCertificate=true;sslProtocol=TLSv1;", 
            servidor
        );

        String sql = "SELECT IdTipoCapita FROM FacturaFinal WHERE NFact = ?";
        
        try (Connection connection = DriverManager.getConnection(connectionUrl);
            PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, nFact);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt("IdTipoCapita");
                }
                return null;
            }

        } catch (SQLException e) {
            System.err.println("Error SQL: " + e.getMessage());
            throw new RuntimeException("Error en la consulta SQL", e);
        }
    }

    @GetMapping("/facturasval")
    public ResponseEntity<?> buscarFacturas(
        @RequestParam(required = false, name = "fechaDesde")
        @DateTimeFormat(pattern = "yyyy-MM-dd")
        LocalDate fechaDesde,

        @RequestParam(required = false, name = "fechaHasta")
        @DateTimeFormat(pattern = "yyyy-MM-dd")
        LocalDate fechaHasta,

        @RequestParam(required = false) String idTercero,
        @RequestParam(required = false) String noContrato,
        @RequestParam(required = false) String nFact
    ) {
        if (fechaDesde != null && fechaHasta != null && fechaDesde.isAfter(fechaHasta)) {
            return ResponseEntity.badRequest().body("fechaDesde no puede ser posterior a fechaHasta");
        }

        try {
            String servidor = getServerFromRegistry();
            String connectionUrl = String.format(
                "jdbc:sqlserver://%s;databaseName=IPSoft100_ST;user=ConexionApi;password=ApiConexion.77;encrypt=true;trustServerCertificate=true;sslProtocol=TLSv1;",
                servidor
            );

            try (Connection conn = DriverManager.getConnection(connectionUrl)) {
                String sql = "EXEC dbo.pa_Net_Facturas_JSON ?, ?, ?, ?, ?, ?, ?, ?";
                
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, "-1"); // @Ips
                    
                    if (fechaDesde != null) {
                        pstmt.setDate(2, Date.valueOf(fechaDesde)); // @Fecha_Ini
                    } else {
                        pstmt.setNull(2, Types.DATE);
                    }
                    
                    if (fechaHasta != null) {
                        pstmt.setDate(3, Date.valueOf(fechaHasta)); // @Fecha_Fin
                    } else {
                        pstmt.setNull(3, Types.DATE);
                    }
                    
                    pstmt.setString(4, "-1"); // @IdUsuario
                    
                    if (idTercero != null && !idTercero.trim().isEmpty()) {
                        try {
                            pstmt.setInt(5, Integer.parseInt(idTercero)); // @IdTerceroKey
                        } catch (NumberFormatException e) {
                            pstmt.setInt(5, -1); 
                        }
                    } else {
                        pstmt.setInt(5, -1);
                    }
                    
                    if (noContrato != null && !noContrato.trim().isEmpty()) {
                        pstmt.setString(6, noContrato); 
                    } else {
                        pstmt.setNull(6, Types.VARCHAR);
                    }
                    
                    if (nFact != null && !nFact.trim().isEmpty()) {
                        pstmt.setString(7, nFact); 
                    } else {
                        pstmt.setNull(7, Types.VARCHAR);
                    }
                    
                    pstmt.setInt(8, -1);

                    try (ResultSet rs = pstmt.executeQuery()) {
                        List<Map<String, Object>> resultados = new ArrayList<>();
                        ResultSetMetaData metaData = rs.getMetaData();
                        int columnCount = metaData.getColumnCount();

                        while (rs.next()) {
                            Map<String, Object> fila = new LinkedHashMap<>();
                            for (int i = 1; i <= columnCount; i++) {
                                String columnName = metaData.getColumnName(i);
                                Object value = rs.getObject(i);

                                if ("FechaFactura".equalsIgnoreCase(columnName)) {
                                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
                                    if (value instanceof Timestamp) {
                                        fila.put(columnName, sdf.format(value));
                                    } else if (value instanceof Date) {
                                        fila.put(columnName, sdf.format(new java.util.Date(((Date) value).getTime())));
                                    } else {
                                        fila.put(columnName, value);
                                    }
                                } else {
                                    fila.put(columnName, (value instanceof String) ? value.toString().trim() : value);
                                }
                            }
                            resultados.add(fila);
                        }

                        return ResponseEntity.ok(resultados);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error al ejecutar el procedimiento: " + e.getMessage());
        }
    }

    @GetMapping("/nombrexml/{idMovDoc}")
    public ResponseEntity<String> obtenerNombreXml(@PathVariable int idMovDoc) {
        String numdoc = "";
        String IdEmpresaGrupo = "";
        String yearSuffix = String.valueOf(LocalDate.now().getYear()).substring(2);
        Connection conn = null;

        try {
            String servidor1 = getServerFromRegistry();

            String connectionUrl = String.format(
                "jdbc:sqlserver://%s;databaseName=IPSoftFinanciero_ST;user=ConexionApi;password=ApiConexion.77;encrypt=true;trustServerCertificate=true;sslProtocol=TLSv1;",
                servidor1
            );

            conn = DriverManager.getConnection(connectionUrl);

            String docQuery = "SELECT Prefijo, Numdoc FROM MovimientoDocumentos WHERE IdMovDoc = ?";
            try (PreparedStatement stmt = conn.prepareStatement(docQuery)) {
                stmt.setInt(1, idMovDoc);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    numdoc = rs.getString("Numdoc");
                }
            }

            String empresaQuery = "SELECT IdEmpresaGrupo FROM MovimientoDocumentos as M INNER JOIN Empresas as E ON e.IdEmpresaKey = m.IdEmpresaKey WHERE IdMovDoc = ?";
            try (PreparedStatement stmt = conn.prepareStatement(empresaQuery)) {
                stmt.setInt(1, idMovDoc);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    IdEmpresaGrupo = rs.getString("IdEmpresaGrupo");
                }
            }

            String formattedNumdoc = String.format("%08d", Integer.parseInt(numdoc));

            String xmlFileName = "ad0" + IdEmpresaGrupo + "000" + yearSuffix + formattedNumdoc + ".xml";

            return ResponseEntity.ok(xmlFileName);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al obtener el nombre del archivo XML: " + e.getMessage());
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {

                }
            }
        }
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
    
}
