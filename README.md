# Dot Widgets (Nothing Phone 3)

13 widgets de estilo Nothing (Android 12+), con vista previa en el selector. Todos se pueden redimensionar.

| Widget | Tamaño | Qué muestra |
|---|---|---|
| Dot · Todo | 5×3 | Reloj + arco del sol, tiempo, progreso, botones y calendario |
| Dot · Hora, tiempo, música y calendario | 5×3 | Hora; portada cuadrada con temperatura al lado; calendario |
| Dot · Todo + música | 5×4 | Todo + canción con portada y controles |
| Dot · Calendario, tiempo y música | 4×4 | Temperatura, calendario y canción abajo |
| Dot · Hora, calendario y música | 4×4 | Hora, calendario y canción abajo |
| Dot · Día, hora, calendario y música | 5×2 | Día y hora pequeños, la semana (o el mes si lo estiras) y la canción a lo ancho abajo |
| Dot · Música | 2×2 | Solo portada; izquierda/centro/derecha = anterior/pausa/siguiente; ecualizador de puntos animado |
| Dot · Reloj y tiempo | 3×2 | Día, hora, arco del sol y temperatura |
| Dot · Progreso | 4×2 | Año, mes, día y hora en puntos |
| Dot · Calendario | 3×3 | Mes con el día de hoy |
| Dot · Reloj y progreso | 4×3 | Reloj y progreso |
| Dot · Vacío | 1×1 (cualquier tamaño) | Totalmente transparente, para rellenar huecos |
| Dot · Botones | 3×1 | Ubicación, ahorro y tema |

Temas: Claro, Oscuro, Negro, Transparente (rojo, azul, verde, rosa, ámbar, blanco), Cristal gris, Cristal negro, Rojo, Rosa, Crema, Noche y Bosque.

Para la música, abre la app y activa "Acceso a notificaciones". Si Android lo bloquea como ajuste restringido: Info de la app → ⋮ → Permitir ajustes restringidos.

## Conseguir el APK sin instalar nada (GitHub)

1. Crea un repositorio nuevo en github.com.
2. Sube el contenido de esta carpeta, incluida `.github/workflows/build.yml`.
3. Ve a la pestaña **Actions**, abre la última ejecución de "Compilar APK" y descarga el artefacto **DotWidgets-apk**.
4. Descomprímelo en el móvil e instala `app-debug.apk`. Te pedirá permitir apps de origen desconocido.

## Con Android Studio

Abre la carpeta y pulsa *Run*. Si te pide el Gradle wrapper, acepta: usa Gradle 8.14.3.

## Botones que activan y desactivan directamente (opcional)

Si no haces nada más, los botones abren el ajuste correspondiente de Android. Para que cambien con un solo toque, ejecuta esto una vez desde un PC con la depuración USB activada:

```
adb shell pm grant com.gryzz.dotwidgets android.permission.WRITE_SECURE_SETTINGS
```

## Ubicación del tiempo

Por defecto es Lorca. Puedes cambiarla desde la app, buscando una ciudad o con "Usar mi ubicación actual".
