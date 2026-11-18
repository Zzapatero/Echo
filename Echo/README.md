# Echo

Echo es una aplicación de mensajería en tiempo real construida en Java. Mantiene sockets TCP para el transporte y ofrece una interfaz web adaptable, hecha con HTML, CSS y JavaScript, con canales, directorio de usuarios y conversaciones privadas.

## Funciones

- Canales: General, Fútbol, Inteligencia Artificial, Noticias, Actualidad y Tecnología.
- Registro e inicio de sesión desde la interfaz.
- Claves almacenadas con PBKDF2 y una sal aleatoria. Las cuentas heredadas se migran al iniciar el servidor y deben elegir una clave nueva en su siguiente acceso.
- Búsqueda de personas registradas con indicador de conexión.
- Mensajes privados a personas conectadas o desconectadas. Los pendientes se guardan y se confirman al recibirlos.
- Historial de canales guardado en CSV.
- Interfaz web adaptable con buscador de personas, presencia y canales.
- Actualización periódica de eventos a través de sesiones TCP persistentes.

## Arquitectura

El servidor TCP escucha en `127.0.0.1:5000` y atiende cada conexión en su propio hilo. El servidor HTTP local sirve la interfaz en `127.0.0.1:8080`; cada navegador conserva una sesión TCP y la web consulta los eventos periódicamente. Los comandos de protocolo se traducen a acciones de usuarios, canales y mensajes. Los CSV locales almacenan las cuentas y el historial; los mensajes privados pendientes se guardan hasta confirmar su entrega.

La web y el servidor solo escuchan en loopback, por lo que Echo está pensado para usarse en el mismo equipo. Para acceso remoto haría falta añadir TLS. La aplicación no requiere frameworks, dependencias externas ni recursos de CDN. El JAR incluido en `lib/` se usa únicamente para las pruebas existentes.

## Requisitos

- JDK 8 o posterior.
- Windows, macOS o Linux con un navegador moderno.

## Compilar y ejecutar en Windows PowerShell

Abre una terminal en la carpeta `Echo` de este proyecto, junto a la carpeta `web`. Mantén esa carpeta como directorio de trabajo para que los archivos CSV locales se creen allí; están excluidos del repositorio.

Compila las clases de producción en una carpeta temporal:

```powershell
$clases = Join-Path $env:TEMP 'echo-classes'
New-Item -ItemType Directory -Force $clases | Out-Null
$fuentes = Get-ChildItem .\Echo -Recurse -Filter *.java |
    Where-Object { $_.FullName -notmatch '[\\/]Pruebas[\\/]' }
javac -encoding UTF-8 -d $clases $fuentes.FullName
```

Inicia el servidor TCP y el puente web:

```powershell
java -cp $clases Servidor.ServidorChat
```

Abre `http://127.0.0.1:8080` en tu navegador, crea una cuenta y empieza a conversar. El servidor debe permanecer abierto mientras uses Echo. Para detenerlo, pulsa `Ctrl+C` en la terminal.

Las cuentas nuevas eligen su clave al registrarse. Si tienes una cuenta local migrada de la versión anterior, Echo te pedirá cambiarla en el primer acceso.

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
