package snapCar.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import prg.glz.FrameworkException;
import prg.util.cnv.ConvertJSON;

public class CallPushService {
    private static final String URL_API = "https://us-central1-snapcar-api.cloudfunctions.net/pushService";

    static {
        TrustManager[] trustAllCertificates = new TrustManager[] {
                new X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }

                    public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                        // No need to implement.
                    }

                    public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                        // No need to implement.
                    }
                }
        };

        try {
            SSLContext sc = SSLContext.getInstance( "SSL" );
            sc.init( null, trustAllCertificates, new SecureRandom() );
            HttpsURLConnection.setDefaultSSLSocketFactory( sc.getSocketFactory() );
        } catch (Exception e) {
            throw new ExceptionInInitializerError( e );
        }
    }

    public CallPushService() throws FrameworkException {
        TimeZone.setDefault( TimeZone.getTimeZone( "GMT" ) );
        // Permite usar HTTPS sin bajar el certificado localmente
    }

    /**
     * <p>
     * Envía un mensaje vía PUSH
     * </p>
     * <ul>
     * <li>nIdUsuario: obligatorio</li>
     * <li>cTitulo: obligatorio</li>
     * <li>cMensaje: obligatorio</li>
     * <li>cPantalla: opcional</li>
     * <li>nIdViaje: opcional</li>
     * <li>nIdEvento: opcional</li>
     * </ul>
     * 
     * @throws FrameworkException
     */
    public String envia(Integer nIdUsuario, String cTitulo, String cMensaje, String cPantalla, Integer nIdViaje, Integer nIdEvento) throws FrameworkException {
        Map<String, Object> mParams = new HashMap<String, Object>();
        mParams.put( "id", nIdUsuario );
        mParams.put( "titulo", cTitulo );
        mParams.put( "mensaje", cMensaje );
        mParams.put( "screen", cPantalla );
        mParams.put( "idViaje", nIdViaje );
        mParams.put( "idEvento", nIdEvento );
        return ejecutaString( URL_API, "POST", mParams );
    }

    private String ejecutaString(String cUrl, String cMetodo, Map<String, Object> mParams) throws FrameworkException {
        // Prepara URL
        HttpURLConnection conn = null;
        try {
            URL url = new URL( cUrl );
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod( cMetodo );
            conn.setRequestProperty( "Content-Type", "application/json; charset=UTF-8" );

            // Envía parámetros
            if (mParams != null && !mParams.isEmpty()) {
                // Arma parámetros JSON para enviar, si viene el objeto "associations", se utiliza, sino se crea un
                // objeto nuevo
                conn.setDoOutput( true );
                OutputStream oFOut = conn.getOutputStream();
                oFOut.write( ConvertJSON.MapToString( mParams ).getBytes( "UTF-8" ) );
                oFOut.close();
            }
            InputStream oFIn = null;
            // No OK
            if (conn.getResponseCode() >= 400) {
                oFIn = conn.getErrorStream();
                if (oFIn == null)
                    oFIn = conn.getInputStream();
            } else {
                oFIn = conn.getInputStream();
            }
            // Lee respuesta
            StringBuffer sb = new StringBuffer();
            byte[] bResp = new byte[20000];
            int len;
            while ((len = oFIn.read( bResp )) > 0) {
                sb.append( new String( bResp, 0, len, "UTF-8" ) );
            }
            oFIn.close();
            conn.disconnect();
            return sb.toString();
        } catch (MalformedURLException e) {
            throw new FrameworkException( "La URL del servidor API es errónea" );
        } catch (IOException e) {
            throw new FrameworkException( "El servidor API no responde:" + e.getMessage(), e );
        }
    }
}
