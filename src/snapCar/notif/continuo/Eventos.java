package snapCar.notif.continuo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.log4j.Logger;

import prg.glz.FrameworkException;
import snapCar.net.CallPushService;

/**
 * Envía push notifications
 * cuando detecta un exceso de velocidad de más de 130 km/h.
 * @author Rodrigo Sobrero
 * @since 2018-05-18
 */

public class Eventos {
	private static Logger	logger	= Logger.getLogger( Eventos.class );
	private Connection		cnx;

	public Eventos(Connection cnx) {
		this.cnx = cnx;
	}

	public void procesa() throws FrameworkException {
		try {
			String cSql = "SELECT fUsuario \n"
					+ "		, nVelocidadMaxima \n"
					+ "		, nValor \n"
					+ "		, pEvento \n"
					+ "		, fVehiculo \n"
					+ " FROM tEvento \n"
					+ " WHERE fTpEvento = 5 \n"
					+ " AND nValor > 130 \n"
					+ " AND tModif >= DATE(fnNow() - INTERVAL 1 DAY) \n"
					+ " AND bNotifica = 0";

			String cUpd = "UPDATE tEvento SET bNotifica = '1' WHERE fUsuario = ? AND pEvento = ?;";

			PreparedStatement psSql = cnx.prepareStatement( cSql );
			PreparedStatement psUpd = cnx.prepareStatement( cUpd );
			ResultSet rsNotif = psSql.executeQuery();

            // Prepara Webservice envía Push
            CallPushService callPush = new CallPushService(cnx);

            while(rsNotif.next()) {
            	int fUsuario = rsNotif.getInt( "fUsuario" );
            	int nValor = rsNotif.getInt( "nValor" );
            	int pEvento = rsNotif.getInt( "pEvento" );
            	int fVehiculo = rsNotif.getInt( "fVehiculo" );

            	try {
            		callPush.envia( fUsuario, "¡Cuidado!", "Registramos un exceso de velocidad a ⚠️" + nValor + " km/h ⚠️", "eventos", null, pEvento, 13, fVehiculo );
        			psUpd.setInt( 1, fUsuario );
        			psUpd.setInt( 2, pEvento );
        			psUpd.execute();
        			cnx.commit();
            	} catch (FrameworkException e) {
            		logger.error( "Error al envíar push notification al usuario con id " + fUsuario, e );
            	}
            }
            rsNotif.close();
            psSql.close();
		} catch (SQLException e) {
			logger.error( "Error al leer las notificaciones con eventos tipo 5", e );
			throw new RuntimeException( e );
		}
	}
}