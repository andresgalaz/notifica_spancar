package snapCar.notif.config;

import java.util.Map;

import prg.glz.FrameworkException;
import prg.util.cnv.ConvertString;

public class Ambiente {
    // private static Logger logger = Logger.getLogger( Ambiente.class );
    public static String AMBIENTE;

    public static String fixSendEnail(String cEmail) throws FrameworkException {
        if (AMBIENTE == null)
            inicio();
        if ("PROD".equals( AMBIENTE ))
            return cEmail;
        return Parametro.get( "mail_test" );
    }

    public static String getNombre() throws FrameworkException {
        if (AMBIENTE == null)
            inicio();
        return AMBIENTE;
    }

    private static void inicio() throws FrameworkException {
        Map<String, String> env = System.getenv();
        AMBIENTE = env.get( "WSAPI_AMBIENTE" );
        if (ConvertString.isEmpty( AMBIENTE ))
            throw new FrameworkException( "No está declarada la variable de ambiente: WSAPI_AMBIENTE" );
    }

}
