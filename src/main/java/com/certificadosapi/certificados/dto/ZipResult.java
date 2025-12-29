package com.certificadosapi.certificados.dto;


/**
 * DTO que representa el resultado de una operación de compresión ZIP.
 * 
 * Esta clase encapsula el contenido binario de un archivo ZIP junto con su nombre,
 * utilizada para retornar archivos comprimidos generados dinámicamente que contienen
 * múltiples documentos (PDFs, XMLs, TXT).
 */
public class ZipResult {

    /** Contenido binario del archivo ZIP */
    private byte[] zipBytes;

    /** Nombre del archivo ZIP */
    private String fileName;


    /**
     * Constructor para crear un nuevo resultado ZIP.
     * 
     * @param zipBytes El contenido binario del archivo ZIP
     * @param fileName El nombre del archivo ZIP
     */
    public ZipResult(byte[] zipBytes, String fileName) {
        this.zipBytes = zipBytes;
        this.fileName = fileName;
    }



    /**
     * Obtiene el contenido binario del archivo ZIP.
     * 
     * @return Array de bytes con el contenido del ZIP
     */
    public byte[] getZipBytes() {
        return zipBytes;
    }

    /**
     * Obtiene el nombre del archivo ZIP.
     * 
     * @return El nombre del archivo
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * Establece el contenido binario del archivo ZIP.
     * 
     * @param zipBytes El nuevo contenido del archivo ZIP
     */
    public void setZipBytes(byte[] zipBytes) {
        this.zipBytes = zipBytes;
    }

    /**
     * Establece el nombre del archivo ZIP.
     * 
     * @param fileName El nuevo nombre del archivo
     */
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }


    
}
