-- select * from tVehiculo where cPatente='NAG223';
-- update tVehiculo set dIniVigencia = dIniVigencia + interval - 1 day;
drop table wMemoryCierreTransf;
call prControlCierreTransferenciaInicioDef(1);
SELECT w.cPatente , w.dIniVigencia
     , DATE_FORMAT(w.dProximoCierre + INTERVAL -1 MONTH, '%d/%m/%Y')    dInicio 
     , DATE_FORMAT(w.dProximoCierre                    , '%d/%m/%Y')    dFin 
     , w.nDiasNoSincro 
     , u.cEmail, u.cNombre                                              cNombre 
     , GREATEST( IFNULL(DATE( w.tUltTransferencia), '0000-00-00') 
               , IFNULL(DATE( w.tUltViaje        ), '0000-00-00') 
               , IFNULL(DATE( w.tUltControl      ), '0000-00-00'))      dSincro 
, nDiasAlCierre               
, nDiasNoSincro
, w.tUltTransferencia, w.tUltViaje, w.tUltControl
 FROM  wMemoryCierreTransf w 
       JOIN tUsuario u ON u.pUsuario = w.fUsuarioTitular 
 WHERE 1=1 -- cPatente = 'LDP315' -- cNombre like '%medi%' -- nDiasAlCierre = -4
-- AND   nDiasNoSincro > 0
 AND   w.cPoliza is not null 
 AND   w.bVigente = '1' order by dInicio;
 

update tVehiculo set dIniVigencia = dIniVigencia + interval - 1 day;
SELECT v.pVehiculo 
      , v.cPatente 
      , v.cPoliza 
      , u.cNombre, u.cEmail 
      , v.dIniVigencia
      , fnPeriodoActual(v.dIniVigencia, 0) dIni
      , v.*
 FROM   tVehiculo v 
        JOIN tUsuario u ON u.pUsuario = v.fUsuarioTitular 
 WHERE  v.cPoliza is not null 
 AND    v.bVigente = '1'
 AND    fnPeriodoActual(v.dIniVigencia, 0) > v.dIniVigencia 
 AND    datediff(fnPeriodoActual(v.dIniVigencia, 0),now()) = -2;
 
 CALL prFacturador( 406 );
 select * from tFactura order by tCreacion desc;
 select * from wMemoryScoreVehiculo;

SET @mesDesface=0;

			SELECT	v.pVehiculo, v.dIniVigencia,
					score.fnPeriodoActual( v.dIniVigencia, -1 + @mesDesface ) dIniCierre,
					score.fnPeriodoActual( v.dIniVigencia, 0 + @mesDesface ) dFinCierre
			FROM	score.tVehiculo v
			WHERE	v.fTpDispositivo = 3
			AND		v.cIdDispositivo is not null
			AND		v.bVigente in ('1')
			AND		v.pVehiculo=406 ;
