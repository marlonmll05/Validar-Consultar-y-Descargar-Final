package com.certificadosapi.certificados.service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
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
 * 
 * Servicio encargado de administrar la configuración de documentos soporte
 *  y su relación con terceros dentro del módulo de facturación.
 * Permite consultar, insertar, actualizar y eliminar configuraciones
 * mediante procedimientos almacenados sobre la base de datos IPSoft100_ST.
 * 
 * @author Marlon Morales Llanos
 * 
*/
@Service
public class AbreviaturaService {

    
    private static final Logger log = LoggerFactory.getLogger(FiltrobusquedaService.class);

    private final DatabaseConfig databaseConfig;

    @Autowired
    public AbreviaturaService(DatabaseConfig databaseConfig){
        this.databaseConfig = databaseConfig;
    }


    /**
    Consulta los documentos soporte filtrando por nombre e inactividad.

    @param nombre Nombre o coincidencia del documento soporte.
    @param inactivo Estado del documento soporte.
    @return Lista de documentos soporte encontrados.
    @throws RuntimeException Error durante la consulta SQL.
    */
    public List<Map<String, Object>> consultarDocSoporte(String nombre, Boolean inactivo) {

        log.info("Iniciando consultarDocSoporte");
        log.debug("Parámetros recibidos: nombre={}, inactivo={}", nombre, inactivo);

        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoft100_ST"))) {

            String sql = "EXEC dbo.pa_net_facturas_consultar_docsoporte ?,?";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setObject(1, nombre);
                stmt.setObject(2, inactivo != null ? (inactivo ? 1 : 0) : 0);

                log.info("Ejecutando procedimiento pa_net_facturas_consultar_docsoporte");

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

                    log.info("consultarDocSoporte retornó {} registros", resultados.size());
                    return resultados;
                }
            }

        } catch (Exception e) {
            log.error("Error en consultarDocSoporte: {}", e.getMessage());
            throw new RuntimeException("Error consultando doc soporte: " + e.getMessage(), e);
        }
    }


    /**

    Consulta los terceros asociados a un documento soporte.

    @param idDocSoporte Identificador del documento soporte.
    @return Lista de terceros asociados.
    @throws RuntimeException Error durante la consulta SQL.
    */
    public List<Map<String, Object>> consultarDocSoporteTercero(Integer idDocSoporte) {

        log.info("Iniciando consultarDocSoporteTercero");
        log.debug("Parámetros recibidos: idDocSoporte={}", idDocSoporte);

        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoft100_ST"))) {

            String sql = "EXEC dbo.pa_net_facturas_consultar_docsoporte_tercero ?";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setObject(1, idDocSoporte);

                log.info("Ejecutando procedimiento pa_net_facturas_consultar_docsoporte_tercero");

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

                    log.info("consultarDocSoporteTercero retornó {} registros", resultados.size());
                    return resultados;
                }
            }

        } catch (Exception e) {
            log.error("Error en consultarDocSoporteTercero: {}", e.getMessage());
            throw new RuntimeException("Error consultando doc soporte tercero: " + e.getMessage(), e);
        }
    }


    /**

        Inserta una nueva configuración de documento soporte para un tercero.


        @param idDocSoporte Identificador del documento soporte.
        @param idTerceroKey Identificador del tercero.
        @param prefijo Prefijo asociado al documento.
        @param obligatorio Indica si el documento es obligatorio.
        @param inactivo Estado del registro.
        @throws IllegalArgumentException Si faltan parámetros requeridos.
        @throws RuntimeException Error durante la inserción SQL.
    */
    public void insertarDocSoporteTercero(Integer idDocSoporte, Integer idTerceroKey, String prefijo, Boolean obligatorio, Boolean inactivo) {

        log.info("Iniciando insertarDocSoporteTercero");
        log.debug("Parámetros recibidos: idDocSoporte={}, idTerceroKey={}, prefijo={}, obligatorio={}, inactivo={}", 
                idDocSoporte, idTerceroKey, prefijo, obligatorio, inactivo);

        if (idDocSoporte == null || idTerceroKey == null || prefijo == null || prefijo.isBlank()) {
            throw new IllegalArgumentException("idDocSoporte, idTerceroKey y prefijo son requeridos");
        }

        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoft100_ST"))) {

            String sql = "EXEC dbo.pa_net_facturas_insertar_docsoporte_tercero ?,?,?,?,?";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, idDocSoporte);
                stmt.setInt(2, idTerceroKey);
                stmt.setString(3, prefijo);
                stmt.setObject(4, obligatorio != null ? (obligatorio ? 1 : 0) : 1);
                stmt.setObject(5, inactivo != null ? (inactivo ? 1 : 0) : 0);

                log.info("Ejecutando procedimiento pa_net_facturas_insertar_docsoporte_tercero");
                stmt.executeUpdate();
                log.info("insertarDocSoporteTercero ejecutado correctamente");
            }

        } catch (Exception e) {
            log.error("Error en insertarDocSoporteTercero: {}", e.getMessage());
            throw new RuntimeException("Error insertando doc soporte tercero: " + e.getMessage(), e);
        }
    }


    /**

        Actualiza una configuración existente de documento soporte y tercero.


        @param id Identificador del registro.
        @param prefijo Prefijo asociado al documento.
        @param idTerceroKey Identificador del tercero.
        @param obligatorio Indica si el documento es obligatorio.
        @param inactivo Estado del registro.
        @throws IllegalArgumentException Si faltan parámetros requeridos.
        @throws RuntimeException Error durante la actualización SQL.
    */
    public void actualizarDocSoporteTercero(Integer id, String prefijo, Integer idTerceroKey, Boolean obligatorio, Boolean inactivo) {

        log.info("Iniciando actualizarDocSoporteTercero");
        log.debug("Parámetros: id={}, prefijo={}, idTerceroKey={}, obligatorio={}, inactivo={}", id, prefijo, idTerceroKey, obligatorio, inactivo);

        if (id == null || prefijo == null || prefijo.isBlank() || idTerceroKey == null) {
            throw new IllegalArgumentException("id, prefijo e idTerceroKey son requeridos");
        }

        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoft100_ST"))) {

            String sql = "EXEC dbo.pa_net_facturas_actualizar_docsoporte_tercero ?,?,?,?,?";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, id);
                stmt.setString(2, prefijo);
                stmt.setInt(3, idTerceroKey);
                stmt.setObject(4, obligatorio != null ? (obligatorio ? 1 : 0) : 1);
                stmt.setObject(5, inactivo != null ? (inactivo ? 1 : 0) : 0);

                log.info("Ejecutando pa_net_facturas_actualizar_docsoporte_tercero");
                stmt.executeUpdate();
                log.info("actualizarDocSoporteTercero ejecutado correctamente");
            }

        } catch (Exception e) {
            log.error("Error en actualizarDocSoporteTercero: {}", e.getMessage());
            throw new RuntimeException("Error actualizando doc soporte tercero: " + e.getMessage(), e);
        }
    }

    /**
    Elimina una configuración de documento soporte asociada a un tercero.

    @param id Identificador del registro.
    @throws IllegalArgumentException Si el id es null.
    @throws RuntimeException Error durante la eliminación SQL.
    */
    public void eliminarDocSoporteTercero(Integer id) {

        log.info("Iniciando eliminarDocSoporteTercero");
        log.debug("Parámetros: id={}", id);

        if (id == null) {
            throw new IllegalArgumentException("El id es requerido");
        }

        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoft100_ST"))) {

            String sql = "EXEC dbo.pa_net_facturas_eliminar_docsoporte_tercero ?";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, id);

                log.info("Ejecutando pa_net_facturas_eliminar_docsoporte_tercero");
                stmt.executeUpdate();
                log.info("eliminarDocSoporteTercero ejecutado correctamente");
            }

        } catch (Exception e) {
            log.error("Error en eliminarDocSoporteTercero: {}", e.getMessage());
            throw new RuntimeException("Error eliminando doc soporte tercero: " + e.getMessage(), e);
        }
    }
    
}
