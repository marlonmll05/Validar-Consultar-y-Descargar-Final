package com.certificadosapi.certificados.dto;


/**
 * DTO inmutable que representa un documento XML con su contenido binario y metadatos.
 * 
 * Esta clase encapsula un archivo XML junto con su nombre de archivo y el identificador
 * del movimiento del documento asociado. Utilizada para transferir documentos XML
 * desde la base de datos hacia los servicios de procesamiento.
 */

public class XmlDocumento {

    /** Contenido binario del documento XML */
    private final byte[] contenido;

    /** Nombre del archivo XML */
    private final String fileName;

    /** Identificador del movimiento del documento en la base de datos */
    private final int idMovDoc;


    /**
     * Constructor para crear un nuevo documento XML.
     * 
     * @param contenido El contenido binario del XML
     * @param fileName El nombre del archivo XML
     * @param idMovDoc El ID de la factura 
     */
    public XmlDocumento(byte[] contenido, String fileName, int idMovDoc) {
        this.contenido = contenido;
        this.fileName = fileName;
        this.idMovDoc = idMovDoc;
    }

    /**
     * Obtiene el contenido binario del documento XML.
     * 
     * @return Array de bytes con el contenido del XML
     */
    public byte[] getContenido() {
        return contenido;
    }

    /**
     * Obtiene el nombre del archivo XML.
     * 
     * @return El nombre del archivo
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * Obtiene el ID de la factura
     * 
     * @return El ID
     */
    public int getIdMovDoc() {
        return idMovDoc;
    }
}
