package com.certificadosapi.certificados.service;


import java.nio.charset.StandardCharsets;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;

import com.certificadosapi.certificados.config.DatabaseConfig;
import com.certificadosapi.certificados.dto.ZipResult;

@Service
public class R202Service {

    private final DatabaseConfig databaseConfig;


    @Autowired
    public R202Service(DatabaseConfig databaseConfig) {
        this.databaseConfig = databaseConfig;
    }
    
    public ZipResult generar202(Integer consultaNum, String tipo, String tipoId, String tipoReg, String consecArch) {

        String archivo_base = "SGD280";
        String fecFin = null;
        String idEmpresaGrupo = null;


        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoft100_ST"))){

            String sqlConsulta = "SELECT FecFin FROM dbo.R202_Reportes WHERE IdRep = ?";

            try (PreparedStatement ps = conn.prepareStatement(sqlConsulta)){
                ps.setInt(1, consultaNum);

                ResultSet rs = ps.executeQuery();

                if (rs.next()){
                    fecFin = rs.getString("FecFin").replace("-", "");
                }
            }

            String sqlIdEmpresa = "SELECT IdEmpresaGrupo FROM IPSoftFinanciero_ST.DBO.Empresas WHERE IdEmpresaKey <> -1";

            try (PreparedStatement ps = conn.prepareStatement(sqlIdEmpresa)){

                ResultSet rs = ps.executeQuery();

                if (rs.next()){
                    idEmpresaGrupo = String.format("%012d", rs.getInt("IdEmpresaGrupo"));
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Error al obtener datos de la base de datos: " + e);
        }

        String nombreArchivo = String.format("%s%s%s%s%s%s%s.txt", archivo_base, tipo, fecFin, tipoId, idEmpresaGrupo, tipoReg, consecArch);



        try(Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoft100_ST"))){


            String procAlm = "EXEC [dbo].[pa_R202_RegTipo2] ?";

            try (CallableStatement ps = conn.prepareCall(procAlm)){

                ps.setInt(1, consultaNum);

                ResultSet rs = ps.executeQuery();
                ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();
                
                StringBuilder contenido = new StringBuilder();

                while(rs.next()){
                    StringBuilder linea = new StringBuilder();

                    for (int i = 1; i <= columnCount; i++){
                        

                        if (i > 1){
                            linea.append("|");
                        }

                        linea.append(rs.getString(i) != null ? rs.getString(i) : "");
                        
                    }

                    contenido.append(linea).append("\n");
                }

                ZipResult zipResult = new ZipResult(contenido.toString().getBytes(StandardCharsets.UTF_8), nombreArchivo);

                return zipResult;

            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Error al obtener datos de la base de datos: " + e);
        }
    }


    public Map<String, Object> consultarOCrear(String FechaIni, String FechaFin, Integer IdTerceroKey) {
        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoft100_ST"))) {

            // Intentar obtener IdRep existente
            String sqlFn = "SELECT [dbo].[fn_R202_ObtenerIdRep](?, ?, ?) AS IdRep";
            Integer idRep = null;

            try (PreparedStatement ps = conn.prepareStatement(sqlFn)) {
                ps.setDate(1, Date.valueOf(FechaIni));
                ps.setDate(2, Date.valueOf(FechaFin));
                ps.setInt(3, IdTerceroKey);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) idRep = rs.getObject("IdRep", Integer.class);
                }
            }

            // Si no existe, ejecutar Init y volver a consultar
            if (idRep == null || idRep == 0) {
                String sqlInit = "EXEC [dbo].[pa_R202_Init] ?, ?, ?, ?";
                try (CallableStatement cs = conn.prepareCall(sqlInit)) {
                    cs.setDate(1, Date.valueOf(FechaIni));
                    cs.setDate(2, Date.valueOf(FechaFin));
                    cs.setInt(3, 1);
                    cs.setInt(4, IdTerceroKey);
                    cs.execute();
                }

                try (PreparedStatement ps = conn.prepareStatement(sqlFn)) {
                    ps.setDate(1, Date.valueOf(FechaIni));
                    ps.setDate(2, Date.valueOf(FechaFin));
                    ps.setInt(3, IdTerceroKey);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) idRep = rs.getObject("IdRep", Integer.class);
                    }
                }

                generarPaso(idRep, "G1");
            }

            if (idRep == null || idRep == 0) {
                throw new IllegalArgumentException("No se pudo obtener el IdRep");
            }

            return Map.of("idRep", idRep);

        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Error: " + e);
        }
    }




    public String generarPaso(Integer idRep, String campo) {

        String[] camposValidos = {"G1","G2","G3","G4","G5","G6","G7","G8","G9"};
        if (!Arrays.asList(camposValidos).contains(campo)) {
            throw new IllegalArgumentException("Campo inválido: " + campo);
        }

        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoft100_ST"))) {
            String sql = "UPDATE R202_Reportes SET " + campo + " = 1 WHERE IdRep = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, idRep);
                ps.executeUpdate();

                return String.format("Paso %S generado exitosamente", campo.substring(1));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Error al generar pasos: " + e);
        }
    }


    public String ejecutarPasos(Integer idRep, String campo) {

        String sql = null; 

        switch (campo) {
            case "G2":
                sql = "EXEC [pa_R202_PesoTalla]  ?";
                break;
            case "G3":
                sql = "EXEC [pa_R202_AgudezaVisual] ?";
                break;
            case "G4":
                sql = "EXEC [pa_R202_AjusteVariables] ?";
                break;
            case "G5":
                sql = "EXEC [pa_R202_Valoraciones] ?";   
                break;
            case "G6":
                sql = "EXEC [pa_R202_MetodoAnticonceptivo] ?";
                break;
            case "G7":
                sql = "[dbo].[pa_202_AyudasDx] ?"; //laboratorios
                break;
            case "G8":
                sql = "[dbo].[pa_R202_Citologia] ?";
                break;

        }

        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoft100_ST"))) {

            try (CallableStatement cs = conn.prepareCall(sql)){
                cs.setInt(1, idRep);

                cs.execute();
            }

            return String.format("Procedimiento de paso %S ejecutado exitosamente", campo.substring(1));

        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Error al ejecutar pasos: " + e);
        }
    }


    public Map<String, Object> consultarProgreso(@RequestParam Integer IdRep){
        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoft100_ST"))) {

            String sql = "SELECT G1, G2, G3, G4, G5, G6, G7, G8, G9 FROM R202_Reportes WHERE IdRep = ?";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, IdRep);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Map.of(
                            "G1", rs.getInt("G1"),
                            "G2", rs.getInt("G2"),
                            "G3", rs.getInt("G3"),
                            "G4", rs.getInt("G4"),
                            "G5", rs.getInt("G5"),
                            "G6", rs.getInt("G6"),
                            "G7", rs.getInt("G7"),
                            "G8", rs.getInt("G8"),
                            "G9", rs.getInt("G9")
                        );
                    } else {
                        throw new IllegalArgumentException("No se encontró el IdRep: " + IdRep);
                    }
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Error al consultar paso actual: " + e);
        }

    }

    public String eliminarR202(Integer idRep) {
        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoft100_ST"))) {
            try (CallableStatement cs = conn.prepareCall("EXEC [pa_R202_EliminarProceso] ?")) {
                cs.setInt(1, idRep);
                cs.execute();
            }
            return String.format("Proceso %d eliminado exitosamente", idRep);
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Error al eliminar proceso: " + e);
        }
    }

}
