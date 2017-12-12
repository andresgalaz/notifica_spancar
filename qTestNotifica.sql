-- select * from tVehiculo where cPatente='NAG223';
-- update tVehiculo set dIniVigencia = dIniVigencia + interval - 1 day;
drop table wMemoryCierreTransf;
call prControlCierreTransferenciaInicioDef(1);

SELECT v.pVehiculo 
      , v.cPatente 
      , v.cPoliza 
      , u.cNombre, u.cEmail 
, datediff(fnPeriodoActual(v.dIniVigencia, 0),now()) nDiasAlCierre      
 FROM   tVehiculo v 
        JOIN tUsuario u ON u.pUsuario = v.fUsuarioTitular 
 WHERE  v.cPoliza is not null 
 AND    v.bVigente = '1' 
 AND    fnPeriodoActual(v.dIniVigencia, 0) > v.dIniVigencia 
 AND    datediff(fnPeriodoActual(v.dIniVigencia, 0),now()) = -2;