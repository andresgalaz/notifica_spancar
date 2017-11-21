package snapCar.notif;

import java.sql.SQLException;

import javax.mail.NoSuchProviderException;

import org.apache.log4j.Logger;

import prg.glz.FrameworkException;
import prg.glz.data.entity.TConexionDB;
import prg.util.db.hlp.ConexionHelper;
import snapCar.mail.Mail;
import snapCar.notif.config.Ambiente;
import snapCar.notif.config.Parametro;

public class Principal {
    private static Logger logger = Logger.getLogger( Principal.class );

    public static void main(String[] args) throws FrameworkException {
        org.apache.log4j.PropertyConfigurator.configure( "log4j.properties" );
        logger.info( "Inicio del proceso" );

        // Conecta a la base de datos
        TConexionDB cnx = new TConexionDB();
        cnx.setcNombre( "nueva" );
        cnx.setcDriverClass( Parametro.get( "db_class" ) );
        cnx.setcUrl( Parametro.get( "db_url" ) );
        cnx.setcUsuario( Parametro.get( "db_usuario" ) );
        cnx.setcPassword( Parametro.get( "db_password" ) );
        cnx.setcTpBD( Parametro.get( "db_tipo" ) );
        ConexionHelper hlp = null;
        try {
            hlp = new ConexionHelper( cnx );
        } catch (Exception e) {
            logger.error( "No se puso conectar a la base de datos:" + cnx.getcUrl() );
            return;
        }
        logger.info( "Ambiente:" + Ambiente.getNombre() );

        // Proceso notificaciones a clientes que están a punto de facturar
        try {
            AFacturar notif = new AFacturar( hlp.getConnection() );
            notif.procesa();
            hlp.getConnection().commit();
        } catch (Exception e) {
            try {
                hlp.getConnection().rollback();
            } catch (SQLException e1) {
            }
            logger.error( "Al procesar notificaciones de clientes a facturar", e );
        }

        // Se instancia aquí para verificar
        Mail mail = null;
        try {
            mail = new Mail();
        } catch (NoSuchProviderException e) {
            logger.error( "No está habilitado el servidor de MAIL", e );
            return;
        }

        // Proceso notificaciones de factura
        try {
            FacturacionAdmin notif = new FacturacionAdmin( hlp.getConnection(), mail );
            notif.procesa();
            hlp.getConnection().commit();
        } catch (Exception e) {
            try {
                hlp.getConnection().rollback();
            } catch (SQLException e1) {
            }
            if (!(e instanceof RuntimeException))
                logger.error( "Al procesar notificaciones de facturación", e );
        }
        hlp.closeConnection();
        logger.info( "Fin del proceso" );
    }
}
