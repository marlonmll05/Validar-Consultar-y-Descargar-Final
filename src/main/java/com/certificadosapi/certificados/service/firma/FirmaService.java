package com.certificadosapi.certificados.service.firma;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.certificadosapi.certificados.config.DatabaseConfig;
import com.certificadosapi.certificados.service.atenciones.GenerarService;
import com.certificadosapi.certificados.util.ServidorUtil;

@Service
public class FirmaService {

    private final DatabaseConfig databaseConfig;
    private final ServidorUtil servidorUtil; 
    private static final Logger log = LoggerFactory.getLogger(GenerarService.class);

    public FirmaService(DatabaseConfig databaseConfig, ServidorUtil servidorUtil){

        this.databaseConfig = databaseConfig; 
        this.servidorUtil = servidorUtil; 
    }

    public List<Map<String, Object>> consultarAdmisionFirmas(String TipoDoc, String Identificacion, Integer IdAtencion) throws Exception {

        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("Asclepius_Documentos"))) {
            String sql = "EXEC dbo.Admision_Pad_firmas_consultar ?,?,?";

            try (CallableStatement cs = conn.prepareCall(sql)) {
                cs.setString(1, TipoDoc);
                cs.setString(2, Identificacion);
                cs.setInt(3, IdAtencion);

                try (ResultSet rs = cs.executeQuery()) {
                    List<Map<String, Object>> resultados = new ArrayList<>();
                    ResultSetMetaData meta = rs.getMetaData();
                    int colCount = meta.getColumnCount();

                    while (rs.next()) {
                        Map<String, Object> fila = new LinkedHashMap<>();
                        for (int i = 1; i <= colCount; i++) {
                            fila.put(meta.getColumnName(i), rs.getObject(i));
                        }
                        resultados.add(fila);
                    }

                    return resultados;
                }
            }
        }
    }

    public void insertarAdmisionFirma(Map<String, Object> datos) throws Exception {

        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("Asclepius_Documentos"))) {
            String sql = "EXEC dbo.PA_AdmisionPadFirma_Insertar ?,?,?,?,?";

            try (CallableStatement cs = conn.prepareCall(sql)) {
                cs.setInt(1, (Integer) datos.get("IdAtencion"));
                cs.setInt(2, (Integer) datos.get("IdAdmision"));
                cs.setInt(3, (Integer) datos.get("IdPacienteKey"));

                if (datos.get("FirmaPad") != null) {
                    String base64 = ((String) datos.get("FirmaPad"))
                        .replace("data:image/jpeg;base64,", "")
                        .trim();
                    cs.setBytes(4, Base64.getDecoder().decode(base64));
                } else {
                    cs.setNull(4, Types.VARBINARY);
                }

                if (datos.get("Encryp_Pad") != null) {
                    cs.setString(5, (String) datos.get("Encryp_Pad"));
                } else {
                    cs.setNull(5, Types.VARCHAR);
                }

                cs.execute();
            }
        }
    }

    public byte[] verFirma(int id) throws Exception {

        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("Asclepius_Documentos"))) {
            String sql = "SELECT FirmaPad FROM Admision_Pad_firma WHERE IdPadKey = ?";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, id);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getBytes("FirmaPad");
                    }
                }
            }
        }

        return null;
    }



    /**
     * Descarga el PDF del servidor de reportes y lo inserta en la DB.
     * Si anexar=true, hace merge con el PDF existente antes de insertar.
     */
    public Long descargarEInsertarPdf(
            Long idAdmision,
            Long idPacienteKey,
            Long idSoporteKey,
            String tipoDocumento,
            boolean anexar
    ) throws SQLException, IOException {

        String urlBase = databaseConfig.parametrosServidor(2);
        if (urlBase == null || urlBase.trim().isEmpty()) {
            throw new IllegalStateException("No se encontró la URL del servidor de reportes.");
        }

        byte[] pdfDescargado;

        try (CloseableHttpClient httpClient = servidorUtil.crearHttpClientConNTLM()) {
            String reportUrl = urlBase + "?/InformesHC/rpt_Anexo5&IdAdmision=" + idAdmision + "&rs:Format=PDF";
            log.info("Descargando PDF desde: {}", reportUrl);

            HttpGet request = new HttpGet(reportUrl);

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                int statusCode = response.getStatusLine().getStatusCode();
                log.info("HTTP statusCode={}", statusCode);

                if (statusCode != 200) {
                    String errorContent = response.getEntity() != null
                            ? EntityUtils.toString(response.getEntity(), "UTF-8") : "";
                    throw new IOException("Error al descargar el informe. Código: " + statusCode
                            + " - " + response.getStatusLine().getReasonPhrase()
                            + "\nDetalle: " + errorContent);
                }

                pdfDescargado = EntityUtils.toByteArray(response.getEntity());
                log.info("PDF descargado. Tamaño={} bytes", pdfDescargado.length);
            }
        }

        byte[] pdfFinal;

        if (anexar) {
            // Buscar PDF existente y hacer merge: existente primero, nuevo al final
            try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("Asclepius_Documentos"))) {

                String sql = """
                    SELECT TOP 1 NameFilePdf
                    FROM tbl_Net_Facturas_ListaPdf
                    WHERE IdAdmision = ? AND IdSoporteKey = ?
                    ORDER BY IdpdfKey DESC
                """;

                byte[] pdfExistente = null;

                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setLong(1, idAdmision);
                    ps.setLong(2, idSoporteKey);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            pdfExistente = rs.getBytes("NameFilePdf");
                        }
                    }
                }

                if (pdfExistente != null && pdfExistente.length > 0) {
                    log.debug("PDF existente encontrado. Tamaño={} bytes. Haciendo merge...", pdfExistente.length);

                    PDFMergerUtility merger = new PDFMergerUtility();
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    merger.setDestinationStream(baos);
                    merger.addSource(new ByteArrayInputStream(pdfExistente));
                    merger.addSource(new ByteArrayInputStream(pdfDescargado));
                    merger.mergeDocuments(null);
                    pdfFinal = baos.toByteArray();
                    log.info("Merge completado. Tamaño final={} bytes", pdfFinal.length);
                } else {
                    log.warn("No se encontró PDF existente para anexar. Se inserta solo el nuevo.");
                    pdfFinal = pdfDescargado;
                }
            }
        } else {
            pdfFinal = pdfDescargado;
        }

        // eliminarSiNo=true siempre: reemplaza cualquier registro anterior
        return databaseConfig.insertarDocumentoPdf(idAdmision, idPacienteKey, idSoporteKey, tipoDocumento, pdfFinal, true, true);
    }

    /**
     * Cuenta cuántos PDFs existen para una admisión y tipo de soporte.
     */
    public int contarPdf(Long idAdmision, Long idSoporteKey) throws SQLException {
        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("Asclepius_Documentos"))) {
            String sql = """
                SELECT COUNT(*) 
                FROM tbl_Net_Facturas_ListaPdf
                WHERE IdAdmision = ? AND IdSoporteKey = ?
            """;

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, idAdmision);
                ps.setLong(2, idSoporteKey);

                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getInt(1);
                }
            }
        }
    }

    public List<Map<String, Object>> obtenerTiposIdentificacion() throws SQLException {
        String sql = "SELECT IdTipoDocFE, DescTipoIdenFE FROM dbo.CodTiposIdentificacion_FE";

        try(Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoft100_ST"));
            PreparedStatement ps = conn.prepareStatement(sql);
            ResultSet rs = ps.executeQuery()) {

            
            List<Map<String, Object>> tipos = new ArrayList<>();
            while(rs.next()) {
                Map<String, Object> filas = new LinkedHashMap<>();
                filas.put("IdTipoDocFE", rs.getString("IdTipoDocFE"));
                filas.put("DescTipoIdenFE", rs.getString("DescTipoIdenFE"));
                tipos.add(filas);
            }
            

            return tipos;
        }
    }
}