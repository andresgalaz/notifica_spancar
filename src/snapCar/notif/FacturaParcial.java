package snapCar.notif;

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
 * Se envía en medio del periodo de facturación, hace un cálculo parcial del descuento y score.
 * </p>
 * 
 * @author agalaz
 *
 */
public class FacturaParcial {
    private static Logger    logger            = Logger.getLogger( FacturaParcial.class );
    private Connection       cnx;
    private static final int DIAS_DESDE_INICIO = 13;
    private static final int MAX_DIAS_SIN_MEDICION = 7;

    public FacturaParcial(Connection cnx) {
        this.cnx = cnx;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void procesa() throws FrameworkException {
        Locale.setDefault( new Locale( "es", "ES" ) );
        SimpleDateFormat fmtNormal = new SimpleDateFormat( "dd/MM/YYYY" );

        try {
            // Este llama al que hace el cálculo: Primer parámetro es el usuario, segundo parámetro es el vehiculo
            PreparedStatement psExecCalc = cnx.prepareStatement( "{CALL prScoreVehiculoRangoFecha( ?, 0, null, null, ?, null, false )}" );
            // Para un vehñiculo solo dedebería generarse un registro en wMemoryScoreVehiculo por cada vez que se
            // calcula
            String cSql = "SELECT \n"
                    + "       nDiasSinMedicion, nKms, nScore, nDescuento  \n"
                    + "     , nQVelocidad, nQAceleracion, nQFrenada, nQCurva \n"
                    + " FROM  wMemoryScoreVehiculo";
            PreparedStatement psListCalc = cnx.prepareStatement( cSql );

            // armado del cursor principal que busca los vehiculo que están a tantos DIAS_DESDE_INICIO
            cSql = "SELECT u.pUsuario, v.pVehiculo \n"
                    + "      , fnFechaCierreIni(v.dIniVigencia, 0) dInicio \n"
                    + "      , fnFechaCierreFin(v.dIniVigencia, 0) dFin \n"
                    + "      , v.cPatente \n"
                    + "      , IFNULL(v.cMarca, 'vehículo')       cMarca \n"
                    + "      , u.cNombre, u.cEmail \n"
                    + " FROM   tVehiculo v \n"
                    + "        JOIN tUsuario u ON u.pUsuario = v.fUsuarioTitular \n"
                    + " WHERE  v.cPoliza is not null \n"
                    + " AND    v.bVigente = '1' \n"                    
                    + " AND    fnFechaCierreIni(v.dIniVigencia, 1) >= v.dIniVigencia \n"
                    + " AND    DATEDIFF(fnFechaCierreIni(v.dIniVigencia, 1),fnNow()) = ? \n";
            PreparedStatement psSql = cnx.prepareStatement( cSql );
            psSql.setInt( 1, DIAS_DESDE_INICIO );
            ResultSet rsNotif = psSql.executeQuery();
            // Prepara Webservice envía Mails
            CallWsMail callMail = new CallWsMail();
            while (rsNotif.next()) {
                int pUsuario = rsNotif.getInt( "pUsuario" );
                int pVehiculo = rsNotif.getInt( "pVehiculo" );
                Date dInicio = ConvertDate.toDate( rsNotif.getDate( "dInicio" ) );
                Date dFin = ConvertDate.toDate( rsNotif.getDate( "dFin" ) );
                String cPatente = rsNotif.getString( "cPatente" );
                String cMarca = rsNotif.getString( "cMarca" );
                String cNombre = rsNotif.getString( "cNombre" );
                String cEmail = rsNotif.getString( "cEmail" );
                String cPrimerNombre = cNombre.split( " " )[0];

                psExecCalc.setInt( 1, pUsuario );
                psExecCalc.setInt( 2, pVehiculo );
                psExecCalc.execute();

                ResultSet rsCalc = psListCalc.executeQuery();
                rsCalc.next();
                int nDiasSinMedicion = rsCalc.getInt( "nDiasSinMedicion" );
                if( nDiasSinMedicion < MAX_DIAS_SIN_MEDICION ){
                    int nKms = rsCalc.getInt( "nKms" );
                    int nScore = rsCalc.getInt( "nScore" );
                    int nDescuento = rsCalc.getInt( "nDescuento" );
                    int nQVelocidad = rsCalc.getInt( "nQVelocidad" );
                    int nQAceleracion = rsCalc.getInt( "nQAceleracion" );
                    int nQFrenada = rsCalc.getInt( "nQFrenada" );
                    int nQCurva = rsCalc.getInt( "nQCurva" );
    
                    rsCalc.close();
    
                    List<Map> to = callMail.createAddressTo( cNombre, cEmail );
    
                    Map mReg = new HashMap();
                    mReg.put( "cPatente", cPatente );
                    mReg.put( "cMarca", cMarca );
                    mReg.put( "cNombre", cPrimerNombre );
                    mReg.put( "dInicio", fmtNormal.format( dInicio ) );
                    mReg.put( "dFin", fmtNormal.format( dFin ) );
                    mReg.put( "nDescuento", nDescuento );
                    mReg.put( "nKms", nKms );
                    mReg.put( "nScore", nScore );
                    mReg.put( "nDescuento", nDescuento );
                    mReg.put( "nRecargo", -nDescuento );
                    mReg.put( "nQVelocidad", nQVelocidad );
                    mReg.put( "nQAceleracion", nQAceleracion );
                    mReg.put( "nQFrenada", nQFrenada );
                    mReg.put( "nQCurva", nQCurva );
    
                    try {
                    if(nDescuento > 10 ) 
                        callMail.ejecuta( "descuento_parcial_10_40", "descuento_parcial", to, mReg );
                    else if(nDescuento >= 0 ) 
                        callMail.ejecuta( "descuento_parcial_0_10", "descuento_parcial", to, mReg );
                    else if(nDescuento < 0 ) { 
                        callMail.ejecuta( "recargo_factura_0_40", "recargo_parcial", to, mReg );
                    }
                    
                    } catch (FrameworkException e) {
                        logger.error( "Al enviar mail a " + cEmail + "por la patente " + cPatente, e );
                    }
                }
            }
            rsNotif.close();
            psSql.close();
            psExecCalc.close();
            psListCalc.close();
        } catch (SQLException e) {
            logger.error( "Al lee notificaciones tipo 2", e );
            throw new RuntimeException( e );
        } catch (ConvertException e) {
            logger.error( "Al convertir fecha de sincronización", e );
            throw new RuntimeException( e );
        }
    }

}
