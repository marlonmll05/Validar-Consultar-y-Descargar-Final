package com.certificadosapi.certificados.service;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.sql.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.certificadosapi.certificados.config.DatabaseConfig;

@Service
public class FiltrobusquedaService {

    private final DatabaseConfig databaseConfig;

    @Autowired
    public FiltrobusquedaService(DatabaseConfig databaseConfig){
        this.databaseConfig = databaseConfig;
    }

    //BUSCAR FACTURAS MANUAL
    public List<Map<String, Object>> buscarFacturas(LocalDate fechaDesde, LocalDate fechaHasta, String idTercero, String noContrato, String nFact, Integer cuentaCobro) {
        if (fechaDesde != null && fechaHasta != null && fechaDesde.isAfter(fechaHasta)) {
            throw new IllegalArgumentException("fechaDesde no puede ser posterior a fechaHasta");
        }

        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoft100_ST"))) {
            String sql = "EXEC dbo.pa_Net_Facturas_JSON ?, ?, ?, ?, ?, ?, ?, ?, ?";
            
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
                        pstmt.setString(6, noContrato); // @NoContrato
                    } else {
                        pstmt.setNull(6, Types.VARCHAR);
                    }
                    
                    if (nFact != null && !nFact.trim().isEmpty()) {
                        pstmt.setString(7, nFact); // @NumFactura
                    } else {
                        pstmt.setNull(7, Types.VARCHAR);
                    }
                    
                    pstmt.setInt(8, -1); // @EstadoValidacion

                    if (cuentaCobro != null) {  
                        pstmt.setInt(9, cuentaCobro); // @CuentaCobro
                    } else {
                        pstmt.setNull(9, Types.INTEGER); 
                    }

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

                    return resultados;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error al ejecutar el procedimiento: " + e.getMessage(), e);
        }
    }

    // Obtener Terceros
    public List<Map<String, Object>> obtenerTerceros() {
        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoftFinanciero_ST"))) {
            String sql = "SELECT DISTINCT T.IdTerceroKey, T.NomTercero FROM IPSoftFinanciero_ST.dbo.Terceros T " +
                         "INNER JOIN IPSoft100_ST.dbo.Contratos C ON C.IdTerceroKey = T.IdTerceroKey " +
                         "ORDER BY T.NomTercero";

            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                List<Map<String, Object>> resultados = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> fila = new LinkedHashMap<>();
                    fila.put("idTerceroKey", rs.getString("IdTerceroKey"));
                    fila.put("nomTercero", rs.getString("NomTercero"));
                    resultados.add(fila);
                }
                return resultados;
            }
        } catch (Exception e) {
            throw new RuntimeException("Error en la consulta: " + e.getMessage(), e);
        }
    }

    // Obtener Contratos
    public List<Map<String, Object>> obtenerContratos(String idTerceroKey) {
        if (idTerceroKey == null || idTerceroKey.isEmpty()) {
            throw new IllegalArgumentException("El IdTerceroKey es requerido.");
        }

        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoft100_ST"))) {
            String sql = "SELECT DISTINCT C.NoContrato, C.NomContrato FROM IPSoft100_ST.dbo.Contratos C WHERE C.IdTerceroKey = ? ORDER BY C.NomContrato";

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
                    return resultados;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error en la consulta: " + e.getMessage(), e);
        }
    }

    //Buscador para el filtro de atenciones (CUADRO VERDE)  
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
            Boolean soloFacturados) {
        try {
            try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoft100_ST"))) {
                String sql = "EXEC dbo.pa_Net_Facturas_Historico_GenSoportes ?,?,?,?,?,?,?,?,?,?,?";
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

                    try (ResultSet rs = stmt.executeQuery()) {
                        List<Map<String, Object>> resultados = new ArrayList<>();
                        ResultSetMetaData meta = rs.getMetaData();
                        int colCount = meta.getColumnCount();

                        while (rs.next()) {
                            Map<String, Object> fila = new LinkedHashMap<>();
                            for (int i = 1; i <= colCount; i++) {
                                String colName = meta.getColumnName(i);
                                Object value = rs.getObject(i);
                                fila.put(colName, value);
                            }
                            resultados.add(fila);
                        }
                        return resultados;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error consultando admisiones: " + e.getMessage(), e);
        }
    }
}

