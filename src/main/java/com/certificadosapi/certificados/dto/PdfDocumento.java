package com.certificadosapi.certificados.dto;

/**
 * DTO inmutable que representa un documento PDF con su contenido binario y metadatos
 * 
 * 
 * Esta clase encapsula un archivo PDF junto con su nombre y su tipo MIME
 * Se utiliza principalmente para transferir documentos PDF desde la base de datos hacia los
 * endpoints de AtencionesController.
 */


public class PdfDocumento {

    /** Contenido binario del PDF (VARBINARY) */
    private final byte[] contenido;

    /** Nombre del Archivo PDF */
    private final String nombre;

    /** Tipo MIME del documento (generalmente "application/pdf") */
    private final String contentType;



    /**
     * Constructor para crear un nuevo documento PDF.
     * 
     * @param contenido el contenido binario del PDF
     * @param nombre el nombre del archivo PDF
     * @param contentType tipo MIME del documento
     */
    public PdfDocumento(byte[] contenido, String nombre, String contentType) {
        this.contenido = contenido;
        this.nombre = nombre;
        this.contentType = contentType;
    }

    /**
     * Obtiene el contenido binario del documento PDF.
     * 
     * @return Array de bytes con el contenido del PDF
     */
    public byte[] getContenido() {
        return contenido;
    }

    /**
     * Obtiene el nombre del archivo PDF.
     * 
     * @return El nombre del archivo
     */
    public String getNombre() {
        return nombre;
    }

    /**
     * Obtiene el tipo MIME del documento.
     * 
     * @return El content type del documento (ej: "application/pdf")
     */
    public String getContentType() {
        return contentType;
    }
}
