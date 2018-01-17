package snapCar.notif;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.log4j.Logger;

import prg.glz.FrameworkException;
import prg.util.cnv.ConvertDate;
import prg.util.cnv.ConvertException;
import snapCar.net.CallWsMail;

/**
 * <p>
 * Envía mensajes a los clientes dos dís antes del cierre de facturación. Discrimina los que tienen mas de 5 días sin
 * sincronizar.
 * </p>
 * <p>
 * Utiliza la tabla temporal wMemoryCierreTransf que se crea con el procedimiento: prControlCierreTransferenciaInicio
 * </p>
 * 
 * @author agalaz
 *
 */
public class AFacturar {
    private static Logger    logger         = Logger.getLogger( AFacturar.class );
    private Connection       cnx;
    private static final int DIAS_AL_CIERRE = 2;

    public AFacturar(Connection cnx) {
        this.cnx = cnx;
    }

    @SuppressWarnings({ "rawtypes" })
    public void procesa() throws FrameworkException {
        Locale.setDefault( new Locale( "es", "ES" ) );
        SimpleDateFormat fmtLargo = new SimpleDateFormat( "EEEE d 'de' MMMM 'de' YYYY " );
        SimpleDateFormat fmtDia = new SimpleDateFormat( "EEEE" );
        SimpleDateFormat fmtSimple = new SimpleDateFormat( "dd/MM/YYY" );

        try {
            {
                // Crea tabla temporal wMemoryCierreTransf, se usa el parámetro 1, indicando que se quiere los que van a
                // vencer
                CallableStatement call = cnx.prepareCall( "{ call prControlCierreTransferenciaInicioDef(1)}" );
                call.execute();
                call.close();
            }
            String cSql = "SELECT w.cPatente \n"
                    + ", w.dProximoCierreIni dInicio \n"
                    + ", w.dProximoCierreFin dFin \n"
                    + ", w.nDiasNoSincro \n"
                    + ", u.cEmail, u.cNombre \n"
                    + " FROM  wMemoryCierreTransf w \n"
                    + "       JOIN tUsuario u ON u.pUsuario = w.fUsuarioTitular \n"
                    + " WHERE nDiasAlCierre = ? \n"
                    + " AND   w.cPoliza is not null \n"
                    + " AND   w.bVigente = '1' \n";
            PreparedStatement psSql = cnx.prepareStatement( cSql );
            psSql.setInt( 1, DIAS_AL_CIERRE );
            ResultSet rsNotif = psSql.executeQuery();
            // Prepara Webservice envía Mails
            CallWsMail callMail = new CallWsMail();
            while (rsNotif.next()) {
                int nDiasNoSincro = rsNotif.getInt( "nDiasNoSincro" );
                String cPatente = rsNotif.getString( "cPatente" );
                String cEmail = rsNotif.getString( "cEmail" );
                String cNombre = rsNotif.getString( "cNombre" );
                String cPrimerNombre = cNombre.split( " " )[0];

                List<Map> to = callMail.createAddressTo( cNombre, cEmail );

                Date dInicio = ConvertDate.toDate( rsNotif.getDate( "dInicio" ) );
                Date dFin = ConvertDate.toDate( rsNotif.getDate( "dFin" ) );

                Map<String, String> mReg = new HashMap<String, String>();
                mReg.put( "cPatente", cPatente );
                mReg.put( "dInicio", fmtSimple.format( dInicio ) );
                mReg.put( "dFin", fmtSimple.format( dFin ) );
                mReg.put( "cFecCierre", fmtLargo.format( dFin ) );
                mReg.put( "cDiaCierre", fmtDia.format( dFin ) );
                mReg.put( "cNombre", cPrimerNombre );
                mReg.put( "nDiasNoSincro", String.valueOf( nDiasNoSincro ) );

                try {
                    if (nDiasNoSincro >= 5) {
                        callMail.ejecuta( "a_facturar_01", "facturar_1", to, mReg );
                    } else {
                        callMail.ejecuta( "a_facturar_02", "facturar_2", to, mReg );
                    }
                } catch (FrameworkException e) {
                    logger.error( "Al enviar mail a " + cEmail + "por la patente " + cPatente, e );
                }
            }
            rsNotif.close();
            psSql.close();
        } catch (ConvertException e) {
            logger.error( "Al convertir fechas de inicio y fin", e );
            throw new RuntimeException( e );
        } catch (SQLException e) {
            logger.error( "Al lee notificaciones tipo 2", e );
            throw new RuntimeException( e );
        }
    }

}
