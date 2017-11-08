package snapCar.notif;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;

import org.apache.log4j.Logger;

public class NotifAFacturar {
    private static Logger    logger         = Logger.getLogger( NotifAFacturar.class );
    private Connection       cnx;
    private static final int DIAS_AL_CIERRE = 2;

    public NotifAFacturar(Connection cnx) {
        this.cnx = cnx;
    }

    public void procesa() {
        try {

            {
                // Crea tabla temporal wMemoryCierreTransf
                CallableStatement call = cnx.prepareCall( "{ call prControlCierreTransferenciaInicio()}" );
                call.execute();
                call.close();
            }
            String cSql = "SELECT pVehiculo, fUsuarioTitular, cPatente \n"
                    + ", GREATEST( IFNULL(DATE( tUltTransferencia), '0000-00-00') \n"
                    + "          , IFNULL(DATE( tUltViaje        ), '0000-00-00') \n"
                    + "          , IFNULL(DATE( tUltControl      ), '0000-00-00')) dUltimoDato \n"
                    + ", DATEDIFF( DATE(NOW()) \n"
                    + "          , GREATEST( IFNULL(DATE( tUltTransferencia), '0000-00-00') \n"
                    + "                    , IFNULL(DATE( tUltViaje        ), '0000-00-00') \n"
                    + "                    , IFNULL(DATE( tUltControl      ), '0000-00-00')) ) nDiasSinSincro \n"
                    + " FROM  wMemoryCierreTransf w \n"
                    + " WHERE DATEDIFF(w.dProximoCierre,NOW()) + CASE WHEN TIMESTAMPDIFF(MONTH,w.dIniVigencia, w.dProximoCierre) <= 1 THEN DAY(LAST_DAY(NOW())) ELSE 0 END = ? \n"
                    + " AND   w.cPoliza is not null \n";
            PreparedStatement psSql = cnx.prepareStatement( cSql );
            psSql.setInt( 1, DIAS_AL_CIERRE );
            ResultSet rsNotif = psSql.executeQuery();
            while (rsNotif.next()) {
                Date dUltimoDato = rsNotif.getDate( "dUltimoDato" );
                int nDiasSinSincro = rsNotif.getInt( "nDiasSinSincro" );
                if (nDiasSinSincro >= 5) {
                    // a_facturar_1
                } else {
                    // a_facturar_2
                }
                ;
            }
            rsNotif.close();
            psSql.close();
        } catch (SQLException e) {
            logger.error( "Al lee notificaciones tipo 2", e );
            throw new RuntimeException( e );
        }
    }

}
