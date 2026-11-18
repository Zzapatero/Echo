# Echo

Echo es una aplicación de mensajería en tiempo real construida en Java. Mantiene sockets TCP para el transporte y ofrece una interfaz de escritorio Swing con tema oscuro, canales, directorio de usuarios y conversaciones privadas.

## Funciones

- Canales: General, Fútbol, Inteligencia Artificial, Noticias, Actualidad y Tecnología.
- Registro e inicio de sesión desde la interfaz.
- Claves almacenadas con PBKDF2 y una sal aleatoria. Las cuentas heredadas se migran al iniciar el servidor y deben elegir una clave nueva en su siguiente acceso.
- Búsqueda de personas registradas con indicador de conexión.
- Mensajes privados a personas conectadas o desconectadas. Los pendientes se guardan y se confirman al recibirlos.
- Historial de canales guardado en CSV.
- Panel local para pausar conexiones, activar mantenimiento y consultar métricas.

## Arquitectura

El servidor TCP escucha en el puerto `5000` y atiende cada conexión en su propio hilo. Los comandos de protocolo se traducen a acciones de usuarios, canales y mensajes. Los usuarios y los mensajes de canal usan CSV; los mensajes privados pendientes se guardan en `mensajes_privados.csv`.

La interfaz cliente es Swing y se conecta por TCP a `127.0.0.1:5000`. El servidor escucha solo en loopback: esta configuración es para uso local en el mismo equipo; no expone credenciales en una red local. Para acceso remoto haría falta añadir TLS. No requiere frameworks ni dependencias externas para la aplicación. El JAR incluido en `lib/` se usa únicamente para las pruebas existentes.

## Requisitos

- JDK 8 o posterior.
- Windows, macOS o Linux con entorno gráfico para usar las interfaces Swing.

## Compilar y ejecutar en Windows PowerShell

Abre una terminal en la carpeta `Echo` de este proyecto. Mantén esa carpeta como directorio de trabajo: los CSV de cuentas y mensajes se crearán localmente al usar la aplicación y están excluidos del repositorio.

Compila las clases de producción en una carpeta temporal:

```powershell
$clases = Join-Path $env:TEMP 'echo-classes'
$fuentes = Get-ChildItem .\Echo -Recurse -Filter *.java |
    Where-Object { $_.FullName -notmatch '[\\/]Pruebas[\\/]' }
javac -encoding UTF-8 -d $clases $fuentes.FullName
```

En una primera terminal, inicia el servidor y su panel de administración:

```powershell
java -cp $clases Servidor.ServidorChat
```

En otra terminal, situada también en esta carpeta, abre el cliente:

```powershell
java -cp $clases Cliente.VentanaChat
```

Las cuentas que ya existían conservan su clave temporal para el primer acceso; después, Echo obliga a reemplazarla por una de al menos 12 caracteres. Las cuentas nuevas eligen su propia clave al registrarse.

## Pruebas existentes

Las pruebas usan archivos CSV relativos al directorio actual. Ejecútalas desde una carpeta temporal para no modificar los datos de la aplicación:

```powershell
$raiz = (Get-Location).Path
$temporal = Join-Path $env:TEMP ('echo-tests-' + [Guid]::NewGuid().ToString('N'))
$clasesPrueba = Join-Path $temporal 'classes'
New-Item -ItemType Directory -Path $clasesPrueba | Out-Null
$fuentes = Get-ChildItem .\Echo -Recurse -Filter *.java
$junit = Join-Path $raiz 'lib\junit-platform-console-standalone-1.13.0-M3.jar'
javac -encoding UTF-8 -cp $junit -d $clasesPrueba $fuentes.FullName
Push-Location $temporal
try {
    java -jar $junit execute --class-path $clasesPrueba --scan-class-path --details summary
} finally {
    Pop-Location
}
```
