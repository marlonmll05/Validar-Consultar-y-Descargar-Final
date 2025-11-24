package com.certificadosapi.certificados.service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.certificadosapi.certificados.config.DatabaseConfig;

@Service
public class InicioService {

    private static final Logger log = LoggerFactory.getLogger(InicioService.class);

    private DatabaseConfig databaseConfig;

    public InicioService(DatabaseConfig databaseConfig){
        this.databaseConfig = databaseConfig;
    }

    public String validarApartado() throws SQLException{

        String sql = "SELECT ValorParametro FROM ParametrosServidor WHERE NomParametro = 'WSD_Online'";

        try(Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoft100_ST")); 
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
