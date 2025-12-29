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

/**
 * Servicio encargado de gestionar operaciones relacionadas con RIPS (Registro Individual
 * de Prestación de Servicios de Salud) y códigos CUV (Código Único de Validación).
 * Proporciona métodos para consultar, actualizar y generar información de validación
 * de facturas ante el ministerio.
 * 
 * @author Marlon Morales Llanos
 */

@Service
public class ApisqlService {

    private static final Logger log = LoggerFactory.getLogger(ApisqlService.class);
    private final DatabaseConfig databaseConfig;


    /**
     * Constructor de ApisqlService con inyección de dependencias.
     * 
     * @param databaseConfig Objeto de configuración para las conexiones a base de datos
     */
    @Autowired
    public ApisqlService(DatabaseConfig databaseConfig) {
        this.databaseConfig = databaseConfig;
    }

    /**
     * Obtiene el código CUV (Código Único de Validación) asociado a una factura específica.
     * Busca en la tabla FacturaFinal el CUV de la factura solicitada.
     * 
     * @param nFact Número de factura para consultar el CUV
     * @return Mapa con la información del resultado:
     *         Si se encuentra: {"success": true, "Rips_CUV": "codigo_cuv"}
     *         Si no se encuentra: {"success": false, "message": "descripción", "Rips_CUV": ""}
     * @throws RuntimeException si ocurre un error SQL o inesperado durante la consulta
     */
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
            log.error("Error SQL en obtenerCuv: {}", e.getMessage());
            throw new RuntimeException("Error de base de datos: " + e.getMessage());

        } catch (Exception e) {
            log.error("Error inesperado en obtenerCuv: {}", e.getMessage());
            throw new RuntimeException("Error inesperado: " + e.getMessage());
        }
    }


   /**
     * Actualiza el código CUV y la fecha de validación en la tabla FacturaFinal.
     * Solo actualiza si existe un registro correspondiente en Rips_Transaccion.
     * 
     * @param nFact Número de factura a actualizar
     * @param ripsCuv Código CUV a asignar (puede ser null o vacío para establecer NULL)
     * @return Mapa con el resultado de la operación:
     *         "mensaje" (String) - Descripción del resultado,
     *         "filasAfectadas" (Integer) - Cantidad de registros actualizados (0 o 1)
     * @throws IllegalArgumentException si nFact es nulo o vacío
     * @throws RuntimeException si ocurre un error SQL o inesperado durante la actualización
     */
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
            log.error("Error SQL en actualizarCuvFF: {}", e.getMessage());
            throw new RuntimeException("Error de base de datos: " + e.getMessage());

        } catch (Exception e) {
            log.error("Error inesperado en actualizarCuvFF: {}", e.getMessage());
            throw new RuntimeException("Error inesperado al actualizar el CUV: " + e.getMessage());
        }
    }

    /**
     * Actualiza el código CUV y el estado de validación en la tabla Rips_Transaccion.
     * Permite establecer simultáneamente el CUV y el estado del proceso de validación.
     * 
     * @param nFact Número de factura a actualizar
     * @param cuv Código CUV a asignar (puede ser null o vacío para establecer NULL)
     * @param idEstadoValidacion ID del estado de validación a establecer
     * @return Mapa con el resultado de la operación:
     *         "mensaje" (String) - Descripción del resultado,
     *         "filasAfectadas" (Integer) - Cantidad de registros actualizados
     * @throws IllegalArgumentException si nFact es nulo o vacío
     * @throws RuntimeException si ocurre un error SQL o inesperado durante la actualización
     */
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
            log.error("Error SQL en actualizarCuvTransaccion: {}", e.getMessage());
            throw new RuntimeException("Error de base de datos: " + e.getMessage());

        } catch (Exception e) {
            log.error("Error inesperado en actualizarCuvTransaccion: {}", e.getMessage());
            throw new RuntimeException("Error al actualizar los datos de RIPS: " + e.getMessage());
        }
    }

    /**
     * Obtiene el estado de validación actual de una transacción RIPS.
     * Consulta el IdEstadoValidacion de la tabla Rips_Transaccion para una factura específica.
     * 
     * @param nFact Número de factura a consultar
     * @return Mapa con la información del estado:
     *         Si se encuentra: {"nFact": "numero", "idEstadoValidacion": estado_o_"null"}
     *         Si no se encuentra: {"mensaje": "descripción del error"}
     * @throws IllegalArgumentException si nFact es nulo o vacío
     * @throws RuntimeException si ocurre un error SQL o inesperado durante la consulta
     */
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
            log.error("Error SQL en obtenerEstadoValidacion: {}", e.getMessage());
            throw new RuntimeException("Error de base de datos: " + e.getMessage());

        } catch (Exception e) {
            log.error("Error inesperado en obtenerEstadoValidacion: {}", e.getMessage());
            throw new RuntimeException("Error al consultar el estado de validación: " + e.getMessage());
        }
    }


    /**
     * Ejecuta el procedimiento almacenado para generar archivos RIPS en formato JSON.
     * Invoca pa_Rips_JSON_Generar con parámetros fijos para generar la documentación
     * requerida para validación ante el ministerio.
     * 
     * @param nFact Número de factura para la cual generar los RIPS
     * @return String con mensaje de confirmación de ejecución exitosa
     * @throws SQLException si ocurre un error SQL durante la ejecución del procedimiento
     * @throws Exception si ocurre cualquier otro error inesperado
     */
    public String ejecutarRips(String nFact) throws SQLException, Exception {

        log.debug("Parámetro recibido: nFact={}", nFact);

        try {
            String connectionUrl = databaseConfig.getConnectionUrl("IPSoft100_ST");
            log.debug("Conectando a BD: {}", connectionUrl);

            try (Connection conn = DriverManager.getConnection(connectionUrl)) {

                String sql = "EXEC pa_Rips_JSON_Generar '" + nFact + "', 0, 1, 1";
                log.debug("SQL ejecutado: {}", sql);

                try (Statement stmt = conn.createStatement()) {

                    stmt.execute(sql);

                    return "Procedimiento ejecutado correctamente para el cliente: " + nFact;
                }
            }

        } catch (SQLException e) {
            log.error("Error SQL en ejecutarRips: {}", e.getMessage());
            throw new SQLException(e.getMessage());

        } catch (Exception e) {
            log.error("Error inesperado en ejecutarRips: {}", e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }
}

