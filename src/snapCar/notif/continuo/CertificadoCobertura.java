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
public class CertificadoCobertura {
    // private static Logger logger = Logger.getLogger( CertificadoCobertura.class );
    private Connection cnx;
    // private Mail mail;

    public CertificadoCobertura(Connection cnx /* , Mail mail */) {
        this.cnx = cnx;
        // this.mail = mail;
    }

    @SuppressWarnings({ "rawtypes" })
    public void procesa() throws SQLException, IOException, FrameworkException {
        // if (mail == null)
        // System.out.println( "Configurar mail antes" );

        // Actualiza el estado del certificado en la tabla tVehiculo
        PreparedStatement psUpd = cnx.prepareStatement( "UPDATE tVehiculo  SET bPdfCobertura = '1' WHERE pVehiculo = ?;" );

        // Armado cursor que detecta los vehículos a facturar
        String cSql = " SELECT v.pVehiculo, v.cPatente, u.cNombre, u.cEmail, IFNULL(m.desc_vehiculo, 'vehículo') cVehiculo \n"
                + " FROM  tVehiculo v \n"
                + "       JOIN tUsuario u ON u.pUsuario = v.fUsuarioTitular \n"
                + "       LEFT JOIN integrity.tMovim m ON m.pMovim = v.fMovimCreacion \n"
                + " WHERE v.bPdfCobertura = '0' \n"
                + " AND   IFNULL(v.cPoliza,'TEST') <> 'TEST' \n"
                + " AND   v.bVigente = '1' \n";
        PreparedStatement psSql = cnx.prepareStatement( cSql );
        ResultSet rs = psSql.executeQuery();

        // Prepara Webservice envía Mails
        CallWsMail callMail = new CallWsMail();
        while (rs.next()) {
            String cPatente = rs.getString( "cPatente" );
            String cUrl = Parametro.get( "file_repos" )  + "poliza/" + cPatente + "_certificado.pdf";
            // Si el archivo no está presente, el mail no se envía, para no hacer el ridículo enviando mails con LINK
            // rotos.
            if (!utilHttp.existeUrl( cUrl ))
                continue;

            Integer pVehiculo = rs.getInt( "pVehiculo" );
            String cEmail = rs.getString( "cEmail" );
            String cNombre = rs.getString( "cNombre" );
            String cVehiculo = rs.getString( "cVehiculo" );
            // Saca el primer nombre
            cNombre = cNombre.split( " " )[0];
            // Deja solo la primera parte que normalmente es MARCA y Modelo
            cVehiculo = cVehiculo.split( " " )[0];

            List<Map> to = callMail.createAddressTo( cNombre, cEmail );
            Map<String, String> mReg = new HashMap<String, String>();
            mReg.put( "cAsunto", "Bienvenido a Snapcar" );
            mReg.put( "cUrlCertificado", cUrl );
            mReg.put( "cPatente", cPatente );
            mReg.put( "cNombre", cNombre );
            mReg.put( "cVehiculo", cVehiculo );

            callMail.ejecuta( "cert_cobertura", "poliza", to, mReg );

            // Actualiza tVehiculo.bPdfCobertura, para no volver a enviar
            psUpd.setInt( 1, pVehiculo );
            psUpd.execute();
            // Se actualiza inmmediatamente, porque el mail ya salió
            cnx.commit();

        }
        rs.close();
        psSql.close();
        psUpd.close();
    }

}
