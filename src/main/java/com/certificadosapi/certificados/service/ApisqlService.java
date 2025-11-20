package com.certificadosapi.certificados.service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.certificadosapi.certificados.config.DatabaseConfig;

@Service
public class ApisqlService {

    private static final Logger log = LoggerFactory.getLogger(ApisqlService.class);
    private final DatabaseConfig databaseConfig;

    @Autowired
    public ApisqlService(DatabaseConfig databaseConfig) {
        this.databaseConfig = databaseConfig;
    }

    // OBTENER CUV POR NFACT
    public Map<String, Object> obtenerCuv(String nFact) {

        try {
            String connectionUrl = databaseConfig.getConnectionUrl("IPSoft100_ST");
            log.debug("Conectando a BD: {}", connectionUrl);

            try (Connection conn = DriverManager.getConnection(connectionUrl)) {

                String sql = "SELECT Rips_CUV FROM FacturaFinal WHERE NFact = ? AND Rips_CUV IS NOT NULL";

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, nFact);

                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            String cuv = rs.getString("Rips_CUV");
                            log.debug("CUV encontrado para {}: {}", nFact, cuv);

                            return Map.of("success", true, "Rips_CUV", cuv);
                        } else {
                            log.debug("No se encontró CUV para factura {}", nFact);

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
            log.error("Error SQL en obtenerCuv: {}", e.getMessage(), e);
            throw new RuntimeException("Error de base de datos: " + e.getMessage(), e);

        } catch (Exception e) {
            log.error("Error inesperado en obtenerCuv: {}", e.getMessage(), e);
            throw new RuntimeException("Error inesperado: " + e.getMessage(), e);
        }
    }


    // ACTUALIZAR CUV EN FACTURAFINAL
    public Map<String, Object> actualizarCuvFF(String nFact, String ripsCuv) {

        log.info("Iniciando actualizarCuvFF");
        log.debug("Parámetros recibidos: nFact={}, ripsCuv={}", nFact, ripsCuv);

        if (nFact == null || nFact.isBlank()) {
            throw new IllegalArgumentException("El parámetro 'nFact' es requerido");
        }

        try {
            String connectionUrl = databaseConfig.getConnectionUrl("IPSoft100_ST");
            log.debug("Conectando a BD: {}", connectionUrl);

            try (Connection conn = DriverManager.getConnection(connectionUrl)) {

                String sql = """
                        UPDATE FF
                        SET FF.Rips_Cuv = ?, FF.Rips_FechaValidacion = GETDATE()
                        FROM FacturaFinal FF
                        WHERE FF.NFact = ?
                        AND EXISTS (SELECT 1 FROM Rips_Transaccion RT WHERE RT.NFact = FF.NFact)
                        """;

                log.debug("SQL ejecutado: {}", sql);

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {

                    String cuvProcesado = (ripsCuv == null || ripsCuv.trim().isEmpty()) ? null : ripsCuv;

                    stmt.setObject(1, cuvProcesado, java.sql.Types.VARCHAR);
                    stmt.setString(2, nFact);

                    log.debug("Parámetros SQL: p1={}, p2={}", cuvProcesado, nFact);

                    int filasAfectadas = stmt.executeUpdate();
                    log.info("CUV Actualizado para Factura: {}", nFact);

                    if (filasAfectadas > 0) {
                        return Map.of(
                                "mensaje", "CUV actualizado correctamente",
                                "filasAfectadas", filasAfectadas
                        );
                    } else {
                        log.warn("No se encontró factura {} para actualizar CUV", nFact);
                        return Map.of(
                                "mensaje", "No se encontró una factura con el NFact especificado.",
                                "filasAfectadas", 0
                        );
                    }
                }
            }
        } catch (SQLException e) {
            log.error("Error SQL en actualizarCuvFF: {}", e.getMessage(), e);
            throw new RuntimeException("Error de base de datos: " + e.getMessage(), e);

        } catch (Exception e) {
            log.error("Error inesperado en actualizarCuvFF: {}", e.getMessage(), e);
            throw new RuntimeException("Error inesperado al actualizar el CUV: " + e.getMessage(), e);
        }
    }

    // ACTUALIZAR CUV + ESTADO VALIDACIÓN EN TRANSACCIÓN
    public Map<String, Object> actualizarCuvTransaccion(String nFact, String cuv, Integer idEstadoValidacion) {

        log.info("Iniciando actualizarCuvTransaccion");
        log.debug("Parámetros recibidos: nFact={}, cuv={}, idEstadoValidacion={}",
                nFact, cuv, idEstadoValidacion);

        if (nFact == null || nFact.isBlank()) {
            throw new IllegalArgumentException("El parámetro 'nFact' es obligatorio");
        }

        if (cuv == null || cuv.isBlank()) {
            cuv = null;
        }

        try {
            String connectionUrl = databaseConfig.getConnectionUrl("IPSoft100_ST");

            log.debug("Conectando a BD: {}", connectionUrl);

            try (Connection conn = DriverManager.getConnection(connectionUrl)) {

                String sql = """
                        UPDATE RP
                        SET RP.CUV = ?, RP.IdEstadoValidacion = ?
                        FROM Rips_Transaccion RP
                        WHERE NFact = ?
                        """;

                log.debug("SQL ejecutado: {}", sql);

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {

                    stmt.setString(1, cuv);
                    stmt.setInt(2, idEstadoValidacion);
                    stmt.setString(3, nFact);

                    log.debug("Parámetros SQL: p1={}, p2={}, p3={}", cuv, idEstadoValidacion, nFact);

                    int filasAfectadas = stmt.executeUpdate();
                    log.info("CUV actualizado en Transacciones para Factura: {}", nFact);

                    if (filasAfectadas > 0) {
                        return Map.of(
                                "mensaje", "CUV e IdEstadoValidacion actualizados correctamente",
                                "filasAfectadas", filasAfectadas
                        );
                    } else {
                        log.warn("No existe transacción RIPS para {}", nFact);
                        return Map.of(
                                "mensaje", "No se encontró una transacción RIPS con el NFact especificado.",
                                "filasAfectadas", 0
                        );
                    }
                }
            }

        } catch (SQLException e) {
            log.error("Error SQL en actualizarCuvTransaccion: {}", e.getMessage(), e);
            throw new RuntimeException("Error de base de datos: " + e.getMessage(), e);

        } catch (Exception e) {
            log.error("Error inesperado en actualizarCuvTransaccion: {}", e.getMessage(), e);
            throw new RuntimeException("Error al actualizar los datos de RIPS: " + e.getMessage(), e);
        }
    }

    // OBTENER ESTADO VALIDACIÓN
    public Map<String, Object> obtenerEstadoValidacion(String nFact) {

        log.info("Iniciando obtenerEstadoValidacion");
        log.debug("Parámetro recibido: nFact={}", nFact);

        if (nFact == null || nFact.isBlank()) {
            throw new IllegalArgumentException("El parámetro 'nFact' es requerido y no puede estar vacío");
        }

        try {
            String connectionUrl = databaseConfig.getConnectionUrl("IPSoft100_ST");

            log.debug("Conectando a BD: {}", connectionUrl);

            try (Connection conn = DriverManager.getConnection(connectionUrl)) {

                String sql = "SELECT RT.IdEstadoValidacion FROM Rips_Transaccion RT WHERE NFact = ?";
                log.debug("SQL ejecutado: {}", sql);

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {

                    stmt.setString(1, nFact);
                    log.debug("Parámetro SQL: p1={}", nFact);

                    try (ResultSet rs = stmt.executeQuery()) {

                        if (rs.next()) {
                            Integer idEstado = rs.getInt("IdEstadoValidacion");
                            if (rs.wasNull()) idEstado = null;

                            log.info("Estado de validación para {}: {}", nFact, idEstado);

                            return Map.of(
                                    "nFact", nFact,
                                    "idEstadoValidacion", idEstado != null ? idEstado : "null"
                            );
                        } else {
                            log.warn("No se encontró estado de validación para {}", nFact);

                            return Map.of("mensaje", "No se encontró una transacción RIPS con el NFact especificado.");
                        }
                    }
                }
            }

        } catch (SQLException e) {
            log.error("Error SQL en obtenerEstadoValidacion: {}", e.getMessage(), e);
            throw new RuntimeException("Error de base de datos: " + e.getMessage(), e);

        } catch (Exception e) {
            log.error("Error inesperado en obtenerEstadoValidacion: {}", e.getMessage(), e);
            throw new RuntimeException("Error al consultar el estado de validación: " + e.getMessage(), e);
        }
    }


    // EJECUTAR GENERACIÓN DE RIPS
    public String ejecutarRips(String nFact) {

        log.info("Iniciando ejecutarRips");
        log.debug("Parámetro recibido: nFact={}", nFact);

        try {
            String connectionUrl = databaseConfig.getConnectionUrl("IPSoft100_ST");
            log.debug("Conectando a BD: {}", connectionUrl);

            try (Connection conn = DriverManager.getConnection(connectionUrl)) {

                String sql = "EXEC pa_Rips_JSON_Generar '" + nFact + "', 0, 1, 1";
                log.debug("SQL ejecutado: {}", sql);

                try (Statement stmt = conn.createStatement()) {

                    stmt.execute(sql);
                    log.info("Procedimiento ejecutado correctamente para {}", nFact);

                    return "Procedimiento ejecutado correctamente para el cliente: " + nFact;
                }
            }

        } catch (SQLException e) {
            log.error("Error SQL en ejecutarRips: {}", e.getMessage(), e);
            throw new RuntimeException("Error de base de datos: " + e.getMessage(), e);

        } catch (Exception e) {
            log.error("Error inesperado en ejecutarRips: {}", e.getMessage(), e);
            throw new RuntimeException("Error al ejecutar RIPS: " + e.getMessage(), e);
        }
    }
}

