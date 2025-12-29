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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.certificadosapi.certificados.dto.PdfDocumento;

import com.certificadosapi.certificados.config.DatabaseConfig;

/**
 * Servicio encargado de la visualización y gestión de documentos PDF asociados a admisiones.
 * Proporciona métodos para listar, obtener y eliminar documentos almacenados en la base de datos,
 * con detección automática de tipo de contenido mediante magic numbers.
 * 
 * @author Marlon Morales Llanos
 */
@Service
public class VerService {

    private static final Logger log = LoggerFactory.getLogger(VerService.class);

    private DatabaseConfig databaseConfig;

    /**
     * Constructor de VerService con inyección de dependencias.
     * 
     * @param databaseConfig Objeto de configuración para las conexiones a base de datos
     */
    @Autowired
    public VerService(DatabaseConfig databaseConfig){
        this.databaseConfig = databaseConfig;
    }


    /**
     * Detecta el tipo de contenido (MIME type) de un archivo mediante análisis de magic numbers.
     * Examina los primeros bytes del archivo para identificar su formato real.
     * 
     * Formatos soportados:
     * - PDF (application/pdf)
     * - PNG (image/png)
     * - JPEG (image/jpeg)
     * - GIF (image/gif)
     * - WEBP (image/webp)
     * 
     * @param data Array de bytes del archivo a analizar
     * @return String con el MIME type detectado. Por defecto retorna "application/pdf"
     *         si no se puede determinar el tipo o si los datos son insuficientes
     */
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
    
    /**
     * Obtiene la lista de documentos PDF asociados a una admisión específica.
     * Utiliza la función de base de datos fn_Net_DocSoporte_NameFile para obtener
     * los nombres descriptivos de los documentos.
     * 
     * @param idAdmision ID de la admisión para consultar sus documentos
     * @return Lista de mapas conteniendo información de documentos con las llaves:
     *         "idSoporteKey" (Long) - ID único del documento,
     *         "nombre" (String) - Nombre descriptivo del documento.
     *         Los resultados están ordenados por IdSoporteKey
     * @throws IllegalArgumentException si no se encuentran documentos para la admisión
     * @throws SQLException si ocurre un error durante la consulta a la base de datos
     */
    public List<Map<String, Object>> listaPdfs(Long idAdmision) throws SQLException {
        
        log.info("Iniciando listaPdfs(idAdmision={})", idAdmision);

        String sql = """
            SELECT l.IdSoporteKey,
                   dbo.fn_Net_DocSoporte_NameFile(?, l.IdSoporteKey) AS Nombre
            FROM tbl_Net_Facturas_ListaPdf l
            WHERE l.IdAdmision = ?
            ORDER BY l.IdSoporteKey
        """;

        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("Asclepius_Documentos"));
             PreparedStatement ps = conn.prepareStatement(sql)) {

            log.debug("Conexión abierta a Asclepius_Documentos para listaPdfs");
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
                    log.warn("listaPdfs(idAdmision={}) no encontró documentos", idAdmision);
                    throw new IllegalArgumentException("No hay documentos para la admisión " + idAdmision);
                }

                log.info("listaPdfs(idAdmision={}) devolvió {} registros", idAdmision, out.size());
                return out;
            }
        }
    }
    
    // VER EL CONTENIDO DE UN PDF
    public PdfDocumento obtenerPdf(Long idAdmision, Long idSoporteKey) throws SQLException {

        log.info("Iniciando obtenerPdf(idAdmision={}, idSoporteKey={})", idAdmision, idSoporteKey);

        String sql = """
            SELECT NameFilePdf,
                   dbo.fn_Net_DocSoporte_NameFile(?, ?) AS Nombre
            FROM tbl_Net_Facturas_ListaPdf
            WHERE IdAdmision = ? AND IdSoporteKey = ?
        """;

        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("Asclepius_Documentos"));
             PreparedStatement ps = conn.prepareStatement(sql)) {

            log.debug("Conexión abierta a Asclepius_Documentos para obtenerPdf");
            ps.setLong(1, idAdmision);
            ps.setLong(2, idSoporteKey);
            ps.setLong(3, idAdmision);
            ps.setLong(4, idSoporteKey);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    log.warn("obtenerPdf no encontró registro para idAdmision={} e idSoporteKey={}", idAdmision, idSoporteKey);
                    throw new NoSuchElementException("Documento no encontrado.");
                }

                byte[] data = rs.getBytes("NameFilePdf");
                if (data == null || data.length == 0) {
                    log.warn("obtenerPdf encontró contenido vacío para idAdmision={} e idSoporteKey={}", idAdmision, idSoporteKey);
                    throw new NoSuchElementException("Contenido vacío del documento.");
                }

                String nombre = rs.getString("Nombre");
                if (nombre == null || nombre.isBlank()) {
                    nombre = "Documento_" + idSoporteKey + ".pdf";
                    log.debug("Nombre vacío en BD, usando nombre por defecto: {}", nombre);
                }

                String contentType = detectarContentType(data);
                log.info("obtenerPdf(idAdmision={}, idSoporteKey={}) obtuvo documento: nombre='{}', contentType='{}', tamaño={} bytes",
                         idAdmision, idSoporteKey, nombre, contentType, data.length);

                return new PdfDocumento(data, nombre, contentType);
            }
        }
    }

    // ELIMINAR PDF MANUALMENTE
    public void eliminarPdf(Long idAdmision, Long idSoporteKey) throws SQLException {

        log.info("Iniciando eliminarPdf(idAdmision={}, idSoporteKey={})", idAdmision, idSoporteKey);

        String sql = "EXEC [dbo].[pa_Net_Eliminar_DocumentoPdf] @IdAdmision = ?, @IdSoporteKey = ?";

        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoft100_ST"));
             PreparedStatement ps = conn.prepareStatement(sql)) {

            log.debug("Conexión abierta a IPSoft100_ST para eliminarPdf");
            ps.setLong(1, idAdmision);
            ps.setLong(2, idSoporteKey);

            ps.execute();
            log.info("eliminarPdf ejecutado correctamente para idAdmision={} e idSoporteKey={}", idAdmision, idSoporteKey);
        }
    }
}