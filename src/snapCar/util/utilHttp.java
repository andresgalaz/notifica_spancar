package snapCar.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.text.DecimalFormat;

import prg.util.cnv.ConvertNumber;
import prg.util.cnv.ConvertString;

public class utilHttp {
    public static boolean existeUrl(String cUrl) throws IOException {
        URL u = new URL( cUrl );
        HttpURLConnection huc = (HttpURLConnection) u.openConnection();
        huc.setRequestMethod( "HEAD" );
        huc.connect();
        int code = huc.getResponseCode();
        if (code == 200)
            return true;
        return false;
    }

    public static String read(String cUrlFile) throws IOException {
        StringBuffer sOut = new StringBuffer();
        InputStream in = null;
        BufferedReader br = null;
        try {
            URL u = new URL( cUrlFile );
            URLConnection conexion = u.openConnection();
            br = new BufferedReader( new InputStreamReader( in = conexion.getInputStream() ) );

            String ln;
            while ((ln = br.readLine()) != null)
                sOut.append( ln + '\n' );
            return sOut.toString();
        } finally {
            if (br != null)
                br.close();
            if (in != null)
                in.close();
        }
    }

    /**
     * <p>
     * Trunca un STRING tratando de poner la mayor cantidiad de palabras hasta el máximo largo aceptado</>
     * 
     * @param c
     * @param maxLen
     * @return
     */
    public static String truncaHastaEspacio(String c, int maxLen) {
        if (c == null)
            return c;
        c = c.trim();
        if (c.length() < maxLen)
            return c;
        String[] a = c.split( " " );
        String s1 = null;
        String s2 = a[0];
        for (int i = 1; i < a.length && s2.length() < maxLen; i++) {
            s1 = s2;
            s2 += ' ' + a[i];
        }
        if (s2.length() < maxLen)
            s1 = s2;
        return s1;
    }

    /**
     * <p>
     * Saca el primer nombre de un nombre completo, y pone en mayúsculas la primera letra y el resto en minusuculas
     * </p>
     * 
     * @param c
     * @return
     */
    public static String primerNombre(String c) {
        if (c == null)
            return c;
        c = c.trim().toLowerCase();
        return ConvertString.firstUpcase( c.split( " " )[0] );
    }

    /**
     * <p>
     * Formatea un número con separdor de miles, sin decimales. Importante: Utiliza el idioma definido por defecto, para
     * que salga en formato español se espera:
     * 
     * </p>
     * 
     * <pre>
     * Locale.setDefault( new Locale( "es", "ES" ) );
     * </pre>
     * 
     * @param o
     * @return
     */
    public static String separaMiles(Object o) {
        Integer n = ConvertNumber.toInteger( o );
        if (n == null)
            return null;
        return new DecimalFormat( "#,###,###" ).format( n );
    }
}
