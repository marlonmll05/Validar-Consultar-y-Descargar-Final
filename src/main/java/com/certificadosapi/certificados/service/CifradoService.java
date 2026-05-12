package com.certificadosapi.certificados.service;

import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.*;

import com.certificadosapi.certificados.dto.ArchivoCifrado;

import javax.crypto.*;
import javax.xml.parsers.*;
import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.security.spec.*;
import java.util.Base64;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class CifradoService {

    // Test Key EPS
    private static final String LLAVE_PUBLICA = "<RSAKeyValue><Modulus>zaI6/RkWtKD94hgdoy/LrqI77LKZrK0wvSBtpZh21bTqpOFTzvIeuSNNyN39fcytB7ChP0a/IoeKj6i4wDiq1T3Ozki+ByLAapUyGhPjkHiBw9VVGsBKghkQLtCAjuRdWtcQErHJ46KkvnXJfNt9sN1w+dyzCIVy010m3v0ZyicUR+2CfOtzrkwU7yI5qnRRPZUXTje0jn3rf2NpUnbAVrCSQEAHjKKcfYdQR2aTMJ8drnzC+a4xgVzhPkV23fx9iQXuUEajIK90jH5Jn02YLGqsQpAMjdFV64Pq3A6AYF4agS7EIy6ukASQZ40qK7KhJrMJvBl4+NkGX6gaRseKEvteuXKcnWTl65aGQZJHVEgH9wf9gMwgpnjWxNfSg88hJV8kLcCgX+y+3rNOFIbZ0saKLS6OjA+2ZS6lsRj9wqfkCQ3GmYN2gkj3W9VAtSezZUGbBaN+Qt6JpDfrEmXYCzXUsVQ95KiJTh4OZnQcr9LmazaJJKyxYPlLUPUUSuq48G9xLspvATR20WlMcljAyRInPXrrY5NAdmSv13mB/2w+sBV9cIjof9JrIVYEve7KkRwJXzpf0Jy6hIXy4KIes9rbBqSbWN8HvRyAD9z4d7reKlVkmj1/n2YV3vkg6r47Ca2E43h798ylLH5njfp0QsB7bz9v2AKvFxOl/sWkXfk=</Modulus><Exponent>AQAB</Exponent></RSAKeyValue>";



    public byte[] cifrar(@RequestParam("archivo") MultipartFile archivo) {
        try {
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

    public ArchivoCifrado cifrarArchivo(byte[] contenido, String extension) throws Exception {

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
        boolean encriptar
    ) throws Exception {

        if (encriptar) {

            ArchivoCifrado cif = cifrarArchivo(contenido, extension);

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