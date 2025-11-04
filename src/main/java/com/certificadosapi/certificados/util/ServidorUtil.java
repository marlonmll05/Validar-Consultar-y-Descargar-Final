package com.certificadosapi.certificados.util;

import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.http.config.Registry;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.NTCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.impl.auth.NTLMSchemeFactory;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;

@Component
public class ServidorUtil {

    //Metodo para leer el servidor de registro
    public String getServerFromRegistry() throws Exception {

        String registryPath = "SOFTWARE\\VB and VBA Program Settings\\Asclepius\\Administrativo";
        String valueName = "Servidor";

        try{
            return Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, registryPath, valueName);
        }catch(Exception e){
            throw new Exception("Error al leer el servidor de registro" + e.getMessage(), e);
        }
    }

    //Metodo para crear un template inseguro para enviar al ministerio
    public RestTemplate crearRestTemplateInseguro() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public void checkClientTrusted(X509Certificate[] xcs, String s) {}
                    public void checkServerTrusted(X509Certificate[] xcs, String s) {}
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);

            return new RestTemplate();
        } catch (Exception e) {
            throw new RuntimeException("Error al crear RestTemplate inseguro: " + e.getMessage(), e);
        }
    }

    public CloseableHttpClient crearHttpClientConNTLM() {
        String dominio = "servergihos";
        String usuario = "Consulta";
        String contrasena = "Informes.01";
        
        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        NTCredentials ntCredentials = new NTCredentials(usuario, contrasena, null, dominio);
        credentialsProvider.setCredentials(AuthScope.ANY, ntCredentials);

        Registry<org.apache.http.auth.AuthSchemeProvider> authSchemeRegistry = 
            RegistryBuilder.<org.apache.http.auth.AuthSchemeProvider>create()
                .register(AuthSchemes.NTLM, new NTLMSchemeFactory())
                .build();

        RequestConfig requestConfig = RequestConfig.custom()
                .setAuthenticationEnabled(true)
                .setTargetPreferredAuthSchemes(Arrays.asList(AuthSchemes.NTLM))
                .setProxyPreferredAuthSchemes(Arrays.asList(AuthSchemes.NTLM))
                .build();

        return HttpClients.custom()
                .setDefaultCredentialsProvider(credentialsProvider)
                .setDefaultAuthSchemeRegistry(authSchemeRegistry)
                .setDefaultRequestConfig(requestConfig)
                .build();
    }
}
