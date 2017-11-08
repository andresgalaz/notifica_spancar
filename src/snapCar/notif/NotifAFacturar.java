package snapCar.notif;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import prg.glz.FrameworkException;
import snapCar.net.CallWsMail;

public class NotifAFacturar {
    private static Logger    logger         = Logger.getLogger( NotifAFacturar.class );
    private Connection       cnx;
    private static final int DIAS_AL_CIERRE = 2;

    public NotifAFacturar(Connection cnx) {
        this.cnx = cnx;
    }

    @SuppressWarnings({ "rawtypes" })
    public void procesa() {
        try {
            {
                // Crea tabla temporal wMemoryCierreTransf
                CallableStatement call = cnx.prepareCall( "{ call prControlCierreTransferenciaInicio()}" );
                call.execute();
                call.close();
            }
            String cSql = "SELECT w.cPatente \n"
                    + ", DATE_FORMAT(w.dProximoCierre + INTERVAL -1 MONTH, '%d/%m/%Y')    dInicio \n"
                    + ", DATE_FORMAT(w.dProximoCierre                    , '%d/%m/%Y')    dFin \n"
                    + ", u.cEmail, u.cNombre                                              cNombre \n"
                    + ", DATEDIFF( DATE(NOW()) \n"
                    + "          , GREATEST( IFNULL(DATE( w.tUltTransferencia), '0000-00-00') \n"
                    + "                    , IFNULL(DATE( w.tUltViaje        ), '0000-00-00') \n"
                    + "                    , IFNULL(DATE( w.tUltControl      ), '0000-00-00')) ) nDiasNoSincro \n"
                    + " FROM  wMemoryCierreTransf w \n"
                    + "       JOIN tUsuario u ON u.pUsuario = w.fUsuarioTitular \n"
                    + " WHERE DATEDIFF(w.dProximoCierre,NOW()) + CASE WHEN TIMESTAMPDIFF(MONTH,w.dIniVigencia, w.dProximoCierre) <= 1 THEN DAY(LAST_DAY(NOW())) ELSE 0 END = ? \n"
                    + " AND   w.cPoliza is not null \n";
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

                Map<String, String> mReg = new HashMap<String, String>();
                mReg.put( "cPatente", cPatente );
                mReg.put( "dInicio", rsNotif.getString( "dInicio" ) );
                mReg.put( "dFin", rsNotif.getString( "dFin" ) );
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
        } catch (SQLException e) {
            logger.error( "Al lee notificaciones tipo 2", e );
            throw new RuntimeException( e );
        }
    }

}