package snapCar.notif.continuo;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import prg.glz.FrameworkException;
import snapCar.factura.CalcAhorro;
import snapCar.net.CallWsMail;
import snapCar.notif.config.Parametro;
import snapCar.util.utilHttp;

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
public class Poliza {
    // private static Logger logger = Logger.getLogger( CertificadoCobertura.class );
    private Connection cnx;
    // private Mail mail;

    public Poliza(Connection cnx /* , Mail mail */) {
        this.cnx = cnx;
        // this.mail = mail;
    }

    @SuppressWarnings({ "rawtypes" })
    public void procesa() throws SQLException, IOException, FrameworkException {
        // if (mail == null)
        // System.out.println( "Configurar mail antes" );

        // Actualiza el estado del certificado en la tabla tVehiculo
        PreparedStatement psUpd = cnx.prepareStatement( "UPDATE tVehiculo  SET bPdfPoliza = '1' WHERE pVehiculo = ?;" );

        // Armado cursor que detecta los vehículos a facturar
        String cSql = " SELECT v.pVehiculo, v.cPatente, u.cNombre, u.cEmail \n"
                // + "          , m.FECHA_EMISION dEmision \n"
                // bAntiguo: Indica si la póliza es vieja, cuando al menos pasarson 200 días
                // desde la vigencia y vuelve a aparecer con bPdfPoliza='0', los 200 días
                // es un parámetro a evaluar todavía
                + "          , (datediff( fnNow(), v.dIniVigencia ) > 200) bAntiguo \n"
                + " FROM  tVehiculo v \n"
                + "       JOIN tUsuario u ON u.pUsuario = v.fUsuarioTitular \n"
                // + "       LEFT JOIN integrity.tMovim m ON m.pMovim = v.fMovimCreacion \n"
                + " WHERE v.bPdfPoliza = '0' \n"
                + " AND   IFNULL(v.cPoliza,'TEST') <> 'TEST' \n"
                + " AND   v.bVigente = '1' \n";
        PreparedStatement psSql = cnx.prepareStatement( cSql );
        ResultSet rs = psSql.executeQuery();

        // Para calcular el ahorro
        CalcAhorro calcAhorro = new CalcAhorro( this.cnx );

        // Prepara Webservice envía Mails
        CallWsMail callMail = new CallWsMail();
        while (rs.next()) {
            String cPatente = rs.getString( "cPatente" );
            String cLinkPoliza = Parametro.get( "file_repos" ) + "poliza/" + cPatente + ".pdf";
            // Si el archivo no está presente, el mail no se envía, para no hacer el ridículo enviando mails con LINK
            // rotos.
            if (!utilHttp.existeUrl( cLinkPoliza ))
                continue;

            Integer pVehiculo = rs.getInt( "pVehiculo" );
            String cEmail = rs.getString( "cEmail" );
            String cNombre = utilHttp.primerNombre( rs.getString( "cNombre" ) );
            boolean bAntiguo = rs.getBoolean( "bAntiguo" );

            List<Map> to = callMail.createAddressTo( cNombre, cEmail );
            Map<String, String> mReg = new HashMap<String, String>();
            mReg.put( "cAsunto", "Bienvenido a Snapcar" );
            mReg.put( "cUrlCertificado", cLinkPoliza );
            mReg.put( "cPatente", cPatente );
            mReg.put( "cNombre", cNombre );
            mReg.put( "cLinkPoliza", cLinkPoliza );
            mReg.put( "subject", "Póliza SnapCar Integrity Vehículo " + cPatente );

            if (bAntiguo) {
                // Calcula ahorro
                calcAhorro.procesa( cPatente, null );
                // Introduce los valores de ahorro y premio a mVal
                mReg.put( "nAhorroAcumulado", utilHttp.separaMiles( calcAhorro.getnAhorroAcum() ) );

                callMail.ejecuta( "poliza_renovacion", "poliza", to, mReg );
            } else
                callMail.ejecuta( "poliza", "poliza", to, mReg );

            // Actualiza tVehiculo.bPdfCobertura, para no volver a enviar
            psUpd.setInt( 1, pVehiculo );
            psUpd.execute();
            // Se actualiza inmmediatamente, porque el mail ya salió
            cnx.commit();

        }
        rs.close();
        psSql.close();
        psUpd.close();
        calcAhorro.close();

    }

}
