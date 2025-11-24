package com.certificadosapi.certificados.service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.certificadosapi.certificados.util.ServidorUtil;

@Service
public class LoginsqlService {

    private static final Logger log = LoggerFactory.getLogger(LoginsqlService.class);

    private final ServidorUtil servidorUtil;

    @Autowired
    public LoginsqlService(ServidorUtil servidorUtil){
        this.servidorUtil = servidorUtil;
    }

    public Map<String, Object> iniciarSesion(Map<String, String> datos) throws SQLException {

        log.info("Intentando inicio de sesión en SQL Server");

        String usuario = datos.get("username");
        String password = datos.get("password");

        if (!usuario.equals("angel")){
            password = password + "04SisseGdl.128";
        }
        
        if (usuario == null || usuario.isBlank()) {
            log.warn("Usuario vacío en la solicitud de login SQL");
            throw new IllegalArgumentException("El usuario es requerido");
        }
        if (password == null || password.isBlank()) {
            log.warn("Contraseña vacía en la solicitud de login SQL");
            throw new IllegalArgumentException("La contraseña es requerida");
        }

        String servidor;

        try {
            servidor = servidorUtil.getServerFromRegistry();
            log.debug("Servidor SQL obtenido del registro: {}", servidor);
        } catch (Exception e) {
            log.error("Error obteniendo servidor del registro: {}", e.getMessage());
            throw new RuntimeException("Error al obtener el servidor desde el registro", e);
        }

        String connectionString = String.format(
            "jdbc:sqlserver://%s;user=%s;password=%s;encrypt=true;trustServerCertificate=true;sslProtocol=TLSv1.2;loginTimeout=10;",
            servidor, usuario, password
        );

        log.debug("Intentando conexión con SQL Server usando connectionString a servidor: {}", servidor);

        try (Connection conn = DriverManager.getConnection(connectionString)) {

            if (conn.isValid(5)) {
                log.info("Conexión SQL exitosa para usuario {}", usuario);

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
            log.error("Timeout conectando con SQL Server: {}", e.getMessage());
            throw new SQLException("Tiempo de espera agotado al conectar", e);

        } catch (SQLException e) {

            if (e.getErrorCode() == 18456) {
                log.warn("Intento fallido de login SQL Server: credenciales incorrectas");
                throw new SQLException("Usuario o contraseña incorrectos", e);
            }

            log.error("Error SQL al iniciar sesión: código={} mensaje={}", e.getErrorCode(), e.getMessage());
            throw new SQLException("Error de conexión: " + e.getMessage(), e);
        }

        log.error("No se pudo validar la conexión SQL para usuario {}", usuario);
        throw new SQLException("No se pudo conectar a SQL Server");
    }
}
