# SDPlayer Lite (Beta)

Reproductor de música sencillo para Android, hecho con Kotlin, Jetpack Compose y Media3.

Versión actual: **1.0 (beta)**. Paquete: `com.sd.play`.

## Características

- **Tres pestañas deslizables:** Álbumes, Carpetas y Todos. La app abre directamente en Carpetas.
- **Carpetas en una sola lista:** muestra todas las carpetas que tienen música, incluidas las anidadas, con su número de canciones. No se muestran rutas de archivos.
- **Búsqueda:** lupa en la cabecera. Filtra por título, artista y álbum en las tres pestañas.
- **Orden seleccionable:** A–Z, Recientes o Más escuchadas.
- **Portadas:** miniaturas en las listas y portada grande en el reproductor completo, tomadas de la biblioteca de Android.
- **Índice A–Z** en Todos, para saltar a una letra. Aparece con el orden A–Z.
- **Reproducción en segundo plano** con notificación y controles en la pantalla de bloqueo, audífonos y auto.
- **Reproductor a pantalla completa:** anterior, pausa, siguiente, aleatorio y repetir (desactivado, canción o carpeta).
- **Mini reproductor flotante** con barra de progreso. Se abre deslizando hacia arriba y se minimiza deslizando hacia abajo.
- **Cola por carpeta:** al tocar una canción, se reproducen las de su misma carpeta.
- **Botón ⏮:** siempre va a la canción anterior cuando existe, sin reiniciar la actual.
- **Modo Likes:** reproduce en aleatorio las 40 canciones más escuchadas.
- **Biblioteca en vivo:** la lista se actualiza cuando se agregan o quitan archivos.
- **Modo oscuro** según el sistema.

## Requisitos

- Android 8.0 (API 26) o superior.
- Procesador arm64 (`arm64-v8a`).

## Permisos

- **Audio** (`READ_MEDIA_AUDIO` o `READ_EXTERNAL_STORAGE` en versiones anteriores): obligatorio. Sin él la app se cierra.
- **Notificaciones:** para mostrar los controles de reproducción.
- **Optimización de batería:** opcional. Si no se concede, la app muestra un aviso arriba para pedirlo.

La app no usa anuncios ni envía datos a servidores.

## Cómo busca canciones nuevas

Mientras la app está visible, recorre el almacenamiento de forma recursiva cada 0,1 segundos y pide a Android indexar los audios que todavía no aparecen. Cuando la app se oculta, se detiene. Esto consume más batería mientras la app está abierta.

## Instalar la beta

1. Entra a la pestaña **Actions** del repositorio y abre la última ejecución en verde.
2. En **Artifacts**, descarga `SDPlayer-Lite-Beta-1.0`.
3. Descomprime el archivo y abre el APK en tu teléfono. Si el sistema lo pide, permite instalar apps de esa fuente.
4. Al abrir la app, acepta el permiso de audio.

El APK está firmado con una clave temporal, solo para poder instalarlo durante la beta.

## Compilar

El proyecto se compila con GitHub Actions. Cada cambio en `main` ejecuta `.github/workflows/build.yml`, que genera `app-release.apk`.

Para compilarlo en tu computadora con Android Studio, abre la carpeta del proyecto y ejecuta `Build > Build APK(s)`.

## Estructura

| Archivo | Función |
|---|---|
| `MainActivity.kt` | Permisos, cabecera con búsqueda y orden, pestañas, escaneo y reproductor |
| `PlaybackService.kt` | Servicio de reproducción con Media3 y conteo de escuchas |
| `Player.kt` | Mini reproductor, reproductor completo, portadas y controles |
| `Screens.kt` | Pantallas de Álbumes, Carpetas y Todos, con índice A–Z |
| `Library.kt` | Lectura de canciones desde MediaStore, orden y carpetas |
| `Scanner.kt` | Escaneo recursivo de archivos de audio |
| `Data.kt` | Base de datos Room para el modo Likes |
| `Theme.kt` | Colores, formas y modo oscuro |

## Estado

Beta. Pendiente:

- Logo e ícono definitivos. Por ahora usa un ícono de disco de vinilo.
- Soporte para tarjeta SD en la pestaña Carpetas.
- Escaneo más eficiente para bibliotecas muy grandes.

## Licencia

Por definir.
