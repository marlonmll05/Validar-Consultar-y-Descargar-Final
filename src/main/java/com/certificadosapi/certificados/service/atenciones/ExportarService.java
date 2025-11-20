package com.certificadosapi.certificados.service.atenciones;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;

import com.certificadosapi.certificados.config.DatabaseConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.certificadosapi.certificados.dto.PdfDocumento;
import com.certificadosapi.certificados.dto.XmlDocumento;

@Service
public class ExportarService {

    private static final Logger log = LoggerFactory.getLogger(ExportarService.class);
    
    private DatabaseConfig databaseConfig;

    @Autowired
    public ExportarService(DatabaseConfig databaseConfig){
        this.databaseConfig = databaseConfig;
    }

    //ENDPOINT PARA EXPORTAR EL CONTENIDO DE UNA ADMISION
    public PdfDocumento exportarPdf(Long idAdmision, Long idSoporteKey) throws SQLException, IOException {
        log.info("Exportando PDF - idAdmision: {}, idSoporteKey: {}", idAdmision, idSoporteKey);

        String sql = "SELECT NameFilePdf FROM tbl_Net_Facturas_ListaPdf WHERE IdAdmision = ? AND IdSoporteKey = ?";

        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("Asclepius_Documentos"));
            PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, idAdmision);
            ps.setLong(2, idSoporteKey);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    log.warn("Documento no encontrado - idAdmision: {}, idSoporteKey: {}", idAdmision, idSoporteKey);
                    throw new NoSuchElementException("Documento no encontrado");
                }

                try (InputStream is = rs.getBinaryStream("NameFilePdf")) {
                    if (is == null) {
                        log.error("Contenido NULL para documento - idSoporteKey: {}", idSoporteKey);
                        throw new NoSuchElementException("Contenido del documento es NULL");
                    }

                    byte[] pdfBytes = is.readAllBytes();
                    if (pdfBytes.length == 0) {
                        log.error("Documento vacío (0 bytes) - idSoporteKey: {}", idSoporteKey);
                        throw new NoSuchElementException("Documento vacío");
                    }

                    log.debug("PDF leído exitosamente - Tamaño: {} bytes", pdfBytes.length);

                    // Obtener nombre real
                    String nombrePdf = "Documento_" + idSoporteKey + ".pdf";
                    try (PreparedStatement psName = conn.prepareStatement(
                            "SELECT dbo.fn_Net_DocSoporte_NameFile(?, ?) AS Nombre")) {
                        psName.setLong(1, idAdmision);
                        psName.setLong(2, idSoporteKey);
                        try (ResultSet rsName = psName.executeQuery()) {
                            if (rsName.next() && rsName.getString("Nombre") != null) {
                                nombrePdf = rsName.getString("Nombre");
                                log.debug("Nombre de archivo obtenido: {}", nombrePdf);
                            } else {
                                log.debug("Usando nombre por defecto: {}", nombrePdf);
                            }
                        }
                    }

                    log.info("PDF exportado exitosamente - Nombre: {}, Tamaño: {} bytes", nombrePdf, pdfBytes.length);
                    return new PdfDocumento(pdfBytes, nombrePdf, "application/pdf");
                }
            }
        }
    }

    //ENDPOINT PARA OBTENER EL CUV DE UNA FACTURA VALIDADA
    public String obtenerRespuestaRips(String nFact) throws SQLException {
        log.info("Obteniendo respuesta RIPS para factura: {}", nFact);
        
        String sql =
            "SELECT TOP 1 MensajeRespuesta " +
            "FROM RIPS_RespuestaApi " +
            "WHERE LTRIM(RTRIM(NFact)) = LTRIM(RTRIM(?)) " +
            "ORDER BY 1 DESC";

        try (Connection c = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoft100_ST"));
            PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, nFact);
            
            log.debug("Ejecutando query para obtener respuesta RIPS");
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String respuesta = rs.getString("MensajeRespuesta");
                    if (respuesta == null || respuesta.isBlank()) {
                        log.warn("Respuesta RIPS vacía para factura: {}", nFact);
                        throw new NoSuchElementException("Respuesta RIPS vacía");
                    }
                    log.info("Respuesta RIPS obtenida exitosamente para factura: {}", nFact);
                    return respuesta;
                }
            }
        }
        
        log.warn("No se encontró respuesta RIPS para factura: {}", nFact);
        throw new NoSuchElementException("No se encontró respuesta RIPS para la factura");
    }

    //ENDPOINT PARA OBTENER EL XML DE UNA FACTURA Y RENOMBRARLO
    public XmlDocumento generarXml(String nFact) throws SQLException {
        log.info("Generando XML para factura: {}", nFact);
        
        String sql = "SELECT FF.NFact, CONVERT(xml, M.DocXml) AS DocXml, T.CUV, FF.IdMovDoc " +
                    "FROM IPSoft100_ST.dbo.FacturaFinal FF " +
                    "INNER JOIN IPSoft100_ST.dbo.Rips_Transaccion T ON T.IdMovDoc = FF.IdMovDoc " +
                    "INNER JOIN IPSoftFinanciero_ST.dbo.MovimientoDocumentos M ON M.IdMovDoc = FF.IdMovDoc " +
                    "WHERE FF.NFact = ?";

        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoft100_ST"));
            PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nFact);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    log.warn("No se encontró registro para factura: {}", nFact);
                    throw new NoSuchElementException("No se encontró registro para la factura");
                }

                String docXml = rs.getString("DocXml");
                int idMovDoc = rs.getInt("IdMovDoc");

                log.debug("Registro encontrado - IdMovDoc: {}", idMovDoc);

                if (docXml == null || docXml.trim().isEmpty()) {
                    log.warn("No hay XML disponible para factura: {}", nFact);
                    throw new NoSuchElementException("No hay XML para la factura");
                }

                log.debug("XML obtenido - Tamaño: {} caracteres", docXml.length());

                try (Connection connFinanciero = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoftFinanciero_ST"))) {
                    
                    String numdoc = "";
                    String docQuery = "SELECT Numdoc FROM MovimientoDocumentos WHERE IdMovDoc = ?";
                    try (PreparedStatement stmt = connFinanciero.prepareStatement(docQuery)) {
                        stmt.setInt(1, idMovDoc);
                        ResultSet rsDoc = stmt.executeQuery();
                        if (rsDoc.next()) {
                            numdoc = rsDoc.getString("Numdoc");
                            log.debug("Numdoc obtenido: {}", numdoc);
                        }
                    }

                    String idEmpresaGrupo = "";
                    String empresaQuery = "SELECT IdEmpresaGrupo FROM MovimientoDocumentos as M INNER JOIN Empresas as E ON E.IdEmpresaKey = M.IdEmpresaKey WHERE IdMovDoc = ?";
                    try (PreparedStatement stmt = connFinanciero.prepareStatement(empresaQuery)) {
                        stmt.setInt(1, idMovDoc);
                        ResultSet rsEmpresa = stmt.executeQuery();
                        if (rsEmpresa.next()) {
                            idEmpresaGrupo = rsEmpresa.getString("IdEmpresaGrupo");
                            log.debug("IdEmpresaGrupo obtenido: {}", idEmpresaGrupo);
                        }
                    }

                    String yearSuffix = String.valueOf(LocalDate.now().getYear()).substring(2);
                    String formattedNumdoc = String.format("%08d", Integer.parseInt(numdoc));
                    String fileName = "ad0" + idEmpresaGrupo + "000" + yearSuffix + formattedNumdoc + ".xml";

                    log.debug("Nombre de archivo XML generado: {}", fileName);

                    byte[] xmlBytes = docXml.getBytes(StandardCharsets.UTF_8);

                    log.info("XML generado exitosamente - Nombre: {}, Tamaño: {} bytes", fileName, xmlBytes.length);
                    return new XmlDocumento(xmlBytes, fileName, idMovDoc);
                }
            }
        }
    }

    public byte[] armarZip(
            String nFact,
            MultipartFile xml,
            MultipartFile jsonFactura,
            List<MultipartFile> pdfs
    ) throws SQLException, IOException {

        log.info("Armando ZIP para factura: {}", nFact);

        if (xml == null || xml.isEmpty()) {
            log.error("Archivo XML faltante en request para factura: {}", nFact);
            throw new IllegalArgumentException("Falta el archivo XML en la parte 'xml'");
        }

        log.debug("Archivo XML recibido - Nombre: {}, Tamaño: {} bytes", xml.getOriginalFilename(), xml.getSize());

        final Pattern ILLEGAL = Pattern.compile("[\\\\/:*?\"<>|]+");
        String sanitizeN = (nFact == null ? "" : ILLEGAL.matcher(nFact).replaceAll("_").trim());
        String folderName = sanitizeN + "/"; 

        Integer idMovDoc = null;
        String numdoc = null, idEmpresaGrupo = null;

        // IdMovDoc
        log.debug("Obteniendo IdMovDoc para factura: {}", nFact);
        try (Connection c = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoft100_ST"));
            PreparedStatement ps = c.prepareStatement("SELECT IdMovDoc FROM FacturaFinal WHERE NFact = ?")) {
            ps.setString(1, nFact);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    idMovDoc = rs.getInt(1);
                    log.debug("IdMovDoc obtenido: {}", idMovDoc);
                }
            }
        }

        if (idMovDoc != null) {
            log.debug("Obteniendo información financiera para IdMovDoc: {}", idMovDoc);
            
            // Prefijo, Numdoc
            try (Connection c = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoftFinanciero_ST"))) {
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT Prefijo, Numdoc FROM MovimientoDocumentos WHERE IdMovDoc = ?")) {
                    ps.setInt(1, idMovDoc);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            numdoc = rs.getString("Numdoc");
                            log.debug("Numdoc obtenido: {}", numdoc);
                        }
                    }
                }

                // IdEmpresaGrupo
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT E.IdEmpresaGrupo " +
                        "FROM MovimientoDocumentos M " +
                        "INNER JOIN Empresas E ON E.IdEmpresaKey = M.IdEmpresaKey " +
                        "WHERE M.IdMovDoc = ?")) {
                    ps.setInt(1, idMovDoc);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            idEmpresaGrupo = rs.getString("IdEmpresaGrupo");
                            log.debug("IdEmpresaGrupo obtenido: {}", idEmpresaGrupo);
                        }
                    }
                }
            }
        }

        // Respuesta validador (CUV)
        log.debug("Obteniendo respuesta del validador para factura: {}", nFact);
        String respuestaValidador = null;
        try (Connection c = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoft100_ST"))) {
            String sql = "SELECT MensajeRespuesta FROM RIPS_RespuestaApi " +
                        "WHERE LTRIM(RTRIM(NFact)) = LTRIM(RTRIM(?))";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, nFact);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        respuestaValidador = rs.getString("MensajeRespuesta");
                        log.debug("Respuesta del validador obtenida");
                    } else {
                        log.debug("No hay respuesta del validador para factura: {}", nFact);
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Advertencia al obtener validador para factura {}: {}", nFact, ex.getMessage());
        }

        // ProcesoId
        String procesoId = "";
        if (respuestaValidador != null && !respuestaValidador.isBlank()) {
            try {
                ObjectMapper om = new ObjectMapper();
                JsonNode node = om.readTree(respuestaValidador);
                if (node.has("ProcesoId")) {
                    procesoId = node.get("ProcesoId").asText("");
                    log.debug("ProcesoId extraído: {}", procesoId);
                }
            } catch (Exception e) {
                log.warn("Error al parsear ProcesoId: {}", e.getMessage());
            }
        }

        // Nombre del XML
        String xmlFileName;
        if (idEmpresaGrupo != null && numdoc != null) {
            String yearSuffix = String.valueOf(java.time.LocalDate.now().getYear()).substring(2);
            String formattedNumdoc;
            try {
                int num = Integer.parseInt(numdoc);
                formattedNumdoc = String.format("%08d", num);
            } catch (NumberFormatException nfe) {
                log.warn("Error al formatear numdoc: {}", nfe.getMessage());
                formattedNumdoc = numdoc;
            }
            xmlFileName = "ad0" + idEmpresaGrupo + "000" + yearSuffix + formattedNumdoc + ".xml";
        } else {
            String original = xml.getOriginalFilename();
            xmlFileName = (original != null && !original.isBlank())
                    ? ILLEGAL.matcher(original).replaceAll("_").trim()
                    : ("Factura_" + sanitizeN + ".xml");
        }

        log.debug("Nombre de archivo XML en ZIP: {}", xmlFileName);

        // Crear ZIP
        log.debug("Iniciando creación del archivo ZIP");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {

            // XML
            ZipEntry xmlEntry = new ZipEntry(folderName + xmlFileName);
            zos.putNextEntry(xmlEntry);
            zos.write(xml.getBytes());
            zos.closeEntry();
            log.debug("XML agregado al ZIP: {}", xmlFileName);

            // JSON
            if (jsonFactura != null && !jsonFactura.isEmpty()) {
                String jsonName = jsonFactura.getOriginalFilename();
                jsonName = (jsonName != null && !jsonName.isBlank())
                        ? ILLEGAL.matcher(jsonName).replaceAll("_").trim()
                        : ("Factura_" + sanitizeN + ".json");
                ZipEntry jsonEntry = new ZipEntry(folderName + jsonName);
                zos.putNextEntry(jsonEntry);
                zos.write(jsonFactura.getBytes());
                zos.closeEntry();
                log.debug("JSON agregado al ZIP: {}", jsonName);
            } else {
                log.debug("No se incluyó archivo JSON en el ZIP");
            }

            // PDFs 
            if (pdfs != null && !pdfs.isEmpty()) {
                log.debug("Agregando {} PDFs al ZIP", pdfs.size());
                int idx = 1;
                for (MultipartFile pdf : pdfs) {
                    if (pdf == null || pdf.isEmpty()) continue;
                    String nombre = pdf.getOriginalFilename();
                    nombre = (nombre != null && !nombre.isBlank())
                            ? ILLEGAL.matcher(nombre).replaceAll("_").trim()
                            : ("Documento_" + idx + ".pdf");
                    ZipEntry pdfEntry = new ZipEntry(folderName + nombre);
                    zos.putNextEntry(pdfEntry);
                    zos.write(pdf.getBytes());
                    zos.closeEntry();
                    log.debug("PDF {} agregado al ZIP: {}", idx, nombre);
                    idx++;
                }
            } else {
                log.debug("No se incluyeron PDFs en el ZIP");
            }

            // TXT CUV
            if (respuestaValidador != null && !respuestaValidador.isBlank()) {
                String safeProc = ILLEGAL.matcher(procesoId == null ? "" : procesoId).replaceAll("_").trim();
                String nombreTxt = "ResultadosMSPS_" + sanitizeN
                        + (safeProc.isBlank() ? "" : ("_ID" + safeProc))
                        + "_A_CUV.txt";
                ZipEntry txtEntry = new ZipEntry(folderName + nombreTxt);
                zos.putNextEntry(txtEntry);
                zos.write(respuestaValidador.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
                log.debug("TXT CUV agregado al ZIP: {}", nombreTxt);
            } else {
                log.debug("No se incluyó archivo TXT CUV en el ZIP");
            }
        }

        byte[] zipBytes = baos.toByteArray();
        log.info("ZIP armado exitosamente para factura: {} - Tamaño: {} bytes", nFact, zipBytes.length);
        
        return zipBytes;
    }

    

    public byte[] exportarCuentaCobro(
        String numeroCuentaCobro,
        MultiValueMap<String, MultipartFile> fileParts) throws IOException {
        
        log.info("Iniciando exportacion ZIP para Cuenta de Cobro {}", numeroCuentaCobro);
    
        final Pattern ILLEGAL = Pattern.compile("[\\\\/:*?\"<>|]+");
        String sanitizeCuenta = ILLEGAL.matcher(numeroCuentaCobro == null ? "" : numeroCuentaCobro).replaceAll("_").trim();
        String folderCuenta = (sanitizeCuenta.isBlank() ? "SIN_NUMERO" : sanitizeCuenta) + "/";
        
        log.debug("Carpeta raiz del ZIP: {}", folderCuenta);

        record PartItem(String tipo, MultipartFile file) {}
        Map<String, List<PartItem>> porNfact = new LinkedHashMap<>();

        log.info("Analizando partes recibidas del frontend... Total keys: {}", fileParts.size());

        for (Map.Entry<String, List<MultipartFile>> e : fileParts.entrySet()) {
            String key = e.getKey();
            if (key == null || key.isBlank()) continue;
            log.warn("Key vacia, se ignora");

            int idx = key.indexOf('_');
            if (idx <= 0 || idx >= key.length() - 1) {
                log.warn("Key '{}' no tiene formato valido <numero_tipo, se ignora", key);
                continue;
            }

            String nFact = key.substring(0, idx).trim();
            String tipo  = key.substring(idx + 1).trim();
            if (!( "xml".equals(tipo) || "jsonFactura".equals(tipo) || "pdfs".equals(tipo) )) {
                log.warn("Tipo '{}' no reconocido para key '{}', se ignora", tipo, key);
                continue;
            }

            List<MultipartFile> files = e.getValue();
            if (files == null || files.isEmpty()){
                log.warn("No hay archivos en key '{}'", key);
                continue;
            }

            for (MultipartFile mf : files) {
                if (mf == null || mf.isEmpty()){
                    log.warn("Archivo vacio para nFact {}", nFact);
                    continue;
                }
                log.debug("Archivo recibido nFact={} tipo={} nombre={}", nFact, tipo, mf.getOriginalFilename());   
                porNfact.computeIfAbsent(nFact, k -> new ArrayList<>()).add(new PartItem(tipo, mf));
            }
        }

        log.info("Facturas detectadas para procesar: {}", porNfact.keySet());

        // === CREACIÓN ZIP ===
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int archivosAgregados = 0;
        Set<String> nombresUsados = new HashSet<>();

        try (ZipOutputStream zos = new ZipOutputStream(baos)) {

            for (Map.Entry<String, List<PartItem>> entry : porNfact.entrySet()) {

                String nFact = entry.getKey();
                log.info("Procesando factura {}", nFact);

                String sanitizeN = ILLEGAL.matcher(nFact == null ? "" : nFact).replaceAll("_").trim();
                String folderFactura = folderCuenta + (sanitizeN.isBlank() ? "SIN_FACTURA" : sanitizeN) + "/";

                log.debug("Carpeta interna para factura {}: {}", nFact, folderFactura);

                // === CLASIFICAR ARCHIVOS ===
                List<MultipartFile> xmls  = new ArrayList<>();
                List<MultipartFile> jsons = new ArrayList<>();
                List<MultipartFile> pdfs  = new ArrayList<>();

                for (PartItem it : entry.getValue()) {
                    switch (it.tipo()) {
                        case "xml" -> xmls.add(it.file());
                        case "jsonFactura" -> jsons.add(it.file());
                        case "pdfs" -> pdfs.add(it.file());
                    }
                }

                log.debug("   XMLs: {}, JSONs: {}, PDFs: {}", xmls.size(), jsons.size(), pdfs.size());

                // === OBTENER DATOS DE BD ===
                Integer idMovDoc = null;
                String numdoc = null, idEmpresaGrupo = null;

                log.debug("Consultando IdMovDoc para {}", nFact);
                try (Connection c = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoft100_ST"));
                    PreparedStatement ps = c.prepareStatement("SELECT IdMovDoc FROM FacturaFinal WHERE NFact = ?")) {

                    ps.setString(1, nFact);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            idMovDoc = rs.getInt(1);
                            log.debug("IdMovDoc={} para factura {}", idMovDoc, nFact);
                        } else {
                            log.warn("No existe IdMovDoc para factura {}", nFact);
                        }
                    }
                } catch (Exception ex) {
                    log.warn("Error consultando IdMovDoc {}: {}", nFact, ex.getMessage());
                }

                if (idMovDoc != null) {
                    log.debug("Consultando datos financieros para IdMovDoc {}", idMovDoc);
                    try (Connection c = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoftFinanciero_ST"))) {

                        try (PreparedStatement ps = c.prepareStatement(
                                "SELECT Prefijo, Numdoc FROM MovimientoDocumentos WHERE IdMovDoc = ?")) {
                            ps.setInt(1, idMovDoc);
                            try (ResultSet rs = ps.executeQuery()) {
                                if (rs.next()) {
                                    numdoc  = rs.getString("Numdoc");
                                    log.debug("Numdoc={}", numdoc);
                                }
                            }
                        }

                        try (PreparedStatement ps = c.prepareStatement(
                                """
                                SELECT E.IdEmpresaGrupo
                                FROM MovimientoDocumentos M
                                INNER JOIN Empresas E ON E.IdEmpresaKey = M.IdEmpresaKey
                                WHERE M.IdMovDoc = ?
                                """)) {
                            ps.setInt(1, idMovDoc);
                            try (ResultSet rs = ps.executeQuery()) {
                                if (rs.next()) {
                                    idEmpresaGrupo = rs.getString("IdEmpresaGrupo");
                                    log.debug("IdEmpresaGrupo={}", idEmpresaGrupo);
                                }
                            }
                        }

                    } catch (Exception ex) {
                        log.warn("Error consultando datos financieros {}: {}", nFact, ex.getMessage());
                    }
                }

                // === CONSULTAR RESPUESTA VALIDADOR ===
                log.debug("🔎 Consultando respuesta validador para {}", nFact);

                String respuestaValidador = null;
                try (Connection c = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoft100_ST"))) {

                    String sql = """
                            SELECT MensajeRespuesta
                            FROM RIPS_RespuestaApi
                            WHERE LTRIM(RTRIM(NFact)) = LTRIM(RTRIM(?))
                            """;

                    try (PreparedStatement ps = c.prepareStatement(sql)) {
                        ps.setString(1, nFact);

                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                respuestaValidador = rs.getString("MensajeRespuesta");
                                log.debug("Validador encontrado para {}", nFact);
                            } else {
                                log.debug("No hay respuesta validador para {}", nFact);
                            }
                        }
                    }

                } catch (Exception ex) {
                    log.warn("Error consultando validador {}: {}", nFact, ex.getMessage());
                }

                // === XML ===
                if (xmls.isEmpty()) {
                    log.warn("No llegó XML para factura {} | SE OMITE ESTA FACTURA", nFact);
                    continue;
                }

                MultipartFile xml = xmls.get(0);
                String xmlFileName;

                if (idEmpresaGrupo != null && numdoc != null) {
                    String yearSuffix = String.valueOf(java.time.LocalDate.now().getYear()).substring(2);
                    String formattedNumdoc;
                    try {
                        formattedNumdoc = String.format("%08d", Integer.parseInt(numdoc));
                    } catch (Exception ex) {
                        formattedNumdoc = numdoc;
                    }
                    xmlFileName = "ad0" + idEmpresaGrupo + "000" + yearSuffix + formattedNumdoc + ".xml";
                } else {
                    xmlFileName = xml.getOriginalFilename();
                    if (xmlFileName == null || xmlFileName.isBlank()) {
                        xmlFileName = "Factura_" + sanitizeN + ".xml";
                    }
                }

                String xmlPath = obtenerNombreUnico(folderFactura, xmlFileName, nombresUsados);
                log.debug("Agregando XML: {}", xmlPath);

                ZipEntry xmlEntry = new ZipEntry(xmlPath);
                zos.putNextEntry(xmlEntry);
                zos.write(xml.getBytes());
                zos.closeEntry();
                archivosAgregados++;

                // === JSON ===
                for (MultipartFile jf : jsons) {
                    String nombre = jf.getOriginalFilename();
                    if (nombre == null || nombre.isBlank()) {
                        nombre = "Factura_" + sanitizeN + ".json";
                    }

                    String jsonPath = obtenerNombreUnico(folderFactura, nombre, nombresUsados);
                    log.debug("Agregando JSON: {}", jsonPath);

                    ZipEntry jsonEntry = new ZipEntry(jsonPath);
                    zos.putNextEntry(jsonEntry);
                    zos.write(jf.getBytes());
                    zos.closeEntry();
                    archivosAgregados++;
                }

                // === PDFs ===
                for (MultipartFile pdf : pdfs) {
                    if (pdf == null || pdf.isEmpty()) continue;

                    String nombre = pdf.getOriginalFilename();
                    if (nombre == null || nombre.isBlank()) {
                        nombre = "Documento.pdf";
                    }

                    String pdfPath = obtenerNombreUnico(folderFactura, nombre, nombresUsados);
                    log.debug("Agregando PDF: {}", pdfPath);

                    ZipEntry pdfEntry = new ZipEntry(pdfPath);
                    zos.putNextEntry(pdfEntry);
                    zos.write(pdf.getBytes());
                    zos.closeEntry();
                    archivosAgregados++;
                }

                // === TXT VALIDACIÓN ===
                if (respuestaValidador != null && !respuestaValidador.isBlank()) {
                    String procesoId = "";

                    try {
                        ObjectMapper om = new ObjectMapper();
                        JsonNode node = om.readTree(respuestaValidador);
                        procesoId = node.has("ProcesoId") ? node.get("ProcesoId").asText("") : "";
                    } catch (Exception e) {
                        log.warn("⚠ No se pudo leer ProcesoId en {}", nFact);
                    }

                    String safeProc = ILLEGAL.matcher(procesoId).replaceAll("_").trim();

                    String nombreTxt = "ResultadosMSPS_" + sanitizeN
                            + (safeProc.isBlank() ? "" : ("_ID" + safeProc))
                            + "_A_CUV.txt";

                    String txtPath = obtenerNombreUnico(folderFactura, nombreTxt, nombresUsados);
                    log.debug("Agregando TXT: {}", txtPath);

                    ZipEntry txtEntry = new ZipEntry(txtPath);
                    zos.putNextEntry(txtEntry);
                    zos.write(respuestaValidador.getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();
                    archivosAgregados++;
                }

                log.info("Factura {} procesada correctamente", nFact);
            }
        }

        if (archivosAgregados == 0) {
            log.error("No se agregaron archivos al ZIP. fileParts no contenía datos válidos");
            throw new IllegalArgumentException("No se encontraron archivos para generar el ZIP");
        }

        log.info("ZIP generado exitosamente para Cuenta {} — Archivos agregados: {}", numeroCuentaCobro, archivosAgregados);

        return baos.toByteArray();
    }


    private String obtenerNombreUnico(String folder, String nombreOriginal, Set<String> nombresUsados) {
        String pathCompleto = folder + nombreOriginal;
        
        // Return inicial si el nombre no esta repetido
        if (nombresUsados.add(pathCompleto)) {
            return pathCompleto;
        }
        
        // Duplicado se separa el nombre de la extension
        int lastDot = nombreOriginal.lastIndexOf('.');
        String nombreBase;
        String extension;
        
        if (lastDot > 0) {
            nombreBase = nombreOriginal.substring(0, lastDot);
            extension = nombreOriginal.substring(lastDot); 
        } else {
            nombreBase = nombreOriginal;
            extension = "";
        }
        
        // Buscar el primer número disponible 
        int contador = 1;
        String nuevoNombre;
        String nuevoPath;
        
        do {
            nuevoNombre = nombreBase + "_" + contador + extension;
            nuevoPath = folder + nuevoNombre;
            contador++;
        } while (!nombresUsados.add(nuevoPath)); 
        
        log.debug("Duplicado detectado: '" + nombreOriginal + "' renombrado a: '" + nuevoNombre + "'");
        
        return nuevoPath;
    }
}
