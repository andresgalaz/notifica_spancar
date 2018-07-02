package snapCar.notif.continuo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.transcoder.Transcoder;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.util.XMLResourceDescriptor;
import org.apache.commons.lang.text.StrSubstitutor;
import org.apache.fop.svg.PDFTranscoder;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import prg.glz.FrameworkException;
import prg.util.cnv.ConvertDate;
import prg.util.cnv.ConvertException;
import prg.util.cnv.ConvertFile;
import prg.util.cnv.ConvertMap;
import prg.util.cnv.ConvertNumber;
import snapCar.amazon.UploadFile;
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
public class EndosoFactura {
    private static Logger logger = Logger.getLogger( EndosoFactura.class );
    private Connection    cnx;

    public EndosoFactura(Connection cnx) {
        this.cnx = cnx;
    }

    @SuppressWarnings({ "rawtypes" })
    public void procesa() throws FrameworkException, TranscoderException, IOException, SAXException, ParserConfigurationException {
        Locale.setDefault( new Locale( "es", "ES" ) );
        SimpleDateFormat fmtLargo = new SimpleDateFormat( "EEEE d 'de' MMMM 'de' YYYY " );
        SimpleDateFormat fmtSimple = new SimpleDateFormat( "dd/MM/YYYY" );
        SimpleDateFormat fmtPeriodo = new SimpleDateFormat( "YYYYMM" );
        
        UploadFile upFile = new UploadFile( Parametro.get( "bucketServer" ), Parametro.get( "bucketName" ) //
                , Parametro.get( "bucketAccessKey" ), Parametro.get( "bucketSecretKey" ) );
        String remotePath = Parametro.get( "bucketPath" );
        remotePath = ConvertFile.fixDir( remotePath == null ? "prorroga" : remotePath );

        // https://app.snapcar.com.ar/wappCar/adm/factura/plantillaFactura.svg
        String cFacturaSVG = utilHttp.read( Parametro.get( "factura_svg" ) );

        try {
            CalcAhorro calcAhorro = new CalcAhorro( this.cnx );

            PreparedStatement psUpd = cnx.prepareStatement( "UPDATE integrity.tMovim SET bPdfProrroga = '1' WHERE pMovim = ?" );

            String cSqlPpal = "SELECT \n"
                    + "       m.pMovim                                       , m.poliza                   as cPoliza \n"
                    + "     , m.nro_patente              as cPatente         , m.fecha_emision            as dEmision \n"
                    + "     , m.fecha_inicio_vig         as dInicioVig       , m.fecha_vencimiento        as dFinVig \n"
                    + "     , m.sumaaseg                 as nSumaAsegurada   , m.desc_vehiculo            as cVehiculo \n"
                    + "     , round(m.porcent_descuento) as nDescuento       , m.documento                as nDNI \n"
                    + "     , u.cEmail                                       , u.cNombre \n"
                    + "     , f.dInicio                                      , f.dFin \n"
                    + "     , ROUND(f.nKms)              as nKms             , ROUND(f.nScore)            as nScore\n"
                    + "     , f.nQViajes                                     , f.nQFrenada \n"
                    + "     , f.nQAceleracion                                , f.nQVelocidad \n"
                    + "     , f.nQCurva                                      , f.nDiasPunta \n"
                    + "     , f.nDiasUso - f.nDiasPunta  as nDiasNoPunta     , f.nDiasSinMedicion \n"
                    // AGALAZ: Da un error de UNSIGNED cuando nDiasSinMedicion es mayor a la cantidad de días
                    // + "     , datediff(f.dFin, f.dInicio) - f.nDiasUso - f.nDiasSinMedicion               as nDiasSinUso \n"
                    + "     , CASE WHEN (datediff(f.dFin, f.dInicio) - f.nDiasUso ) > f.nDiasSinMedicion \n"
                    + "          THEN datediff(f.dFin, f.dInicio) - f.nDiasUso - f.nDiasSinMedicion \n"
                    + "       ELSE \n"
                    + "          0 \n"
                    + "       END                        as nDiasSinUso \n"                     
                    + " FROM  integrity.tMovim m \n"
                    + "       INNER JOIN tVehiculo v ON v.cPatente = m.nro_patente AND v.bVigente = '1' \n"
                    + "       INNER JOIN tUsuario  u ON u.pUsuario = v.fUsuarioTitular \n"
                    + "       INNER JOIN tFactura  f ON f.pVehiculo = v.pVehiculo \n"
                    + "                             AND f.pTpFactura = 1 \n"
                    // Como la medición se hace 7 días antes del cierre, la fecha de vigencia de la prorroga siempre
                    // debería estar dentro rango de fechas de medición
                    + "                             AND (f.dFin + INTERVAL 7 DAY) BETWEEN m.fecha_inicio_vig AND m.fecha_vencimiento \n"
                    + " WHERE m.cod_endoso = '9900' \n"
                    + " AND   m.bPdfProrroga = '0' \n"
                    + " ORDER BY cPatente, dEmision desc \n";

            PreparedStatement psSql = cnx.prepareStatement( cSqlPpal );
            ResultSet rsNotif = psSql.executeQuery();
            // Prepara Webservice envía Mails
            CallWsMail callMail = new CallWsMail();
            while (rsNotif.next()) {
                Map<String, Object> mVal = ConvertMap.fromResultSet( rsNotif );

                // Formatea Fechas
                mVal.put( "cFecInicio", fmtSimple.format( mVal.get( "dInicio" ) ) );
                mVal.put( "cFecFin", fmtSimple.format( mVal.get( "dFin" ) ) );
                mVal.put( "cFecInicioVig", fmtSimple.format( mVal.get( "dInicioVig" ) ) );
                mVal.put( "cFecFinVig", fmtSimple.format( mVal.get( "dFinVig" ) ) );

                // Recupera PK
                int pMovim = (int) mVal.get( "pMovim" );
                // Patente y Periodo
                String cPatente = (String) mVal.get( "cPatente" );
                Date dInicioVig = ConvertDate.toSqlDate( mVal.get( "dInicioVig" ));
                // Recupera Email a enviar
                String cEmail = (String) mVal.get( "cEmail" );
                String cNombre = (String) mVal.get( "cNombre" );

                // Ajustes
                mVal.put( "cNombre", utilHttp.truncaHastaEspacio( (String) mVal.get( "cNombre" ), 30 ) );
                mVal.put( "cVehiculo", utilHttp.truncaHastaEspacio( (String) mVal.get( "cVehiculo" ), 25 ) );
                mVal.put( "nDNI", utilHttp.separaMiles( mVal.get( "nDNI" ) ) );
                mVal.put( "nSumaAsegurada", utilHttp.separaMiles( mVal.get( "nSumaAsegurada" ) ) );

                // Calcula ahorro a la fecha 
                calcAhorro.procesa( cPatente, dInicioVig );

                // Introduce los valores de ahorro y premio a mVal
                mVal.put( "nAhorro", utilHttp.separaMiles( calcAhorro.getnAhorro() ) );
                mVal.put( "nAhorroAcumulado", utilHttp.separaMiles( calcAhorro.getnAhorroAcum() ) );
                mVal.put( "nPremio", utilHttp.separaMiles( calcAhorro.getnPremio() ) );
                mVal.put( "nPremioSD", utilHttp.separaMiles( calcAhorro.getnPremioSD() ) );
                mVal.put( "nAhorroBruto", utilHttp.separaMiles( calcAhorro.getnAhorroBruto() ) );
                mVal.put( "nImpuestoAhorro", utilHttp.separaMiles( calcAhorro.getnImpuestoAhorro() ) );
                
                // Arma PDF, lo sube a S3 y envía el MAIL
                List<Map> to = callMail.createAddressTo( cNombre, cEmail );
                String cPeriodoFact = fmtPeriodo.format( ConvertDate.toDate( mVal.get( "dInicioVig" ) ) );
                String cNombrePDF = mVal.get( "cPatente" ) + "_" + cPeriodoFact + ".pdf";

                StrSubstitutor ss = new StrSubstitutor( mVal );
                ss.setVariablePrefix( "{{" );
                ss.setVariableSuffix( "}}" );
                String cFacturaSVGreplaced = ss.replace( cFacturaSVG );
                File fPdf = buildPDF( cNombrePDF, cFacturaSVGreplaced );
                if (fPdf != null) {
                    upFile.copy( fPdf, remotePath + cNombrePDF );
                    fPdf.delete();
                    logger.info( "Se genero PDF prorroga:" + fPdf );

                    // Arma mail para enviar
                    Map<String, Object> mValMail = new HashMap<String, Object>();
                    mValMail.put( "cPrimerNombre", utilHttp.primerNombre( (String) mVal.get( "cNombre" ) ) );
                    mValMail.put( "cFecInicio", mVal.get( "cFecInicio" ) );
                    mValMail.put( "cFecFin", mVal.get( "cFecFin" ) );
                    mValMail.put( "cFecFinLarga", fmtLargo.format( mVal.get( "dFin" ) ) );
                    mValMail.put( "nDescuento", mVal.get( "nDescuento" ) );
                    mValMail.put( "nAhorro", calcAhorro.getnAhorro() );
                    mValMail.put( "cVehiculo", mVal.get( "cVehiculo" ) );

                    try {
                        String cLinkPoliza = Parametro.get( "file_repos" ) + "poliza/" + cPatente + "_" + cPeriodoFact + ".pdf";
                        // Si el archivo no está presente, el mail no se envía, para no hacer el ridículo enviando mails
                        // con LINK rotos.
                        if (utilHttp.existeUrl( cLinkPoliza )) {
                            // int nDescuento = ConvertNumber.toInteger( mVal.get( "nDescuento" ) );
                            int nScore = ConvertNumber.toInteger( mVal.get( "nScore" ) );
                            int nDiasSinMedicion = ConvertNumber.toInteger( mVal.get( "nDiasSinMedicion" ) );

                            mValMail.put( "cAsunto", cPatente );
                            mValMail.put( "cLinkProrroga", Parametro.get( "file_repos" ) + remotePath + cNombrePDF );
                            mValMail.put( "cLinkPoliza", cLinkPoliza );

                            // callMail.ejecuta( "prorroga_noSync", "prorroga", to, mValMail );
                            // callMail.ejecuta( "prorroga_10_40", "prorroga", to, mValMail );
                            // callMail.ejecuta( "prorroga_10_90a", "prorroga", to, mValMail );
                            // callMail.ejecuta( "prorroga_10_90b", "prorroga", to, mValMail );
                            // callMail.ejecuta( "prorroga_recargo", "prorroga", to, mValMail );

                            if (nDiasSinMedicion >= 15 && calcAhorro.getnDescuento() <= 0 ) {
                                // Descuento 0 sin sincronización
                                callMail.ejecuta( "prorroga_noSync", "prorroga", to, mValMail );
                            } else if (calcAhorro.getnDescuento() > 10) {
                                // Descuento entre 10 y 40
                                callMail.ejecuta( "prorroga_10_40", "prorroga", to, mValMail );
                            } else if (calcAhorro.getnDescuento() >= 0) {
                                // Descuento entre 0 y 10
                                if (nScore > 90)
                                    callMail.ejecuta( "prorroga_10_90a", "prorroga", to, mValMail );
                                else
                                    callMail.ejecuta( "prorroga_10_90b", "prorroga", to, mValMail );
                            } else {
                                // Recargo
                                callMail.ejecuta( "prorroga_recargo", "prorroga", to, mValMail );
                            }

                            // Marca como enviada la prorroga
                            psUpd.setInt( 1, pMovim );
                            psUpd.execute();
                            cnx.commit();
                        } else {
                            logger.warn( "No se envió mail de prorroga porque el PDF de póliza no está en S3: " + cLinkPoliza );
                        }
                    } catch (FrameworkException e) {
                        logger.error( "Al enviar mail de PRORROGA a " + cEmail + " por la patente " + cNombre, e );
                    }
                }
            }
            rsNotif.close();
            psSql.close();
            psUpd.close();
            calcAhorro.close();

        } catch (ConvertException e) {
            logger.error( "Al convertir fechas", e );
            throw new RuntimeException( e );
        } catch (SQLException e) {
            logger.error( "Al enviar prorrogas", e );
            throw new RuntimeException( e );
        }
    }

    /**
     * <p>
     * Convierte de SVG a PDF
     * </p>
     * 
     * @param cNombrePDF
     * @param cSVG
     * @return
     */
    private File buildPDF(String cNombrePDF, String cSVG) {
        StringReader reader = null;
        try {
            reader = new StringReader( cSVG );
            // Crea XML Document a partir de un String que contiene el SVG
            String parser = XMLResourceDescriptor.getXMLParserClassName();
            SAXSVGDocumentFactory f = new SAXSVGDocumentFactory( parser );
            String uri = "local"; // Parametro.get( "factura_svg" );
            Document doc = f.createDocument( uri, reader );
            TranscoderInput input_svg_image = new TranscoderInput( doc );
            // Define la salida OutputStream para el PDF y asocia a TranscoderOutput
            OutputStream pdf_ostream = new FileOutputStream( cNombrePDF );
            TranscoderOutput output_pdf_file = new TranscoderOutput( pdf_ostream );
            // Crea un PDF Transcoder and define el como y ajusta el tamaño del PDF
            Transcoder transcoder = new PDFTranscoder();
            // transcoder.addTranscodingHint( PDFTranscoder.KEY_PIXEL_UNIT_TO_MILLIMETER, (25.4f / 72f) );
            transcoder.addTranscodingHint( PDFTranscoder.KEY_PIXEL_UNIT_TO_MILLIMETER, 1.0f );
            // Escribe la salida en formato PDF
            transcoder.transcode( input_svg_image, output_pdf_file );
            // Cierra el Stream
            pdf_ostream.flush();
            pdf_ostream.close();

            return new File( cNombrePDF );
        } catch (Exception e) {
            logger.error( "No se pudo generar PDF a partir del SVG", e );
            return null;
        } finally {
            if (reader != null)
                reader.close();
        }
    }

}
