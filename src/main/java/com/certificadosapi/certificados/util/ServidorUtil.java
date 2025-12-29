package com.certificadosapi.certificados.util;

import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.sql.SQLException;
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

import com.certificadosapi.certificados.config.DatabaseConfig;

@Component
public class ServidorUtil {

    private final DatabaseConfig databaseConfig;

    public ServidorUtil(DatabaseConfig databaseConfig){
        this.databaseConfig = databaseConfig;
    }


/**
 * Utilidad encargada de la configuración de conexiones hacia servidores externos.
 *
 * Esta clase provee métodos para:
 * 
 *   Crear un {@link RestTemplate} con validación SSL deshabilitada (uso controlado).
 *   Crear un {@link CloseableHttpClient} configurado con autenticación NTLM.

 *
 * Advertencia: El uso de SSL inseguro debe limitarse exclusivamente
 * a entornos controlados (QA, integración con servicios legacy o ministerios).
 */

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

    public CloseableHttpClient crearHttpClientConNTLM() throws SQLException {
        String dominio = "servergihos";
        String usuario = databaseConfig.parametrosServidor(3);
        String contrasena = databaseConfig.parametrosServidor(4);
        
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
