package com.certificadosapi.certificados.model;

public class ZipResult {

    private byte[] zipBytes;
    private String fileName;

    public ZipResult(byte[] zipBytes, String fileName) {
        this.zipBytes = zipBytes;
        this.fileName = fileName;
    }

    public byte[] getZipBytes() {
        return zipBytes;
    }

    public String getFileName() {
        return fileName;
    }

    public void setZipBytes(byte[] zipBytes) {
        this.zipBytes = zipBytes;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }


    
}
