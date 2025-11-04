package com.certificadosapi.certificados.controller;

import java.math.BigDecimal;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import com.certificadosapi.certificados.service.ApisqlService;
import com.certificadosapi.certificados.service.EditarjsonService;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

@RestController
@RequestMapping("/api/sql")
public class ApisqlController {

    private final ApisqlService apisqlService;
    private EditarjsonService editarJsonService;

    @Autowired
    public ApisqlController(EditarjsonService editarjsonService, ApisqlService apisqlService){
        this.editarJsonService = editarjsonService;
        this.apisqlService = apisqlService;
    }

    private String getServerFromRegistry() throws Exception {
        String registryPath = "SOFTWARE\\VB and VBA Program Settings\\Asclepius\\Administrativo";
        String valueName = "Servidor";
        
        try {
            return Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, registryPath, valueName);
        } catch (Exception e) {
            throw new Exception("Error al leer el servidor desde el registro", e);
        }
    }

    // Método para obtener CUV
    @GetMapping("/cuv")
    public ResponseEntity<Map<String, Object>> obtenerCuv(@RequestParam String nFact) {
        Map<String, Object> resultado = apisqlService.obtenerCuv(nFact);
        return ResponseEntity.ok(resultado);
    }


    // Método para actualizar CUV
    @PostMapping("/agregarcuv")
    public ResponseEntity<Map<String, Object>> actualizarCuvFF(
        @RequestParam String nFact,
        @RequestParam(required = false) String ripsCuv
    ) {
        Map<String, Object> resultado = apisqlService.actualizarCuvFF(nFact, ripsCuv);
        return ResponseEntity.ok(resultado);
    }




    
    @PostMapping("/actualizarcuvrips")
    public ResponseEntity<?> actualizarCuvRips(
        @RequestParam String nFact,
        @RequestParam(required = false) String cuv,
        @RequestParam Integer idEstadoValidacion
    ) {
        if (nFact == null || nFact.isBlank()) {
            return ResponseEntity.badRequest().body("El parámetro 'nFact' es obligatorio.");
        }

        if (cuv == null || cuv.isBlank()) {
            cuv = null; 
        }

        try {
            String servidor = getServerFromRegistry();
            String connectionUrl = String.format(
                "jdbc:sqlserver://%s;databaseName=IPSoft100_ST;user=ConexionApi;password=ApiConexion.77;encrypt=true;trustServerCertificate=true;sslProtocol=TLSv1;",
                servidor
            );

            try (Connection conn = DriverManager.getConnection(connectionUrl)) {
                String sql = "UPDATE RP SET RP.CUV = ?, RP.IdEstadoValidacion = ? FROM Rips_Transaccion RP WHERE NFact = ?";

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, (cuv != null && !cuv.isBlank()) ? cuv : null);
                    stmt.setInt(2, idEstadoValidacion);
                    stmt.setString(3, nFact);

                    int filasAfectadas = stmt.executeUpdate();

                    if (filasAfectadas > 0) {
                        return ResponseEntity.ok(Map.of(
                            "mensaje", "CUV e IdEstadoValidacion actualizados correctamente",
                            "filasAfectadas", filasAfectadas
                        ));
                    } else {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body("No se encontró una transacción RIPS con el NFact especificado.");
                    }
                }
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error al actualizar los datos de RIPS: " + e.getMessage());
        }
    }

    @GetMapping("/estadovalidacion")
    public ResponseEntity<?> obtenerEstadoValidacion(@RequestParam String nFact) {

        if (nFact == null || nFact.isBlank()) {
            return ResponseEntity.badRequest().body("El parámetro 'nFact' es requerido y no puede estar vacío");
        }

        try {
            String servidor = getServerFromRegistry();
            String connectionUrl = String.format(
                "jdbc:sqlserver://%s;databaseName=IPSoft100_ST;user=ConexionApi;password=ApiConexion.77;encrypt=true;trustServerCertificate=true;sslProtocol=TLSv1;",
                servidor
            );

            try (Connection conn = DriverManager.getConnection(connectionUrl)) {
                String sql = "SELECT RT.IdEstadoValidacion FROM Rips_Transaccion RT WHERE NFact = ?";

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, nFact);

                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            Integer idEstadoValidacion = rs.getInt("IdEstadoValidacion");
                            if (rs.wasNull()) {
                                idEstadoValidacion = null;
                            }

                            return ResponseEntity.ok(Map.of(
                                "nFact", nFact,
                                "idEstadoValidacion", idEstadoValidacion != null ? idEstadoValidacion : "null"
                            ));
                        } else {
                            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body("No se encontró una transacción RIPS con el NFact especificado.");
                        }
                    }
                }
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error al consultar el estado de validación: " + e.getMessage());
        }
    }

    @GetMapping("/ejecutarRips")
    public ResponseEntity<?> ejecutarRips(@RequestParam String Nfact) {
        try {
            String servidor = getServerFromRegistry();
            String connectionUrl = String.format(
                "jdbc:sqlserver://%s;databaseName=IPSoft100_ST;user=ConexionApi;password=ApiConexion.77;encrypt=true;trustServerCertificate=true;sslProtocol=TLSv1;",
                servidor
            );

            try (Connection conn = DriverManager.getConnection(connectionUrl)) {

                String sql = "EXEC pa_Rips_JSON_Generar '" + Nfact + "', 0, 1, 1";

                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(sql); 

                    return ResponseEntity.ok("Procedimiento ejecutado correctamente para el cliente: " + Nfact);
                }
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(e.getMessage());
        }
    }

    // Método para actualizar campos
    @PostMapping("/actualizarCampos")
    public ResponseEntity<String> actualizarCampos(
        @RequestBody Map<String, Object> datos,
        @RequestParam int idMovDoc 
    ) throws Exception {
        editarJsonService.actualizarCampos(datos, idMovDoc);

        return ResponseEntity.ok().body("Datos Actualizados Correctamente");
    }
}





