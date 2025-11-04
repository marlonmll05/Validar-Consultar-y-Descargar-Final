package com.certificadosapi.certificados.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

import java.sql.*;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Service;

import com.certificadosapi.certificados.config.DatabaseConfig;


@Service
public class EditarjsonService {    
    
    private final DatabaseConfig databaseConfig;

    @Autowired
    public EditarjsonService(DatabaseConfig databaseConfig){
        this.databaseConfig = databaseConfig;
    }

    @SuppressWarnings("unchecked")
    public void actualizarCampos(Map<String, Object> datos, int idMovDoc) throws Exception {

        if (idMovDoc <= 0) {
            throw new IllegalArgumentException("El ID del documento debe ser mayor a 0");
        }

        if (datos == null || datos.isEmpty()) {
            throw new IllegalArgumentException("No se recibieron datos para actualizar");
        }

        String connectionUrl = databaseConfig.getConnectionUrl("IPSoft100_ST");

        try (Connection conn = DriverManager.getConnection(connectionUrl)) {
            conn.setAutoCommit(false);

            if (datos.containsKey("numDocumentoIdObligado") || datos.containsKey("numFactura") || 
                datos.containsKey("tipoNota") || datos.containsKey("numNota")) {
                actualizarTransaccion(conn, datos, idMovDoc);
            }

            if (datos.containsKey("usuarios")) {
                List<Map<String, Object>> usuarios = (List<Map<String, Object>>) datos.get("usuarios");
                for (Map<String, Object> usuario : usuarios) {
                    actualizarUsuarios(conn, usuario, idMovDoc);
                    
                    if (usuario.containsKey("servicios")) {
                        Map<String, Object> servicios = (Map<String, Object>) usuario.get("servicios");
                        
                        if (servicios.containsKey("consultas")) {
                            List<Map<String, Object>> consultas = (List<Map<String, Object>>) servicios.get("consultas");
                            for (Map<String, Object> consulta : consultas) {
                                actualizarConsulta(conn, consulta, idMovDoc);
                            }
                        }
                        
                        if (servicios.containsKey("procedimientos")) {
                            List<Map<String, Object>> procedimientos = (List<Map<String, Object>>) servicios.get("procedimientos");
                            for (Map<String, Object> procedimiento : procedimientos) {
                                actualizarProcedimientos(conn, procedimiento, idMovDoc);
                            }
                        }
                        
                        if (servicios.containsKey("urgencias")) {
                            List<Map<String, Object>> urgencias = (List<Map<String, Object>>) servicios.get("urgencias");
                            for (Map<String, Object> urgencia : urgencias) {
                                actualizarUrgencias(conn, urgencia, idMovDoc);
                            }
                        }
                        
                        if (servicios.containsKey("hospitalizacion")) {
                            List<Map<String, Object>> hospitalizacion = (List<Map<String, Object>>) servicios.get("hospitalizacion");
                            for (Map<String, Object> hospi : hospitalizacion) {
                                actualizarHospitalizacion(conn, hospi, idMovDoc);
                            }
                        }
                        
                        if (servicios.containsKey("recienNacidos")) {
                            List<Map<String, Object>> recienNacidos = (List<Map<String, Object>>) servicios.get("recienNacidos");
                            for (Map<String, Object> rn : recienNacidos) {
                                actualizarRecienNacidos(conn, rn, idMovDoc);
                            }
                        }
                        
                        if (servicios.containsKey("medicamentos")) {
                            List<Map<String, Object>> medicamentos = (List<Map<String, Object>>) servicios.get("medicamentos");
                            for (Map<String, Object> med : medicamentos) {
                                actualizarMedicamentos(conn, med, idMovDoc);
                            }
                        }
                        
                        if (servicios.containsKey("otrosServicios")) {
                            List<Map<String, Object>> otrosServicios = (List<Map<String, Object>>) servicios.get("otrosServicios");
                            for (Map<String, Object> otro : otrosServicios) {
                                actualizarOtrosServicios(conn, otro, idMovDoc);
                            }
                        }
                    }
                }
            }

            conn.commit();

        } catch (SQLException sqlEx) {
            throw new RuntimeException("Error al actualizar los campos en la base de datos: " + sqlEx.getMessage(), sqlEx);
        }
    }



    // ==================== FUNCIONES PARA CAMPOS QUE PERMITEN NULL ====================

    private Object parseFechaConHoraNull(Object valor) {
        if (valor instanceof String str) {
            if (str.isBlank()) {
                return "";
            }
            if ("null".equalsIgnoreCase(str)) {
                return null;
            }
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                LocalDateTime dateTime = LocalDateTime.parse(str, formatter);
                return Timestamp.valueOf(dateTime);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Fecha con hora inválida (esperado yyyy-MM-dd HH:mm): " + str, e);
            }
        }

        if (valor == null) {
            return null;
        }

        throw new IllegalArgumentException("Tipo de dato no válido para fecha con hora: " + valor.getClass());
    }

    private Object parseBigDecimalNull(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof String str) {
            if (str.isBlank()) {
                return "";
            }
            throw new IllegalArgumentException("No se acepta String como valor para BigDecimal: " + str);
        }

        if (value instanceof BigDecimal) {
            return value;
        }

        if (value instanceof Number) {
            return new BigDecimal(value.toString());
        }

        throw new IllegalArgumentException("Tipo de dato no soportado para BigDecimal: " + value.getClass());
    }

    private String parseStringNull(Object valor, int maxLength) {
        if (valor == null) {
            return null;
        }

        String str = valor.toString();

        if ("null".equalsIgnoreCase(str)) {
            return null;
        }

        if (str.length() > maxLength) {
            throw new IllegalArgumentException("String excede longitud máxima (" + maxLength + "): " + str);
        }

        return str;
    }

    private Object parseIntegerNull(Object valor) {
        if (valor == null) {
            return null;
        }

        String str = valor.toString().trim();

        if (str.isEmpty() || "null".equalsIgnoreCase(str)) {
            return null;
        }

        try {
            if (valor instanceof Number) {
                return ((Number) valor).intValue();
            }
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            System.err.println("Entero inválido: " + valor);
            return null;
        }
    }



// ==================== FUNCIONES PARA CAMPOS QUE NO PERMITEN NULL ====================

    private Object parseFechaSinHoraNoNull(Object valor) {
        if (valor == null) {
            throw new IllegalArgumentException("Valor recibido es null (no debe serlo)");
        }

        if (valor instanceof String str) {
            if (str.isBlank()) {
                return "";
            }
            if ("null".equalsIgnoreCase(str)) {
                throw new IllegalArgumentException("Valor 'null' como texto no es válido");
            }
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                LocalDate localDate = LocalDate.parse(str, formatter);
                return java.sql.Date.valueOf(localDate);
            } catch (DateTimeParseException e) {
                System.err.println("Fecha inválida (esperado yyyy-MM-dd): " + str);
            }
        }

        throw new IllegalArgumentException("Tipo de dato no válido: se esperaba String");
    }

    private Object parseFechaConHoraNoNull(Object valor) {
        if (valor == null) {
            throw new IllegalArgumentException("Valor recibido es null (no debe serlo)");
        }

        if (valor instanceof String str) {
            if (str.isBlank()) {
                return "";
            }
            if ("null".equalsIgnoreCase(str)) {
                throw new IllegalArgumentException("Valor 'null' como texto no es válido");
            }
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                LocalDateTime dateTime = LocalDateTime.parse(str, formatter);
                return Timestamp.valueOf(dateTime);
            } catch (DateTimeParseException e) {
                System.err.println("Fecha con hora inválida (esperado yyyy-MM-dd HH:mm): " + str);
            }
        }

        throw new IllegalArgumentException("Tipo de dato no válido: se esperaba String");
    }

    private Object parseBigDecimalNoNull(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("Valor null no permitido para BigDecimal");
        }

        if (value instanceof String str) {
            if (str.isBlank()) {
                return ""; 
            }
            if ("null".equalsIgnoreCase(str)) {
                throw new IllegalArgumentException("Texto 'null' no permitido como valor para BigDecimal");
            }
            throw new IllegalArgumentException("No se acepta String como valor para BigDecimal: " + str);
        }

        if (value instanceof BigDecimal) {
            return value;
        }

        if (value instanceof Number) {
            return new BigDecimal(value.toString());
        }

        throw new IllegalArgumentException("Tipo de dato no soportado para BigDecimal: " + value.getClass());
    }

    private String parseStringNoNull(Object valor, int maxLength) {
        if (valor == null) {
            throw new IllegalArgumentException("No se permite valor null para String");
        }

        String str = valor.toString();

        if ("null".equalsIgnoreCase(str)) {
            throw new IllegalArgumentException("No se permite el texto 'null' como valor String");
        }

        if (str.length() > maxLength) {
            throw new IllegalArgumentException("String excede longitud máxima (" + maxLength + "): " + str);
        }

        return str;
    }

    private Object parseIntegerNoNull(Object valor) {
        if (valor == null) {
            throw new IllegalArgumentException("No se permite valor null para Integer");
        }

        if (valor instanceof String str) {
            if (str.isBlank()) {
                return ""; 
            }
            throw new IllegalArgumentException("No se permite String como valor para Integer: \"" + str + "\"");
        }

        if (valor instanceof Number) {
            return ((Number) valor).intValue();
        }

        throw new IllegalArgumentException("Tipo de dato no soportado para Integer: " + valor.getClass());
    }

    private void actualizarTransaccion(Connection conn, Map<String, Object> datos, int idMovDoc) throws SQLException {
        String sql = "UPDATE dbo.Rips_Transaccion SET IdEmpresaGrupo = ?, NFact = ?, tipoNota = ?, numNota = ? WHERE IdMovDoc = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, parseStringNoNull(datos.get("numDocumentoIdObligado"), 15)); 
            stmt.setString(2, parseStringNoNull(datos.get("numFactura"), 15));           
            stmt.setString(3, parseStringNull(datos.get("tipoNota"), 2));                 
            stmt.setString(4, parseStringNull(datos.get("numNota"), 20));          
            stmt.setInt(5, idMovDoc); 

            int rowsAffected = stmt.executeUpdate();
            System.out.println("Transacción actualizada: " + rowsAffected + " filas afectadas");
        }
    }

    private void actualizarUsuarios(Connection conn, Map<String, Object> usuarios, int idMovDoc) throws SQLException {
        String sql = """
            UPDATE dbo.Rips_Usuarios
            SET tipoDocumentoIdentificacion = ?, numDocumentoIdentificacion = ?, tipoUsuario = ?, 
                fechaNacimiento = ?, codSexo = ?, codPaisResidencia = ?, ResHabitual = ?, 
                codZonaTerritorialResidencia = ?, incapacidad = ?, codPaisOrigen = ?
            FROM dbo.Rips_Usuarios U
            INNER JOIN dbo.Rips_Transaccion T ON T.IdRips = U.IdRips
            WHERE T.IdMovDoc = ? AND U.consecutivo = ?
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, parseStringNoNull(usuarios.get("tipoDocumentoIdentificacion"), 3));
            stmt.setString(2, parseStringNoNull(usuarios.get("numDocumentoIdentificacion"), 20));
            stmt.setString(3, parseStringNoNull(usuarios.get("tipoUsuario"), 3));
            stmt.setTimestamp(4, new java.sql.Timestamp(((java.sql.Date) parseFechaSinHoraNoNull(usuarios.get("fechaNacimiento"))).getTime()));
            stmt.setString(5, parseStringNoNull(usuarios.get("codSexo"), 1));
            stmt.setString(6, parseStringNoNull(usuarios.get("codPaisResidencia"), 5));
            stmt.setString(7, parseStringNoNull(usuarios.get("codMunicipioResidencia"), 5));
            stmt.setString(8, parseStringNoNull(usuarios.get("codZonaTerritorialResidencia"), 2));
            stmt.setString(9, parseStringNoNull(usuarios.get("incapacidad"), 2));
            stmt.setString(10, parseStringNoNull(usuarios.get("codPaisOrigen"), 5));
            stmt.setInt(11, idMovDoc);
            stmt.setInt(12, (Integer) parseIntegerNoNull(usuarios.get("consecutivo")));

            int rowsAffected = stmt.executeUpdate();
            System.out.println("Usuarios actualizado: " + rowsAffected + " fila(s) afectada(s)");
        }
    }

    private void actualizarConsulta(Connection conn, Map<String, Object> consulta, int idMovDoc) throws SQLException {
        String sql = """
            UPDATE dbo.Rips_Consulta
            SET codPrestador = ?, fechaInicioAtencion = ?, numAutorizacion = ?, codConsulta = ?, 
                modalidadGrupoServicioTecSal = ?, grupoServicios = ?, codServicio = ?,
                finalidadTecnologiaSalud = ?, causaMotivoAtencion = ?, codDiagnosticoPrincipal = ?, 
                codDiagnosticoRelacionado1 = ?, codDiagnosticoRelacionado2 = ?,
                codDiagnosticoRelacionado3 = ?, tipoDiagnosticoPrincipal = ?, tipoDocumentoIdentificacion = ?, 
                numDocumentoIdentificacion = ?, vrServicio = ?,
                tipoPagoModerador = ?, valorPagoModerador = ?, numFEVPagoModerador = ?
            FROM dbo.Rips_Consulta C
            INNER JOIN dbo.Rips_Usuarios U ON U.IdRips_Usuario = C.IdRips_Usuario
            INNER JOIN dbo.Rips_Transaccion T ON T.IdRips = U.IdRips
            WHERE T.IdMovDoc = ? AND C.consecutivo = ?
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, parseStringNoNull(consulta.get("codPrestador"), 12));                             
            stmt.setTimestamp(2, (Timestamp) parseFechaConHoraNull(consulta.get("fechaInicioAtencion")));           
            stmt.setString(3, parseStringNull(consulta.get("numAutorizacion"), 30));                           
            stmt.setString(4, parseStringNoNull(consulta.get("codConsulta"), 9));                                
            stmt.setString(5, parseStringNoNull(consulta.get("modalidadGrupoServicioTecSal"), 2));              
            stmt.setString(6, parseStringNull(consulta.get("grupoServicios"), 2));                            
            stmt.setInt(7, (Integer) parseIntegerNoNull(consulta.get("codServicio")));                                           
            stmt.setString(8, parseStringNull(consulta.get("finalidadTecnologiaSalud"), 3));                  
            stmt.setString(9, parseStringNull(consulta.get("causaMotivoAtencion"), 2));                       
            stmt.setString(10, parseStringNull(consulta.get("codDiagnosticoPrincipal"), 25));                 
            stmt.setString(11, parseStringNull(consulta.get("codDiagnosticoRelacionado1"), 25));              
            stmt.setString(12, parseStringNull(consulta.get("codDiagnosticoRelacionado2"), 25));              
            stmt.setString(13, parseStringNull(consulta.get("codDiagnosticoRelacionado3"), 25));              
            stmt.setString(14, parseStringNull(consulta.get("tipoDiagnosticoPrincipal"), 2));                 
            stmt.setString(15, parseStringNull(consulta.get("tipoDocumentoIdentificacion"), 2));              
            stmt.setString(16, parseStringNull(consulta.get("numDocumentoIdentificacion"), 20));              
            stmt.setInt(17, (Integer) parseIntegerNoNull(consulta.get("vrServicio")));                                           
            stmt.setString(18, parseStringNoNull(consulta.get("conceptoRecaudo"), 2)); 
            stmt.setInt(19, (Integer) parseIntegerNoNull(consulta.get("valorPagoModerador")));                                  
            stmt.setString(20, parseStringNull(consulta.get("numFEVPagoModerador"), 20));                     
            stmt.setInt(21, idMovDoc);
            stmt.setInt(22, (Integer) parseIntegerNoNull(consulta.get("consecutivo")));                                        

            int rowsAffected = stmt.executeUpdate();
            System.out.println("Consulta actualizada: " + rowsAffected + " fila(s) afectada(s)");
        }
    }

    private void actualizarProcedimientos(Connection conn, Map<String, Object> procedimientos, int idMovDoc) throws SQLException {
        String sql = """
            UPDATE dbo.Rips_Procedimientos
            SET codPrestador = ?, fechaInicioAtencion = ?, idMIPRES = ?, numAutorizacion = ?,
                codProcedimiento = ?, viaingresoServicioSalud = ?, modalidadGrupoServicioTecSal = ?,
                grupoServicios = ?, codServicio = ?, finalidadTecnologiaSalud = ?,
                tipoDocumentoIdentificacion = ?, numDocumentoIdentificacion = ?,
                codDiagnosticoPrincipal = ?, codDiagnosticoRelacionado = ?, codComplicacion = ?,
                vrServicio = ?, tipoPagoModerador = ?, valorPagoModerador = ?, numFEVPagoModerador = ?
            FROM dbo.Rips_Procedimientos P
            INNER JOIN dbo.Rips_Usuarios U ON U.IdRips_Usuario = P.IdRips_Usuario
            INNER JOIN dbo.Rips_Transaccion T ON T.IdRips = U.IdRips
            WHERE T.IdMovDoc = ? AND P.consecutivo = ?
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, parseStringNoNull(procedimientos.get("codPrestador"), 12));                    
            stmt.setTimestamp(2, (Timestamp) parseFechaConHoraNull(procedimientos.get("fechaInicioAtencion")));                                                         
            stmt.setString(3, parseStringNull(procedimientos.get("idMIPRES"), 20));                         
            stmt.setString(4, parseStringNull(procedimientos.get("numAutorizacion"), 30));                  
            stmt.setString(5, parseStringNoNull(procedimientos.get("codProcedimiento"), 9));                  
            stmt.setString(6, parseStringNull(procedimientos.get("viaIngresoServicioSalud"), 3));          
            stmt.setString(7, parseStringNoNull(procedimientos.get("modalidadGrupoServicioTecSal"), 2));      
            stmt.setString(8, parseStringNull(procedimientos.get("grupoServicios"), 2));                    
            stmt.setInt(9, (Integer) parseIntegerNoNull(procedimientos.get("codServicio")));                                  
            stmt.setString(10, parseStringNull(procedimientos.get("finalidadTecnologiaSalud"), 3));        
            stmt.setString(11, parseStringNoNull(procedimientos.get("tipoDocumentoIdentificacion"), 2));     
            stmt.setString(12, parseStringNoNull(procedimientos.get("numDocumentoIdentificacion"), 20));     
            stmt.setString(13, parseStringNull(procedimientos.get("codDiagnosticoPrincipal"), 25));        
            stmt.setString(14, parseStringNull(procedimientos.get("codDiagnosticoRelacionado"), 25));      
            stmt.setString(15, parseStringNull(procedimientos.get("codComplicacion"), 25));                 
            stmt.setInt(16, (Integer) parseIntegerNoNull(procedimientos.get("vrServicio")));                                  
            stmt.setString(17, parseStringNoNull(procedimientos.get("conceptoRecaudo"), 2));               
            stmt.setInt(18, (Integer) parseIntegerNoNull(procedimientos.get("valorPagoModerador")));                          
            stmt.setString(19, parseStringNull(procedimientos.get("numFEVPagoModerador"), 20));            
            stmt.setInt(20, idMovDoc);
            stmt.setInt(21, (Integer) parseIntegerNoNull(procedimientos.get("consecutivo")));                                 

            int rowsAffected = stmt.executeUpdate();
            System.out.println("Procedimientos actualizado: " + rowsAffected + " fila(s) afectada(s)");
        }
    }

    private void actualizarUrgencias(Connection conn, Map<String, Object> urgencias, int idMovDoc) throws SQLException {
        String sql = """
            UPDATE dbo.Rips_Urg
            SET codPrestador = ?, fechaInicioAtencion = ?, causaMotivoAtencion = ?, codDiagnosticoPrincipal = ?,
                codDiagnosticoPrincipalE = ?, codDiagnosticoRelacionadoE1 = ?, codDiagnosticoRelacionadoE2 = ?,
                codDiagnosticoRelacionadoE3 = ?, condicionDestinoUsuarioEgreso = ?, codDiagnosticoCausaMuerte = ?,
                fechaEgreso = ?
            FROM dbo.Rips_Urg UR
            INNER JOIN dbo.Rips_Usuarios U ON U.IdRips_Usuario = UR.IdRips_Usuario
            INNER JOIN dbo.Rips_Transaccion T ON T.IdRips = U.IdRips
            WHERE T.IdMovDoc = ? AND UR.consecutivo = ?
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, parseStringNoNull(urgencias.get("codPrestador"), 12));                 
            stmt.setTimestamp(2, (Timestamp) parseFechaConHoraNull(urgencias.get("fechaInicioAtencion")));                                                   
            stmt.setString(3, parseStringNoNull(urgencias.get("causaMotivoAtencion"), 2));           
            stmt.setString(4, parseStringNull(urgencias.get("codDiagnosticoPrincipal"), 6));       
            stmt.setString(5, parseStringNull(urgencias.get("codDiagnosticoPrincipalE"), 6));   
            stmt.setString(6, parseStringNull(urgencias.get("codDiagnosticoRelacionadoE1"), 6));   
            stmt.setString(7, parseStringNull(urgencias.get("codDiagnosticoRelacionadoE2"), 6));   
            stmt.setString(8, parseStringNull(urgencias.get("codDiagnosticoRelacionadoE3"), 6));   
            stmt.setString(9, parseStringNull(urgencias.get("condicionDestinoUsuarioEgreso"), 3)); 
            stmt.setString(10, parseStringNull(urgencias.get("codDiagnosticoCausaMuerte"), 6));    
            stmt.setTimestamp(11, (Timestamp) parseFechaConHoraNull(urgencias.get("fechaEgreso")));                                                  
            stmt.setInt(12, idMovDoc);
            stmt.setInt(13, (Integer) parseIntegerNoNull(urgencias.get("consecutivo")));                             

            int rowsAffected = stmt.executeUpdate();
            System.out.println("Urgencias actualizado: " + rowsAffected + " fila(s) afectada(s)");
        }
    }

    private void actualizarHospitalizacion(Connection conn, Map<String, Object> hospitalizacion, int idMovDoc) throws SQLException {
        String sql = """
            UPDATE dbo.Rips_Hospitalizacion
            SET codPrestador = ?, viaingresoServicioSalud = ?, fechaInicioAtencion = ?, numAutorizacion = ?,
                causaMotivoAtencion = ?, codDiagnosticoPrincipal = ?, codDiagnosticoPrincipalE = ?,
                codDiagnosticoRelacionadoE1 = ?, codDiagnosticoRelacionadoE2 = ?,
                codDiagnosticoRelacionadoE3 = ?, codComplicacion = ?, condicionDestinoUsuarioEgreso = ?,
                codDiagnosticoCausaMuerte = ?, fechaEgreso = ?
            FROM dbo.Rips_Hospitalizacion H
            INNER JOIN dbo.Rips_Usuarios U ON U.IdRips_Usuario = H.IdFRips_Usuario
            INNER JOIN dbo.Rips_Transaccion T ON T.IdRips = U.IdRips
            WHERE T.IdMovDoc = ? AND H.consecutivo = ?
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, parseStringNoNull(hospitalizacion.get("codPrestador"), 12));                
            stmt.setString(2, parseStringNull(hospitalizacion.get("viaIngresoServicioSalud"), 3));    
            stmt.setTimestamp(3, (Timestamp) parseFechaConHoraNull(hospitalizacion.get("fechaInicioAtencion")));                                                         
            stmt.setString(4, parseStringNull(hospitalizacion.get("numAutorizacion"), 30));           
            stmt.setString(5, parseStringNoNull(hospitalizacion.get("causaMotivoAtencion"), 2));          
            stmt.setString(6, parseStringNull(hospitalizacion.get("codDiagnosticoPrincipal"), 6));    
            stmt.setString(7, parseStringNull(hospitalizacion.get("codDiagnosticoPrincipalE"), 6));   
            stmt.setString(8, parseStringNull(hospitalizacion.get("codDiagnosticoRelacionadoE1"), 6));
            stmt.setString(9, parseStringNull(hospitalizacion.get("codDiagnosticoRelacionadoE2"), 6));
            stmt.setString(10, parseStringNull(hospitalizacion.get("codDiagnosticoRelacionadoE3"), 6));
            stmt.setString(11, parseStringNull(hospitalizacion.get("codComplicacion"), 6));           
            stmt.setString(12, parseStringNull(hospitalizacion.get("condicionDestinoUsuarioEgreso"), 3));
            stmt.setString(13, parseStringNull(hospitalizacion.get("codDiagnosticoCausaMuerte"), 6)); 
            stmt.setTimestamp(14, (Timestamp) parseFechaConHoraNull(hospitalizacion.get("fechaEgreso")));                                                        
            stmt.setInt(15, idMovDoc); 
            stmt.setInt(16, (Integer) parseIntegerNoNull(hospitalizacion.get("consecutivo"))); 

            int rowsAffected = stmt.executeUpdate();
            System.out.println("Hospitalización actualizada: " + rowsAffected + " fila(s) afectada(s)");
        }
    }

    private void actualizarRecienNacidos(Connection conn, Map<String, Object> recienNacidos, int idMovDoc) throws SQLException {
        String sql = """
            UPDATE dbo.Rips_RecienNacidos
            SET codPrestador = ?, tipoDocumentoIdentificacion = ?, numDocumentoIdentificacion = ?, fechaNacimiento = ?,
                edadGestacional = ?, numConsultasCPrenatal = ?, codSexoBiologico = ?, peso = ?,
                codDiagnosticoPrincipal = ?, condicionDestinoUsuarioEgreso = ?, codDiagnosticoCausaMuerte = ?,
                fechaEgreso = ?
            FROM dbo.Rips_RecienNacidos RN
            INNER JOIN dbo.Rips_Usuarios U ON U.IdRips_Usuario = RN.IdRips_Usuario
            INNER JOIN dbo.Rips_Transaccion T ON T.IdRips = U.IdRips
            WHERE T.IdMovDoc = ? AND RN.consecutivo = ? 
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, parseStringNoNull(recienNacidos.get("codPrestador"), 12));                  
            stmt.setString(2, parseStringNull(recienNacidos.get("tipoDocumentoIdentificacion"), 2));  
            stmt.setString(3, parseStringNull(recienNacidos.get("numDocumentoIdentificacion"), 20));  
            stmt.setTimestamp(4,(Timestamp) parseFechaConHoraNull(recienNacidos.get("fechaNacimiento")));                                                    
            stmt.setInt(5, (Integer) parseIntegerNoNull(recienNacidos.get("edadGestacional")));                           
            stmt.setInt(6, (Integer) parseIntegerNoNull(recienNacidos.get("numConsultasCPrenatal")));                     
            stmt.setString(7, parseStringNoNull(recienNacidos.get("codSexoBiologico"), 2));               
            stmt.setBigDecimal(8, (BigDecimal) parseBigDecimalNull(recienNacidos.get("peso")));                    
            stmt.setString(9, parseStringNull(recienNacidos.get("codDiagnosticoPrincipal"), 9));      
            stmt.setString(10, parseStringNoNull(recienNacidos.get("condicionDestinoUsuarioEgreso"), 2)); 
            stmt.setString(11, parseStringNull(recienNacidos.get("codDiagnosticoCausaMuerte"), 9));   
            stmt.setTimestamp(12, (Timestamp) parseFechaConHoraNoNull(recienNacidos.get("fechaEgreso")));                                                                        
            stmt.setInt(13, idMovDoc);
            stmt.setInt(14, (Integer) parseIntegerNoNull(recienNacidos.get("consecutivo"))); 

            int rowsAffected = stmt.executeUpdate();
            System.out.println("Recién Nacidos actualizado: " + rowsAffected + " filas afectadas");
        }
    }

    private void actualizarMedicamentos(Connection conn, Map<String, Object> medicamentos, int idMovDoc) throws SQLException {
        String sql = """
            UPDATE dbo.Rips_Medicamentos
            SET codPrestador = ?, numAutorizacion = ?, idMIPRES = ?, fechaDispensAdmon = ?, 
                codDiagnosticoPrincipal = ?, codDiagnosticoRelacionado = ?, tipoMedicamento = ?, 
                codTecnologiaSalud = ?, nomTecnologiaSalud = ?, concentracionMedicamento = ?, 
                unidadMedida = ?, formaFarmaceutica = ?, unidadMinDispensa = ?, cantidadMedicamento = ?, 
                diasTratamiento = ?, vrUnitMedicamento = ?, vrServicio = ?, 
                tipoPagoModerador = ?, valorPagoModerador = ?, numFEVPagoModerador = ?, 
                tipoDocumentoIdentificacion = ?, numDocumentoIdentificacion = ?
            FROM dbo.Rips_Medicamentos M
            INNER JOIN dbo.Rips_Usuarios U ON U.IdRips_Usuario = M.IdRips_Usuario
            INNER JOIN dbo.Rips_Transaccion T ON T.IdRips = U.IdRips
            WHERE T.IdMovDoc = ? AND M.consecutivo = ?
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, parseStringNoNull(medicamentos.get("codPrestador"), 12));                 
            stmt.setString(2, parseStringNull(medicamentos.get("numAutorizacion"), 30));              
            stmt.setString(3, parseStringNull(medicamentos.get("idMIPRES"), 20));                     
            stmt.setTimestamp(4, (Timestamp) parseFechaConHoraNull(medicamentos.get("fechaDispensAdmon")));       
            stmt.setString(5, parseStringNull(medicamentos.get("codDiagnosticoPrincipal"), 25));      
            stmt.setString(6, parseStringNull(medicamentos.get("codDiagnosticoRelacionado"), 25));    
            stmt.setString(7, parseStringNoNull(medicamentos.get("tipoMedicamento"), 2));               
            stmt.setString(8, parseStringNull(medicamentos.get("codTecnologiaSalud"), 20));           
            stmt.setString(9, parseStringNull(medicamentos.get("nomTecnologiaSalud"), 100));          
            stmt.setInt(10, (Integer) parseIntegerNull(medicamentos.get("concentracionMedicamento")));
            stmt.setInt(11, (Integer) parseIntegerNull(medicamentos.get("unidadMedida")));            
            stmt.setString(12, parseStringNull(medicamentos.get("formaFarmaceutica"), 10));           
            stmt.setInt(13, (Integer) parseIntegerNoNull(medicamentos.get("unidadMinDispensa")));       
            stmt.setBigDecimal(14, (BigDecimal) parseBigDecimalNoNull(medicamentos.get("cantidadMedicamento")));    
            stmt.setInt(15, (Integer) parseIntegerNull(medicamentos.get("diasTratamiento")));         
            stmt.setBigDecimal(16, (BigDecimal) parseBigDecimalNoNull(medicamentos.get("vrUnitMedicamento")));   
            stmt.setBigDecimal(17, (BigDecimal) parseBigDecimalNull(medicamentos.get("vrServicio")));          
            stmt.setString(18, parseStringNoNull(medicamentos.get("conceptoRecaudo"), 2));            
            stmt.setInt(19, (Integer) parseIntegerNoNull(medicamentos.get("valorPagoModerador")));      
            stmt.setString(20, parseStringNull(medicamentos.get("numFEVPagoModerador"), 20));         
            stmt.setString(21, parseStringNull(medicamentos.get("tipoDocumentoIdentificacion"), 2));  
            stmt.setString(22, parseStringNull(medicamentos.get("numDocumentoIdentificacion"), 20));  
            stmt.setInt(23, idMovDoc);
            stmt.setInt(24, (Integer) parseIntegerNoNull(medicamentos.get("consecutivo"))); 

            int rowsAffected = stmt.executeUpdate();
            System.out.println("Medicamentos actualizado: " + rowsAffected + " fila(s) afectada(s)");
        }
    }

    private void actualizarOtrosServicios(Connection conn, Map<String, Object> otrosServicios, int idMovDoc) throws SQLException {
        String sql = """
            UPDATE dbo.Rips_OtrosServicios
            SET codPrestador = ?, numAutorizacion = ?, idMIPRES = ?, fechaSuministroTecnologia = ?, tipoOS = ?,
                codTecnologiaSalud = ?, nomTecnologiaSalud = ?, cantidadOS = ?, tipoDocumentoIdentificacion = ?,
                numDocumentoIdentificacion = ?, vrUnitOS = ?, vrServicio = ?, conceptoRecaudo = ?, valorPagoModerador = ?,
                numFEVPagoModerador = ?
            FROM dbo.Rips_OtrosServicios OS
            INNER JOIN dbo.Rips_Usuarios U ON U.IdRips_Usuario = OS.IdRips_Usuario
            INNER JOIN dbo.Rips_Transaccion T ON T.IdRips = U.IdRips
            WHERE T.IdMovDoc = ? AND OS.consecutivo = ?
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, parseStringNoNull(otrosServicios.get("codPrestador"), 12));
            stmt.setString(2, parseStringNull(otrosServicios.get("numAutorizacion"), 30));
            stmt.setString(3, parseStringNull(otrosServicios.get("idMIPRES"), 20));
            stmt.setTimestamp(4, (Timestamp) parseFechaConHoraNoNull(otrosServicios.get("fechaSuministroTecnologia")));
            stmt.setString(5, parseStringNoNull(otrosServicios.get("tipoOS"), 2));
            stmt.setString(6, parseStringNoNull(otrosServicios.get("codTecnologiaSalud"), 20));
            stmt.setString(7, parseStringNull(otrosServicios.get("nomTecnologiaSalud"), 60));
            stmt.setInt(8, (Integer) parseIntegerNoNull(otrosServicios.get("cantidadOS")));
            stmt.setString(9, parseStringNull(otrosServicios.get("tipoDocumentoIdentificacion"), 2));
            stmt.setString(10, parseStringNull(otrosServicios.get("numDocumentoIdentificacion"), 20));
            stmt.setBigDecimal(11, (BigDecimal) parseBigDecimalNoNull(otrosServicios.get("vrUnitOS")));
            stmt.setBigDecimal(12, (BigDecimal) parseBigDecimalNoNull(otrosServicios.get("vrServicio")));
            stmt.setString(13, parseStringNoNull(otrosServicios.get("conceptoRecaudo"), 2));
            stmt.setInt(14, (Integer) parseIntegerNoNull(otrosServicios.get("valorPagoModerador")));
            stmt.setString(15, parseStringNull(otrosServicios.get("numFEVPagoModerador"), 20));
            stmt.setInt(16, idMovDoc);
            stmt.setInt(17, (Integer) parseIntegerNoNull(otrosServicios.get("consecutivo")));

            int rowsAffected = stmt.executeUpdate();
            System.out.println("Otros Servicios actualizado: " + rowsAffected + " fila(s) afectada(s)");
        } catch (SQLException e) {
            System.err.println("Error en actualizarOtrosServicios: " + e.getMessage());
            throw e;
        }
    }
}
