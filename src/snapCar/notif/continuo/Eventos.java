package snapCar.notif.continuo;

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
import snapCar.net.CallPushService;

/**
 * Envía push notifications
 * cuando detecta un exceso de velocidad
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
					+ "FROM score.tEvento \n"
					+ "WHERE fTpEvento = 3 \n"
					+ "AND nVelocidadMaxima > nValor \n"
					+ "AND tModif = fnNow() \n"
					+ "AND nNotificacion = 0";
			
			PreparedStatement psSql = cnx.prepareStatement( cSql );
			ResultSet rsNotif = psSql.executeQuery();
			
            // Prepara Webservice envía Push
            CallPushService callPush = new CallPushService();
            
            while(rsNotif.next()) {
            	int fUsuario = rsNotif.getInt( "fUsuario" );
            	int nVelocidadMaxima = rsNotif.getInt( "nVelocidadMaxima" );
            	int nValor = rsNotif.getInt( "nValor" );
            	int pEvento = rsNotif.getInt( "pEvento" );

            	try {
            		callPush.envia( fUsuario, "¡Cuidado!", "Registramos un exceso de velocidad a ⚠️" + nValor + " km/h ⚠️ en una zona de " + nVelocidadMaxima + " km/h.", "eventos", null, pEvento );
            		psSql.setInt( 1, 1 );
            	} catch (FrameworkException e) {
            		logger.error( "" );
            	}
            }
		} catch (SQLException e) {
			logger.error( "", e );
			throw new RuntimeException( e );
		}
	}
}