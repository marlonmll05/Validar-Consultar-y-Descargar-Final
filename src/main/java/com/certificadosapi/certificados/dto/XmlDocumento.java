package com.certificadosapi.certificados.dto;

public class XmlDocumento {
    private final byte[] contenido;
    private final String fileName;
    private final int idMovDoc;

    public XmlDocumento(byte[] contenido, String fileName, int idMovDoc) {
        this.contenido = contenido;
        this.fileName = fileName;
        this.idMovDoc = idMovDoc;
    }

    public byte[] getContenido() {
        return contenido;
    }

    public String getFileName() {
        return fileName;
    }

    public int getIdMovDoc() {
        return idMovDoc;
    }
}
