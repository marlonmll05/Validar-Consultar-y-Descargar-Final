package com.certificadosapi.certificados.dto;

public class ArchivoCifrado {

    private byte[] enc;
    private byte[] key;

    public ArchivoCifrado(byte[] enc, byte[] key){

        this.enc = enc;
        this.key = key;
    }

    public byte[] getEnc() {
        return enc;
    }

    public byte[] getKey() {
        return key;

    }
}
