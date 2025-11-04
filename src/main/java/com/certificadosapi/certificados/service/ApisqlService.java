package com.certificadosapi.certificados.service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;

import com.certificadosapi.certificados.config.DatabaseConfig;

public class ApisqlService {

    private final DatabaseConfig databaseConfig;

    @Autowired
    public ApisqlService(DatabaseConfig databaseConfig){
        this.databaseConfig = databaseConfig;
    }

    public Map<String, Object> obtenerCuv(String nFact) {
        try {
            String connectionUrl = databaseConfig.getConnectionUrl("IPSoft100_ST");

            try (Connection conn = DriverManager.getConnection(connectionUrl)) {
                String sql = "SELECT Rips_CUV FROM FacturaFinal WHERE NFact = ? and Rips_CUV is not null";

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, nFact);

                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            String cuv = rs.getString("Rips_CUV");
                            return Map.of(
                                "success", true,
                                "Rips_CUV", cuv
                            );
                        } else {
                            return Map.of(
                                "success", false,
                                "message", "No se encontró la factura o no tiene CUV",
                                "Rips_CUV", ""
                            );
                        }
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error de base de datos: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("Error inesperado: " + e.getMessage(), e);
        }
    }


    
}
