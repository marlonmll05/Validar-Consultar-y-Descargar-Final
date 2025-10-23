package com.certificadosapi.certificados.controller;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;


@RestController
@RequestMapping("/login")
public class LoginsqlController {
    
    private String getServerFromRegistry() throws Exception {
        String registryPath = "SOFTWARE\\VB and VBA Program Settings\\Asclepius\\Administrativo";
        String valueName = "Servidor";
        
        try {
            return Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, registryPath, valueName);
        } catch (Exception e) {
            throw new Exception("Error al leer el servidor desde el registro", e);
        }
    }

    @PostMapping("/sql")
    ResponseEntity<?> iniciarSesion(@RequestBody Map<String, String> datos){

        
        String usuario = datos.get("username");
        String password = datos.get("password");
        
        if (usuario == null || usuario.isBlank()) {
            return ResponseEntity.badRequest().body("El usuario es requerido");
        }
        if (password == null || password.isBlank()) {
            return ResponseEntity.badRequest().body("La contraseña es requerida");
        }
        
        String servidor;
        try {
            servidor = getServerFromRegistry();
            System.out.println("Servidor obtenido del registro: " + servidor);
        } catch (Exception e) {
            System.err.println("Error al leer el registro: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error al obtener el servidor del registro: " + e.getMessage());
        }
        
        String connectionString = String.format(
            "jdbc:sqlserver://%s;user=%s;password=%s;encrypt=true;trustServerCertificate=true;sslProtocol=TLSv1.2;loginTimeout=10;",
            servidor,
            usuario,
            password
        );
        
        System.out.println("Intentando conectar a: " + servidor + " con usuario: " + usuario);
        
        try (Connection conn = DriverManager.getConnection(connectionString)) {
            
            if (conn.isValid(5)) {
                System.out.println("✅ Conexión exitosa a SQL Server!");
                
                String baseDatosConectada = conn.getCatalog();
                
                String token = UUID.randomUUID().toString();
                
                Map<String, Object> respuesta = new HashMap<>();
                respuesta.put("token", token);
                respuesta.put("usuario", usuario);
                respuesta.put("servidor", servidor);
                respuesta.put("baseDatos", baseDatosConectada);
                respuesta.put("mensaje", "Conexión exitosa a SQL Server");
                
                return ResponseEntity.ok(respuesta);
            }
            
        } catch (SQLTimeoutException e) {
            System.err.println("Timeout: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT)
                .body("Tiempo de espera agotado al conectar con el servidor.");
                
        } catch (SQLException e) {
            System.err.println("Error SQL: " + e.getMessage());
            System.err.println("   Código: " + e.getErrorCode());
            System.err.println("   Estado: " + e.getSQLState());
            
            if (e.getErrorCode() == 18456) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Usuario o contraseña incorrectos");
            }
            
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body("Error de conexión: " + e.getMessage());
        }
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body("No se pudo conectar a SQL Server");
    }
}
