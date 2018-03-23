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
import snapCar.notif.continuo.CertificadoCobertura;
import snapCar.notif.continuo.EndosoFactura;
import snapCar.notif.continuo.Poliza;
import snapCar.notif.diario.AFacturar;
import snapCar.notif.diario.CierreFactura;
import snapCar.notif.diario.FacturaParcial;
import snapCar.notif.diario.FacturacionAdmin;
import snapCar.notif.diario.NoSincro;

public class Principal {
    private static Logger logger = Logger.getLogger( Principal.class );

    public static void main(String[] args) throws FrameworkException {
        org.apache.log4j.PropertyConfigurator.configure( "log4j.properties" );

        if (args.length == 0) {
            logger.error( "Falta parámetro de tipo de proceso: continuo o diario " );
            return;
        }

        logger.info( "Inicio del proceso v7.0c" );

        // Conecta a la base de datos
        TConexionDB cnx = new TConexionDB();
        cnx.setcNombre( "nueva" );
        cnx.setcDriverClass( Parametro.get( "db_class" ) );
        cnx.setcUrl( Parametro.get( "db_url" ) );
        cnx.setcUsuario( Parametro.get( "db_usuario" ) );
        cnx.setcPassword( Parametro.get( "db_password" ) );
        cnx.setcTpBD( Parametro.get( "db_tipo" ) );
        ConexionHelper cnxHlp = null;
        try {
            cnxHlp = new ConexionHelper( cnx );
        } catch (Exception e) {
            logger.error( "No se puso conectar a la base de datos:" + cnx.getcUrl() );
            return;
        }
        logger.info( "Ambiente:" + Ambiente.getNombre() );

        /*
         * Inicia proceso de acuerdo al argumento ingresado
         * 
         * certif: Genera los certificados, el cual es invocado varias veces por día, ni bien hay novedades en los
         * movimientos de Integrity.
         * 
         * notif: Envía de mails de notificaciones y se corre una sola vez en la mañana, esto para no reenviar
         * notificaciones a los usuarios.
         */
        try {
            if ("continuo".equalsIgnoreCase( args[0] ))
                callContinuo( cnxHlp );
            else if ("diario".equalsIgnoreCase( args[0] ))
                callDiaria( cnxHlp );
            else
                logger.error( "Parámetro desconocido: " + args[0] + "\n"
                        + "Se esperaba: continuo o diario." );
        } catch (Exception e) {
            logger.error( "Error inesperado", e );
        }

        cnxHlp.closeConnection();
        logger.info( "Fin del proceso" );
    }

    /**
     * Notificaciones por mail.
     * 
     * @param cnxHlp
     */
    private static void callDiaria(ConexionHelper cnxHlp) {
        // Proceso notificaciones a clientes con cierre y que aún tienen días sin sincronizar
        try {
            NoSincro notif = new NoSincro( cnxHlp.getConnection() );
            notif.procesa();
            cnxHlp.getConnection().commit();
        } catch (Exception e) {
            rollback( cnxHlp );
            logger.error( "Al procesar notificaciones de clientes que no sincronizaron", e );
        }

        // Proceso notificaciones a clientes que están a punto de facturar
        try {
            AFacturar notif = new AFacturar( cnxHlp.getConnection() );
            notif.procesa();
            cnxHlp.getConnection().commit();
        } catch (Exception e) {
            rollback( cnxHlp );
            logger.error( "Al procesar notificaciones de clientes a facturar", e );
        }

        // Proceso notificaciones a clientes con cierre y que aún tienen días sin sincronizar
        try {
            FacturaParcial notif = new FacturaParcial( cnxHlp.getConnection() );
            notif.procesa();
            cnxHlp.getConnection().commit();
        } catch (Exception e) {
            rollback( cnxHlp );
            logger.error( "Al procesar notificaciones de clientes a facturar", e );
        }

        // Proceso notificaciones a clientes con cierre y que aún tienen días sin sincronizar
        try {
            CierreFactura notif = new CierreFactura( cnxHlp.getConnection() );
            notif.procesa();
            cnxHlp.getConnection().commit();
        } catch (Exception e) {
            rollback( cnxHlp );
            logger.error( "Al procesar notificaciones de clientes al cierre de factura", e );
        }

        Mail mail = null;
        try {
            // Se instancia servicio SMTP mail
            mail = new Mail();

            // Proceso notificaciones de factura
            FacturacionAdmin notif = new FacturacionAdmin( cnxHlp.getConnection(), mail );
            notif.procesa();
            cnxHlp.getConnection().commit();

        } catch (NoSuchProviderException e) {
            logger.error( "No está habilitado el servidor de MAIL", e );
        } catch (Exception e) {
            rollback( cnxHlp );
            if (!(e instanceof RuntimeException))
                logger.error( "Al procesar facturación", e );
        }
    }

    private static void callContinuo(ConexionHelper cnxHlp) {
        try {
            CertificadoCobertura notif = new CertificadoCobertura( cnxHlp.getConnection() );
            notif.procesa();
            cnxHlp.getConnection().commit();
        } catch (Exception e) {
            rollback( cnxHlp );
            logger.error( "Al procesar notificaciones de certificados de cobertura", e );
        }

        try {
            EndosoFactura notif = new EndosoFactura( cnxHlp.getConnection() );
            notif.procesa();
            cnxHlp.getConnection().commit();
        } catch (Exception e) {
            rollback( cnxHlp );
            logger.error( "Al procesar notificaciones de prorrogas", e );
        }

        try {
            Poliza notif = new Poliza( cnxHlp.getConnection() );
            notif.procesa();
            cnxHlp.getConnection().commit();
        } catch (Exception e) {
            rollback( cnxHlp );
            logger.error( "Al procesar notificaciones de pólizas nuevas", e );
        }

    }

    private static void rollback(ConexionHelper cnxHlp) {
        try {
            cnxHlp.getConnection().rollback();
        } catch (SQLException e1) {
        }
    }
}
