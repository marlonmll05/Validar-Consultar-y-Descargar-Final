package com.certificadosapi.certificados.service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;

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

    public Map<String, Object> actualizarCuvFF(String nFact, String ripsCuv) {
        if (nFact == null || nFact.isBlank()) {
            throw new IllegalArgumentException("El parámetro 'nFact' es requerido");
        }

        try {
            String connectionUrl = databaseConfig.getConnectionUrl("IPSoft100_ST");

            try (Connection conn = DriverManager.getConnection(connectionUrl)) {
                String sql = "UPDATE FF SET FF.Rips_Cuv = ? FROM FacturaFinal FF WHERE FF.NFact = ? AND EXISTS (SELECT 1 FROM Rips_Transaccion RT WHERE RT.NFact = FF.NFact)";

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    String cuvProcesado = (ripsCuv == null || ripsCuv.trim().isEmpty()) ? null : ripsCuv;
                    stmt.setObject(1, cuvProcesado, java.sql.Types.VARCHAR);
                    stmt.setString(2, nFact);

                    int filasAfectadas = stmt.executeUpdate();

                    if (filasAfectadas > 0) {
                        return Map.of(
                            "mensaje", "CUV actualizado correctamente",
                            "filasAfectadas", filasAfectadas
                        );
                    } else {
                        return Map.of(
                            "mensaje", "No se encontró una factura con el NFact especificado.",
                            "filasAfectadas", 0
                        );
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error de base de datos: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("Error inesperado al actualizar el CUV: " + e.getMessage(), e);
        }
    }


    public Map<String, Object> actualizarCuvTransaccion(String nFact, String cuv, Integer idEstadoValidacion) {
        if (nFact == null || nFact.isBlank()) {
            throw new IllegalArgumentException("El parámetro 'nFact' es obligatorio");
        }

        if (cuv == null || cuv.isBlank()) {
            cuv = null; 
        }

        try {
            String connectionUrl = databaseConfig.getConnectionUrl("IPSoft100_ST");

            try (Connection conn = DriverManager.getConnection(connectionUrl)) {
                String sql = "UPDATE RP SET RP.CUV = ?, RP.IdEstadoValidacion = ? FROM Rips_Transaccion RP WHERE NFact = ?";

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, (cuv != null && !cuv.isBlank()) ? cuv : null);
                    stmt.setInt(2, idEstadoValidacion);
                    stmt.setString(3, nFact);

                    int filasAfectadas = stmt.executeUpdate();

                    if (filasAfectadas > 0) {
                        return Map.of(
                            "mensaje", "CUV e IdEstadoValidacion actualizados correctamente",
                            "filasAfectadas", filasAfectadas
                        );
                    } else {
                        return Map.of(
                            "mensaje", "No se encontró una transacción RIPS con el NFact especificado.",
                            "filasAfectadas", 0
                        );
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error de base de datos: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("Error al actualizar los datos de RIPS: " + e.getMessage(), e);
        }
    }

}
