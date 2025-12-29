package com.certificadosapi.certificados.service;

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

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.certificadosapi.certificados.config.DatabaseConfig;
import com.certificadosapi.certificados.util.ServidorUtil;

/**
 * Servicio encargado de la gestión de reportes y operaciones con documentos PDF.
 * Este servicio maneja la descarga de PDFs por cada Factura, 
 * la combinación de documentos y la verificación de facturas.
 * 
 * @author Marlon Morales Llanos
 */

@Service 
public class ReporteService {

    private static final Logger log = LoggerFactory.getLogger(ReporteService.class);

    private final DatabaseConfig databaseConfig;
    private final ServidorUtil servidorUtil;

    @Autowired
    public ReporteService(DatabaseConfig databaseConfig, ServidorUtil servidorUtil){
        this.databaseConfig = databaseConfig;
        this.servidorUtil = servidorUtil;
    }


    /**
     * Descarga archivos PDF desde Reporting Service y los combina en un solo documento.
     * Acepta múltiples IDs de admisión separados por comas y genera un PDF combinado.
     * 
     * @param idAdmision Cadena de IDs de admisión separados por comas a procesar
     * @param nombreArchivo Nombre del archivo (actualmente no utilizado en la implementación)
     * @param nombreSoporte Nombre del documento/reporte de soporte a generar
     * @return Array de bytes que contiene el documento PDF combinado
     * @throws SQLException si ocurre un error en la base de datos
     * @throws IllegalArgumentException si el nombre del soporte está vacío o no se pudo procesar ningún PDF
     * @throws RuntimeException si hay un error al descargar o procesar los PDFs
     */
    public byte[] descargarPdf(String idAdmision, String nombreArchivo, String nombreSoporte) throws SQLException {
        log.info("Iniciando descarga de PDF para admisiones={}, soporte={}", idAdmision, nombreSoporte);

        if (nombreSoporte == null || nombreSoporte.trim().isEmpty()) {
            log.error("Nombre de soporte vacío en solicitud de descarga");
            throw new IllegalArgumentException("El nombre del soporte es requerido.");
        }

        String urlBase = databaseConfig.parametrosServidor(2);

        if (urlBase == null || urlBase.trim().isEmpty()) {
            log.error("URLReportServerWS viene vacía");
            throw new IllegalArgumentException("No se encontró la URL del servidor de reportes.");
        }


        // Descargar PDFs para cada ID de admisión
        try (CloseableHttpClient httpClient = servidorUtil.crearHttpClientConNTLM()) {

            String[] ids = idAdmision.split(",");
            PDFMergerUtility merger = new PDFMergerUtility();
            ByteArrayOutputStream mergedOutput = new ByteArrayOutputStream();
            merger.setDestinationStream(mergedOutput);

            boolean hayAlMenosUnPdf = false;

            for (String id : ids) {
                id = id.trim();
                log.info("Descargando PDF para IdAdmision={}", id);

                String reportUrl = urlBase + "?" + nombreSoporte + "&IdAdmision=" + id + "&rs:Format=PDF";

                log.debug("URL generada para Reporting Service: {}", reportUrl);

                HttpGet request = new HttpGet(reportUrl);

                try (CloseableHttpResponse response = httpClient.execute(request)) {

                    int statusCode = response.getStatusLine().getStatusCode();
                    log.debug("Respuesta HTTP para IdAdmision {} -> {}", id, statusCode);

                    if (statusCode == 200) {
                        byte[] pdfBytes = EntityUtils.toByteArray(response.getEntity());
                        log.debug("PDF recibido para IdAdmision {} con tamaño {} bytes", id, pdfBytes.length);

                        merger.addSource(new ByteArrayInputStream(pdfBytes));
                        hayAlMenosUnPdf = true;

                    } else {
                        String errorContent = EntityUtils.toString(response.getEntity(), "UTF-8");
                        log.error("Error al descargar PDF para IdAdmision {} -> Código={} Detalle={}", id, statusCode, errorContent);

                        throw new RuntimeException(
                                "Error al descargar el informe. Código: " + statusCode + " - " + errorContent
                        );
                    }
                }
            }

            if (!hayAlMenosUnPdf) {
                log.warn("Ningún PDF pudo ser descargado en la solicitud para admisiones {}", idAdmision);
                throw new IllegalArgumentException("No se pudo procesar ninguna admisión.");
            }

            log.info("Combinando PDFs descargados");
            merger.mergeDocuments(null);

            log.info("PDF combinado entregado exitosamente");
            return mergedOutput.toByteArray();

        } catch (IOException e) {
            log.error("Error procesando PDF: {}", e.getMessage());
            throw new RuntimeException("Excepción al conectar o procesar los PDFs: " + e.getMessage(), e);
        }
    }


    /**
     * Obtiene la lista de documentos de soporte activos desde la base de datos.
     * Solo retorna documentos donde Inactivo está en 0 (activo).
     * 
     * @return Lista de mapas conteniendo información de documentos con las llaves:
     *         "Id" (Integer), "nombreDocSoporte" (String), "nombreRptService" (String)
     * @throws RuntimeException si ocurre un error de base de datos durante la consulta
     */
    public List<Map<String, Object>> obtenerDocumentosSoporte() {
        log.info("Consultando documentos de soporte activos");

        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoft100_ST"))) {

            String sql = "SELECT Id, NombreDocSoporte, NombreRptService FROM tbl_Net_Facturas_DocSoporte WHERE Inactivo = 0";

            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {

                List<Map<String, Object>> resultados = new ArrayList<>();

                while (rs.next()) {
                    Map<String, Object> fila = new LinkedHashMap<>();
                    fila.put("Id", rs.getInt("Id"));
                    fila.put("nombreDocSoporte", rs.getString("NombreDocSoporte"));
                    fila.put("nombreRptService", rs.getString("NombreRptService"));
                    resultados.add(fila);
                }

                log.debug("Documentos de soporte obtenidos: {}", resultados.size());
                return resultados;
            }

        } catch (SQLException e) {
            log.error("Error SQL al obtener documentos de soporte: {}", e.getMessage());
            throw new RuntimeException("Error al obtener los documentos de soporte: " + e.getMessage(), e);
        }
    }


    /**
     * Verifica si existe una factura en la base de datos para un ID de admisión y documento dados.
     * Consulta la tabla tbl_Net_Facturas_ListaPdf para verificar la existencia del registro.
     * 
     * @param idAdmision El ID de admisión a buscar
     * @param idDoc El ID del documento de soporte (IdSoporteKey) a buscar
     * @return Mapa con una sola llave "existe" (Boolean): true si la factura existe, false en caso contrario
     * @throws RuntimeException si ocurre un error de base de datos durante la verificación
     */
    public Map<String, Boolean> existeFactura(String idAdmision, Integer idDoc) {
        log.info("Verificando existencia de factura IdAdmision={} IdSoporteKey={}", idAdmision, idDoc);

        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoft100_ST"))) {

            String sql = "SELECT COUNT(*) FROM dbo.tbl_Net_Facturas_ListaPdf WHERE IdSoporteKey = ? AND IdAdmision = ?";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, idDoc);
                ps.setString(2, idAdmision);

                try (ResultSet rs = ps.executeQuery()) {

                    if (rs.next() && rs.getInt(1) > 0) {
                        log.debug("Factura encontrada para IdAdmision={} IdSoporteKey={}", idAdmision, idDoc);
                        return Map.of("existe", true);
                    }
                }
            }

            log.debug("No existe factura para IdAdmision={} IdSoporteKey={}", idAdmision, idDoc);
            return Map.of("existe", false);

        } catch (Exception e) {
            log.error("Error al verificar factura IdAdmision={} Detalle={}", idAdmision, e.getMessage());
            throw new RuntimeException("Error: " + e.getMessage(), e);
        }
    }
}


