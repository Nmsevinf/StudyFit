# StudyFit

App Android para estudiantes que junta **estudio** y **salud**: bloquea distracciones mientras estudias, te guía con técnicas de estudio, te «llama» para moverte, comer algo sano y volver a estudiar, y cuida tu vista, postura, hidratación y sueño.

Todo funciona **sin internet** y los datos se guardan solo en el móvil.

## Qué hace

| Parte | Cómo funciona |
|---|---|
| **Arranque** | Botón, **etiqueta NFC** en la mesa (apoyas el móvil y empieza) o **horario automático** (te llama a la hora elegida). |
| **Bloqueo** | Durante el bloque de estudio, si abres una app no permitida te devuelve a StudyFit (servicio de accesibilidad). Teléfono y 112 siempre permitidos. Puedes permitir apps de estudio (PDF, calculadora…). **Modo estricto** opcional: fija la pantalla. |
| **Técnicas guiadas** | Pomodoro, Pomodoro largo, 52/17, Feynman, Recuerdo activo, Repetición espaciada, Cornell y SQ3R. Cada una con pasos que cambian según el minuto. |
| **Ejercicio** | Al acabar cada bloque te «llama» a pantalla completa con una rutina para **casa, aire libre, universidad o biblioteca**, con cronómetro por ejercicio. |
| **Vuelta al estudio** | Te vuelve a llamar al acabar el descanso con la misma técnica. |
| **Tentempié** | En el 2º descanso (y cada 3 después) te propone tentempiés que ayudan a concentrarte sin engordar (versión ligera por la noche). |
| **Salud** | Vista 20-20-20 cada 20 min, postura cada 30 min, contador de agua con objetivo diario, recordatorios de agua/movimiento cada 90 min y aviso de hora de dormir. |
| **Planificador** | Exámenes con cuenta atrás y minutos recomendados por día; temas con repetición espaciada (1, 3, 7, 14, 30 días). |
| **Estadísticas** | Tiempo hoy/semana/mes, racha, gráfico de 7 días, técnica que mejor te funciona, por asignatura y evolución del estrés. |
| **Ánimo** | Al acabar cada sesión: concentración y estrés (1–5). Si el estrés es alto, consejos para bajarlo. |
| **Sonido ambiente** | Ruido blanco, marrón o lluvia generados en el móvil (0 datos). |
| **Emergencia** | Salir manteniendo pulsado + botón de llamada al 112. |

## Instalar la APK

1. En el móvil entra en **github.com/nmsevinf/StudyFit/releases** y descarga `StudyFit.apk` (la última versión).
2. Ábrela. Android te pedirá permitir «instalar apps de origen desconocido» para tu navegador: acepta.
3. Abre StudyFit → **Ajustes** y activa los permisos marcados con ⚠️.
4. **Bloqueo de apps:** en Android 13 o superior, si el interruptor sale gris («ajuste restringido»): Ajustes › Aplicaciones › StudyFit › menú ⋮ › **Permitir ajustes restringidos**, y vuelve a activarlo.

Cada vez que se sube código a `main`, GitHub compila una APK nueva y la publica en *Releases*. Se instala encima de la anterior sin perder datos.

## Probar sin instalar (VS Code)

1. `git clone https://github.com/nmsevinf/StudyFit` y abre la carpeta en VS Code.
2. Instala la extensión **Live Preview** (Microsoft) o **Live Server**.
3. Clic derecho en `preview/index.html` → *Show Preview* (o *Open with Live Server*).

La prueba reproduce las pantallas y el flujo con el tiempo acelerado (cámbialo arriba). El bloqueo real, las alarmas con el móvil bloqueado y el NFC solo funcionan en la APK.

## Compilar en tu ordenador (opcional)

Requiere JDK 17 y Android SDK (o Android Studio):

```bash
./gradlew assembleRelease
# APK en app/build/outputs/apk/release/app-release.apk
```

## Estructura

```
app/src/main/java/com/nmsevinf/studyfit/
  Content.kt      técnicas, ejercicios, tentempiés y consejos (textos)
  Engine.kt       motor de la sesión, alarmas exactas, notificaciones y "llamadas"
  Blocker.kt      servicio de accesibilidad que bloquea apps durante el estudio
  Store.kt        datos locales (sesiones, exámenes, temas, ajustes)
  Ambient.kt      generador de sonido ambiente
  *Activity.kt    pantallas
preview/          versión de prueba en navegador
```

> `app/studyfit.keystore` es la clave de firma del proyecto. Sirve para que las actualizaciones se instalen encima. Si algún día publicas en Google Play, crea una clave nueva y privada.
