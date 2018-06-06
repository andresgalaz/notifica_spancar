package snapCar.notif.diario;

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
import snapCar.net.CallPushService;

/**
 * <p>
 * Se envía al momento de cierre de la facturación a los clientes que tengan al menos 1 días sin sincronizar a la fecha
 * de cierre. Este proceso buscar los que cerraron justamente ayer.
 * </p>
 * <p>
 * Utiliza la tabla temporal wMemoryCierreTransf que se crea con el procedimiento: prControlCierreTransferenciaInicio
 * </p>
 * 
 * @author agalaz
 *
 */
public class CierreFactura {
    private static Logger    logger         = Logger.getLogger( CierreFactura.class );
    private Connection       cnx;
    // Es negavtivo porque se buscan los que ya cerraron, justo ayer
    private static final int DIAS_AL_CIERRE = -1;

    public CierreFactura(Connection cnx) {
        this.cnx = cnx;
    }

    @SuppressWarnings({ "rawtypes" })
    public void procesa() throws FrameworkException {
        Locale.setDefault( new Locale( "es", "ES" ) );
        SimpleDateFormat fmtLargo = new SimpleDateFormat( "EEEE d 'de' MMMM 'de' YYYY " );
        try {
            {
                // Crea tabla temporal wMemoryCierreTransf, se usa el parámetro cero porque se quiere los que ya
                // vencieron, no los que van a vencer
                CallableStatement call = cnx.prepareCall( "{ call prControlCierreTransferenciaInicioDef(0)}" );
                call.execute();
                call.close();
            }
            String cSql = "SELECT w.cPatente \n"
                    + "     , DATE_FORMAT(w.dProximoCierreIni, '%d/%m/%Y')    dInicio \n"
                    + "     , DATE_FORMAT(w.dProximoCierreFin, '%d/%m/%Y')    dFin \n"
                    + "		, w.pVehiculo \n"
                    + "     , w.nDiasNoSincro \n"
                    + "		, w.fUsuarioTitular \n"
                    + "     , u.cEmail, u.cNombre                                              cNombre \n"
                    /*
                     * Fecha : 29/01/2018
                     * Autor: A.GALAZ
                     * Motivo: Se deja de utilizar la tabla tInicioTransferencia, porque distorsiona
                     * La fecha real del último viaje o control file.
                     *
                     * + " , GREATEST( IFNULL(DATE( w.tUltTransferencia), '0000-00-00') \n"
                     * + " , IFNULL(DATE( w.tUltViaje ), '0000-00-00') \n"
                     * + " , IFNULL(DATE( w.tUltControl ), '0000-00-00')) dSincro \n"
                     */
                    + "     , GREATEST( IFNULL(DATE( w.tUltViaje        ), '0000-00-00') \n"
                    + "     		  , IFNULL(DATE( w.tUltControl      ), '0000-00-00'))      dSincro \n"
                    + " FROM  wMemoryCierreTransf w \n"
                    + "       JOIN tUsuario u ON u.pUsuario = w.fUsuarioTitular \n"
                    + " WHERE nDiasAlCierreAnt = ? \n"
                    + " AND   nDiasNoSincro > 0 \n"
                    + " AND   w.cPoliza is not null \n"
                    + " AND   w.bVigente = '1' \n";
            PreparedStatement psSql = cnx.prepareStatement( cSql );
            psSql.setInt( 1, DIAS_AL_CIERRE );
            ResultSet rsNotif = psSql.executeQuery();
            // Prepara Webservice envía Mails
            CallWsMail callMail = new CallWsMail();
            // Prepara Webservice envía Push
            CallPushService callPush = new CallPushService(cnx);

            while (rsNotif.next()) {
            	int nfUsuarioTitular = rsNotif.getInt( "fUsuarioTitular" );
                int nDiasNoSincro = rsNotif.getInt( "nDiasNoSincro" );
                int cVehiculo = rsNotif.getInt( "pVehiculo" );
                String cPatente = rsNotif.getString( "cPatente" );
                String cEmail = rsNotif.getString( "cEmail" );
                String cNombre = rsNotif.getString( "cNombre" );
                String cPrimerNombre = cNombre.split( " " )[0];
                Date dSincro = ConvertDate.toDate( rsNotif.getDate( "dSincro" ) );

                List<Map> to = callMail.createAddressTo( cNombre, cEmail );

                Map<String, String> mReg = new HashMap<String, String>();
                mReg.put( "cPatente", cPatente );
                mReg.put( "cFecSincro", fmtLargo.format( dSincro ) );
                mReg.put( "cNombre", cPrimerNombre );
                mReg.put( "nDiasNoSincro", String.valueOf( nDiasNoSincro ) );

                try {
                    /** 
                     * Envía push notification cuando al usuario
                     * le cerró el periodo y no sincronizó.
                     * @author Rodrigo Sobrero
                     * @since 2018-05-17
                     */

                	if (nDiasNoSincro == 1) {
                		callPush.envia( nfUsuarioTitular,
                				"Hoy cerró tu periodo de facturación",
                				"¡" + cPrimerNombre + ", hoy cerró tu periodo de facturación!⏰ Sincronizá para obtener tu descuento.💸",
                				"", null, null, 11, cVehiculo );
                	} else if (nDiasNoSincro >= 2) {
                		callPush.envia( nfUsuarioTitular,
                				"Tenés días pendientes de sincronización",
                				"¡" + cPrimerNombre + ", sincronizá para obtener tu descuento!💸 Todavía hay días pendientes.⏰",
                				"", null, null, 11, cVehiculo );
                	}

                    callMail.ejecuta( "cerro_periodo_factura", "cerro_periodo", to, mReg );
                } catch (FrameworkException e) {
                    logger.error( "Al enviar mail a " + cEmail + "por la patente " + cPatente, e );
                }
            }
            rsNotif.close();
            psSql.close();
        } catch (SQLException e) {
            logger.error( "Al lee notificaciones tipo 2", e );
            throw new RuntimeException( e );
        } catch (ConvertException e) {
            logger.error( "Al convertir fecha de sincronización", e );
            throw new RuntimeException( e );
        }
    }

}
