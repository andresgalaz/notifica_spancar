package snapCar.factura;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class CalcAhorro {
    private PreparedStatement psAhorro;
    private int               nDescuento;
    private int               nAhorro;
    private int               nAhorroAcum;
    private int               nPremio;
    private int               nPremioSD;
    private int               nImpuestoAhorro;
    private int               nAhorroBruto;

    public CalcAhorro(Connection cnx) throws SQLException {
        this.psAhorro = cnx.prepareStatement( "SELECT ROUND(nDescuento) nDescuento\n"
                + " , ROUND(nPrima)  nPrima , ROUND(nPrimaSD)  nPrimaSD \n"
                + " , ROUND(nPremio) nPremio, ROUND(nPremioSD) nPremioSD \n"
                + " FROM  vFacturaProrroga \n"
                + " WHERE cPatente = ? \n"
                + " AND   dInicio <= ? \n"
                + " ORDER BY dEmision \n" );
    }

    // public void procesa(String cPatente, java.util.Date dInicioVig) throws SQLException {
    // try {
    // procesa( cPatente, ConvertDate.toSqlDate( dInicioVig ) );
    // } catch (ConvertException e) {
    // throw new SQLException( "Al convertir fecha inicio:" + dInicioVig, e );
    // }
    // }

    public void procesa(String cPatente, java.sql.Date dInicioVig) throws SQLException {
        if(dInicioVig==null)
            dInicioVig = new java.sql.Date(new java.util.Date().getTime());
        // Abre cursor sobre la facturas previas
        psAhorro.setString( 1, cPatente );
        psAhorro.setObject( 2, dInicioVig );
        ResultSet rsAhorro = psAhorro.executeQuery();

        // Inicializa valores
        nDescuento = nAhorro = nAhorroAcum = nPremio = nPremioSD = nImpuestoAhorro = nAhorroBruto = 0;

        // Calcula
        while (rsAhorro.next()) {
            // Se leen todos pero solo interesa mantener la última fila
            nDescuento = rsAhorro.getInt( "nDescuento" );
            // nPrima = rsAhorro.getInt( "nPrima" );
            nPremio = rsAhorro.getInt( "nPremio" );
            if (nDescuento == 0) {
                // nPrimaSD = nPrima;
                nPremioSD = nPremio;
                nAhorro = 0;
            } else {
                // nPrimaSD = rsAhorro.getInt( "nPrimaSD" );
                nPremioSD = rsAhorro.getInt( "nPremioSD" );
                nAhorro = nPremioSD - nPremio;
            }
            nAhorroAcum += nAhorro;
        }
        rsAhorro.close();

        // Valores para forzar el descuento al premio y no la prima
        if (nDescuento != 0) {
            // Esto es para mostrar el descuento como si fuera en el premio ( y no la prima técnica como en
            // realida es ). Es una ocurrencia de Bruno.
            nAhorroBruto = (int) Math.round( nPremioSD * nDescuento / 100.0 );
            // Esto no tiene ningún sentido, pero Bruno insiste
            nImpuestoAhorro = nAhorro - nAhorroBruto;
        }
    }

    public int getnDescuento() {
        return nDescuento;
    }

    public int getnAhorro() {
        return nAhorro;
    }

    public int getnAhorroAcum() {
        return nAhorroAcum;
    }

    public int getnPremio() {
        return nPremio;
    }

    public int getnPremioSD() {
        return nPremioSD;
    }

    public int getnImpuestoAhorro() {
        return nImpuestoAhorro;
    }

    public int getnAhorroBruto() {
        return nAhorroBruto;
    }

    public void close() throws SQLException {
        if (psAhorro != null)
            psAhorro.close();
    }
}
