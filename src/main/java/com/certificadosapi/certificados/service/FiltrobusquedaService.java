package com.certificadosapi.certificados.service;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.sql.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.certificadosapi.certificados.config.DatabaseConfig;


/**
 * Servicio encargado de realizar búsquedas y filtrados de información del sistema.
 * Proporciona métodos para consultar facturas, terceros, contratos, atenciones
 * y cuentas de cobro mediante diversos criterios de filtrado.
 * 
 * @author Marlon Morales Llanos
 */

@Service
public class FiltrobusquedaService {

    private static final Logger log = LoggerFactory.getLogger(FiltrobusquedaService.class);

    private final DatabaseConfig databaseConfig;


    /**
     * Busca facturas en el sistema aplicando múltiples criterios de filtrado.
     * Ejecuta el procedimiento almacenado pa_Net_Facturas_JSON y retorna los resultados
     * con formato especial para el campo FechaFactura.
     * 
     * @param fechaDesde Fecha inicial del rango de búsqueda (puede ser null)
     * @param fechaHasta Fecha final del rango de búsqueda (puede ser null)
     * @param idTercero ID del tercero a filtrar (puede ser null o vacío)
     * @param noContrato Número de contrato a filtrar (puede ser null o vacío)
     * @param nFact Número de factura a filtrar (puede ser null o vacío)
     * @param cuentaCobro Número de cuenta de cobro a filtrar (puede ser null)
     * @param tipoFecha Indica el tipo de fecha a utilizar en el filtrado
     * @return Lista de mapas conteniendo los datos de las facturas encontradas.
     *         Cada mapa representa una fila con los nombres de columna como llaves
     * @throws IllegalArgumentException si fechaDesde es posterior a fechaHasta
     * @throws RuntimeException si ocurre un error durante la ejecución del procedimiento
     */
    @Autowired
    public FiltrobusquedaService(DatabaseConfig databaseConfig){
        this.databaseConfig = databaseConfig;
    }

    // BUSCAR FACTURAS MANUAL
    public List<Map<String, Object>> buscarFacturas(
            LocalDate fechaDesde,
            LocalDate fechaHasta,
            String idTercero,
            String noContrato,
            String nFact,
            Integer cuentaCobro,
            boolean tipoFecha
        ) {

        log.info("Iniciando búsqueda manual de facturas");
        log.debug("Parametros recibidos: fechaDesde={}, fechaHasta={}, idTercero={}, noContrato={}, nFact={}, cuentaCobro={}, tipoFecha={}",
            fechaDesde, fechaHasta, idTercero, noContrato, nFact, cuentaCobro, tipoFecha);


        if (fechaDesde != null && fechaHasta != null && fechaDesde.isAfter(fechaHasta)) {
            log.warn("Fecha inválida: fechaDesde={} es posterior a fechaHasta={}", fechaDesde, fechaHasta);
            throw new IllegalArgumentException("fechaDesde no puede ser posterior a fechaHasta");
        }

        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoft100_ST"))) {

            log.debug("Conexión establecida para consultar pa_Net_Facturas_JSON");

            String sql = "EXEC dbo.pa_Net_Facturas_JSON ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?";

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, "-1");

                if (fechaDesde != null) pstmt.setDate(2, Date.valueOf(fechaDesde));
                else pstmt.setNull(2, Types.DATE);

                if (fechaHasta != null) pstmt.setDate(3, Date.valueOf(fechaHasta));
                else pstmt.setNull(3, Types.DATE);

                pstmt.setString(4, "-1");

                try {
                    pstmt.setInt(5, (idTercero != null && !idTercero.isBlank()) ? Integer.parseInt(idTercero) : -1);
                } catch (NumberFormatException ex) {
                    log.warn("IdTercero inválido='{}' — se envía -1", idTercero);
                    pstmt.setInt(5, -1);
                }

                pstmt.setObject(6, (noContrato != null && !noContrato.isBlank()) ? noContrato : null);

                pstmt.setObject(7, (nFact != null && !nFact.isBlank()) ? nFact : null);

                pstmt.setInt(8, -1);

                pstmt.setObject(9, (cuentaCobro != null) ? cuentaCobro : null);

                pstmt.setNull(10, Types.BIT);

                pstmt.setBoolean(11, tipoFecha); 

                log.debug("Ejecutando procedimiento pa_Net_Facturas_JSON");

                try (ResultSet rs = pstmt.executeQuery()) {

                    List<Map<String, Object>> resultados = new ArrayList<>();
                    ResultSetMetaData meta = rs.getMetaData();
                    int colCount = meta.getColumnCount();

                    while (rs.next()) {
                        Map<String, Object> fila = new LinkedHashMap<>();
                        for (int i = 1; i <= colCount; i++) {

                            String colName = meta.getColumnName(i);
                            Object value = rs.getObject(i);

                            if ("FechaFactura".equalsIgnoreCase(colName)) {
                                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

                                if (value instanceof Timestamp) {
                                    fila.put(colName, sdf.format(value));
                                } else if (value instanceof Date) {
                                    fila.put(colName, sdf.format(new java.util.Date(((Date) value).getTime())));
                                } else fila.put(colName, value);
                            } else {
                                fila.put(colName, (value instanceof String) ? value.toString().trim() : value);
                            }
                        }
                        resultados.add(fila);
                    }

                    log.info("Búsqueda de facturas retornó {} registros", resultados.size());
                    return resultados;
                }
            }

        } catch (Exception e) {
            log.error("Error ejecutando pa_Net_Facturas_JSON: {}", e.getMessage());
            throw new RuntimeException("Error al ejecutar el procedimiento: " + e.getMessage(), e);
        }
    }



    /**
     * Obtiene la lista completa de terceros registrados en el sistema.
     * Solo incluye terceros que tienen contratos asociados.
     * 
     * @return Lista de mapas conteniendo la información de terceros con las llaves:
     *         "idTerceroKey" (String) - ID único del tercero,
     *         "nomTercero" (String) - Nombre del tercero.
     *         Los resultados están ordenados alfabéticamente por nombre
     * @throws RuntimeException si ocurre un error durante la consulta
     */
    public List<Map<String, Object>> obtenerTerceros() {
        log.info("Obteniendo terceros del sistema");

        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoftFinanciero_ST"))) {

            String sql = """
            SELECT DISTINCT T.IdTerceroKey, T.NomTercero
            FROM IPSoftFinanciero_ST.dbo.Terceros T
            INNER JOIN IPSoft100_ST.dbo.Contratos C ON C.IdTerceroKey = T.IdTerceroKey
            ORDER BY T.NomTercero
            """;

            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {

                List<Map<String, Object>> resultados = new ArrayList<>();

                while (rs.next()) {
                    Map<String, Object> fila = new LinkedHashMap<>();
                    fila.put("idTerceroKey", rs.getString("IdTerceroKey"));
                    fila.put("nomTercero", rs.getString("NomTercero"));
                    resultados.add(fila);
                }

                log.debug("Se encontraron {} terceros", resultados.size());
                return resultados;
            }

        } catch (Exception e) {
            log.error("Error consultando terceros: {}", e.getMessage());
            throw new RuntimeException("Error en la consulta: " + e.getMessage(), e);
        }
    }



    /**
     * Obtiene los contratos asociados a un tercero específico.
     * 
     * @param idTerceroKey ID del tercero para consultar sus contratos
     * @return Lista de mapas conteniendo información de contratos con las llaves:
     *         "noContrato" (String) - Número del contrato,
     *         "nomContrato" (String) - Nombre descriptivo del contrato.
     *         Los resultados están ordenados alfabéticamente por nombre de contrato
     * @throws IllegalArgumentException si el idTerceroKey es nulo o está vacío
     * @throws RuntimeException si ocurre un error durante la consulta
     */
    public List<Map<String, Object>> obtenerContratos(String idTerceroKey) {

        log.info("Obteniendo contratos para idTerceroKey={}", idTerceroKey);

        if (idTerceroKey == null || idTerceroKey.isBlank()) {
            log.warn("IdTerceroKey vacío en solicitud de contratos");
            throw new IllegalArgumentException("El IdTerceroKey es requerido.");
        }

        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoft100_ST"))) {

            String sql = """
            SELECT DISTINCT C.NoContrato, C.NomContrato
            FROM IPSoft100_ST.dbo.Contratos C
            WHERE C.IdTerceroKey = ?
            ORDER BY C.NomContrato
            """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, idTerceroKey);

                try (ResultSet rs = stmt.executeQuery()) {

                    List<Map<String, Object>> resultados = new ArrayList<>();

                    while (rs.next()) {
                        Map<String, Object> fila = new LinkedHashMap<>();
                        fila.put("noContrato", rs.getString("NoContrato"));
                        fila.put("nomContrato", rs.getString("NomContrato"));
                        resultados.add(fila);
                    }

                    log.debug("Contratos encontrados: {}", resultados.size());
                    return resultados;
                }
            }

        } catch (Exception e) {
            log.error("Error consultando contratos: {}", e.getMessage());
            throw new RuntimeException("Error en la consulta: " + e.getMessage(), e);
        }
    }



    /**
     * Busca atenciones médicas en el sistema aplicando múltiples criterios de filtrado.
     * Ejecuta el procedimiento almacenado pa_Net_Facturas_Historico_GenSoportes
     * para obtener el historial de atenciones.
     * 
     * @param IdAtencion ID específico de atención a buscar (puede ser null)
     * @param HistClinica Número de historia clínica (puede ser null)
     * @param Cliente ID del cliente/paciente (puede ser null)
     * @param NoContrato Número de contrato asociado (puede ser null)
     * @param IdAreaAtencion ID del área donde se realizó la atención (puede ser null)
     * @param IdUnidadAtencion ID de la unidad de atención (puede ser null)
     * @param FechaDesde Fecha inicial del rango de búsqueda (puede ser null)
     * @param FechaHasta Fecha final del rango de búsqueda (puede ser null)
     * @param nFact Número de factura asociada (puede ser null)
     * @param nCuentaCobro Número de cuenta de cobro (puede ser null)
     * @param soloFacturados Indica si solo se deben retornar atenciones facturadas (puede ser null)
     * @param cantSoportes Cantidad de documentos soportes igual o mayor al valor especificado (puede ser null)
     * @return Lista de mapas donde cada mapa representa una atención encontrada.
     *         Las llaves del mapa corresponden a los nombres de columna del resultado
     * @throws RuntimeException si ocurre un error durante la ejecución del procedimiento
     */
    public List<Map<String, Object>> buscarAtenciones(
            Long IdAtencion,
            String HistClinica,
            Integer Cliente,
            String NoContrato,
            Integer IdAreaAtencion,
            Integer IdUnidadAtencion,
            LocalDate FechaDesde,
            LocalDate FechaHasta,
            String nFact,
            Integer nCuentaCobro,
            Boolean soloFacturados,
            Integer cantSoportes) {

        log.info("Iniciando búsqueda de atenciones");

        log.info("Parametros recibidos: " +
            "IdAtencion={}, HistClinica={}, Cliente={}, NoContrato={}, IdAreaAtencion={}, IdUnidadAtencion={}, " +
            "FechaDesde={}, FechaHasta={}, nFact={}, nCuentaCobro={}, soloFacturados={}, cantSoportes={}",
            IdAtencion, HistClinica, Cliente, NoContrato, IdAreaAtencion, IdUnidadAtencion,
            FechaDesde, FechaHasta, nFact, nCuentaCobro, soloFacturados, cantSoportes);

        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoft100_ST"))) {

            String sql = "EXEC dbo.pa_Net_Facturas_Historico_GenSoportes ?,?,?,?,?,?,?,?,?,?,?,?";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setObject(1, IdAtencion);
                stmt.setObject(2, HistClinica);
                stmt.setObject(3, Cliente);
                stmt.setObject(4, NoContrato);
                stmt.setObject(5, IdAreaAtencion);
                stmt.setObject(6, IdUnidadAtencion);
                stmt.setObject(7, FechaDesde != null ? Date.valueOf(FechaDesde) : null);
                stmt.setObject(8, FechaHasta != null ? Date.valueOf(FechaHasta) : null);
                stmt.setObject(9, nFact);
                stmt.setObject(10, nCuentaCobro);
                stmt.setObject(11, soloFacturados);
                stmt.setObject(12, cantSoportes);

                log.debug("Ejecutando procedimiento pa_Net_Facturas_Historico_GenSoportes");

                try (ResultSet rs = stmt.executeQuery()) {

                    List<Map<String, Object>> resultados = new ArrayList<>();
                    ResultSetMetaData meta = rs.getMetaData();
                    int colCount = meta.getColumnCount();

                    while (rs.next()) {
                        Map<String, Object> fila = new LinkedHashMap<>();
                        for (int i = 1; i <= colCount; i++) {
                            fila.put(meta.getColumnName(i), rs.getObject(i));
                        }
                        resultados.add(fila);
                    }

                    log.info("Consulta de atenciones retornó {} registros", resultados.size());
                    return resultados;
                }
            }

        } catch (Exception e) {
            log.error("Error consultando atenciones: {}", e.getMessage());
            throw new RuntimeException("Error consultando admisiones: " + e.getMessage(), e);
        }
    }

    /**
     * Consulta cuentas de cobro del sistema aplicando criterios de filtrado.
     * Ejecuta el procedimiento almacenado Facturacion_CC_Lista para obtener
     * las cuentas de cobro que coincidan con los filtros proporcionados.
     * 
     * @param nCuentaCobro Número específico de cuenta de cobro (puede ser null)
     * @param fechaDesde Fecha inicial del rango de búsqueda (puede ser null)
     * @param fechaHasta Fecha final del rango de búsqueda (puede ser null)
     * @param idTercero ID del tercero asociado a la cuenta (puede ser null)
     * @param noContrato Número de contrato asociado (puede ser null)
     * @return Lista de mapas donde cada mapa representa una cuenta de cobro.
     *         Las llaves del mapa corresponden a los nombres de columna del resultado
     * @throws RuntimeException si ocurre un error durante la ejecución del procedimiento
     */
    public List<Map<String, Object>> consultarCuentaCobro(
        Integer nCuentaCobro,
        LocalDate fechaDesde,
        LocalDate fechaHasta,
        String idTercero,
        String noContrato) {

        log.info("Iniciando búsqueda de cuentas de cobro");

        log.info("Parametros recibidos: " +
            "nCuentaCobro={}, FechaDesde={}, FechaHasta={}, idTercero={}, NoContrato={}",
            nCuentaCobro, fechaDesde, fechaHasta, idTercero, noContrato);

        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoft100_ST"))) {

            String sql = "EXEC dbo.Facturacion_CC_Lista ?,?,?,?,?";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setObject(1, nCuentaCobro);
                stmt.setObject(2, fechaDesde != null ? Date.valueOf(fechaDesde) : null);
                stmt.setObject(3, fechaHasta != null ? Date.valueOf(fechaHasta) : null);
                stmt.setObject(4, idTercero);
                stmt.setObject(5, noContrato);

                log.debug("Ejecutando procedimiento Facturacion_CC_Lista");

                try (ResultSet rs = stmt.executeQuery()) {

                    List<Map<String, Object>> resultados = new ArrayList<>();
                    ResultSetMetaData meta = rs.getMetaData();
                    int colCount = meta.getColumnCount();

                    while (rs.next()) {
                        Map<String, Object> fila = new LinkedHashMap<>();
                        for (int i = 1; i <= colCount; i++) {
                            fila.put(meta.getColumnName(i), rs.getObject(i));
                        }
                        resultados.add(fila);
                    }

                    log.info("Cuenta de cobro retornó {} registros", resultados.size());
                    return resultados;
                }
            }

        } catch (Exception e) {
            log.error("Error consultando cuentas de cobro: {}", e.getMessage());
            throw new RuntimeException("Error consultando cuentas de cobro: " + e.getMessage(), e);
        }
    }
}
