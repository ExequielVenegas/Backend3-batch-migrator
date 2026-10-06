# Batch Migrator: MySQL local

Proceso puntual de carga ETL. Se ejecuta en el PC, lee CSV y escribe en MySQL;
no se despliega en EC2 ni se arranca automáticamente con el Compose de servicios.
Los datos existentes ya permiten probar Account y los BFF.

## Jobs y destinos

| Job | Entrada predeterminada | Tabla destino |
|---|---|---|
| reporteTransaccionesJob | input/transacciones.csv | transacciones_diarias |
| calculoInteresesJob | input/intereses.csv | calculo_intereses |
| generacionEstadosCuentaJob | input/cuentas_anuales.csv | estado_cuenta_anual |

Las entradas predeterminadas son recursos classpath. Para CSV externos se
configuran batch.input.transacciones, batch.input.intereses y
batch.input.cuentas-anuales con una ubicación file:. Elegir explícitamente
spring.batch.job.name; el job por defecto actual es generacionEstadosCuentaJob.
No ejecutar el JAR sin revisar el job y sus parámetros, porque el arranque
habilita la ejecución batch.

## Compilar y probar

Requisitos: JDK 21+ y Maven Wrapper incluido.

```powershell
.\mvnw.cmd clean package
```
