# SDPlayer Lite (Beta)

Reproductor de música sencillo para Android, hecho con Kotlin, Jetpack Compose y Media3.

Versión actual: **1.0 (beta)**. Paquete: `com.sd.play`.

## Características

- **Tres pestañas deslizables:** Álbumes, Carpetas y Todos.
- **Carpetas estilo explorador:** la raíz se muestra como `root` y cada subcarpeta se abre al tocarla. No se muestran rutas.
- **Reproducción en segundo plano** con notificación y controles en la pantalla de bloqueo, audífonos y auto.
- **Reproductor a pantalla completa:** anterior, pausa, siguiente, aleatorio y repetir (desactivado, canción o carpeta).
- **Mini reproductor flotante:** se abre deslizando hacia arriba y se minimiza deslizando hacia abajo.
- **Cola por carpeta:** al tocar una canción, se reproducen las de su misma carpeta.
- **Botón ⏮:** siempre va a la canción anterior cuando existe, sin reiniciar la actual.
- **Modo Likes:** reproduce en aleatorio las 40 canciones más escuchadas.
- **Biblioteca en vivo:** la lista se actualiza sola cuando se agregan o quitan archivos mientras la app está abierta.
- **Modo oscuro** según el sistema.

## Requisitos

- Android 8.0 (API 26) o superior.
- Procesador arm64 (`arm64-v8a`).

## Permisos

- **Audio** (`READ_MEDIA_AUDIO` o `READ_EXTERNAL_STORAGE` en versiones anteriores): obligatorio. Sin él la app se cierra.
- **Notificaciones:** para mostrar los controles de reproducción.
- **Optimización de batería:** opcional. Si no se concede, la app muestra un aviso arriba para pedirlo.

La app no usa anuncios ni envía datos a servidores.

## Instalar la beta

1. Entra a la pestaña **Actions** del repositorio y abre la última ejecución en verde.
2. En **Artifacts**, descarga `SDPlayer-Lite-Beta-1.0`.
3. Descomprime el archivo y abre el APK en tu teléfono. Si el sistema lo pide, permite instalar apps de esa fuente.
4. Al abrir la app, acepta el permiso de audio.

El APK está firmado con una clave temporal, solo para poder instalarlo durante la beta.

## Compilar

El proyecto se compila con GitHub Actions. Cada cambio en `main` ejecuta el archivo `.github/workflows/build.yml`, que genera `app-release.apk`.

Para compilarlo en tu computadora con Android Studio, abre la carpeta del proyecto y ejecuta `Build > Build APK(s)`.

## Estructura

| Archivo | Función |
|---|---|
| `MainActivity.kt` | Permisos, pestañas, banner de batería, escaneo y navegación |
| `PlaybackService.kt` | Servicio de reproducción con Media3 y conteo de escuchas |
| `Player.kt` | Mini reproductor, reproductor completo y controles |
| `Screens.kt` | Pantallas de Álbumes, Carpetas y Todos |
| `Library.kt` | Lectura de canciones desde MediaStore |
| `Scanner.kt` | Escaneo recursivo de archivos de audio |
| `Data.kt` | Base de datos Room para el modo Likes |
| `Theme.kt` | Colores y formas |

## Estado

Beta. Pendiente:

- Logo e ícono definitivos.
- Soporte para tarjeta SD en la pestaña Carpetas.
- Escaneo más eficiente para bibliotecas muy grandes.

## Licencia

Por definir.
