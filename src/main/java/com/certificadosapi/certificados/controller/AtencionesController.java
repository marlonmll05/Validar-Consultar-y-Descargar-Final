package com.certificadosapi.certificados.controller;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;

@RestController
@RequestMapping("/api")
public class AtencionesController {

    private String getServerFromRegistry() throws Exception {
        String registryPath = "SOFTWARE\\VB and VBA Program Settings\\Asclepius\\Administrativo";
        String valueName = "Servidor";
        
        try {
            return Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, registryPath, valueName);
        } catch (Exception e) {
            throw new Exception("Error al leer el servidor desde el registro", e);
        }
    }

    @ControllerAdvice
    public class GlobalExceptionHandler {

        @ExceptionHandler(MethodArgumentTypeMismatchException.class)
        public ResponseEntity<String> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
            if (ex.getRequiredType() == LocalDate.class) {
                return ResponseEntity
                    .badRequest()
                    .body("⚠️ Formato de fecha inválido. Usa 'yyyy-MM-dd' sin espacios ni saltos de línea. Valor recibido: " + ex.getValue());
            }
            return ResponseEntity
                .badRequest()
                .body("Parámetro inválido: " + ex.getMessage());
        }
    }

    @GetMapping("/admisiones")
    public ResponseEntity<?> buscarAdmisiones(
        @RequestParam(required = false) Long IdAtencion,
        @RequestParam(required = false) String HistClinica,
        @RequestParam(required = false) Integer Cliente,
        @RequestParam(required = false) String NoContrato,
        @RequestParam(required = false) Integer IdAreaAtencion,
        @RequestParam(required = false) Integer IdUnidadAtencion,
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyyMMdd") LocalDate FechaDesde,
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyyMMdd") LocalDate FechaHasta,
        @RequestParam(required = false) String nFact,
        @RequestParam(required = false) Integer nCuentaCobro,
        @RequestParam(required = false) Boolean soloFacturados
    ) {
        try {

            System.out.printf(
                "➡️ Filtros recibidos: IdAtencion=%s, HistClinica=%s, Cliente=%s, NoContrato=%s, " +
                "IdAreaAtencion=%s, IdUnidadAtencion=%s, FechaDesde=%s, FechaHasta=%s, nFact=%s, nCuentaCobro=%s",
                IdAtencion, HistClinica, Cliente, NoContrato, IdAreaAtencion, IdUnidadAtencion, FechaDesde, FechaHasta, nFact, nCuentaCobro
            );

            String servidor = getServerFromRegistry();
            String connectionUrl = String.format(
                "jdbc:sqlserver://%s;databaseName=IPSoft100_ST;user=ConexionApi;password=ApiConexion.77;encrypt=true;trustServerCertificate=true;sslProtocol=TLSv1;",
                servidor
            );

            try (Connection conn = DriverManager.getConnection(connectionUrl)) {
                String sql = "EXEC dbo.pa_Net_Facturas_Historico_GenSoportes ?,?,?,?,?,?,?,?,?,?, ?";
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
                        return ResponseEntity.ok(resultados);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }

}
