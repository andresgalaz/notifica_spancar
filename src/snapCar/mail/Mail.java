package snapCar.mail;

import java.util.Properties;

import javax.activation.DataHandler;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.NoSuchProviderException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;

import org.apache.log4j.Logger;

import prg.util.cnv.ConvertString;
import snapCar.notif.config.Parametro;

public class Mail {
    private static Logger      logger         = Logger.getLogger( Mail.class );
    private String             cUsuarioSmtp;
    private String             cPasswordSmtp;
    private Session            mailSession;
    private Transport          transport;

    public static final String TP_ADJUNTO_CSV = "text/csv";

    public Mail() throws NoSuchProviderException {
        Properties props = new Properties();
        props.put( "mail.transport.protocol", "smtp" );
        props.put( "mail.smtp.host", Parametro.get( "smtp_host" ) );
        cUsuarioSmtp = Parametro.get( "smtp_user" );
        cPasswordSmtp = Parametro.get( "smtp_password" );
        if (!ConvertString.isEmpty( cUsuarioSmtp ) && !ConvertString.isEmpty( cPasswordSmtp ))
            props.put( "mail.smtp.auth", "true" );
        else
            props.put( "mail.smtp.auth", "false" );

        Authenticator auth = new SMTPAuthenticator();
        mailSession = Session.getDefaultInstance( props, auth );
        // uncomment for debugging infos to stdout
        // mailSession.setDebug(true);
        transport = mailSession.getTransport();
    }

    public void envia(String cSubject, String cFrom, String cTo, String cContenido) throws MessagingException {
        envia( cSubject, cFrom, cTo, cContenido, new MimeBodyPart[] {} );
    }

    public void envia(String cSubject, String cFrom, String cTo, String cContenido, MimeBodyPart adjunto) throws MessagingException {
        envia( cSubject, cFrom, cTo, cContenido, new MimeBodyPart[] { adjunto } );
    }

    public void envia(String cSubject, String cFrom, String[] cTo, String cContenido) throws MessagingException {
        envia( cSubject, cFrom, cTo, cContenido, new MimeBodyPart[] {} );
    }

    public void envia(String cSubject, String cFrom, String[] cTo, String cContenido, MimeBodyPart adjunto) throws MessagingException {
        envia( cSubject, cFrom, cTo, cContenido, new MimeBodyPart[] { adjunto } );
    }

    // Recive la lista de mails TO como un string separado por coma o punto y coma
    public void envia(String cSubject, String cFrom, String cTo, String cContenido, MimeBodyPart[] adjuntos) throws MessagingException {
        String[] arr = cTo.split( "\\s*[,; ]\\s*" );
        for (int i = 0; i < arr.length; i++)
            arr[i] = arr[i].trim();
        envia( cSubject, cFrom, arr, cContenido, adjuntos );
    }

    // Primitiva
    private void envia(String cSubject, String cFrom, String[] cTo, String cContenido, MimeBodyPart[] adjuntos) throws MessagingException {
        MimeMessage message = new MimeMessage( mailSession );
        message.setSubject( cSubject );
        message.setFrom( new InternetAddress( cFrom ) );
        for (int i = 0; i < cTo.length; i++) {
            message.addRecipient( Message.RecipientType.TO, new InternetAddress( cTo[i] ) );
        }

        Multipart multipart = new MimeMultipart();

        // MimeBodyPart textPart = new MimeBodyPart();
        // textPart.setText( "Facturación\nPrueba", "UTF-8" );
        // multipart.addBodyPart( textPart );

        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent( cContenido, "text/html; charset=UTF-8" );
        multipart.addBodyPart( htmlPart );

        for (int i = 0; i < adjuntos.length; i++)
            multipart.addBodyPart( adjuntos[i] );

        message.setContent( multipart );

        transport.connect();
        transport.sendMessage( message, message.getRecipients( Message.RecipientType.TO ) );
        transport.close();
        logger.info( "Mensaje enviado a " + cTo[0] + " : " + cSubject );
    }

    public MimeBodyPart creaAdjunto(String cNombre, String cTipo, byte[] dataByte) throws MessagingException {
        MimeBodyPart attachFilePart = new MimeBodyPart();
        attachFilePart.setDataHandler( new DataHandler( new ByteArrayDataSource( dataByte, cTipo ) ) );
        attachFilePart.setFileName( cNombre );
        return attachFilePart;
    }

    private class SMTPAuthenticator extends javax.mail.Authenticator {
        public PasswordAuthentication getPasswordAuthentication() {
            return new PasswordAuthentication( Mail.this.cUsuarioSmtp, Mail.this.cPasswordSmtp );
        }
    }
}
