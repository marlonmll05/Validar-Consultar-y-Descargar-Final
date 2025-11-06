package com.certificadosapi.certificados.dto;

public class PdfDocumento {
    private final byte[] contenido;
    private final String nombre;
    private final String contentType;

    public PdfDocumento(byte[] contenido, String nombre, String contentType) {
        this.contenido = contenido;
        this.nombre = nombre;
        this.contentType = contentType;
    }

    public byte[] getContenido() {
        return contenido;
    }

    public String getNombre() {
        return nombre;
    }

    public String getContentType() {
        return contentType;
    }
}
