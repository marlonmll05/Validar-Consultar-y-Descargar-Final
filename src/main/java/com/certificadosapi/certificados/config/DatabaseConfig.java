package com.certificadosapi.certificados.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;

@Configuration
public class DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    @Value("${db.username}")
    private String dbUsername;

    @Value("${db.password}")
    private String dbPassword;

    //Metodo para leer el servidor de registro
    public String getServerFromRegistry() throws Exception {

        String registryPath = "SOFTWARE\\VB and VBA Program Settings\\Asclepius\\Administrativo";
        String valueName = "Servidor";

        try{
            return Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, registryPath, valueName);
        }catch(Exception e){
            throw new Exception("Error al leer el servidor de registro" + e.getMessage(), e);
        }
    }



    public String getConnectionUrl(String dbName){
        if (!dbName.equals("IPSoft100_ST" ) && !dbName.equals("IPSoftFinanciero_ST") && !dbName.equals("Asclepius_Documentos")){
            throw new IllegalArgumentException("Nombre de la base de datos incorrecto" + dbName);
        }

        try{
            String servidor = getServerFromRegistry();

            return String.format("jdbc:sqlserver://%s;databaseName=%s;user=%s;password=%s;encrypt=true;trustServerCertificate=true;sslProtocol=TLSv1;", 
                                servidor, dbName, dbUsername, dbPassword);
        } catch(Exception e){
            throw new RuntimeException("Error al obtener URL de conexión: " + e.getMessage(), e);
        }
    }

    public String parametrosServidor(Integer option) throws SQLException{

        Map<Integer, String> dict = new HashMap<>();
        dict.put(1, "WSD_Online");
        dict.put(2, "URLReportServerWS");
        dict.put(3, "FE_RS_UsuarioGen");
        dict.put(4, "FE_RS_UsuarioGenPwd");
        dict.put(5, "WCC_Online");

        String value = dict.get(option);
            
        String sql = String.format("SELECT ValorParametro FROM ParametrosServidor WHERE NomParametro = '%s'", value);

        try(Connection conn = DriverManager.getConnection(getConnectionUrl("IPSoftFinanciero_ST")); 
            PreparedStatement stmt = conn.prepareStatement(sql)
            ){

            try(ResultSet rs = stmt.executeQuery()){

                String respuesta = null;

                while(rs.next()){
                    respuesta = rs.getString("ValorParametro");
                }

                log.info("Respuesta recibida: {}", respuesta);
                return respuesta;
            }
        } 
    }
    
}
