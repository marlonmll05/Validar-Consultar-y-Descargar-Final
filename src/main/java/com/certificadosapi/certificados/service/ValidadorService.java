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

import org.springframework.web.bind.annotation.*;

import com.certificadosapi.certificados.service.ValidadorService;


import java.sql.*;

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

    
}
