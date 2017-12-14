package snapCar.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.log4j.Logger;

import prg.glz.FrameworkException;
import prg.util.cnv.ConvertJSON;
import prg.util.cnv.ConvertList;
import prg.util.cnv.ConvertMap;
import snapCar.notif.config.Ambiente;
import snapCar.notif.config.Parametro;

/**
 * <p>
 * Envía Mails utilizando un Webservice REST, el servidor utilizado actualmente es Mandril de MailChimp
 * <p>
 * 
 * @author agalaz
 *
 */
public class CallWsMail {
    private static Logger logger = Logger.getLogger( CallWsMail.class );

    @SuppressWarnings("rawtypes")
    private Map           mapData;
    private String        wsMailUrl;

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public CallWsMail() {
        wsMailUrl = Parametro.get( "ws_mail_url" );
        String wsMailKey = Parametro.get( "ws_mail_key" );
        String wsMailReplyTo = Parametro.get( "ws_mail_reply_to" );
        String wsMailBccAddress = Parametro.get( "ws_mail_bcc_address" );

        if (wsMailUrl == null || wsMailKey == null || wsMailReplyTo == null)
            throw new RuntimeException( "Faltan parámetros de definción del servicio" );

        this.mapData = new HashMap();
        this.mapData.put( "key", wsMailKey );
        this.mapData.put( "template_content", new ArrayList( 0 ) );
        Map message = new HashMap();
        {
            Map headers = new HashMap();
            headers.put( "Reply-To", wsMailReplyTo );
            message.put( "headers", headers );
        }
        message.put( "google_analytics_domains", new String[] { "snapcar.com.ar" } );
        message.put( "bcc_address", wsMailBccAddress );
        message.put( "merge", true );
        message.put( "merge_language", "handlebars" );

        this.mapData.put( "message", message );
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void ejecuta(String cTemplate, String cTag, List<Map> lisTo, Map mVars) throws FrameworkException {
        // Prepara URL
        try {
            URL url = new URL( wsMailUrl );
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty( "Content-Type", "application/json" );
            conn.setRequestMethod( "POST" );

            this.mapData.put( "template_name", cTemplate );
            // Envía parámetros
            Map message = (Map) this.mapData.get( "message" );
            message.put( "to", lisTo );
            message.put( "tags", new String[] { cTag } );

            List<Map> globalMergeVars = new ArrayList<Map>();
            Iterator it = mVars.entrySet().iterator();
            while (it.hasNext()) {
                Entry par = (Entry) it.next();
                Map mElem = new HashMap( 2 );
                mElem.put( "name", par.getKey().toString() );
                mElem.put( "content", par.getValue().toString() );
                globalMergeVars.add( mElem );
            }
            message.put( "global_merge_vars", globalMergeVars );

            conn.setDoOutput( true );
            OutputStream oFOut = conn.getOutputStream();
            String cData = ConvertJSON.MapToString( this.mapData );
            logger.debug( cData );

            oFOut.write( cData.getBytes() );
            oFOut.close();

            InputStream oFIn = null;
            // No OK
            if (conn.getResponseCode() >= 400) {
                oFIn = conn.getErrorStream();
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
            Map mError = null;
            try {
                String cResp = sb.toString();
                if( cResp.startsWith( "[" )){
                    List aResp = ConvertList.fromJsonString( cResp );
                    if(aResp.size()>0)
                        mError = (Map) aResp.get( 0 );
                } else
                    mError = ConvertMap.fromJsonString( sb.toString() );
            } catch (Exception e) {
                throw new FrameworkException( "Error inesperado al procesar respuesta", e );
            }
            if (mError != null && "sent".equals( mError.get( "status" )))
                logger.debug( "Se envió mensaje correctamente" );
            else {
                if(mError.containsKey( "name" ))
                    throw new FrameworkException( mError.get( "name" ) + "\n" + mError.get( "message" ) );
                throw new FrameworkException( ConvertJSON.MapToString( mError ));
            }
        } catch (MalformedURLException e) {
            throw new FrameworkException( "La URL del servidor API es errónea" );
        } catch (IOException e) {
            throw new FrameworkException( "El servidor API no responde" );
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public List<Map> createAddressTo(String cNombre, String cEmail) throws FrameworkException {
        Map m = new HashMap();
        // Ambiente.fixSendEnail cambia el mail de destino para evitar accidentes de envios en ambientes que no sean
        // producción
        m.put( "email", Ambiente.fixSendEnail( cEmail ) );
        m.put( "name", cNombre );
        m.put( "type", "to" );
        List<Map> lisTo = new ArrayList<Map>();
        lisTo.add( m );
        return lisTo;
    }
}