package snapCar.notif.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.apache.log4j.Logger;

import prg.glz.FrameworkException;
import prg.util.cnv.ConvertFile;
import prg.util.cnv.ConvertNumber;

public class Parametro {
    private static Logger     logger = Logger.getLogger( Parametro.class );
    private static Properties prop;

    public static String get(String llave) {
        if (prop == null)
            inicio();
        return prop.getProperty( llave );
    }

    public static int getInt(String llave) {
        if (prop == null)
            inicio();
        String c = prop.getProperty( llave );
        Integer n = null;
        try {
            n = ConvertNumber.toInteger( c );
        } catch (NumberFormatException e) {
        }
        if (n == null)
            return 0;
        return n;
    }

    private static void inicio() {
        if (prop != null)
            return;

        String cAmbiente;
        try {
            cAmbiente = "_" + Ambiente.getNombre().toLowerCase();
        } catch (FrameworkException e) {
            cAmbiente = "";
        }
        prop = new Properties();

        InputStream input = null;
        try {
            input = new FileInputStream( "config" + cAmbiente + ".properties" );

            // load a properties file
            prop.load( input );

            // get the property value and print it out
            logger.info( "db_url     :" + prop.getProperty( "db_url" ) );
            logger.info( "db_class   :" + prop.getProperty( "db_class" ) );
            logger.info( "db_usuario :" + prop.getProperty( "db_usuario" ) );
            logger.info( "db_tipo    :" + prop.getProperty( "db_tipo" ) );

            logger.info( "smtp_host     :" + prop.getProperty( "smtp_host" ) );
            logger.info( "smtp_port     :" + prop.getProperty( "smtp_port" ) );
            logger.info( "smtp_ssl      :" + prop.getProperty( "smtp_ssl" ) );
            logger.info( "smtp_user     :" + prop.getProperty( "smtp_user" ) );
            logger.info( "smtp_password :" + prop.getProperty( "smtp_password" ) );

            logger.info( "mail_from :" + prop.getProperty( "mail_from" ) );
            logger.info( "mail_admin :" + prop.getProperty( "mail_admin" ) );

            logger.info( "ws_mail_url         :" + prop.getProperty( "ws_mail_url" ) );
            logger.info( "ws_mail_key         :" + prop.getProperty( "ws_mail_key" ) );
            logger.info( "ws_mail_reply_to    :" + prop.getProperty( "ws_mail_reply_to" ) );
            logger.info( "ws_mail_bcc_address1:" + prop.getProperty( "ws_mail_bcc_address1" ) );
            logger.info( "ws_mail_bcc_address2:" + prop.getProperty( "ws_mail_bcc_address2" ) );

            logger.info( "file_repos :" + prop.getProperty( "file_repos" ) );
            logger.info( "factura_svg :" + prop.getProperty( "factura_svg" ) );
            logger.info( "mail_test :" + prop.getProperty( "mail_test" ) );

        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            ConvertFile.close( input );
        }
    }

}
