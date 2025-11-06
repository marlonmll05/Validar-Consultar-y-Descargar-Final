package com.certificadosapi.certificados.service.atenciones;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import com.certificadosapi.certificados.config.DatabaseConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.DataFormatReaders;

@Service
public class ExportarService {

    private DatabaseConfig databaseConfig;

    @Autowired
    public ExportarService(DatabaseConfig databaseConfig){
        this.databaseConfig = databaseConfig;
    }

    public byte[] exportarCuentaCobro(
        String numeroCuentaCobro,
        MultiValueMap<String, MultipartFile> fileParts) throws Exception {
    
        final Pattern ILLEGAL = Pattern.compile("[\\\\/:*?\"<>|]+");
        String sanitizeCuenta = ILLEGAL.matcher(numeroCuentaCobro == null ? "" : numeroCuentaCobro).replaceAll("_").trim();
        String folderCuenta = (sanitizeCuenta.isBlank() ? "SIN_NUMERO" : sanitizeCuenta) + "/";

        record PartItem(String tipo, MultipartFile file) {}
        Map<String, List<PartItem>> porNfact = new LinkedHashMap<>();

        for (Map.Entry<String, List<MultipartFile>> e : fileParts.entrySet()) {
            String key = e.getKey();
            if (key == null || key.isBlank()) continue;

            int idx = key.indexOf('_');
            if (idx <= 0 || idx >= key.length() - 1) {
                continue;
            }

            String nFact = key.substring(0, idx).trim();
            String tipo  = key.substring(idx + 1).trim();
            if (!( "xml".equals(tipo) || "jsonFactura".equals(tipo) || "pdfs".equals(tipo) )) {
                continue;
            }

            List<MultipartFile> files = e.getValue();
            if (files == null || files.isEmpty()) continue;

            for (MultipartFile mf : files) {
                if (mf == null || mf.isEmpty()) continue;
                porNfact.computeIfAbsent(nFact, k -> new ArrayList<>()).add(new PartItem(tipo, mf));
            }
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int archivosAgregados = 0;

        Set<String> nombresUsados = new HashSet<>();

        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (Map.Entry<String, List<PartItem>> entry : porNfact.entrySet()) {
                String nFact = entry.getKey();
                String sanitizeN = ILLEGAL.matcher(nFact == null ? "" : nFact).replaceAll("_").trim();
                String folderFactura = folderCuenta + (sanitizeN.isBlank() ? "SIN_FACTURA" : sanitizeN) + "/";

                // Clasificar por tipo
                List<MultipartFile> xmls  = new ArrayList<>();
                List<MultipartFile> jsons = new ArrayList<>();
                List<MultipartFile> pdfs  = new ArrayList<>();
                for (PartItem it : entry.getValue()) {
                    String t = it.tipo();
                    if ("xml".equals(t)) xmls.add(it.file());
                    else if ("jsonFactura".equals(t)) jsons.add(it.file());
                    else if ("pdfs".equals(t)) pdfs.add(it.file());
                }

                Integer idMovDoc = null;
                String numdoc = null, idEmpresaGrupo = null;

                try (Connection c = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoft100_ST"));
                     PreparedStatement ps = c.prepareStatement("SELECT IdMovDoc FROM FacturaFinal WHERE NFact = ?")) {
                    ps.setString(1, nFact);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) idMovDoc = rs.getInt(1);
                    }
                } catch (Exception ex) {
                    System.err.println("Warn IdMovDoc " + nFact + ": " + ex.getMessage());
                }

                if (idMovDoc != null) {
                    try (Connection c = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoftFinanciero_ST"))) {
                        try (PreparedStatement ps = c.prepareStatement(
                                "SELECT Prefijo, Numdoc FROM MovimientoDocumentos WHERE IdMovDoc = ?")) {
                            ps.setInt(1, idMovDoc);
                            try (ResultSet rs = ps.executeQuery()) {
                                if (rs.next()) {
                                    numdoc  = rs.getString("Numdoc");
                                }
                            }
                        }
                        try (PreparedStatement ps = c.prepareStatement(
                                "SELECT E.IdEmpresaGrupo " +
                                        "FROM MovimientoDocumentos M " +
                                        "INNER JOIN Empresas E ON E.IdEmpresaKey = M.IdEmpresaKey " +
                                        "WHERE M.IdMovDoc = ?")) {
                            ps.setInt(1, idMovDoc);
                            try (ResultSet rs = ps.executeQuery()) {
                                if (rs.next()) idEmpresaGrupo = rs.getString("IdEmpresaGrupo");
                            }
                        }
                    } catch (Exception ex) {
                        System.err.println("Warn Financiero " + nFact + ": " + ex.getMessage());
                    }
                }

                // Respuesta validador
                String respuestaValidador = null;
                try (Connection c = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoft100_ST"))) {
                    String sql = "SELECT MensajeRespuesta FROM RIPS_RespuestaApi " +
                            "WHERE LTRIM(RTRIM(NFact)) = LTRIM(RTRIM(?))";
                    try (PreparedStatement ps = c.prepareStatement(sql)) {
                        ps.setString(1, nFact);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                respuestaValidador = rs.getString("MensajeRespuesta");
                            }
                        }
                    }
                } catch (Exception ex) {
                    System.err.println("Warn Validador " + nFact + ": " + ex.getMessage());
                }

                // === XML ===
                if (!xmls.isEmpty()) {
                    MultipartFile xml = xmls.get(0);
                    String xmlFileName;
                    if (idEmpresaGrupo != null && numdoc != null) {
                        String yearSuffix = String.valueOf(java.time.LocalDate.now().getYear()).substring(2);
                        String formattedNumdoc;
                        try {
                            int num = Integer.parseInt(numdoc);
                            formattedNumdoc = String.format("%08d", num);
                        } catch (NumberFormatException nfe) {
                            formattedNumdoc = numdoc;
                        }
                        xmlFileName = "ad0" + idEmpresaGrupo + "000" + yearSuffix + formattedNumdoc + ".xml";
                    } else {
                        String original = xml.getOriginalFilename();
                        xmlFileName = (original != null && !original.isBlank())
                                ? ILLEGAL.matcher(original).replaceAll("_").trim()
                                : ("Factura_" + (sanitizeN.isBlank() ? "SIN_FACTURA" : sanitizeN) + ".xml");
                    }

                    String xmlPath = obtenerNombreUnico(folderFactura, xmlFileName, nombresUsados);

                    ZipEntry xmlEntry = new ZipEntry(xmlPath);
                    zos.putNextEntry(xmlEntry);
                    zos.write(xml.getBytes());
                    zos.closeEntry();
                    archivosAgregados++;
                } else {
                    System.out.println("⚠️ No llegó XML para nFact " + nFact + ", se omite esta factura.");
                    continue;
                }

                // === JSON ===
                for (MultipartFile jf : jsons) {
                    String nombre = jf.getOriginalFilename();
                    nombre = (nombre != null && !nombre.isBlank())
                            ? ILLEGAL.matcher(nombre).replaceAll("_").trim()
                            : ("Factura_" + (sanitizeN.isBlank() ? "SIN_FACTURA" : sanitizeN) + ".json");

                    String jsonPath = obtenerNombreUnico(folderFactura, nombre, nombresUsados);

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
                    nombre = (nombre != null && !nombre.isBlank())
                            ? ILLEGAL.matcher(nombre).replaceAll("_").trim()
                            : "Documento.pdf";

                    String pdfPath = obtenerNombreUnico(folderFactura, nombre, nombresUsados);

                    ZipEntry pdfEntry = new ZipEntry(pdfPath);
                    zos.putNextEntry(pdfEntry);
                    zos.write(pdf.getBytes());
                    zos.closeEntry();
                    archivosAgregados++;
                }

                // === TXT ===
                if (respuestaValidador != null && !respuestaValidador.isBlank()) {
                    String procesoId = "";
                    try {
                        ObjectMapper om = new ObjectMapper();
                        JsonNode node = om.readTree(respuestaValidador);
                        if (node.has("ProcesoId")) procesoId = node.get("ProcesoId").asText("");
                    } catch (Exception ex) {
                        System.out.println("Warn parse ProcesoId " + nFact + ": " + ex.getMessage());
                    }
                    String safeProc = ILLEGAL.matcher(procesoId == null ? "" : procesoId).replaceAll("_").trim();
                    String nombreTxt = "ResultadosMSPS_" + (sanitizeN.isBlank() ? "SIN_FACTURA" : sanitizeN)
                            + (safeProc.isBlank() ? "" : ("_ID" + safeProc))
                            + "_A_CUV.txt";

                    String txtPath = obtenerNombreUnico(folderFactura, nombreTxt, nombresUsados);

                    ZipEntry txtEntry = new ZipEntry(txtPath);
                    zos.putNextEntry(txtEntry);
                    zos.write(respuestaValidador.getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();
                    archivosAgregados++;
                }
            }
        }

        if (archivosAgregados == 0) {
            throw new Exception("No se encontraron archivos para generar el ZIP");
        }

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
        
        System.out.println("Duplicado detectado: '" + nombreOriginal + "' → renombrado a '" + nuevoNombre + "'");
        
        return nuevoPath;
    }

}
