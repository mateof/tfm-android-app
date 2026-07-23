# TFM Android

Cliente Android (Kotlin + Jetpack Compose, Material 3) para
**TelegramFileManager**: gestiona los ficheros que guardas en canales de
Telegram a través de la API REST v1 + SignalR del servidor.

## Funcionalidades

- **Primer arranque guiado**: configuración de URL del servidor y API key, con
  verificación de conexión antes de guardar.
- **Login de Telegram** (sesión compartida del servidor): por **QR** (con
  deep-link `tg://login` si Telegram está en el mismo dispositivo, y soporte de
  2FA) o por **teléfono** (código + contraseña 2FA).
- **Canales**: guardados / todos / favoritos, búsqueda, detalles con estadísticas,
  crear canal, unirse por invitación, crear/actualizar índice, mensajes recientes
  con descarga directa de adjuntos, salir del canal.
- **Explorador de ficheros del canal**: navegación con breadcrumbs, filtros por
  tipo, ordenación, búsqueda recursiva, multi-selección, crear carpeta, renombrar,
  copiar/mover, eliminar, **subir ficheros desde el dispositivo** y descargar
  **al servidor** (transfer gestionado) o **al dispositivo** (DownloadManager,
  carpeta `Download/TFM`).
- **Ficheros locales del servidor**: navegación, filtros, subir desde el
  dispositivo, **enviar a un canal de Telegram** (sin duplicar bytes), renombrar,
  eliminar, vaciar caché de streaming.
- **Transferencias en tiempo real**: hub SignalR `/hubs/transfers` con
  reconexión automática — velocidad, progreso, colas; pausar/reanudar/detener
  globales y pausar/cancelar/reintentar por elemento; badge con actividad en la
  barra inferior.
- **Reproductor de audio** en segundo plano (Media3 + MediaSession: notificación,
  auriculares, cola, aleatorio, repetición) con mini-player persistente.
- **Playlists** del servidor: crear, borrar, reordenar, añadir pistas desde el
  explorador, reproducir completas (resolución de URLs en paralelo) y descargar
  al servidor.
- **Streaming de vídeo** (ExoPlayer, pantalla completa) tanto de ficheros de
  Telegram como locales del servidor.
- **Ajustes**: info del servidor, cuenta de Telegram (logout), y configuración
  del servidor (descargas simultáneas, chunks paralelos, conexiones por
  descarga, hash).

## Rendimiento

- Las llamadas independientes se lanzan **en paralelo** (`async`/`awaitAll`),
  p. ej. info + usuario + config en Ajustes, o la resolución de pistas de una
  playlist (con límite de concurrencia).
- El progreso de transferencias llega por **push** (SignalR) en vez de polling.
- Estados de carga con spinner en todas las pantallas y sin bloqueos del hilo
  principal (todo IO va por corrutinas).

## Compilar

Requisitos: JDK 17+ y Android SDK 35.

```bash
./gradlew assembleDebug
```

## Release (CI)

`.github/workflows/release.yml` compila y firma el APK en cada push a `main` y
publica una GitHub Release `v<versionName>`.

Secrets del repositorio:

| Secret | Descripción |
| --- | --- |
| `KEYSTORE_BASE64` | Keystore `.jks` en base64. |
| `KEYSTORE_PASSWORD` | Contraseña del keystore. |
| `KEY_PASSWORD` | Contraseña de la clave. |
| `KEY_ALIAS` | (Opcional) alias de la clave; por defecto `tfm`. |

Para subir versión: edita `versionCode` / `versionName` en
`app/build.gradle.kts`.
