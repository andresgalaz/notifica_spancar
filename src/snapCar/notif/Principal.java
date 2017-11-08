package snapCar.notif;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.mail.NoSuchProviderException;

import org.apache.log4j.Logger;

import prg.glz.FrameworkException;
import prg.glz.data.entity.TConexionDB;
import prg.util.db.hlp.ConexionHelper;
import snapCar.mail.Mail;
import snapCar.net.CallMandril;
import snapCar.notif.config.Parametro;

public class Principal {
    private static Logger logger = Logger.getLogger( Principal.class );

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static void main(String[] args) throws FrameworkException {
        CallMandril mandril = new CallMandril();
        List<Map> lisTo = new ArrayList<Map>();
        Map mTo = new HashMap();
        mTo.put( "email", "andres.galaz@snapcar.com.ar" );
        mTo.put( "name", "David Raskovan" );
        mTo.put( "type", "to" );

        lisTo.add( mTo );

        Map mVars = new HashMap();
        mVars.put( "cNombre", "Javier 220" );
        mVars.put( "nDiasNoSincro", "30" );
        mVars.put( "dInicio", "15/09/2017" );
        mVars.put( "dFin", "15/10/2017" );

        String cResult = mandril.ejecuta( "a_facturar_01", "factura_1", lisTo, mVars );
        System.out.println( cResult );

        org.apache.log4j.PropertyConfigurator.configure( "log4j.properties" );
        logger.info( "Inicio del proceso" );

        // Se instancia aquí para verificar
        Mail mail = null;
        try {
            mail = new Mail();
        } catch (NoSuchProviderException e) {
            logger.error( "No está habilitado el servidor de MAIL", e );
            return;
        }

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

        // Proceso las notificaciones de factura
        try {
            NotifFactura notif = new NotifFactura( hlp.getConnection(), mail );
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
