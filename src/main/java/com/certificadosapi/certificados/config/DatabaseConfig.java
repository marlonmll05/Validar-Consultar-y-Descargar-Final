package com.certificadosapi.certificados.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import com.certificadosapi.certificados.util.ServidorUtil;

@Configuration
public class DatabaseConfig {

    private final ServidorUtil servidorUtil;

    @Value("${db.username}")
    private String dbUsername;

    @Value("${db.password}")
    private String dbPassword;

    public DatabaseConfig(ServidorUtil servidorUtil){
        this.servidorUtil = servidorUtil;
    }

    public String getConnectionUrl(String dbName){
        if (!dbName.equals("IPSoft100_ST" ) && !dbName.equals("IPSoftFinanciero_ST")){
            throw new IllegalArgumentException("Nombre de la base de datos incorrecto" + dbName);
        }

        try{
            String servidor = servidorUtil.getServerFromRegistry();

            return String.format("jdbc:sqlserver://%s;databaseName=%s;user=%s;password=%s;encrypt=true;trustServerCertificate=true;sslProtocol=TLSv1;", 
                                servidor, dbName, dbUsername, dbPassword);
        } catch(Exception e){
            throw new RuntimeException("Error al obtener URL de conexión: " + e.getMessage(), e);
        }
    }
    
}
