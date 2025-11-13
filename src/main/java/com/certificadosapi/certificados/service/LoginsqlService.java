package com.certificadosapi.certificados.service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.certificadosapi.certificados.util.ServidorUtil;

@Service
public class LoginsqlService {

    private ServidorUtil servidorUtil;

    @Autowired
    public LoginsqlService(ServidorUtil servidorUtil){
        this.servidorUtil = servidorUtil;
    }

    
    public Map<String, Object> iniciarSesion(Map<String, String> datos) throws SQLException {
        String usuario = datos.get("username");
        String password = datos.get("password");

        if (usuario == null || usuario.isBlank()) {
            throw new IllegalArgumentException("El usuario es requerido");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("La contraseña es requerida");
        }

        String servidor;
        try {
            servidor = servidorUtil.getServerFromRegistry();
        } catch (Exception e) {
            throw new RuntimeException("Error al obtener el servidor desde el registro", e);
        }

        String connectionString = String.format(
            "jdbc:sqlserver://%s;user=%s;password=%s;encrypt=true;trustServerCertificate=true;sslProtocol=TLSv1.2;loginTimeout=10;",
            servidor,
            usuario,
            password
        );

        try (Connection conn = DriverManager.getConnection(connectionString)) {
            if (conn.isValid(5)) {
                String baseDatosConectada = conn.getCatalog();
                String token = UUID.randomUUID().toString();

                Map<String, Object> respuesta = new HashMap<>();
                respuesta.put("token", token);
                respuesta.put("usuario", usuario);
                respuesta.put("servidor", servidor);
                respuesta.put("baseDatos", baseDatosConectada);
                respuesta.put("mensaje", "Conexión exitosa a SQL Server");

                return respuesta;
            }
        } catch (SQLTimeoutException e) {
            throw new SQLException("Tiempo de espera agotado al conectar con el servidor", e);
        } catch (SQLException e) {
            if (e.getErrorCode() == 18456) {
                throw new SQLException("Usuario o contraseña incorrectos", e);
            }
            throw new SQLException("Error de conexión: " + e.getMessage(), e);
        }

        throw new SQLException("No se pudo conectar a SQL Server");
    }
}
