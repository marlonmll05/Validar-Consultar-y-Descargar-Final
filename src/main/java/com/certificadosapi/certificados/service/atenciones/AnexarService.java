package com.certificadosapi.certificados.service.atenciones;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.certificadosapi.certificados.config.DatabaseConfig;

@Service
public class AnexarService {

    private static final Logger log = LoggerFactory.getLogger(AnexarService.class);

    private final DatabaseConfig databaseConfig;

    @Autowired
    public AnexarService(DatabaseConfig databaseConfig){
        this.databaseConfig = databaseConfig;
    }

    // ENDPOINT PARA VER TODAS LAS TIPIFICACIONES DE ANEXOS
    public List<Map<String, Object>> obtenerDocumentosSoporteSinFiltros() throws SQLException {

        log.info("Iniciando obtenerDocumentosSoporteSinFiltros()");

        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoft100_ST"))) {
            String sql = "EXEC pa_Net_Facturas_Tablas ?, ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, 8);
                stmt.setInt(2, -1);
                log.debug("Ejecutando pa_Net_Facturas_Tablas 8, -1 para obtenerDocumentosSoporteSinFiltros");

                try (ResultSet rs = stmt.executeQuery()) {
                    List<Map<String, Object>> resultados = new ArrayList<>();
                    while (rs.next()) {
                        Map<String, Object> fila = new LinkedHashMap<>();
                        fila.put("Id", rs.getInt("Id"));
                        fila.put("nombreDocSoporte", rs.getString("NombreDocSoporte"));
                        fila.put("nombreRptService", rs.getString("NombreRptService"));
                        fila.put("TipoDocumento", rs.getInt("TipoDocumento"));
                        resultados.add(fila);
                    }
                    
                    log.info("obtenerDocumentosSoporteSinFiltros() devolvió {} registros", resultados.size());
                    return resultados;
                }
            }
        }
    }

    // ENDPOINT PARA VER LOS SOPORTES DE ANEXOS
    public List<Map<String, Object>> obtenerDocumentosSoporte() throws SQLException {

        log.info("Iniciando obtenerDocumentosSoporte()");

        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoft100_ST"))) {
            String sql = "EXEC pa_Net_Facturas_Tablas ?, ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, 9);
                stmt.setInt(2, -1);
                log.debug("Ejecutando pa_Net_Facturas_Tablas 9, -1 para obtenerDocumentosSoporte");

                try (ResultSet rs = stmt.executeQuery()) {
                    List<Map<String, Object>> resultados = new ArrayList<>();
                    while (rs.next()) {
                        Map<String, Object> fila = new LinkedHashMap<>();
                        fila.put("Id", rs.getInt("Id"));
                        fila.put("nombreDocSoporte", rs.getString("NombreDocSoporte"));
                        fila.put("nombreRptService", rs.getString("NombreRptService"));
                        fila.put("TipoDocumento", rs.getInt("TipoDocumento"));
                        resultados.add(fila);
                    }
                    
                    log.info("obtenerDocumentosSoporte() devolvió {} registros", resultados.size());
                    return resultados;
                }
            }
        }
    }

    // ENDPOINT PARA INSERTAR PDFS
    public Long insertarListaPdf(
            Long idAdmision,
            Long idPacienteKey,
            Long idSoporteKey,
            String tipoDocumento,
            List<MultipartFile> files,
            boolean eliminarSiNo,
            boolean automatico
    ) throws SQLException, IOException {

        log.info("Iniciando insertarListaPdf(idAdmision={}, idPacienteKey={}, idSoporteKey={}, tipoDocumento={}, filesCount={}, eliminarSiNo={}, automatico={})",
                idAdmision, idPacienteKey, idSoporteKey, tipoDocumento,
                (files != null ? files.size() : 0), eliminarSiNo, automatico);

        if (files == null || files.isEmpty()) {
            log.warn("insertarListaPdf llamado sin archivos para anexar. idAdmision={}, idSoporteKey={}", idAdmision, idSoporteKey);
            throw new IllegalArgumentException("Debe enviar al menos un archivo PDF.");
        }

        // Validar que todos los archivos sean PDF
        for (MultipartFile f : files) {
            if (f == null) {
                log.warn("Archivo nulo recibido en lista de files para idAdmision={}, idSoporteKey={}", idAdmision, idSoporteKey);
                throw new IllegalArgumentException("Se encontró un archivo nulo en la lista.");
            }
            String ct = f.getContentType();
            if (ct == null || !ct.equalsIgnoreCase("application/pdf")) {
                log.error("Archivo con contentType no válido={} para idAdmision={}, idSoporteKey={}. Solo se permiten PDF.",
                        ct, idAdmision, idSoporteKey);
                throw new IllegalArgumentException("Todos los archivos deben ser PDF.");
            }
        }

        byte[] pdfFinal;

        // Unificar PDFs
        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("Asclepius_Documentos"))) {
            log.debug("Conexión abierta a Asclepius_Documentos para merge de PDFs. idAdmision={}, idSoporteKey={}",
                      idAdmision, idSoporteKey);

            PDFMergerUtility merger = new PDFMergerUtility();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            merger.setDestinationStream(baos);

            if (!eliminarSiNo) {
                String sqlSelect = """
                    SELECT TOP 1 NameFilePdf 
                    FROM tbl_Net_Facturas_ListaPdf 
                    WHERE IdAdmision = ? AND IdSoporteKey = ? 
                    ORDER BY IdpdfKey DESC
                """;

                try (PreparedStatement psSel = conn.prepareStatement(sqlSelect)) {
                    psSel.setLong(1, idAdmision);
                    psSel.setLong(2, idSoporteKey);

                    log.debug("Buscando último PDF existente para concatenar. idAdmision={}, idSoporteKey={}",
                              idAdmision, idSoporteKey);

                    try (ResultSet rsSel = psSel.executeQuery()) {
                        if (rsSel.next()) {
                            byte[] pdfExistente = rsSel.getBytes("NameFilePdf");
                            if (pdfExistente != null && pdfExistente.length > 0) {
                                log.debug("Se encontró PDF existente para concatenar. Tamaño={} bytes", pdfExistente.length);
                                merger.addSource(new ByteArrayInputStream(pdfExistente));
                            } else {
                                log.debug("Se encontró registro pero NameFilePdf vacío o nulo.");
                            }
                        } else {
                            log.debug("No se encontró PDF previo para esta admisión/soporte. Solo se usarán archivos nuevos.");
                        }
                    }
                }

                eliminarSiNo = true;
            }

            for (MultipartFile f : files) {
                log.debug("Añadiendo PDF al merge. Nombre={}, tamaño={} bytes", f.getOriginalFilename(), f.getSize());
                merger.addSource(f.getInputStream());
            }

            log.debug("Iniciando merge de documentos PDF.");
            merger.mergeDocuments(null);
            pdfFinal = baos.toByteArray();
            log.info("Merge de PDFs completado. Tamaño final del PDF unificado={} bytes", pdfFinal.length);
        }

        // Insertar el PDF unificado en IPSoft100_ST
        try (Connection connIPSoft = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoft100_ST"))) {
            log.debug("Conexión abierta a IPSoft100_ST para insertar PDF unificado. idAdmision={}, idSoporteKey={}",
                     idAdmision, idSoporteKey);

            String sql = "EXEC dbo.pa_Net_Insertar_DocumentoPdf ?, ?, ?, ?, ?, ?, ?, ?";

            try (PreparedStatement ps = connIPSoft.prepareStatement(sql)) {
                ps.setLong(1, idAdmision);
                ps.setLong(2, idPacienteKey);
                ps.setLong(3, idSoporteKey);
                ps.setBoolean(4, false);
                ps.setString(5, tipoDocumento);
                ps.setBinaryStream(6, new ByteArrayInputStream(pdfFinal), pdfFinal.length);
                ps.setBoolean(7, eliminarSiNo);
                ps.setBoolean(8, automatico);

                log.debug("Ejecutando pa_Net_Insertar_DocumentoPdf con eliminarSiNo={}, automatico={}",
                          eliminarSiNo, automatico);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        long idGenerado = rs.getLong("IdpdfKey");
                        log.info("PDF unificado insertado correctamente en tbl_Net_Facturas_ListaPdf. IdpdfKey={}",
                                 idGenerado);
                        return idGenerado;
                    }
                    log.error("pa_Net_Insertar_DocumentoPdf no retornó IdpdfKey para idAdmision={}, idSoporteKey={}",
                              idAdmision, idSoporteKey);
                    throw new SQLException("No se pudo obtener el ID del PDF insertado");
                }
            }
        }
    }
}

