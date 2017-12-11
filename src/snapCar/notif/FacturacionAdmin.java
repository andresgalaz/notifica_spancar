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

import prg.util.cnv.ConvertMap;
import prg.util.cnv.ConvertTimestamp;
import snapCar.mail.Mail;
import snapCar.notif.config.Parametro;

/**
 * <p>
 * Envia mensajes al área Administrativa informando las facturaciones realizadas
 * </p>
 * 
 * @author agalaz
 *
 */
public class FacturacionAdmin {
    private static Logger    logger         = Logger.getLogger( FacturacionAdmin.class );
    private Connection       cnx;
    private Mail             mail;

    private static final int DIAS_AL_CIERRE = -2;

    public FacturacionAdmin(Connection cnx, Mail mail) {
        this.cnx = cnx;
        this.mail = mail;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void procesa() throws SQLException {
        // Este llama al que hace el cálculo: Primer parámetro es el usuario, segundo parámetro es el vehiculo
        PreparedStatement psExecCalc = cnx.prepareStatement( "{CALL prFacturador( ? )}" );
        // Para un vehñiculo solo dedebería generarse un registro en wMemoryScoreVehiculo por cada vez que se
        // calcula

        PreparedStatement psListCalc = null;
        {
            String cSqlCampos = ""
                    + "       pVehiculo        as idVehiculo    , pUsuario         as idUsuario     , dInicio          as iniPeriodo      \n"
                    + "     , dFin             as finPeriodo    , dInstalacion     as fecInstalacion, tUltimoViaje     as ultViaje        \n"
                    + "     , tUltimaSincro    as ultSincro     , nKms             as kms           , nKmsPond         as kmsPond         \n"
                    + "     , nScore           as score         , nQViajes         as qViajes       , nQFrenada        as qFrenadas       \n"
                    + "     , nQAceleracion    as qAceleraciones, nQVelocidad      as qExcesosVel   , nQCurva          as qCurvas         \n"
                    + "     , nDescuento       as descuento     , nDescuentoKM     as descuentoKm   , nDescuentoSinUso as descuentoSinUso \n"
                    + "     , nDescuentoPunta  as descuentoPunta, nDiasTotal       as diasTotal     , nDiasUso         as diasUso         \n"
                    + "     , nDiasPunta       as diasPunta     , nDiasSinMedicion as diasSinMedicion \n"
                    + "     , (nDescuentoKM + nDescuentoSinUso + nDescuentoPunta)  as descuentoSinPond \n";

            String cSql = ""
                    + "SELECT 'Real' as tpCalculo, " + cSqlCampos + " FROM  wMemoryScoreVehiculo \n"
                    + "UNION ALL \n"
                    + "SELECT 'Sin multa' cTpFactura, " + cSqlCampos + " FROM  wMemoryScoreVehiculoSinMulta \n";

            psListCalc = cnx.prepareStatement( cSql );
        }

        // Armado cursor que detecta los vehículos a facturar
        String cSql = "SELECT v.pVehiculo \n"
                + "      , v.cPatente \n"
                + "      , v.cPoliza \n"
                + "      , v.fUsuarioTitular \n"
                + "      , u.cNombre, u.cEmail \n"
                + "      , v.dIniVigencia \n"
                + " FROM   tVehiculo v \n"
                + "        JOIN tUsuario u ON u.pUsuario = v.fUsuarioTitular \n"
                + " WHERE  v.cPoliza is not null \n"
                + " AND    v.bVigente = '1' \n"
                + " AND    fnPeriodoActual(v.dIniVigencia, 0) > v.dIniVigencia \n"
                // Dias al cierre
                + " AND    datediff(fnPeriodoActual(v.dIniVigencia, 0),now()) = ? \n";
        PreparedStatement psSql = cnx.prepareStatement( cSql );
        psSql.setInt( 1, DIAS_AL_CIERRE );
        ResultSet rsNotif = psSql.executeQuery();

        String cFecFacturacion = ConvertTimestamp.toString( new Date() );
        List<Map> dataFactura = new ArrayList<Map>();
        while (rsNotif.next()) {
            int pVehiculo = rsNotif.getInt( "pVehiculo" );
            String cPatente = rsNotif.getString( "cPatente" );
            int pUsuario = rsNotif.getInt( "fUsuarioTitular" );
            String cNombre = rsNotif.getString( "cNombre" );
            String cEmail = rsNotif.getString( "cEmail" );
            String cPoliza = rsNotif.getString( "cPoliza" );
            String cFecIniVigencia = rsNotif.getString( "dIniVigencia" );
            // Factura
            psExecCalc.setInt( 1, pVehiculo );
            psExecCalc.execute();

            ResultSet rsDet = psListCalc.executeQuery();
            while (rsDet.next()) {
                Map mReg = ConvertMap.fromResultSet( rsDet );
                mReg.put( "patente", cPatente );
                mReg.put( "idUsuario", pUsuario );
                mReg.put( "nombre", cNombre );
                mReg.put( "email", cEmail );
                mReg.put( "poliza", cPoliza );
                mReg.put( "inicioVigencia", cFecIniVigencia );
                mReg.put( "fecFacturacion", cFecFacturacion );

                dataFactura.add( mReg );
            }
        }
        rsNotif.close();
        psSql.close();
        psExecCalc.close();
        psListCalc.close();
        // Una vez cerrado los cursores, deberíamos tener en la lista dataFactura todos los registros facturados, se
        // envía una mail administrativo de control
        if (dataFactura.size() > 0) {
            // Prepara un adjunto del tipo CSV con la información
            MimeBodyPart adjCsv;
            try {
                adjCsv = this.mail.creaAdjunto( "facturacion-" + cFecFacturacion.replaceAll( ":", "" ) + ".csv", Mail.TP_ADJUNTO_CSV //
                // Los CSV si se levantan con Windows en mejor codificación ISO que UTF
                        , armaCsv( dataFactura ).getBytes( "ISO8859-1" ) );
                String cFrom = Parametro.get( "mail_from" );
                String cToEmails = Parametro.get( "mail_admin" );

                this.mail.envia( "Facturación " + cFecFacturacion, cFrom, cToEmails, armaMail( dataFactura ), adjCsv );
            } catch (UnsupportedEncodingException e) {
                logger.error( "No se pudo crear CSV por error de codificación", e );
                throw new RuntimeException( e );
            } catch (MessagingException e) {
                logger.error( "No se pudo enviar el mail", e );
                throw new RuntimeException( e );
            }
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
        sb.append( "finPeriodo;" );
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
            sb.append( m.get( "finPeriodo" ) + ";" );
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
