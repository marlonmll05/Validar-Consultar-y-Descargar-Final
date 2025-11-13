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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.certificadosapi.certificados.config.DatabaseConfig;

@Service
public class AnexarService {

    private DatabaseConfig databaseConfig;

    @Autowired
    public AnexarService(DatabaseConfig databaseConfig){
        this.databaseConfig = databaseConfig;

    }

    //ENDPOINT PARA VER TODAS LAS TIPIFICACIONES DE ANEXOS
    public List<Map<String, Object>> obtenerDocumentosSoporteSinFiltros() throws SQLException {

        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoft100_ST"))) {
            String sql = "EXEC pa_Net_Facturas_Tablas ?, ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, 8);  
                stmt.setInt(2, -1);  

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
                    return resultados;
                }
            }
        }
    }

    //ENDPOINT PARA VER LOS SOPORTES DE ANEXOS
    public List<Map<String, Object>> obtenerDocumentosSoporte() throws SQLException {

        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoft100_ST"))) {
            String sql = "EXEC pa_Net_Facturas_Tablas ?, ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, 9);  
                stmt.setInt(2, -1);  

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
                    return resultados;
                }
            }
        } 
    }

    //ENDPOINT PARA INSERTAR PDFS
     public Long insertarListaPdf(
            Long idAdmision,
            Long idPacienteKey,
            Long idSoporteKey,
            String tipoDocumento,
            List<MultipartFile> files,
            boolean eliminarSiNo,
            boolean automatico
    ) throws SQLException, IOException {

        // Validar que todos los archivos sean PDF
        for (MultipartFile f : files) {
            String ct = f.getContentType();
            if (ct == null || !ct.equalsIgnoreCase("application/pdf")) {
                throw new IllegalArgumentException("Todos los archivos deben ser PDF.");
            }
        }

        byte[] pdfFinal;

        // Unificar PDFs
        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("Asclepius_Documentos"))) {
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
                    
                    try (ResultSet rsSel = psSel.executeQuery()) {
                        if (rsSel.next()) {
                            byte[] pdfExistente = rsSel.getBytes("NameFilePdf");
                            if (pdfExistente != null && pdfExistente.length > 0) {
                                merger.addSource(new ByteArrayInputStream(pdfExistente));
                            }
                        }
                    }
                }

                eliminarSiNo = true;
            }

            for (MultipartFile f : files) {
                merger.addSource(f.getInputStream());
            }

            merger.mergeDocuments(null);
            pdfFinal = baos.toByteArray();
        }

        // Insertar el PDF unificado
        try (Connection connIPSoft = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoft100_ST"))) {
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

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        long idGenerado = rs.getLong("IdpdfKey");
                        return idGenerado;
                    }
                    throw new SQLException("No se pudo obtener el ID del PDF insertado");
                }
            }
        }
    }
}
