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

import com.certificadosapi.certificados.config.DatabaseConfig;


/**
 * Servicio encargado de la autenticación de usuarios con SQL Server.
 * Gestiona el inicio de sesión validando credenciales directamente con la base de datos
 * y genera tokens de sesión para usuarios autenticados.
 * 
 * @author Marlon Morales Llanos
 */

@Service
public class LoginsqlService {

    private static final Logger log = LoggerFactory.getLogger(LoginsqlService.class);

    private final DatabaseConfig databaseConfig;

    /**
     * Constructor de LoginsqlService con inyección de dependencias.
     * 
     * @param databaseConfig Objeto de configuración para las conexiones a base de datos
     */

    @Autowired
    public LoginsqlService(DatabaseConfig databaseConfig){
        this.databaseConfig = databaseConfig;
    }

    /**
     * Inicia sesión en SQL Server validando las credenciales del usuario.
     * 
     * Aplica un sufijo especial a la contraseña para usuarios distintos a "angel".
     * 
     * Obtiene el servidor desde el registro del sistema y establece una conexión
     * para validar las credenciales.
     * 
     * @param datos Mapa conteniendo las credenciales con las llaves "username" y "password"
     * @return Mapa con información de la sesión incluyendo:
     *         "token" (String) - Token UUID generado para la sesión,
     *         "usuario" (String) - Nombre del usuario autenticado,
     *         "servidor" (String) - Servidor SQL al que se conectó,
     *         "baseDatos" (String) - Base de datos activa en la conexión,
     *         "mensaje" (String) - Mensaje de confirmación
     * @throws IllegalArgumentException si el usuario es nulo o está vacío
     * @throws SQLException si las credenciales son incorrectas (código 18456),
     *         si hay timeout en la conexión, o si ocurre cualquier otro error SQL
     * @throws RuntimeException si hay error al obtener el servidor del registro
     */

    public Map<String, Object> iniciarSesion(Map<String, String> datos) throws SQLException {

        log.info("Intentando inicio de sesión en SQL Server");

        String usuario = datos.get("username");
        String password = datos.get("password");

        if (!usuario.equals("angel")){
            password = password + "04SisseGdl.l28";
        }
        if (usuario == null || usuario.isBlank()) {
            log.warn("Usuario vacío en la solicitud de login SQL");
            throw new IllegalArgumentException("El usuario es requerido");
        }


        String servidor;

        try {
            servidor = databaseConfig.getServerFromRegistry();
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
