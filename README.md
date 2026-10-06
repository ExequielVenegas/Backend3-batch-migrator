# Batch Migrator: MySQL local

## Compilar y probar

Requisitos: JDK 21+ y Maven Wrapper incluido.

```powershell
.\mvnw.cmd clean package
```

Las pruebas usan H2 en modo MySQL: no escriben en tu MySQL. Verifican los tres
jobs, el rechazo de cargas completadas con los mismos parámetros y la conservación
de datos al repetir los scripts de negocio. La validación real MySQL queda separada.
