package com.certificadosapi.certificados.service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.certificadosapi.certificados.config.DatabaseConfig;
import com.certificadosapi.certificados.service.atenciones.AnexarService;


/**
 * Servicio para la gestión de consultas IHCE (Historia Clínica Electrónica).
 * Provee métodos para consultar RDAs (Registros de Atención) de pacientes
 * mediante procedimientos almacenados en la base de datos Asclepius_Documentos.
 * 
 * @author Marlon Morales Llanos
 */

@Service
public class IhceService {

    private static final Logger log = LoggerFactory.getLogger(AnexarService.class);

    private final DatabaseConfig databaseConfig;

    @Autowired
    public IhceService(DatabaseConfig databaseConfig){
        this.databaseConfig = databaseConfig;
    }

    /**
     * Ejecuta un procedimiento almacenado según el tipo de consulta RDA solicitado.
     *
     * Tipos disponibles:
     *   1  → Pa_IOHC_Audit_RDA_Paciente
     *   2  → Pa_IOHC_Audit_RDA_Urgencias
     *   6  → Pa_IOHC_Audit_RDA_Procedimientos_EnAtencion
     *   7  → Pa_IOHC_Audit_RDA_Medicamentos_EnAtencion
     *   8  → Pa_IOHC_Audit_RDA_OtrasTecnologiasAdministradas_EnAtencion
     *   10 → Pa_IOHC_Audit_RDA_Formulacion
     *   11 → Pa_IOHC_Audit_RDA_OrdenesMedicas_AlEgreso
     *   12 → Pa_IOHC_Audit_RDA_OrdenesMedicas_AlEgresoOtrasTecnologias
     *
     * @param IdAdmision ID de la admisión del paciente
     * @param IdSesion   ID de la sesión/entorno de atención
     * @param Tipo       Tipo de consulta RDA (ver tabla anterior)
     * @return Lista de mapas con las columnas y valores retornados por el procedimiento
     * @throws SQLException Si ocurre un error en la conexión o ejecución del procedimiento
     */
    public List<Map<String, Object>> RdaConsulta(Integer IdAdmision, Integer IdSesion, Integer Tipo) throws SQLException {
        
        String sql = null;

        switch (Tipo) {
            case 1:
                sql = "EXEC Pa_IOHC_Audit_RDA_Paciente ?, ?"; 
                break;
            case 2:
                sql = "EXEC Pa_IOHC_Audit_RDA_Urgencias ?, ?";
                break;
            case 6:
                sql = "EXEC Pa_IOHC_Audit_RDA_Procedimientos_EnAtencion ?, ?";
                break;
            case 7:
                sql = "EXEC Pa_IOHC_Audit_RDA_Medicamentos_EnAtencion ?, ?";
                break;
            case 8:
                sql = "EXEC Pa_IOHC_Audit_RDA_OtrasTecnologiasAdministradas_EnAtencion ?, ?";
                break;
            case 10:
                sql = "EXEC Pa_IOHC_Audit_RDA_Formulacion ?, ?";
                break;
            case 11:
                sql = "EXEC Pa_IOHC_Audit_RDA_OrdenesMedicas_AlEgreso ?, ?";
                break;
            case 12:
                sql = "EXEC Pa_IOHC_Audit_RDA_OrdenesMedicas_AlEgresoOtrasTecnologias ?, ?";
                break;
           

        }

        log.info("Iniciando obtener RdaConsulta()");

        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("Asclepius_Documentos"))) {

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, IdAdmision);
                stmt.setInt(2, IdSesion);
                log.info("Ejecutando: " + sql);

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

                    log.info("Consulta de RDA retornó {} registros", resultados.size());
                    return resultados;
                }
            }
        }
    }

    /**
     * Obtiene la lista de RDAs disponibles según su documento.
     * Ejecuta el procedimiento pa_IOHC_Obtener_ListaRDA que retorna los registros
     * de atención asociados a un, incluyendo IdAdmision, IdSesionEntorno
     * y nombre del RDA.
     *
     * @param tipoId Tipo de identificación del paciente (CC, TI, CE, etc.)
     * @param numId  Número de identificación del paciente
     * @return Lista de mapas con IdAdmision, IdSesionEntorno y nombre_rda por cada RDA encontrado
     * @throws SQLException Si ocurre un error en la conexión o ejecución del procedimiento
     */
    public List<Map<String, Object>> ObtenerListaRda(String tipoId, String numId) throws SQLException {

        log.info("Iniciando ObtenerListaRda()");

        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("Asclepius_Documentos"))) {
            String sql = "EXEC pa_IOHC_Obtener_ListaRDA ?, ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, tipoId);
                stmt.setString(2, numId);
                log.debug("Ejecutando pa_IOHC_Obtener_ListaRDA");

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

                    log.info("Lista de RDA retornó {} registros", resultados.size());
                    return resultados;
                }
            }
        }
    }
}

  