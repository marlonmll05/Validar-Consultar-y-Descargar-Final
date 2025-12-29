package com.certificadosapi.certificados.service.atenciones;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Service;

import com.certificadosapi.certificados.config.DatabaseConfig;


/**
 * Servicio encargado de realizar consultas a base de datos
 * relacionadas con facturación, soportes y validaciones de cuentas de cobro.
 *
 * Contiene métodos para:
 * - Cargar tablas dinámicas para filtros
 * - Validar existencia de facturas
 * - Consultar anexos por admisión
 * - Validar cuentas de cobro mediante procedimientos almacenados
 */
@Service
public class ConsultaService { 

    private static final Logger log = LoggerFactory.getLogger(ConsultaService.class);

    private DatabaseConfig databaseConfig;

    @Autowired
    public ConsultaService(DatabaseConfig databaseConfig){
        this.databaseConfig = databaseConfig;

    }

    /**
     * Obtiene datos genéricos desde el procedimiento almacenado
     * pa_Net_Facturas_Tablas.
     *
     * Este método se utiliza principalmente para llenar
     * los selects de los filtros de búsqueda en el frontend.
     *
     * @param idTabla Identificador del tipo de tabla a consultar
     * @param id Identificador adicional (dependiente del idTabla)
     * @return Lista de filas representadas como Map columna → valor
     * @throws SQLException Error de base de datos
     */
    public List<Map<String, Object>> obtenerTablas(int idTabla, int id) throws SQLException {
        
        log.info("Obteniendo datos de tabla - idTabla: {}, id: {}", idTabla, id);
        log.debug("Ejecutando procedimiento almacenado: pa_Net_Facturas_Tablas");

        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoft100_ST"));
             PreparedStatement ps = conn.prepareStatement("EXEC dbo.pa_Net_Facturas_Tablas ?, ?")) {
            
            ps.setInt(1, idTabla);
            ps.setInt(2, id);

            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> resultados = new ArrayList<>();
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();

                log.debug("Procesando resultados con {} columnas", colCount);

                while (rs.next()) {
                    Map<String, Object> fila = new LinkedHashMap<>();
                    for (int i = 1; i <= colCount; i++) {
                        String colName = meta.getColumnName(i);
                        Object value = rs.getObject(i);
                        fila.put(colName, (value instanceof String) ? value.toString().trim() : value);
                    }
                    resultados.add(fila);
                }

                if (resultados.isEmpty()) {
                    log.warn("No se encontraron resultados para idTabla: {}, id: {}", idTabla, id);
                } else {
                    log.info("Se obtuvieron {} registros para idTabla: {}", resultados.size(), idTabla);
                }

                return resultados;
            }
        }
    }

    /**
     * Verifica si existe una factura de venta (soporte 18)
     * asociada a una admisión específica.
     *
     * @param idAdmision Identificador de la admisión
     * @return Map con la clave "cantidad" indicando el número de registros encontrados
     * @throws SQLException Error de base de datos
     */
    public Map<String, Integer> countSoporte18(Long idAdmision) throws SQLException {

        log.info("Verificando factura de venta (soporte 18) para idAdmision: {}", idAdmision);
        
        String sql = "SELECT COUNT(*) AS Cantidad " +
                     "FROM dbo.tbl_Net_Facturas_ListaPdf " +
                     "WHERE IdAdmision = ? AND IdSoporteKey = 18";

        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("Asclepius_Documentos"));
             PreparedStatement ps = conn.prepareStatement(sql)) {

            log.debug("Ejecutando query de conteo para soporte 18");
            ps.setLong(1, idAdmision);

            try (ResultSet rs = ps.executeQuery()) {
                int cantidad = 0;
                if (rs.next()) {
                    cantidad = rs.getInt("Cantidad");
                }
                
                if (cantidad > 0) {
                    log.info("Factura de venta encontrada para idAdmision: {} (cantidad: {})", idAdmision, cantidad);
                } else {
                    log.warn("No existe factura de venta para idAdmision: {}", idAdmision);
                }
                
                return Map.of("cantidad", cantidad);
            }
        }
    }

    /**
     * Obtiene los IDs de los soportes (anexos)
     * asociados a una admisión específica.
     *
     * @param idAdmision Identificador de la admisión
     * @return Lista de IdSoporteKey encontrados
     * @throws SQLException Error de base de datos
     */
    public List<Long> obtenerAnexosPorAdmision(Long idAdmision) throws SQLException {

        log.info("Obteniendo anexos para idAdmision: {}", idAdmision);
        
        String sql = "SELECT IdSoporteKey " +
                     "FROM tbl_Net_Facturas_ListaPdf PDF " +
                     "INNER JOIN IPSoft100_ST.dbo.tbl_Net_Facturas_DocSoporte DS ON PDF.IdSoporteKey = DS.Id " +
                     "WHERE IdAdmision = ? " +
                     "ORDER BY IdSoporteKey";

        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("Asclepius_Documentos"));
             PreparedStatement ps = conn.prepareStatement(sql)) {

            log.debug("Ejecutando query para obtener IdSoporteKey");
            ps.setLong(1, idAdmision);

            try (ResultSet rs = ps.executeQuery()) {
                List<Long> anexos = new ArrayList<>();
                while (rs.next()) {
                    anexos.add(rs.getLong("IdSoporteKey"));
                }

                if (anexos.isEmpty()){
                    log.warn("No se encontraron resultados para idAdmision: {}", idAdmision);
                }
                else{
                    log.info("Se encontraron {} para la idAdmision {} - IDs: {}", anexos.size(), idAdmision, anexos);
                }
                    
                return anexos;
            }
        }
    }
    
    /**
     * Valida una Cuenta de Cobro mediante el procedimiento almacenado
     * Pa_FacturacionExportacion_DS_Validar.
     *
     * @param cuentaCobro Número de la cuenta de cobro
     * @return Resultado de la validación en formato String
     * @throws SQLException Error de base de datos
     */
    public String validarCuentacobro (Integer cuentaCobro) throws SQLException {
        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoft100_ST"))) {
            String sql = "EXEC Pa_FacturacionExportacion_DS_Validar ?, ?";
            try (CallableStatement cStatement = conn.prepareCall(sql)) {
                cStatement.setInt(1, cuentaCobro);  

                cStatement.registerOutParameter(2, Types.LONGVARCHAR);

                cStatement.execute();

                String resultado = cStatement.getString(2);

                log.info("Resultados validacion Cuenta Cobro {}", resultado);

                return resultado;
            }
        }
    } 
}
