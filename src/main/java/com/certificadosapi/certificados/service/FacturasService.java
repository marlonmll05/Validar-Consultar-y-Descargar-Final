package com.certificadosapi.certificados.service;

import com.certificadosapi.certificados.config.DatabaseConfig;
import com.certificadosapi.certificados.dto.ZipResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class FacturasService {

    private final DatabaseConfig databaseConfig;


    @Autowired
    public FacturasService(DatabaseConfig databaseConfig){
        this.databaseConfig = databaseConfig;
    }
    
    // Método para exportar el XML en el servicio
    public byte[] exportDocXml(int idMovDoc) throws SQLException {
        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoftFinanciero_ST"))) {
            
            String query = "SELECT CONVERT(XML, DocXmlEnvelope) AS DocXml FROM MovimientoDocumentos WHERE IdMovDoc = ?";
            
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, idMovDoc);
                
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        String docXmlContent = rs.getString("DocXml");
                        if (docXmlContent != null) {
                            return docXmlContent.getBytes(StandardCharsets.UTF_8);
                        } else {
                            throw new SQLException("El campo DocXmlEnvelope está vacío para el ID proporcionado.");
                        }
                    } else {
                        throw new IllegalArgumentException("No se encontró ningún documento para el ID proporcionado.");
                    }
                }
            }  
        } 
    }

    //Metodo parar crear JSON de Facturas
    public byte[] generarjson(int idMovDoc) throws SQLException, JsonProcessingException {

        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoft100_ST"))) {

            String facturasQuery = "SELECT IdEmpresaGrupo, NFact, tipoNota, numNota FROM dbo.Rips_Transaccion WHERE IdMovDoc = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(facturasQuery)) {
                pstmt.setInt(1, idMovDoc);
                try (ResultSet facturasRs = pstmt.executeQuery()) {
                    ObjectMapper mapper = new ObjectMapper();
                    ObjectNode resultado = mapper.createObjectNode();
                    ArrayNode usuariosNode = mapper.createArrayNode();

                    while (facturasRs.next()) {
                        String numDocumentoIdObligado = facturasRs.getString("IdEmpresaGrupo");
                        String numFactura = facturasRs.getString("NFact");
                        String tipoNota = facturasRs.getString("tipoNota");
                        String numNota = facturasRs.getString("numNota");

                        resultado.put("numDocumentoIdObligado", numDocumentoIdObligado);
                        resultado.put("numFactura", numFactura);
                        resultado.put("tipoNota", tipoNota);
                        resultado.put("numNota", numNota);

                        String usuariosQuery = "SELECT IdRips_Usuario, tipoDocumentoIdentificacion, numDocumentoIdentificacion, tipoUsuario, fechaNacimiento, codSexo, CodPaisResidencia, ResHabitual, codZonaTerritorialResidencia, incapacidad, consecutivo, codPaisOrigen FROM dbo.Rips_Usuarios INNER JOIN dbo.Rips_Transaccion ON dbo.Rips_Transaccion.IdRips=dbo.Rips_Usuarios.IdRips WHERE dbo.Rips_Transaccion.IdMovDoc = ?";
                        try (PreparedStatement usuariosStmt = conn.prepareStatement(usuariosQuery)) {
                            usuariosStmt.setInt(1, idMovDoc);
                            try (ResultSet usuariosRs = usuariosStmt.executeQuery()) {
                                while (usuariosRs.next()) {
                                    ObjectNode usuarioNode = mapper.createObjectNode();
                                    int idRipsUsuario = usuariosRs.getInt("IdRips_Usuario");

                                    usuarioNode.put("tipoDocumentoIdentificacion", usuariosRs.getString("tipoDocumentoIdentificacion"));
                                    usuarioNode.put("numDocumentoIdentificacion", usuariosRs.getString("numDocumentoIdentificacion"));
                                    usuarioNode.put("tipoUsuario", usuariosRs.getString("tipoUsuario"));

                                    Timestamp fechaNacimientoTimestamp = usuariosRs.getTimestamp("fechaNacimiento");
                                    if (fechaNacimientoTimestamp != null) {
                                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                                        String fechaNacimientoStr = sdf.format(fechaNacimientoTimestamp);
                                        usuarioNode.put("fechaNacimiento", fechaNacimientoStr);
                                    }

                                    usuarioNode.put("codSexo", usuariosRs.getString("codSexo"));
                                    usuarioNode.put("codPaisResidencia", usuariosRs.getString("codPaisResidencia"));
                                    usuarioNode.put("codMunicipioResidencia", usuariosRs.getString("ResHabitual")); 
                                    usuarioNode.put("codZonaTerritorialResidencia", usuariosRs.getString("codZonaTerritorialResidencia"));
                                    usuarioNode.put("incapacidad", usuariosRs.getString("incapacidad"));
                                    usuarioNode.put("consecutivo", usuariosRs.getInt("consecutivo"));
                                    usuarioNode.put("codPaisOrigen", usuariosRs.getString("codPaisOrigen"));

                                    ObjectNode serviciosNode = mapper.createObjectNode();

                                    String consultasQuery = "SELECT C.codPrestador, C.fechaInicioAtencion, C.numAutorizacion, C.codConsulta, C.modalidadGrupoServicioTecSal, C.grupoServicios, C.codServicio, C.finalidadTecnologiaSalud, C.causaMotivoAtencion, C.codDiagnosticoPrincipal, C.codDiagnosticoRelacionado1, C.codDiagnosticoRelacionado2, C.codDiagnosticoRelacionado3, C.tipoDiagnosticoPrincipal, C.tipoDocumentoIdentificacion, C.numDocumentoIdentificacion, C.vrServicio, C.tipoPagoModerador, C.valorPagoModerador, C.numFEVPagoModerador, C.consecutivo FROM dbo.Rips_Consulta C INNER JOIN dbo.Rips_Usuarios U ON U.IdRips_Usuario=C.IdRips_Usuario INNER JOIN dbo.Rips_Transaccion T ON T.IdRips=U.IdRips WHERE C.IdRips_Usuario = ?";
                                    try (PreparedStatement consultasStmt = conn.prepareStatement(consultasQuery)) {
                                        consultasStmt.setInt(1, idRipsUsuario);
                                        try (ResultSet consultasRs = consultasStmt.executeQuery()) {
                                            ArrayNode consultasNode = mapper.createArrayNode();
                                            while (consultasRs.next()) {
                                                String numAutorizacion = consultasRs.getString("numAutorizacion");
                                                String numFEVPagoModerador = consultasRs.getString("numFEVPagoModerador");

                                                ObjectNode consultaNode = mapper.createObjectNode();
                                                consultaNode.put("codPrestador", consultasRs.getString("codPrestador"));

                                                Timestamp fechaInicioAtencionTimestamp = consultasRs.getTimestamp("fechaInicioAtencion");
                                                if (fechaInicioAtencionTimestamp != null) {
                                                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm"); 
                                                    String fechaInicioAtencionStr = sdf.format(fechaInicioAtencionTimestamp);
                                                    consultaNode.put("fechaInicioAtencion", fechaInicioAtencionStr);
                                                } else {
                                                    consultaNode.putNull("fechaInicioAtencion");  
                                                }

                                                consultaNode.put("numAutorizacion", (numAutorizacion != null && !numAutorizacion.isEmpty()) ? numAutorizacion : null);
                                                consultaNode.put("codConsulta", consultasRs.getString("codConsulta"));
                                                consultaNode.put("modalidadGrupoServicioTecSal", consultasRs.getString("modalidadGrupoServicioTecSal"));
                                                consultaNode.put("grupoServicios", consultasRs.getString("grupoServicios"));
                                                consultaNode.put("codServicio", consultasRs.getInt("codServicio"));
                                                consultaNode.put("finalidadTecnologiaSalud", consultasRs.getString("finalidadTecnologiaSalud"));
                                                consultaNode.put("causaMotivoAtencion", consultasRs.getString("causaMotivoAtencion"));
                                                consultaNode.put("codDiagnosticoPrincipal", consultasRs.getString("codDiagnosticoPrincipal"));
                                                consultaNode.put("codDiagnosticoRelacionado1", consultasRs.getString("codDiagnosticoRelacionado1"));
                                                consultaNode.put("codDiagnosticoRelacionado2", consultasRs.getString("codDiagnosticoRelacionado2"));
                                                consultaNode.put("codDiagnosticoRelacionado3", consultasRs.getString("codDiagnosticoRelacionado3"));
                                                consultaNode.put("tipoDiagnosticoPrincipal", consultasRs.getString("tipoDiagnosticoPrincipal"));
                                                consultaNode.put("tipoDocumentoIdentificacion", consultasRs.getString("tipoDocumentoIdentificacion"));
                                                consultaNode.put("numDocumentoIdentificacion", consultasRs.getString("numDocumentoIdentificacion"));
                                                consultaNode.put("vrServicio", consultasRs.getInt("vrServicio"));
                                                consultaNode.put("conceptoRecaudo", consultasRs.getString("tipoPagoModerador")); 
                                                consultaNode.put("valorPagoModerador", consultasRs.getInt("valorPagoModerador"));
                                                consultaNode.put("numFEVPagoModerador", (numFEVPagoModerador != null && !numFEVPagoModerador.isEmpty()) ? numFEVPagoModerador : null);
                                                consultaNode.put("consecutivo", consultasRs.getInt("consecutivo"));
                                                
                                                consultasNode.add(consultaNode);
                                            }

                                            if (consultasNode.size() > 0) {
                                                serviciosNode.set("consultas", consultasNode);
                                            }
                                        }
                                    }

                                    String procedimientosQuery = "SELECT P.codPrestador, P.fechaInicioAtencion, P.idMIPRES, P.numAutorizacion, P.codProcedimiento, P.viaingresoServicioSalud, P.modalidadGrupoServicioTecSal, P.grupoServicios, P.codServicio, P.finalidadTecnologiaSalud, P.tipoDocumentoIdentificacion, P.numDocumentoIdentificacion, P.codDiagnosticoPrincipal, P.codDiagnosticoRelacionado, P.codComplicacion, P.vrServicio, P.tipoPagoModerador, P.valorPagoModerador, P.numFEVPagoModerador, P.consecutivo FROM dbo.Rips_Procedimientos P INNER JOIN dbo.Rips_Usuarios U ON U.IdRips_Usuario=P.IdRips_Usuario INNER JOIN dbo.Rips_Transaccion T ON T.IdRips=U.IdRips WHERE P.IdRips_Usuario = ?";
                                    try (PreparedStatement procedimientosStmt = conn.prepareStatement(procedimientosQuery)) {
                                        procedimientosStmt.setInt(1, idRipsUsuario);
                                        try (ResultSet procedimientosRs = procedimientosStmt.executeQuery()) {
                                            ArrayNode procedimientosNode = mapper.createArrayNode();
                                            while (procedimientosRs.next()) {
                                                String idMIPRES = procedimientosRs.getString("idMIPRES");
                                                String numAutorizacion = procedimientosRs.getString("numAutorizacion");
                                                String numFEVPagoModerador = procedimientosRs.getString("numFEVPagoModerador");

                                                ObjectNode procedimientoNode = mapper.createObjectNode();
                                                procedimientoNode.put("codPrestador", procedimientosRs.getString("codPrestador"));

                                                Timestamp fechaInicioAtencionTimestamp = procedimientosRs.getTimestamp("fechaInicioAtencion");
                                                if (fechaInicioAtencionTimestamp != null) {
                                                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                                                    String fechaInicioAtencionStr = sdf.format(fechaInicioAtencionTimestamp);
                                                    procedimientoNode.put("fechaInicioAtencion", fechaInicioAtencionStr);
                                                } else {
                                                    procedimientoNode.putNull("fechaInicioAtencion");  
                                                }

                                                procedimientoNode.put("idMIPRES", (idMIPRES != null && !idMIPRES.isEmpty()) ? idMIPRES : null);
                                                procedimientoNode.put("numAutorizacion", (numAutorizacion != null && !numAutorizacion.isEmpty()) ? numAutorizacion : null);
                                                procedimientoNode.put("codProcedimiento", procedimientosRs.getString("codProcedimiento"));
                                                procedimientoNode.put("viaIngresoServicioSalud", procedimientosRs.getString("viaIngresoServicioSalud"));
                                                procedimientoNode.put("modalidadGrupoServicioTecSal", procedimientosRs.getString("modalidadGrupoServicioTecSal"));
                                                procedimientoNode.put("grupoServicios", procedimientosRs.getString("grupoServicios"));
                                                procedimientoNode.put("codServicio", procedimientosRs.getInt("codServicio"));
                                                procedimientoNode.put("finalidadTecnologiaSalud", procedimientosRs.getString("finalidadTecnologiaSalud"));
                                                procedimientoNode.put("tipoDocumentoIdentificacion", procedimientosRs.getString("tipoDocumentoIdentificacion"));
                                                procedimientoNode.put("numDocumentoIdentificacion", procedimientosRs.getString("numDocumentoIdentificacion"));
                                                procedimientoNode.put("codDiagnosticoPrincipal", procedimientosRs.getString("codDiagnosticoPrincipal"));
                                                procedimientoNode.put("codDiagnosticoRelacionado", procedimientosRs.getString("codDiagnosticoRelacionado"));
                                                procedimientoNode.put("codComplicacion", procedimientosRs.getString("codComplicacion"));
                                                procedimientoNode.put("vrServicio", procedimientosRs.getInt("vrServicio"));
                                                procedimientoNode.put("conceptoRecaudo", procedimientosRs.getString("tipoPagoModerador"));
                                                procedimientoNode.put("valorPagoModerador", procedimientosRs.getInt("valorPagoModerador"));
                                                procedimientoNode.put("numFEVPagoModerador", (numFEVPagoModerador != null && !numFEVPagoModerador.isEmpty()) ? numFEVPagoModerador : null);
                                                procedimientoNode.put("consecutivo", procedimientosRs.getInt("consecutivo"));

                                                procedimientosNode.add(procedimientoNode);
                                            }

                                            if (procedimientosNode.size() > 0) {
                                                serviciosNode.set("procedimientos", procedimientosNode);
                                            }
                                        }
                                    }

                                    String urgenciasQuery = "SELECT UR.codPrestador, UR.fechaInicioAtencion, UR.causaMotivoAtencion, UR.codDiagnosticoPrincipal, UR.codDiagnosticoPrincipalE, UR.codDiagnosticoRelacionadoE1, UR.codDiagnosticoRelacionadoE2, UR.codDiagnosticoRelacionadoE3, UR.condicionDestinoUsuarioEgreso, UR.codDiagnosticoCausaMuerte, UR.fechaEgreso, UR.consecutivo FROM dbo.Rips_Urg UR INNER JOIN dbo.Rips_Usuarios U ON U.IdRips_Usuario=UR.IdRips_Usuario INNER JOIN dbo.Rips_Transaccion T ON T.IdRips=U.IdRips WHERE UR.IdRips_Usuario = ?";
                                    try (PreparedStatement urgenciasStmt = conn.prepareStatement(urgenciasQuery)) {
                                        urgenciasStmt.setInt(1, idRipsUsuario);
                                        try (ResultSet urgenciasRs = urgenciasStmt.executeQuery()) {
                                            ArrayNode urgenciasNode = mapper.createArrayNode();
                                            while (urgenciasRs.next()) {
                                                ObjectNode urgenciaNode = mapper.createObjectNode();
                                                urgenciaNode.put("codPrestador", urgenciasRs.getString("codPrestador"));

                                                Timestamp fechaInicioAtencionTimestamp = urgenciasRs.getTimestamp("fechaInicioAtencion");
                                                if (fechaInicioAtencionTimestamp != null) {
                                                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                                                    String fechaInicioAtencionStr = sdf.format(fechaInicioAtencionTimestamp);
                                                    urgenciaNode.put("fechaInicioAtencion", fechaInicioAtencionStr);
                                                } else {
                                                    urgenciaNode.putNull("fechaInicioAtencion");
                                                }

                                                urgenciaNode.put("causaMotivoAtencion", urgenciasRs.getString("causaMotivoAtencion"));
                                                urgenciaNode.put("codDiagnosticoPrincipal", urgenciasRs.getString("codDiagnosticoPrincipal"));
                                                urgenciaNode.put("codDiagnosticoPrincipalE", urgenciasRs.getString("codDiagnosticoPrincipalE"));
                                                urgenciaNode.put("codDiagnosticoRelacionadoE1", urgenciasRs.getString("codDiagnosticoRelacionadoE1"));
                                                urgenciaNode.put("codDiagnosticoRelacionadoE2", urgenciasRs.getString("codDiagnosticoRelacionadoE2"));
                                                urgenciaNode.put("codDiagnosticoRelacionadoE3", urgenciasRs.getString("codDiagnosticoRelacionadoE3"));
                                                urgenciaNode.put("condicionDestinoUsuarioEgreso", urgenciasRs.getString("condicionDestinoUsuarioEgreso")); 
                                                urgenciaNode.put("codDiagnosticoCausaMuerte", urgenciasRs.getString("codDiagnosticoCausaMuerte"));

                                                Timestamp fechaEgresoTimestamp = urgenciasRs.getTimestamp("fechaEgreso");
                                                if (fechaEgresoTimestamp != null) {
                                                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                                                    String fechaEgresoStr = sdf.format(fechaEgresoTimestamp);
                                                    urgenciaNode.put("fechaEgreso", fechaEgresoStr);
                                                } else {
                                                    urgenciaNode.putNull("fechaEgreso");
                                                }
                                                
                                                urgenciaNode.put("consecutivo", urgenciasRs.getInt("consecutivo"));
                                                urgenciasNode.add(urgenciaNode);
                                            }

                                            if (urgenciasNode.size() > 0) {
                                                serviciosNode.set("urgencias", urgenciasNode);
                                            }
                                        }
                                    }

                                    String hospitalizacionesQuery = "SELECT H.codPrestador, H.viaingresoServicioSalud, H.fechaInicioAtencion, H.numAutorizacion, H.causaMotivoAtencion, H.codDiagnosticoPrincipal, H.codDiagnosticoPrincipalE, H.codDiagnosticoRelacionadoE1, H.codDiagnosticoRelacionadoE2, H.codDiagnosticoRelacionadoE3, H.codComplicacion, H.condicionDestinoUsuarioEgreso, H.codDiagnosticoCausaMuerte, H.fechaEgreso, H.consecutivo FROM dbo.Rips_Hospitalizacion H INNER JOIN dbo.Rips_Usuarios U ON U.IdRips_Usuario=H.IdFRips_Usuario INNER JOIN dbo.Rips_Transaccion T ON T.IdRips=U.IdRips WHERE H.IdFrips_Usuario = ?";
                                    try (PreparedStatement hospitalizacionesStmt = conn.prepareStatement(hospitalizacionesQuery)) {
                                        hospitalizacionesStmt.setInt(1, idRipsUsuario);
                                        try (ResultSet hospitalizacionesRs = hospitalizacionesStmt.executeQuery()) {
                                            ArrayNode hospitalizacionesNode = mapper.createArrayNode();
                                            while (hospitalizacionesRs.next()) {
                                                String numAutorizacion = hospitalizacionesRs.getString("numAutorizacion");

                                                ObjectNode hospitalizacionNode = mapper.createObjectNode();
                                                hospitalizacionNode.put("codPrestador", hospitalizacionesRs.getString("codPrestador"));
                                                hospitalizacionNode.put("viaIngresoServicioSalud", hospitalizacionesRs.getString("viaingresoServicioSalud"));

                                                Timestamp fechaInicioAtencionTimestamp = hospitalizacionesRs.getTimestamp("fechaInicioAtencion");
                                                if (fechaInicioAtencionTimestamp != null) {
                                                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm"); 
                                                    String fechaInicioAtencionStr = sdf.format(fechaInicioAtencionTimestamp);
                                                    hospitalizacionNode.put("fechaInicioAtencion", fechaInicioAtencionStr);
                                                } else {
                                                    hospitalizacionNode.putNull("fechaInicioAtencion");  
                                                }

                                                hospitalizacionNode.put("numAutorizacion", (numAutorizacion != null && !numAutorizacion.isEmpty()) ? numAutorizacion : null);
                                                hospitalizacionNode.put("causaMotivoAtencion", hospitalizacionesRs.getString("causaMotivoAtencion"));
                                                hospitalizacionNode.put("codDiagnosticoPrincipal", hospitalizacionesRs.getString("codDiagnosticoPrincipal"));
                                                hospitalizacionNode.put("codDiagnosticoPrincipalE", hospitalizacionesRs.getString("codDiagnosticoPrincipalE"));
                                                hospitalizacionNode.put("codDiagnosticoRelacionadoE1", hospitalizacionesRs.getString("codDiagnosticoRelacionadoE1"));
                                                hospitalizacionNode.put("codDiagnosticoRelacionadoE2", hospitalizacionesRs.getString("codDiagnosticoRelacionadoE2"));
                                                hospitalizacionNode.put("codDiagnosticoRelacionadoE3", hospitalizacionesRs.getString("codDiagnosticoRelacionadoE3"));
                                                hospitalizacionNode.put("codComplicacion", hospitalizacionesRs.getString("codComplicacion"));
                                                hospitalizacionNode.put("condicionDestinoUsuarioEgreso", hospitalizacionesRs.getString("condicionDestinoUsuarioEgreso"));
                                                hospitalizacionNode.put("codDiagnosticoCausaMuerte", hospitalizacionesRs.getString("codDiagnosticoCausaMuerte"));

                                                Timestamp fechaEgresoTimestamp = hospitalizacionesRs.getTimestamp("fechaEgreso");
                                                if (fechaEgresoTimestamp != null) {
                                                    SimpleDateFormat sdf2 = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                                                    String fechaEgresoStr = sdf2.format(fechaEgresoTimestamp);
                                                    hospitalizacionNode.put("fechaEgreso", fechaEgresoStr);
                                                } else {
                                                    hospitalizacionNode.putNull("fechaEgreso");  
                                                }

                                                hospitalizacionNode.put("consecutivo", hospitalizacionesRs.getInt("consecutivo"));
                                                hospitalizacionesNode.add(hospitalizacionNode);
                                            }
                                            if (hospitalizacionesNode.size() > 0) {
                                                serviciosNode.set("hospitalizacion", hospitalizacionesNode);
                                            }
                                        }
                                    }

                                    String recienNacidosQuery = "SELECT RN.codPrestador, RN.tipoDocumentoIdentificacion, RN.numDocumentoIdentificacion, RN.fechaNacimiento, RN.edadGestacional, RN.numConsultasCPrenatal, RN.codSexoBiologico, RN.peso, RN.codDiagnosticoPrincipal, RN.condicionDestinoUsuarioEgreso, RN.codDiagnosticoCausaMuerte, RN.fechaEgreso, RN.consecutivo FROM dbo.Rips_RecienNacidos RN INNER JOIN dbo.Rips_Usuarios U ON U.IdRips_Usuario=RN.IdRips_Usuario INNER JOIN dbo.Rips_Transaccion T ON T.IdRips=U.IdRips WHERE RN.IdRips_Usuario = ?";
                                    try (PreparedStatement recienNacidosStmt = conn.prepareStatement(recienNacidosQuery)) {
                                        recienNacidosStmt.setInt(1, idRipsUsuario);
                                        try (ResultSet recienNacidosRs = recienNacidosStmt.executeQuery()) {
                                            ArrayNode recienNacidosNode = mapper.createArrayNode();
                                            while (recienNacidosRs.next()) {
                                                ObjectNode recienNacidoNode = mapper.createObjectNode();
                                                recienNacidoNode.put("codPrestador", recienNacidosRs.getString("codPrestador"));
                                                recienNacidoNode.put("tipoDocumentoIdentificacion", recienNacidosRs.getString("tipoDocumentoIdentificacion"));
                                                recienNacidoNode.put("numDocumentoIdentificacion", recienNacidosRs.getString("numDocumentoIdentificacion"));

                                                Timestamp fechaNacimientoTimestamp2 = recienNacidosRs.getTimestamp("fechaNacimiento");
                                                if (fechaNacimientoTimestamp2 != null) {
                                                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm"); 
                                                    String fechaNacimientoStr = sdf.format(fechaNacimientoTimestamp2);
                                                    recienNacidoNode.put("fechaNacimiento", fechaNacimientoStr);
                                                } else {
                                                    recienNacidoNode.putNull("fechaNacimiento");  
                                                }

                                                recienNacidoNode.put("edadGestacional", recienNacidosRs.getInt("edadGestacional"));
                                                recienNacidoNode.put("numConsultasCPrenatal", recienNacidosRs.getInt("numConsultasCPrenatal"));
                                                recienNacidoNode.put("codSexoBiologico", recienNacidosRs.getString("codSexoBiologico"));
                                                recienNacidoNode.put("peso", recienNacidosRs.getBigDecimal("peso"));
                                                recienNacidoNode.put("codDiagnosticoPrincipal", recienNacidosRs.getString("codDiagnosticoPrincipal"));
                                                recienNacidoNode.put("condicionDestinoUsuarioEgreso", recienNacidosRs.getString("condicionDestinoUsuarioEgreso"));
                                                recienNacidoNode.put("codDiagnosticoCausaMuerte", recienNacidosRs.getString("codDiagnosticoCausaMuerte"));

                                                Timestamp fechaEgresoTimestamp = recienNacidosRs.getTimestamp("fechaEgreso");
                                                if (fechaEgresoTimestamp != null) {
                                                    SimpleDateFormat sdf2 = new SimpleDateFormat("yyyy-MM-dd HH:mm"); 
                                                    String fechaEgresoStr = sdf2.format(fechaEgresoTimestamp);
                                                    recienNacidoNode.put("fechaEgreso", fechaEgresoStr);
                                                }

                                                recienNacidoNode.put("consecutivo", recienNacidosRs.getInt("consecutivo"));
                                                recienNacidosNode.add(recienNacidoNode);
                                            }

                                            if (recienNacidosNode.size() > 0) {
                                                serviciosNode.set("recienNacidos", recienNacidosNode);
                                            }
                                        }
                                    }

                                    String medicamentosQuery = "SELECT M.codPrestador, M.numAutorizacion, M.idMIPRES, M.fechaDispensAdmon, M.codDiagnosticoPrincipal, M.codDiagnosticoRelacionado, M.tipoMedicamento, M.codTecnologiaSalud, M.nomTecnologiaSalud, M.concentracionMedicamento, M.unidadMedida, M.formaFarmaceutica, M.unidadMinDispensa, M.cantidadMedicamento, M.diasTratamiento, M.tipoDocumentoIdentificacion, M.numDocumentoidentificacion, M.vrUnitMedicamento, M.vrServicio, M.tipoPagoModerador, M.valorPagoModerador, M.numFEVPagoModerador, M.consecutivo FROM dbo.Rips_Medicamentos M INNER JOIN dbo.Rips_Usuarios U ON U.IdRips_Usuario=M.IdRips_Usuario INNER JOIN dbo.Rips_Transaccion T ON T.IdRips=U.IdRips WHERE M.IdRips_Usuario = ?";
                                    try (PreparedStatement medicamentosStmt = conn.prepareStatement(medicamentosQuery)) {
                                        medicamentosStmt.setInt(1, idRipsUsuario);
                                        try (ResultSet medicamentosRs = medicamentosStmt.executeQuery()) {
                                            ArrayNode medicamentosNode = mapper.createArrayNode();
                                            while (medicamentosRs.next()) {

                                                String numAutorizacion = medicamentosRs.getString("numAutorizacion");
                                                String idMIPRES = medicamentosRs.getString("idMIPRES");
                                                String numFEVPagoModerador = medicamentosRs.getString("numFEVPagoModerador");

                                                ObjectNode medicamentoNode = mapper.createObjectNode();
                                                medicamentoNode.put("codPrestador", medicamentosRs.getString("codPrestador"));
                                                medicamentoNode.put("numAutorizacion", (numAutorizacion != null && !numAutorizacion.isEmpty()) ? numAutorizacion : null);
                                                medicamentoNode.put("idMIPRES", (idMIPRES != null && !idMIPRES.isEmpty()) ? idMIPRES : null);

                                                Timestamp fechaDispensAdmonTimestamp = medicamentosRs.getTimestamp("fechaDispensAdmon");
                                                if (fechaDispensAdmonTimestamp != null) {
                                                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm"); 
                                                    String fechaDispensAdmonStr = sdf.format(fechaDispensAdmonTimestamp);
                                                    medicamentoNode.put("fechaDispensAdmon", fechaDispensAdmonStr);
                                                } else {
                                                    medicamentoNode.putNull("fechaDispensAdmon"); 
                                                }

                                                medicamentoNode.put("codDiagnosticoPrincipal", medicamentosRs.getString("codDiagnosticoPrincipal"));
                                                medicamentoNode.put("codDiagnosticoRelacionado", medicamentosRs.getString("codDiagnosticoRelacionado"));
                                                medicamentoNode.put("tipoMedicamento", medicamentosRs.getString("tipoMedicamento"));
                                                medicamentoNode.put("codTecnologiaSalud", medicamentosRs.getString("codTecnologiaSalud"));
                                                medicamentoNode.put("nomTecnologiaSalud", medicamentosRs.getString("nomTecnologiaSalud"));
                                                medicamentoNode.put("concentracionMedicamento", medicamentosRs.getInt("concentracionMedicamento"));
                                                medicamentoNode.put("unidadMedida", medicamentosRs.getInt("unidadMedida"));
                                                medicamentoNode.put("formaFarmaceutica", medicamentosRs.getString("formaFarmaceutica"));
                                                medicamentoNode.put("unidadMinDispensa", medicamentosRs.getInt("unidadMinDispensa"));
                                                medicamentoNode.put("cantidadMedicamento", medicamentosRs.getBigDecimal("cantidadMedicamento"));
                                                medicamentoNode.put("diasTratamiento", medicamentosRs.getInt("diasTratamiento"));
                                                medicamentoNode.put("tipoDocumentoIdentificacion", medicamentosRs.getString("tipoDocumentoIdentificacion"));
                                                medicamentoNode.put("numDocumentoIdentificacion", medicamentosRs.getString("numDocumentoidentificacion"));
                                                medicamentoNode.put("vrUnitMedicamento", medicamentosRs.getBigDecimal("vrUnitMedicamento"));
                                                medicamentoNode.put("vrServicio", medicamentosRs.getBigDecimal("vrServicio"));
                                                medicamentoNode.put("conceptoRecaudo", medicamentosRs.getString("tipoPagoModerador"));
                                                medicamentoNode.put("valorPagoModerador", medicamentosRs.getInt("valorPagoModerador"));
                                                medicamentoNode.put("numFEVPagoModerador", (numFEVPagoModerador != null && !numFEVPagoModerador.isEmpty()) ? numFEVPagoModerador : null);
                                                medicamentoNode.put("consecutivo", medicamentosRs.getInt("consecutivo"));
                                                medicamentosNode.add(medicamentoNode);
                                            }

                                            if (medicamentosNode.size() > 0) {
                                                serviciosNode.set("medicamentos", medicamentosNode);
                                            }
                                        }
                                    }

                                    String otroServiciosQuery = "SELECT OS.codPrestador, OS.numAutorizacion, OS.idMIPRES, OS.fechaSuministroTecnologia, OS.tipoOS, OS.codTecnologiaSalud, OS.nomTecnologiaSalud, OS.cantidadOS, OS.tipoDocumentoIdentificacion, OS.numDocumentoIdentificacion, OS.vrUnitOS, OS.vrServicio, OS.conceptoRecaudo, OS.valorPagoModerador, OS.numFEVPagoModerador, OS.consecutivo FROM dbo.Rips_OtrosServicios OS INNER JOIN dbo.Rips_Usuarios U ON U.IdRips_Usuario=OS.IdRips_Usuario INNER JOIN dbo.Rips_Transaccion T ON T.IdRips=U.IdRips WHERE OS.IdRips_Usuario = ?";
                                    try (PreparedStatement otroServiciosStmt = conn.prepareStatement(otroServiciosQuery)) {
                                        otroServiciosStmt.setInt(1, idRipsUsuario);
                                        try (ResultSet otroServiciosRs = otroServiciosStmt.executeQuery()) {
                                            ArrayNode otroServiciosNode = mapper.createArrayNode();
                                            while (otroServiciosRs.next()) {
                                                String numAutorizacion = otroServiciosRs.getString("numAutorizacion");
                                                String idMIPRES = otroServiciosRs.getString("idMIPRES");
                                                String numFEVPagoModerador = otroServiciosRs.getString("numFEVPagoModerador");

                                                ObjectNode otroServicioNode = mapper.createObjectNode();
                                                otroServicioNode.put("codPrestador", otroServiciosRs.getString("codPrestador"));

                                                otroServicioNode.put("numAutorizacion", (numAutorizacion != null && !numAutorizacion.isEmpty()) ? numAutorizacion : null);
                                                otroServicioNode.put("idMIPRES", (idMIPRES != null && !idMIPRES.isEmpty()) ? idMIPRES : null);

                                                Timestamp fechaSuministroTecnologiaTimestamp = otroServiciosRs.getTimestamp("fechaSuministroTecnologia");
                                                if (fechaSuministroTecnologiaTimestamp != null) {
                                                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                                                    String fechaSuministroTecnologiaStr = sdf.format(fechaSuministroTecnologiaTimestamp);
                                                    otroServicioNode.put("fechaSuministroTecnologia", fechaSuministroTecnologiaStr);
                                                }

                                                otroServicioNode.put("tipoOS", otroServiciosRs.getString("tipoOS"));
                                                otroServicioNode.put("codTecnologiaSalud", otroServiciosRs.getString("codTecnologiaSalud"));
                                                otroServicioNode.put("nomTecnologiaSalud", otroServiciosRs.getString("nomTecnologiaSalud"));
                                                otroServicioNode.put("cantidadOS", otroServiciosRs.getInt("cantidadOS"));
                                                otroServicioNode.put("tipoDocumentoIdentificacion", otroServiciosRs.getString("tipoDocumentoIdentificacion"));
                                                otroServicioNode.put("numDocumentoIdentificacion", otroServiciosRs.getString("numDocumentoIdentificacion"));
                                                otroServicioNode.put("vrUnitOS", otroServiciosRs.getBigDecimal("vrUnitOS"));
                                                otroServicioNode.put("vrServicio", otroServiciosRs.getBigDecimal("vrServicio"));
                                                otroServicioNode.put("conceptoRecaudo", otroServiciosRs.getString("conceptoRecaudo"));
                                                otroServicioNode.put("valorPagoModerador", otroServiciosRs.getInt("valorPagoModerador"));
                                                otroServicioNode.put("numFEVPagoModerador", (numFEVPagoModerador != null && !numFEVPagoModerador.isEmpty() ? numFEVPagoModerador : null));
                                                otroServicioNode.put("consecutivo", otroServiciosRs.getInt("consecutivo"));
                                                
                                                otroServiciosNode.add(otroServicioNode);
                                            }

                                            if (otroServiciosNode.size() > 0) {
                                                serviciosNode.set("otrosServicios", otroServiciosNode);
                                            }
                                        }
                                    }

                                    if (serviciosNode.size() > 0) {
                                        usuarioNode.set("servicios", serviciosNode);
                                        usuariosNode.add(usuarioNode);
                                    }
                                }
                            }
                        }
                    }
                    
                    resultado.set("usuarios", usuariosNode);
                    
                    if (usuariosNode.size() == 0) {
                        throw new RuntimeException("No se encontraron datos en Usuarios");
                    }
                    
                    return mapper.writerWithDefaultPrettyPrinter()
                            .writeValueAsString(resultado)
                            .getBytes(StandardCharsets.UTF_8);
                }
            }
        }
    }

    //Metodo para crear TXT de Facturas
    public Map<String, byte[]> generarTxt(int idMovDoc) throws SQLException {
        Map<String, byte[]> txtFiles = new HashMap<>();
    
        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoft100_ST"));){
            
            StringBuilder sbFacturas = new StringBuilder();
            String facturasQuery = "SELECT IdEmpresaGrupo, NFact, tipoNota, numNota FROM dbo.Rips_Transaccion WHERE IdMovDoc = ?";
            
            try(PreparedStatement pstmt = conn.prepareStatement(facturasQuery);){
                pstmt.setInt(1, idMovDoc);
                try(ResultSet facturasRs = pstmt.executeQuery()){
                    while (facturasRs.next()) {
                        sbFacturas.append(String.format("%s,%s,%s,%s\n",
                                facturasRs.getString("IdEmpresaGrupo"),
                                facturasRs.getString("NFact"),
                                facturasRs.getString("tipoNota"),
                                facturasRs.getString("numNota")));
                    }
                }
            }

            if (sbFacturas.length() > 0) {
                txtFiles.put("transaccion.txt", sbFacturas.toString().getBytes(StandardCharsets.UTF_8));
            }

            StringBuilder sbUsuarios = new StringBuilder();
            String usuariosQuery = "SELECT tipoDocumentoIdentificacion, numDocumentoIdentificacion, tipoUsuario, fechaNacimiento, codSexo, CodPaisResidencia, ResHabitual, codZonaTerritorialResidencia, incapacidad, consecutivo, codPaisOrigen FROM dbo.Rips_Usuarios INNER JOIN dbo.Rips_Transaccion ON dbo.Rips_Transaccion.IdRips=dbo.Rips_Usuarios.IdRips WHERE dbo.Rips_Transaccion.IdMovDoc = ?";
            
            try(PreparedStatement usuariosStmt = conn.prepareStatement(usuariosQuery)){
                usuariosStmt.setInt(1, idMovDoc);
                try(ResultSet usuariosRs = usuariosStmt.executeQuery()){
                    while (usuariosRs.next()) {
                        sbUsuarios.append(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%d,%s\n",
                                usuariosRs.getString("tipoDocumentoIdentificacion"),
                                usuariosRs.getString("numDocumentoIdentificacion"),
                                usuariosRs.getString("tipoUsuario"),
                                usuariosRs.getTimestamp("fechaNacimiento") != null ? new SimpleDateFormat("yyyy-MM-dd").format(usuariosRs.getTimestamp("fechaNacimiento")) : null,
                                usuariosRs.getString("codSexo"),
                                usuariosRs.getString("CodPaisResidencia"),
                                usuariosRs.getString("ResHabitual"),
                                usuariosRs.getString("codZonaTerritorialResidencia"),
                                usuariosRs.getString("incapacidad"),
                                usuariosRs.getInt("consecutivo"),
                                usuariosRs.getString("codPaisOrigen")));
                    }
                }
            }
            
            if (sbUsuarios.length() > 0) {
                txtFiles.put("usuarios.txt", sbUsuarios.toString().getBytes(StandardCharsets.UTF_8));
            }

            StringBuilder sbConsultas = new StringBuilder();
            String consultasQuery = "SELECT C.codPrestador, C.fechaInicioAtencion, C.numAutorizacion, C.codConsulta, C.modalidadGrupoServicioTecSal, C.grupoServicios, C.codServicio\n" +
                    ", C.finalidadTecnologiaSalud, C.causaMotivoAtencion, C.codDiagnosticoPrincipal, C.codDiagnosticoRelacionado1, C.codDiagnosticoRelacionado2\n" +
                    ", C.codDiagnosticoRelacionado3, C.tipoDiagnosticoPrincipal, C.tipoDocumentoIdentificacion, C.numDocumentoIdentificacion, C.vrServicio\n" +
                    ", C.tipoPagoModerador, C.valorPagoModerador, C.numFEVPagoModerador, C.consecutivo \n" +
                    "FROM dbo.Rips_Consulta C\n" +
                    "INNER JOIN dbo.Rips_Usuarios U ON U.IdRips_Usuario=C.IdRips_Usuario\n" +
                    "INNER JOIN dbo.Rips_Transaccion T ON T.IdRips=U.IdRips \n" +
                    "WHERE T.IdMovDoc = ?";

            try (PreparedStatement consultasStmt = conn.prepareStatement(consultasQuery)) {
                consultasStmt.setInt(1, idMovDoc);
                try (ResultSet consultasRs = consultasStmt.executeQuery()) {

                    while (consultasRs.next()) {
                        String numAutorizacion = consultasRs.getString("numAutorizacion");
                        String numFevPagoModerador = consultasRs.getString("numFEVPagoModerador");


                        sbConsultas.append(String.format("%s,%s,%s,%s,%s,%s,%d,%s,%s,%s,%s,%s,%s,%s,%s,%s,%d,%s,%d,%s,%d\n",
                                consultasRs.getString("codPrestador"),
                                consultasRs.getTimestamp("fechaInicioAtencion") != null ? new SimpleDateFormat("yyyy-MM-dd HH:mm").format(consultasRs.getTimestamp("fechaInicioAtencion")) : null,
                                numAutorizacion != null ? numAutorizacion : null, 
                                consultasRs.getString("codConsulta"),
                                consultasRs.getString("modalidadGrupoServicioTecSal"),
                                consultasRs.getString("grupoServicios"),
                                consultasRs.getInt("codServicio"),
                                consultasRs.getString("finalidadTecnologiaSalud"),
                                consultasRs.getString("causaMotivoAtencion"),
                                consultasRs.getString("codDiagnosticoPrincipal"),
                                consultasRs.getString("codDiagnosticoRelacionado1"),
                                consultasRs.getString("codDiagnosticoRelacionado2"),
                                consultasRs.getString("codDiagnosticoRelacionado3"),
                                consultasRs.getString("tipoDiagnosticoPrincipal"),
                                consultasRs.getString("tipoDocumentoIdentificacion"),
                                consultasRs.getString("numDocumentoIdentificacion"),
                                consultasRs.getInt("vrServicio"),
                                consultasRs.getString("tipoPagoModerador"),
                                consultasRs.getInt("valorPagoModerador"),
                                numFevPagoModerador != null ? numFevPagoModerador : null,
                                consultasRs.getInt("consecutivo")));
                    }
                }
            }

            if (sbConsultas.length() > 0) {
                txtFiles.put("consultas.txt", sbConsultas.toString().getBytes(StandardCharsets.UTF_8));
            }

            StringBuilder sbProcedimientos = new StringBuilder();
            String procedimientosQuery = "SELECT P.codPrestador, P.fechaInicioAtencion, P.idMIPRES, P.numAutorizacion, P.codProcedimiento, P.viaIngresoServicioSalud, P.modalidadGrupoServicioTecSal, P.grupoServicios, P.codServicio, P.finalidadTecnologiaSalud, P.tipoDocumentoIdentificacion, P.numDocumentoIdentificacion, P.codDiagnosticoPrincipal, P.codDiagnosticoRelacionado, P.codComplicacion, P.vrServicio, P.tipoPagoModerador, P.valorPagoModerador, P.numFEVPagoModerador, P.consecutivo FROM dbo.Rips_Procedimientos P INNER JOIN dbo.Rips_Usuarios U ON U.IdRips_Usuario=P.IdRips_Usuario INNER JOIN dbo.Rips_Transaccion T ON T.IdRips=U.IdRips WHERE T.IdMovDoc = ?";

            try (PreparedStatement procedimientosStmt = conn.prepareStatement(procedimientosQuery)) {
                procedimientosStmt.setInt(1, idMovDoc);
                try (ResultSet procedimientosRs = procedimientosStmt.executeQuery()) {
                    while (procedimientosRs.next()) {

                        String numAutorizacion = procedimientosRs.getString("numAutorizacion");
                        String idMIPRES = procedimientosRs.getString("idMIPRES");
                        String numFevPagoModerador = procedimientosRs.getString("numFEVPagoModerador");
        
                        sbProcedimientos.append(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%d,%s,%s,%s,%s,%s,%s,%d,%s,%d,%s,%d\n",
                                procedimientosRs.getString("codPrestador"),
                                procedimientosRs.getTimestamp("fechaInicioAtencion") != null ? new SimpleDateFormat("yyyy-MM-dd HH:mm").format(procedimientosRs.getTimestamp("fechaInicioAtencion")) : null,
                                idMIPRES != null ? idMIPRES : null,
                                numAutorizacion != null ? numAutorizacion : null, 
                                procedimientosRs.getString("codProcedimiento"),
                                procedimientosRs.getString("viaIngresoServicioSalud"),
                                procedimientosRs.getString("modalidadGrupoServicioTecSal"),
                                procedimientosRs.getString("grupoServicios"),
                                procedimientosRs.getInt("codServicio"),
                                procedimientosRs.getString("finalidadTecnologiaSalud"),
                                procedimientosRs.getString("tipoDocumentoIdentificacion"),
                                procedimientosRs.getString("numDocumentoIdentificacion"),
                                procedimientosRs.getString("codDiagnosticoPrincipal"),
                                procedimientosRs.getString("codDiagnosticoRelacionado"),
                                procedimientosRs.getString("codComplicacion"),
                                procedimientosRs.getInt("vrServicio"),
                                procedimientosRs.getString("tipoPagoModerador"),
                                procedimientosRs.getInt("valorPagoModerador"),
                                numFevPagoModerador != null ? numFevPagoModerador : null,
                                procedimientosRs.getInt("consecutivo")));
                    }
                }
            }

            if (sbProcedimientos.length() > 0) {
                txtFiles.put("procedimientos.txt", sbProcedimientos.toString().getBytes(StandardCharsets.UTF_8));
            }

            StringBuilder sbUrgencias = new StringBuilder();
            String urgenciasQuery = "SELECT UR.codPrestador, UR.fechaInicioAtencion, UR.causaMotivoAtencion, UR.codDiagnosticoPrincipal, UR.codDiagnosticoPrincipalE, UR.codDiagnosticoRelacionadoE1, UR.codDiagnosticoRelacionadoE2, UR.codDiagnosticoRelacionadoE3, UR.condicionDestinoUsuarioEgreso, UR.codDiagnosticoCausaMuerte, UR.fechaEgreso, UR.consecutivo FROM dbo.Rips_Urg UR INNER JOIN dbo.Rips_Usuarios U ON U.IdRips_Usuario=UR.IdRips_Usuario INNER JOIN dbo.Rips_Transaccion T ON T.IdRips=U.IdRips WHERE T.IdMovDoc = ?";

            try (PreparedStatement urgenciasStmt = conn.prepareStatement(urgenciasQuery)) {
                urgenciasStmt.setInt(1, idMovDoc);
                try (ResultSet urgenciasRs = urgenciasStmt.executeQuery()) {
                    while (urgenciasRs.next()) {
                        sbUrgencias.append(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%d\n",
                                urgenciasRs.getString("codPrestador"),
                                urgenciasRs.getTimestamp("fechaInicioAtencion") != null ? new SimpleDateFormat("yyyy-MM-dd HH:mm").format(urgenciasRs.getTimestamp("fechaInicioAtencion")) : null,
                                urgenciasRs.getString("causaMotivoAtencion"),
                                urgenciasRs.getString("codDiagnosticoPrincipal"),
                                urgenciasRs.getString("codDiagnosticoPrincipalE"),
                                urgenciasRs.getString("codDiagnosticoRelacionadoE1"),
                                urgenciasRs.getString("codDiagnosticoRelacionadoE2"),
                                urgenciasRs.getString("codDiagnosticoRelacionadoE3"),
                                urgenciasRs.getString("condicionDestinoUsuarioEgreso"),
                                urgenciasRs.getString("codDiagnosticoCausaMuerte"),
                                urgenciasRs.getTimestamp("fechaEgreso") != null ? new SimpleDateFormat("yyyy-MM-dd HH:mm").format(urgenciasRs.getTimestamp("fechaEgreso")) : null,
                                urgenciasRs.getInt("consecutivo")));
                    }
                }
            }

            if (sbUrgencias.length() > 0) {
                txtFiles.put("urgencias.txt", sbUrgencias.toString().getBytes(StandardCharsets.UTF_8));
            }

            StringBuilder sbHospitalizaciones = new StringBuilder();
            String hospitalizacionesQuery = "SELECT H.codPrestador, H.viaingresoServicioSalud, H.fechaInicioAtencion, H.numAutorizacion,\n" +
                    "H.causaMotivoAtencion, H.codDiagnosticoPrincipal, H.codDiagnosticoPrincipalE,\n" +
                    "H.codDiagnosticoRelacionadoE1, H.codDiagnosticoRelacionadoE2, \n" +
                    "H.codDiagnosticoRelacionadoE3, H.codComplicacion, H.condicionDestinoUsuarioEgreso, \n" +
                    "H.codDiagnosticoCausaMuerte, H.fechaEgreso, H.consecutivo FROM dbo.Rips_Hospitalizacion H\n" +
                    "INNER JOIN dbo.Rips_Usuarios U ON U.IdRips_Usuario=H.IdFRips_Usuario\n" +
                    "INNER JOIN dbo.Rips_Transaccion T ON T.IdRips=U.IdRips \n" +
                    "WHERE T.IdMovDoc = ?";

            try (PreparedStatement hospitalizacionesStmt = conn.prepareStatement(hospitalizacionesQuery)) {
                hospitalizacionesStmt.setInt(1, idMovDoc);
                try (ResultSet hospitalizacionesRs = hospitalizacionesStmt.executeQuery()) {
                    while (hospitalizacionesRs.next()) {
                        String numAutorizacion = hospitalizacionesRs.getString("numAutorizacion");

                        sbHospitalizaciones.append(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%d\n",
                                hospitalizacionesRs.getString("codPrestador"),
                                hospitalizacionesRs.getString("viaingresoServicioSalud"),
                                hospitalizacionesRs.getTimestamp("fechaInicioAtencion") != null ? new SimpleDateFormat("yyyy-MM-dd HH:mm").format(hospitalizacionesRs.getTimestamp("fechaInicioAtencion")) : null,
                                numAutorizacion != null ? numAutorizacion : null,
                                hospitalizacionesRs.getString("causaMotivoAtencion"),
                                hospitalizacionesRs.getString("codDiagnosticoPrincipal"),
                                hospitalizacionesRs.getString("codDiagnosticoPrincipalE"),
                                hospitalizacionesRs.getString("codDiagnosticoRelacionadoE1"),
                                hospitalizacionesRs.getString("codDiagnosticoRelacionadoE2"),
                                hospitalizacionesRs.getString("codDiagnosticoRelacionadoE3"),
                                hospitalizacionesRs.getString("codComplicacion"),
                                hospitalizacionesRs.getString("condicionDestinoUsuarioEgreso"),
                                hospitalizacionesRs.getString("codDiagnosticoCausaMuerte"),
                                hospitalizacionesRs.getTimestamp("fechaEgreso") != null ? new SimpleDateFormat("yyyy-MM-dd HH:mm").format(hospitalizacionesRs.getTimestamp("fechaEgreso")) : null,
                                hospitalizacionesRs.getInt("consecutivo")));
                    }
                }
            }

            if (sbHospitalizaciones.length() > 0) {
                txtFiles.put("hospitalizacion.txt", sbHospitalizaciones.toString().getBytes(StandardCharsets.UTF_8));
            }

            StringBuilder sbRecienNacidos = new StringBuilder();
            String recienNacidosQuery = "SELECT RN.codPrestador, RN.tipoDocumentoIdentificacion, RN.numDocumentoIdentificacion, RN.fechaNacimiento, \n" +
                    "RN.edadGestacional, RN.numConsultasCPrenatal, RN.codSexoBiologico, RN.peso, RN.codDiagnosticoPrincipal, \n" +
                    "RN.condicionDestinoUsuarioEgreso, RN.codDiagnosticoCausaMuerte, RN.fechaEgreso, RN.consecutivo FROM dbo.Rips_RecienNacidos RN\n" +
                    "INNER JOIN dbo.Rips_Usuarios U ON U.IdRips_Usuario=RN.IdRips_Usuario\n" +
                    "INNER JOIN dbo.Rips_Transaccion T ON T.IdRips=U.IdRips \n" +
                    "WHERE T.IdMovDoc = ?";

            try (PreparedStatement recienNacidosStmt = conn.prepareStatement(recienNacidosQuery)) {
                recienNacidosStmt.setInt(1, idMovDoc);
                try (ResultSet recienNacidosRs = recienNacidosStmt.executeQuery()) {
                    while (recienNacidosRs.next()) {
                        sbRecienNacidos.append(String.format("%s,%s,%s,%s,%d,%d,%s,%s,%s,%s,%s,%s,%d\n",
                                recienNacidosRs.getString("codPrestador"),
                                recienNacidosRs.getString("tipoDocumentoIdentificacion"),
                                recienNacidosRs.getString("numDocumentoIdentificacion"),
                                recienNacidosRs.getTimestamp("fechaNacimiento") != null ? new SimpleDateFormat("yyyy-MM-dd HH:mm").format(recienNacidosRs.getTimestamp("fechaNacimiento")) : null,
                                recienNacidosRs.getInt("edadGestacional"),
                                recienNacidosRs.getInt("numConsultasCPrenatal"),
                                recienNacidosRs.getString("codSexoBiologico"),
                                recienNacidosRs.getBigDecimal("peso"),
                                recienNacidosRs.getString("codDiagnosticoPrincipal"),
                                recienNacidosRs.getString("condicionDestinoUsuarioEgreso"),
                                recienNacidosRs.getString("codDiagnosticoCausaMuerte"),
                                recienNacidosRs.getTimestamp("fechaEgreso") != null ? new SimpleDateFormat("yyyy-MM-dd HH:mm").format(recienNacidosRs.getTimestamp("fechaEgreso")) : null,
                                recienNacidosRs.getInt("consecutivo")));
                    }
                }
            }

            if (sbRecienNacidos.length() > 0) {
                txtFiles.put("recienNacidos.txt", sbRecienNacidos.toString().getBytes(StandardCharsets.UTF_8));
            }


            StringBuilder sbMedicamentos = new StringBuilder();
            String medicamentosQuery = "SELECT M.codPrestador, M.numAutorizacion, M.idMIPRES, M.fechaDispensAdmon, M.codDiagnosticoPrincipal, \n" +
                    "M.codDiagnosticoRelacionado, M.tipoMedicamento, M.codTecnologiaSalud, M.nomTecnologiaSalud, \n" +
                    "M.concentracionMedicamento, M.unidadMedida, M.formaFarmaceutica, M.unidadMinDispensa, \n" +
                    "M.cantidadMedicamento, M.diasTratamiento, M.tipoDocumentoIdentificacion, M.numDocumentoidentificacion, \n" +
                    "M.vrUnitMedicamento, M.vrServicio, M.tipoPagoModerador, M.valorPagoModerador, M.numFEVPagoModerador, \n" +
                    "M.consecutivo FROM dbo.Rips_Medicamentos M\n" +
                    "INNER JOIN dbo.Rips_Usuarios U ON U.IdRips_Usuario=M.IdRips_Usuario\n" +
                    "INNER JOIN dbo.Rips_Transaccion T ON T.IdRips=U.IdRips \n" +
                    "WHERE T.IdMovDoc = ?";

            try (PreparedStatement medicamentosStmt = conn.prepareStatement(medicamentosQuery)) {
                medicamentosStmt.setInt(1, idMovDoc);
                try (ResultSet medicamentosRs = medicamentosStmt.executeQuery()) {
                    while (medicamentosRs.next()) {
                        String numAutorizacion = medicamentosRs.getString("numAutorizacion");
                        String idMIPRES = medicamentosRs.getString("idMIPRES");
                        String numFevPagoModerador = medicamentosRs.getString("numFEVPagoModerador");

                        sbMedicamentos.append(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%d,%d,%s,%d,%s,%d,%s,%s,%s,%s,%s,%d,%s,%d\n",
                                medicamentosRs.getString("codPrestador"),
                                numAutorizacion != null ? numAutorizacion : null,
                                idMIPRES != null ? idMIPRES : null,
                                medicamentosRs.getTimestamp("fechaDispensAdmon") != null ? new SimpleDateFormat("yyyy-MM-dd HH:mm").format(medicamentosRs.getTimestamp("fechaDispensAdmon")) : null,
                                medicamentosRs.getString("codDiagnosticoPrincipal"),
                                medicamentosRs.getString("codDiagnosticoRelacionado"),
                                medicamentosRs.getString("tipoMedicamento"),
                                medicamentosRs.getString("codTecnologiaSalud"),
                                medicamentosRs.getString("nomTecnologiaSalud"),
                                medicamentosRs.getInt("concentracionMedicamento"),
                                medicamentosRs.getInt("unidadMedida"),
                                medicamentosRs.getString("formaFarmaceutica"),
                                medicamentosRs.getInt("unidadMinDispensa"),
                                medicamentosRs.getBigDecimal("cantidadMedicamento"),
                                medicamentosRs.getInt("diasTratamiento"),
                                medicamentosRs.getString("tipoDocumentoIdentificacion"),
                                medicamentosRs.getString("numDocumentoidentificacion"),
                                medicamentosRs.getBigDecimal("vrUnitMedicamento"),
                                medicamentosRs.getBigDecimal("vrServicio"),
                                medicamentosRs.getString("tipoPagoModerador"),
                                medicamentosRs.getInt("valorPagoModerador"),
                                numFevPagoModerador != null ? numFevPagoModerador : null,
                                medicamentosRs.getInt("consecutivo")));
                    }
                }
            }

            if (sbMedicamentos.length() > 0) {
                txtFiles.put("medicamentos.txt", sbMedicamentos.toString().getBytes(StandardCharsets.UTF_8));
            }

            StringBuilder sbOtrosServicios = new StringBuilder();
            String otroServiciosQuery = "SELECT OS.codPrestador, OS.numAutorizacion, OS.idMIPRES, OS.fechaSuministroTecnologia, OS.tipoOS, \n" +
                    "OS.codTecnologiaSalud, OS.nomTecnologiaSalud, OS.cantidadOS, OS.tipoDocumentoIdentificacion, \n" +
                    "OS.numDocumentoIdentificacion, OS.vrUnitOS, OS.vrServicio, OS.conceptoRecaudo, OS.valorPagoModerador, \n" +
                    "OS.numFEVPagoModerador, OS.consecutivo FROM dbo.Rips_OtrosServicios OS\n" +
                    "INNER JOIN dbo.Rips_Usuarios U ON U.IdRips_Usuario=OS.IdRips_Usuario\n" +
                    "INNER JOIN dbo.Rips_Transaccion T ON T.IdRips=U.IdRips \n" +
                    "WHERE T.IdMovDoc = ?";

            try(PreparedStatement otroServiciosStmt = conn.prepareStatement(otroServiciosQuery)){
                otroServiciosStmt.setInt(1, idMovDoc);
                try(ResultSet otroServiciosRs = otroServiciosStmt.executeQuery()){
                    while (otroServiciosRs.next()) {
                        String numAutorizacion = otroServiciosRs.getString("numAutorizacion");
                        String idMIPRES = otroServiciosRs.getString("idMIPRES");
                        String numFevPagoModerador = otroServiciosRs.getString("numFEVPagoModerador");

                        sbOtrosServicios.append(String.format("%s,%s,%s,%s,%s,%s,%s,%d,%s,%s,%s,%s,%s,%d,%s,%d\n",
                                otroServiciosRs.getString("codPrestador"),
                                numAutorizacion != null ? numAutorizacion : null,
                                idMIPRES != null ? idMIPRES : null,
                                otroServiciosRs.getTimestamp("fechaSuministroTecnologia") != null ? new SimpleDateFormat("yyyy-MM-dd HH:mm").format(otroServiciosRs.getTimestamp("fechaSuministroTecnologia")) : null,
                                otroServiciosRs.getString("tipoOS"),
                                otroServiciosRs.getString("codTecnologiaSalud"),
                                otroServiciosRs.getString("nomTecnologiaSalud"),
                                otroServiciosRs.getInt("cantidadOS"),
                                otroServiciosRs.getString("tipoDocumentoIdentificacion"),
                                otroServiciosRs.getString("numDocumentoIdentificacion"),
                                otroServiciosRs.getBigDecimal("vrUnitOS"), 
                                otroServiciosRs.getBigDecimal("vrServicio"),
                                otroServiciosRs.getString("conceptoRecaudo"),
                                otroServiciosRs.getInt("valorPagoModerador"),
                                numFevPagoModerador != null ? numFevPagoModerador : null,
                                otroServiciosRs.getInt("consecutivo")));
                    }
                }          
            }

            if (sbOtrosServicios.length() > 0) {
                txtFiles.put("otros_servicios.txt", sbOtrosServicios.toString().getBytes(StandardCharsets.UTF_8));
            }
            
            if (txtFiles.isEmpty()) {
                throw new RuntimeException("No se encontraron datos para el ID propocionado");
            }
            
            return txtFiles;
    
        }
    }

    // Metodo para generar Zip de Facturas y XML
    public ZipResult generarzip(
        int idMovDoc, 
        String tipoArchivo, 
        boolean incluirXml) throws IOException, SQLException { 

        if (!tipoArchivo.equalsIgnoreCase("json") &&
            !tipoArchivo.equalsIgnoreCase("txt") &&
            !tipoArchivo.equalsIgnoreCase("ambos")) {
            throw new IllegalArgumentException("Tipo de archivo no válido. Debe ser 'json', 'txt' o 'ambos'");
        }

        String prefijo = "";
        String numdoc = "";
        String IdEmpresaGrupo = "";

        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoftFinanciero_ST"));
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ZipOutputStream zos = new ZipOutputStream(baos)) {

            // Obtener prefijo y numdoc
            String docQuery = "SELECT Prefijo, Numdoc FROM MovimientoDocumentos WHERE IdMovDoc = ?";
            try (PreparedStatement stmt = conn.prepareStatement(docQuery)) {
                stmt.setInt(1, idMovDoc);
                try (ResultSet rs = stmt.executeQuery()) {  
                    if (rs.next()) {
                        prefijo = rs.getString("Prefijo");
                        numdoc = rs.getString("Numdoc");
                    } else {
                        throw new IllegalArgumentException("No se encontró documento con IdMovDoc: " + idMovDoc);
                    }
                }
            }

            // Obtener IdEmpresaGrupo
            String empresaQuery = "SELECT IdEmpresaGrupo FROM MovimientoDocumentos as M INNER JOIN Empresas as E ON e.IdEmpresaKey =m.IdEmpresaKey  WHERE IdMovDoc = ?";
            try (PreparedStatement stmt = conn.prepareStatement(empresaQuery)) {
                stmt.setInt(1, idMovDoc);
                try (ResultSet rs = stmt.executeQuery()) {  
                    if (rs.next()) {
                        IdEmpresaGrupo = rs.getString("IdEmpresaGrupo");
                    }
                }
            }

            String yearSuffix = String.valueOf(LocalDate.now().getYear()).substring(2);
            String formattedNumdoc = String.format("%08d", Integer.parseInt(numdoc));
            String folderName = "Fac_" + prefijo + numdoc + "/";


            if (incluirXml) {
                byte[] xmlBytes = exportDocXml(idMovDoc); 
                ZipEntry xmlEntry = new ZipEntry(folderName + "ad0" + IdEmpresaGrupo + "000" + yearSuffix + formattedNumdoc + ".xml");
                zos.putNextEntry(xmlEntry);
                zos.write(xmlBytes); 
                zos.closeEntry();
            } 
            

            boolean jsonRequired = "json".equals(tipoArchivo) || "ambos".equals(tipoArchivo);
            if (jsonRequired) {
                byte[] jsonResponse = generarjson(idMovDoc);
                if (jsonResponse != null && jsonResponse.length > 0) {
                    ZipEntry jsonEntry = new ZipEntry(folderName + "RipsFac_" + prefijo + numdoc + ".json");
                    zos.putNextEntry(jsonEntry);
                    zos.write(jsonResponse);
                    zos.closeEntry();
                }
            }

            boolean txtRequired = "txt".equals(tipoArchivo) || "ambos".equals(tipoArchivo);
            if (txtRequired) {
                Map<String, byte[]> txtFiles = generarTxt(idMovDoc);

                if (txtFiles != null && !txtFiles.isEmpty()) {
                    for (Map.Entry<String, byte[]> entry : txtFiles.entrySet()) {
                        ZipEntry txtEntry = new ZipEntry(folderName + entry.getKey());
                        zos.putNextEntry(txtEntry);
                        zos.write(entry.getValue());
                        zos.closeEntry();
                    }
                }
            }

            String respuestaValidador = null;
            try (Connection connRips = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoft100_ST"))) {
                String respuestaSQL = "SELECT MensajeRespuesta From RIPS_RespuestaApi WHERE Nfact = ?";
                try (PreparedStatement stmt = connRips.prepareStatement(respuestaSQL)) {
                    stmt.setString(1, prefijo + numdoc);
                    try (ResultSet rs = stmt.executeQuery()) {  
                        if (rs.next()) {
                            respuestaValidador = rs.getString("MensajeRespuesta");
                        }
                    }
                }
            } catch (SQLException e) {
                System.err.println("Error al consultar rips_RespuestaAPI: " + e.getMessage());
            }

            if (respuestaValidador != null && !respuestaValidador.isBlank()) {
                try {
                    String procesoId = "";
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode jsonNode = mapper.readTree(respuestaValidador);

                    if (jsonNode.has("ProcesoId")) {
                        procesoId = jsonNode.get("ProcesoId").asText();
                    }

                    String nombreTxt = folderName + "ResultadosMSPS_" + prefijo + numdoc + "_ID" + procesoId + "_A_CUV.txt";
                    ZipEntry zipEntry = new ZipEntry(nombreTxt);
                    zos.putNextEntry(zipEntry);
                    zos.write(respuestaValidador.getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();
                } catch (Exception e) {
                    System.err.println("Error al procesar respuesta del validador: " + e.getMessage());
                }
            }

            zos.finish();
            zos.flush();

            byte[] zipBytes = baos.toByteArray();
            String fileName = "Fac_" + prefijo + numdoc + ".zip"; 
            return new ZipResult(zipBytes, fileName);
        }
    }


    //METODO PARA GENERAR ADMISION
    public List<Map<String, Object>> generarAdmision(int idMovDoc, int idDoc) throws SQLException {

        String connectionUrl = databaseConfig.getConnectionUrl("IPSoft100_ST");

        try (Connection conn = DriverManager.getConnection(connectionUrl)) {
            String sql = "EXEC dbo.pa_Net_Facturas_GenSoportes ?, ?";

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, idMovDoc);
                pstmt.setInt(2, idDoc);
                boolean hasResults = pstmt.execute();

                while (!hasResults && pstmt.getUpdateCount() != -1) {
                    hasResults = pstmt.getMoreResults();
                }

                if (hasResults) {
                    try (ResultSet rs = pstmt.getResultSet()) {
                        List<Map<String, Object>> resultados = new ArrayList<>();
                        ResultSetMetaData metaData = rs.getMetaData();
                        int columnCount = metaData.getColumnCount();

                        while (rs.next()) {
                            Map<String, Object> fila = new LinkedHashMap<>();
                            for (int i = 1; i <= columnCount; i++) {
                                fila.put(metaData.getColumnName(i), rs.getObject(i));
                            }
                            resultados.add(fila);
                        }

                        return resultados;
                    }
                } else {
                    throw new RuntimeException("El procedimiento no devolvió resultados.");
                }
            }
        }
    }
}

