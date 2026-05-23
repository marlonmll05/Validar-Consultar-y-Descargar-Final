package com.certificadosapi.certificados.service;

import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.*;

import com.certificadosapi.certificados.config.DatabaseConfig;
import com.certificadosapi.certificados.dto.ArchivoCifrado;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.*;
import javax.xml.parsers.*;
import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.security.spec.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class CifradoService {

    
    private static final Logger log = LoggerFactory.getLogger(CifradoService.class);

    private final DatabaseConfig databaseConfig;


    /**
     * Constructor de CifradoService con inyección de dependencias.
     * 
     * @param databaseConfig Objeto de configuración para las conexiones a base de datos
     */

    @Autowired
    public CifradoService(DatabaseConfig databaseConfig) {
        this.databaseConfig = databaseConfig;
    }

    public String obtenerLlavePublica(Integer idTerceroKey) {

        log.info("Iniciando obtenerLlavePublica");
        log.debug("Parámetro recibido: idTerceroKey={}", idTerceroKey);

        if (idTerceroKey == null) {
            throw new IllegalArgumentException("El parámetro 'idTerceroKey' es requerido y no puede estar vacío");
        }

        try {
            String connectionUrl = databaseConfig.getConnectionUrl("IPSoft100_ST");
            log.debug("Conectando a BD: {}", connectionUrl);

            try (Connection conn = DriverManager.getConnection(connectionUrl)) {

                String sql = "SELECT Key_Encryp_publ FROM dbo.Terceros_Clientes WHERE IdTerceroKey = ?";
                log.debug("SQL ejecutado: {}", sql);

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {

                    stmt.setInt(1, idTerceroKey);
                    log.debug("Parámetro SQL: p1={}", idTerceroKey);

                    try (ResultSet rs = stmt.executeQuery()) {

                        if (rs.next()) {
                            String llavePublica = rs.getString("Key_Encryp_publ");

                            log.info("Llave pública obtenida para idTerceroKey={}", idTerceroKey);

                            return llavePublica;
                            
                        }  else {
                            log.warn("No se encontró llave pública para idTerceroKey={}", idTerceroKey);
                            throw new IllegalArgumentException("No se encontró un cliente con el IdTerceroKey especificado.");
                        }
                    }
                }
            }

        } catch (SQLException e) {
            log.error("Error SQL en obtenerLlavePublica: {}", e.getMessage());
            throw new RuntimeException("Error de base de datos: " + e.getMessage());

        } catch (Exception e) {
            log.error("Error inesperado en obtenerLlavePublica: {}", e.getMessage());
            throw new RuntimeException("Error al obtener la llave pública: " + e.getMessage());
        }
    }

    public byte[] cifrar(MultipartFile archivo, Integer IdTerceroKey) {
        try {

            String LLAVE_PUBLICA = obtenerLlavePublica(IdTerceroKey);

            byte[] contenido = archivo.getBytes();
            String nombre = archivo.getOriginalFilename();
            @SuppressWarnings("null")
            String extension = nombre.substring(nombre.lastIndexOf("."));

            // AES
            KeyGenerator kg = KeyGenerator.getInstance("AES");
            kg.init(256);
            SecretKey llaveAES = kg.generateKey();

            Cipher aes = Cipher.getInstance("AES/CBC/PKCS5Padding");
            aes.init(Cipher.ENCRYPT_MODE, llaveAES);
            byte[] iv = aes.getIV();
            byte[] cifrado = aes.doFinal(contenido);

            // RSA
            byte[] llaveCifrada = cifrarRSA(llaveAES.getEncoded(), LLAVE_PUBLICA);

            // Guardar .enc
            byte[] extBytes = extension.getBytes("UTF-8");
            byte[] longExt = new byte[]{(byte) extBytes.length, 0, 0, 0};

            ByteArrayOutputStream enc = new ByteArrayOutputStream();
            enc.write(longExt);
            enc.write(extBytes);
            enc.write(iv);
            enc.write(cifrado);
            enc.close();

            ByteArrayOutputStream zipStream = new ByteArrayOutputStream();
            ZipOutputStream zip = new ZipOutputStream(zipStream);
            
            zip.putNextEntry(new ZipEntry(nombre + ".enc"));
            zip.write(enc.toByteArray());
            zip.closeEntry();
            
            zip.putNextEntry(new ZipEntry(nombre + ".enc.key"));
            zip.write(llaveCifrada);
            zip.closeEntry();
            zip.close();

            return zipStream.toByteArray();

        } catch(Exception e){
            throw new RuntimeException("Error cifrando", e);
        } 
    }

    public ArchivoCifrado cifrarArchivo(byte[] contenido, String extension, Integer IdTerceroKey) throws Exception {


        String LLAVE_PUBLICA = obtenerLlavePublica(IdTerceroKey);

        // AES
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(256);
        SecretKey llaveAES = kg.generateKey();

        Cipher aes = Cipher.getInstance("AES/CBC/PKCS5Padding");
        aes.init(Cipher.ENCRYPT_MODE, llaveAES);
        byte[] iv = aes.getIV();
        byte[] cifrado = aes.doFinal(contenido);

        // RSA
        byte[] llaveCifrada = cifrarRSA(llaveAES.getEncoded(), LLAVE_PUBLICA);

        // .enc
        byte[] extBytes = extension.getBytes("UTF-8");
        byte[] longExt = new byte[]{(byte) extBytes.length, 0, 0, 0};

        ByteArrayOutputStream enc = new ByteArrayOutputStream();
        enc.write(longExt);
        enc.write(extBytes);
        enc.write(iv);
        enc.write(cifrado);

        ArchivoCifrado result = new ArchivoCifrado(enc.toByteArray(), llaveCifrada);

        return result;
    }

    public void agregarArchivoZip(
        ZipOutputStream zos,
        String ruta,
        byte[] contenido,
        String extension,
        boolean encriptar, 
        Integer IdTerceroKey
    ) throws Exception {

        if (encriptar) {

            ArchivoCifrado cif = cifrarArchivo(contenido, extension, IdTerceroKey);

            // archivo cifrado
            zos.putNextEntry(new ZipEntry(ruta + ".enc"));
            zos.write(cif.getEnc());
            zos.closeEntry();

            // llave
            zos.putNextEntry(new ZipEntry(ruta + ".enc.key"));
            zos.write(cif.getKey());
            zos.closeEntry();

        } else {

            zos.putNextEntry(new ZipEntry(ruta));
            zos.write(contenido);
            zos.closeEntry();
        }
    }

    // ===================== HELPERS RSA =====================
    private byte[] cifrarRSA(byte[] datos, String xmlLlave) throws Exception {
        PublicKey llave = cargarPublica(xmlLlave);
        Cipher rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        rsa.init(Cipher.ENCRYPT_MODE, llave);
        return rsa.doFinal(datos);
    }

    private PublicKey cargarPublica(String xml) throws Exception {
        Document doc = parsearXml(xml);
        byte[] mod = Base64.getDecoder().decode(doc.getElementsByTagName("Modulus").item(0).getTextContent());
        byte[] exp = Base64.getDecoder().decode(doc.getElementsByTagName("Exponent").item(0).getTextContent());
        return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(new BigInteger(1, mod), new BigInteger(1, exp)));
    }

    private Document parsearXml(String xml) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new java.io.ByteArrayInputStream(xml.getBytes()));
    }
    
}