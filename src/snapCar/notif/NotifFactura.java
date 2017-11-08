package snapCar.notif;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;

import org.apache.log4j.Logger;

import prg.util.cnv.ConvertList;
import snapCar.mail.Mail;
import snapCar.notif.config.Parametro;

public class NotifFactura {
    private static Logger logger = Logger.getLogger( NotifFactura.class );
    private Connection    cnx;
    private Mail          mail;

    public NotifFactura(Connection cnx, Mail mail) {
        this.cnx = cnx;
        this.mail = mail;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void procesa() {
        try {
            String cFrom = Parametro.get( "mail_from" );
            String cToEmails = null;

            String cSql = "SELECT n.pNotificacion, n.cMensaje, t.cEmails "
                    + " FROM tNotificacion n "
                    + "      JOIN tTpNotificacion t ON t.pTpNotificacion = n.fTpNotificacion "
                    + " WHERE n.fTpNotificacion = 2 AND n.tEnviado is null";
            PreparedStatement psSql = cnx.prepareStatement( cSql );
            PreparedStatement psUpd = cnx.prepareStatement( "UPDATE tNotificacion SET tEnviado = NOW() WHERE pNotificacion = ?" );

            ResultSet rsNotif = psSql.executeQuery();
            List<Map> data = new ArrayList<Map>();
            while (rsNotif.next()) {
                int pNotificacion = rsNotif.getInt( "pNotificacion" );
                String cMensaje = rsNotif.getString( "cMensaje" );
                if (cToEmails == null)
                    cToEmails = rsNotif.getString( "cEmails" );
                // Junta los datos de las notificaciones pendientes de enviar, así solo envía un mail por todo el
                // conjunto
                data.addAll( ConvertList.fromJsonString( cMensaje ) );

                psUpd.setInt( 1, pNotificacion );
                psUpd.execute();
            }
            rsNotif.close();
            psSql.close();
            psUpd.close();

            if (data.size() > 0) {
                // tomamos la fecha del primer registro
                String cFecFact = (String) data.get( 0 ).get( "fecFacturacion" );
                // Prepara un adjunto del tipo CSV con la información
                MimeBodyPart adjCsv = this.mail.creaAdjunto( "facturacion-" + cFecFact.replaceAll( ":", "" ) + ".csv", Mail.TP_ADJUNTO_CSV //
                // Los CSV si se levantan con Windows en mejor codificación ISO que UTF
                        , armaCsv( data ).getBytes( "ISO8859-1" ) );
                this.mail.envia( "Facturación " + cFecFact, cFrom, cToEmails, armaMail( data ), adjCsv );
            }
        } catch (SQLException e) {
            logger.error( "Al lee notificaciones tipo 2", e );
            throw new RuntimeException( e );
        } catch (MessagingException e) {
            logger.error( "No se pudo enviar el mail", e );
            throw new RuntimeException( e );
        } catch (UnsupportedEncodingException e) {
            logger.error( "No se pudo crear CSV por error de codificación", e );
            throw new RuntimeException( e );
        }
    }

    @SuppressWarnings("rawtypes")
    private String armaMail(List<Map> data) throws UnsupportedEncodingException {
        StringBuffer sb = new StringBuffer();

        sb.append( "<!DOCTYPE HTML PUBLIC '-//W3C//DTD HTML 4.01//EN' 'http://www.w3.org/TR/html4/strict.dtd'> \n" );
        sb.append( "<html><head><meta http-equiv='Content-Type' content='text/html; charset=utf-8'>\n" );
        sb.append( "<title>Facturación</title> \n" );
        sb.append( "<style type='text/css'> \n" );
        sb.append( "outlook a {padding: 0;} \n" );
        sb.append( "body {font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; width: 100% !important;" );
        sb.append( "     -webkit-text-size-adjust: 100%;-ms-text-size-adjust: 100%;margin: 0;padding: 0;} \n" );
        sb.append( ".divTable{display: table;width: 100%;} \n" );
        sb.append( ".divTableRow {display: table-row;} \n" );
        sb.append( ".divTableHeading {background-color: #EEE;display: table-header-group;} \n" );
        sb.append( ".divTableCell, .divTableHead {border: 1px solid #999999;display: table-cell;padding: 3px 10px;} \n" );
        sb.append( ".divTableHeading {background-color: #EEE;display: table-header-group;font-weight: bold;} \n" );
        sb.append( ".divTableFoot {background-color: #EEE;display: table-footer-group;font-weight: bold;} \n" );
        sb.append( "  </style> \n" );
        sb.append( "</head> \n" );
        sb.append( "<body leftmargin='0' topmargin='0' marginwidth='0' marginheight='0' style='margin: 0px; padding: 0px;' >\n" );
        sb.append( "<h1>Resultado de Facturación</h1>\n" );
        sb.append( "<div class='divTable' style='width: 5%;border: 1px solid #000;' >\n" );
        sb.append( "  <div class='divTableRow'>\n" );
        sb.append( "    <div class='divTableCell'>Id. Vehiculo</div>\n" );
        sb.append( "    <div class='divTableCell'>Patente</div>\n" );
        sb.append( "    <div class='divTableCell'>Periodo</div>\n" );
        sb.append( "    <div class='divTableCell'>Inicio Vigencia</div>\n" );
        sb.append( "    <div class='divTableCell'>Id. Usuario</div>\n" );
        sb.append( "    <div class='divTableCell'>Nombre</div>\n" );
        sb.append( "    <div class='divTableCell'>Email</div>\n" );
        sb.append( "    <div class='divTableCell'>Descuento</div>\n" );
        sb.append( "    <div class='divTableCell'>Fecha Facturación</div>\n" );
        sb.append( "  </div>\n" );

        for (int i = 0; i < data.size(); i++) {
            Map m = (Map) data.get( i );
            if (!"Real".equals( m.get( "tpCalculo" ) ))
                continue;
            sb.append( "  <div class='divTableRow'>\n" );
            sb.append( "    <div class='divTableCell'>" + m.get( "idVehiculo" ) + "</div>\n" );
            sb.append( "    <div class='divTableCell'>" + m.get( "patente" ) + "</div>\n" );
            sb.append( "    <div class='divTableCell'>" + m.get( "iniPeriodo" ) + " - " + m.get( "finPeriodo" ) + "</div>\n" );
            sb.append( "    <div class='divTableCell'>" + m.get( "inicioVigencia" ) + "</div>\n" );
            sb.append( "    <div class='divTableCell'>" + m.get( "idUsuario" ) + "</div>\n" );
            sb.append( "    <div class='divTableCell'>" + m.get( "nombre" ) + "</div>\n" );
            sb.append( "    <div class='divTableCell'>" + m.get( "email" ) + "</div>\n" );
            sb.append( "    <div class='divTableCell'>" + m.get( "descuento" ) + "</div>\n" );
            sb.append( "    <div class='divTableCell'>" + m.get( "fecFacturacion" ) + "</div>\n" );
            sb.append( "  </div>\n" );
        }
        sb.append( "</div>\n" );
        sb.append( "<br/>\n" );
        sb.append( "<br/>\n" );
        {
            // Arma DEEP LINK sistema de administración
            String cFecFactIni = URLEncoder.encode( (String) data.get( 0 ).get( "fecFacturacion" ), "UTF-8" );
            String cFecFactFin = URLEncoder.encode( (String) data.get( data.size() - 1 ).get( "fecFacturacion" ), "UTF-8" );
            String cLink = "https://app.snapcar.com.ar/wappCar/do/adm/factura/controlFecha.vm";

            cLink += "?" + "fecIni=" + cFecFactIni;
            cLink += "&" + "fecFin=" + cFecFactFin;
            sb.append( "<div><a href='" + cLink + "'>Acceso Sistema Facturación</a></div>\n" );
        }
        sb.append( "</body>\n" );
        sb.append( "</html>\n" );

        BufferedWriter writer = null;
        try {
            SimpleDateFormat sp = new SimpleDateFormat( "YYYYMMddHHmmss" );
            writer = new BufferedWriter( new FileWriter( "enviados/" + sp.format( new Date() ) + ".html" ) );
            writer.write( sb.toString() );
        } catch (IOException e) {
        } finally {
            try {
                if (writer != null)
                    writer.close();
            } catch (IOException e) {
            }
        }
        return sb.toString();
    }

    @SuppressWarnings("rawtypes")
    private String armaCsv(List data) {
        StringBuffer sb = new StringBuffer();

        sb.append( "Id. Vehiculo;" );
        sb.append( "Patente;" );
        sb.append( "Periodo Inicio;" );
        sb.append( "Periodo Fin;" );
        sb.append( "Inicio Vigencia;" );
        sb.append( "Id. Usuario;" );
        sb.append( "Nombre;" );
        sb.append( "Email;" );
        sb.append( "Descuento;" );
        sb.append( "Fecha Facturación\n" );
        sb.append( "tpCalculo;" );
        sb.append( "idVehiculo;" );
        sb.append( "patente;" );
        sb.append( "poliza;" );
        sb.append( "periodo;" );
        sb.append( "inicioVigencia;" );
        sb.append( "fecInstalacion;" );
        sb.append( "email;" );
        sb.append( "idUsuario;" );
        sb.append( "nombre;" );
        sb.append( "iniPeriodo;" );
        sb.append( "finperiodo;" );
        sb.append( "kms;" );
        sb.append( "kmsPond;" );
        sb.append( "score;" );
        sb.append( "descuentoKm;" );
        sb.append( "descuentoSinUso;" );
        sb.append( "descuentoPunta;" );
        sb.append( "descuentoSinPond;" );
        sb.append( "descuento;" );
        sb.append( "qViajes;" );
        sb.append( "qFrenadas;" );
        sb.append( "qAceleraciones;" );
        sb.append( "qExcesosVel;" );
        sb.append( "qCurvas;" );
        sb.append( "diasTotal;" );
        sb.append( "diasUso;" );
        sb.append( "diasPunta;" );
        sb.append( "diasSinMedicion;" );
        sb.append( "ultViaje;" );
        sb.append( "ultSincro;" );
        sb.append( "fecFacturacion\n" );

        for (int i = 0; i < data.size(); i++) {
            Map m = (Map) data.get( i );
            sb.append( m.get( "tpCalculo" ) + ";" );
            sb.append( m.get( "idVehiculo" ) + ";" );
            sb.append( m.get( "patente" ) + ";" );
            sb.append( m.get( "poliza" ) + ";" );
            sb.append( m.get( "periodo" ) + ";" );
            sb.append( m.get( "inicioVigencia" ) + ";" );
            sb.append( m.get( "fecInstalacion" ) + ";" );
            sb.append( m.get( "email" ) + ";" );
            sb.append( m.get( "idUsuario" ) + ";" );
            sb.append( m.get( "nombre" ) + ";" );
            sb.append( m.get( "iniPeriodo" ) + ";" );
            sb.append( m.get( "finperiodo" ) + ";" );
            sb.append( m.get( "kms" ) + ";" );
            sb.append( m.get( "kmsPond" ) + ";" );
            sb.append( m.get( "score" ) + ";" );
            sb.append( m.get( "descuentoKm" ) + ";" );
            sb.append( m.get( "descuentoSinUso" ) + ";" );
            sb.append( m.get( "descuentoPunta" ) + ";" );
            sb.append( m.get( "descuentoSinPond" ) + ";" );
            sb.append( m.get( "descuento" ) + ";" );
            sb.append( m.get( "qViajes" ) + ";" );
            sb.append( m.get( "qFrenadas" ) + ";" );
            sb.append( m.get( "qAceleraciones" ) + ";" );
            sb.append( m.get( "qExcesosVel" ) + ";" );
            sb.append( m.get( "qCurvas" ) + ";" );
            sb.append( m.get( "diasTotal" ) + ";" );
            sb.append( m.get( "diasUso" ) + ";" );
            sb.append( m.get( "diasPunta" ) + ";" );
            sb.append( m.get( "diasSinMedicion" ) + ";" );
            sb.append( m.get( "ultViaje" ) + ";" );
            sb.append( m.get( "ultSincro" ) + ";" );
            sb.append( m.get( "fecFacturacion" ) + "\n" );
        }
        return sb.toString();
    }

}
