package com.certificadosapi.certificados.service.atenciones;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.certificadosapi.certificados.dto.PdfDocumento;

import com.certificadosapi.certificados.config.DatabaseConfig;

@Service
public class VerService {

    private DatabaseConfig databaseConfig;

    @Autowired
    public VerService(DatabaseConfig databaseConfig){
        this.databaseConfig = databaseConfig;
    }

    // === Helper para detectar content-type por "magic numbers" ===
    private String detectarContentType(byte[] data) {
        if (data != null && data.length >= 4) {
            // %PDF
            if ((data[0] & 0xFF) == 0x25 && data[1] == 0x50 && data[2] == 0x44 && data[3] == 0x46) {
                return "application/pdf";
            }
            // PNG
            if ((data[0] & 0xFF) == 0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47) {
                return "image/png";
            }
            // JPG
            if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8) {
                return "image/jpeg";
            }
            // GIF
            if (data[0] == 'G' && data[1] == 'I' && data[2] == 'F') {
                return "image/gif";
            }
            // WEBP (RIFF....WEBP)
            if (data.length >= 12 &&
                data[0]=='R' && data[1]=='I' && data[2]=='F' && data[3]=='F' &&
                data[8]=='W' && data[9]=='E' && data[10]=='B' && data[11]=='P') {
                return "image/webp";
            }
        }
        // Por defecto, PDF
        return "application/pdf";
    }
    
    //ENDPOINT PARA VER LA LISTA DE PDFS INSERTADOS EN LA TABLA
    public List<Map<String, Object>> listaPdfs(Long idAdmision) throws SQLException {
    
        String sql = """
            SELECT l.IdSoporteKey,
                dbo.fn_Net_DocSoporte_NameFile(?, l.IdSoporteKey) AS Nombre
            FROM tbl_Net_Facturas_ListaPdf l
            WHERE l.IdAdmision = ?
            ORDER BY l.IdSoporteKey
        """;

        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("Asclepius_Documentos"));
            PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idAdmision);
            ps.setLong(2, idAdmision);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String,Object>> out = new ArrayList<>();
                while (rs.next()) {
                    Map<String,Object> fila = new LinkedHashMap<>();
                    fila.put("idSoporteKey", rs.getLong("IdSoporteKey"));
                    fila.put("nombre", rs.getString("Nombre"));
                    out.add(fila);
                }
                    if (out.isEmpty()) {
                        throw new IllegalArgumentException("No hay documentos para la admisión " + idAdmision);
                
                    }
                return out;
            }
        }
    }
    
    

    //ENDPOINT PARA VER EL CONTENIDO DE UN PDF
    public PdfDocumento obtenerPdf(Long idAdmision, Long idSoporteKey) throws SQLException {
        String sql = """
            SELECT NameFilePdf,
                dbo.fn_Net_DocSoporte_NameFile(?, ?) AS Nombre
            FROM tbl_Net_Facturas_ListaPdf
            WHERE IdAdmision = ? AND IdSoporteKey = ?
        """;

        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("Asclepius_Documentos"));
            PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idAdmision);
            ps.setLong(2, idSoporteKey);
            ps.setLong(3, idAdmision);
            ps.setLong(4, idSoporteKey);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new NoSuchElementException("Documento no encontrado.");
                }

                byte[] data = rs.getBytes("NameFilePdf");
                if (data == null || data.length == 0) {
                    throw new NoSuchElementException("Contenido vacío del documento.");
                }

                String nombre = rs.getString("Nombre");
                if (nombre == null || nombre.isBlank()) {
                    nombre = "Documento_" + idSoporteKey + ".pdf";
                }

                String contentType = detectarContentType(data);

                return new PdfDocumento(data, nombre, contentType);
            }
        }
    }

    //ENDPOINT PARA ELIMINAR PDFS MANUALMENTE
    public void eliminarPdf(
            Long idAdmision,
            Long idSoporteKey) throws SQLException {

            String sql = "EXEC [dbo].[pa_Net_Eliminar_DocumentoPdf] @IdAdmision = ?, @IdSoporteKey = ?";

        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoft100_ST"));
            PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, idAdmision);
            ps.setLong(2, idSoporteKey);
            ps.execute();
        }
    } 
}
