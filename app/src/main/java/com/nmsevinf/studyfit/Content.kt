package com.nmsevinf.studyfit

import java.util.Calendar

/** Paso guiado dentro de un bloque de estudio: aparece a partir del minuto [at]. */
data class Step(val at: Int, val text: String)

data class Technique(
    val id: String,
    val name: String,
    val short: String,
    val howTo: String,
    val studyMin: Int,
    val breakMin: Int,
    val longBreakMin: Int,
    val longEvery: Int,
    val steps: List<Step>,
)

object Techniques {
    val all = listOf(
        Technique(
            "pomodoro", "Pomodoro", "25 min estudio · 5 descanso",
            "Bloques cortos de concentración total. Ideal si te cuesta arrancar o te distraes mucho.",
            25, 5, 15, 4,
            listOf(
                Step(0, "Escribe en un papel UNA tarea concreta para este bloque (p. ej. «hacer ejercicios 1–4 del tema 3»)."),
                Step(1, "Trabaja solo en esa tarea. Si te viene otra idea a la cabeza, apúntala y sigue."),
                Step(20, "Últimos minutos: termina la frase o el ejercicio en el que estás."),
                Step(24, "Marca lo que has hecho. En el descanso no mires apuntes."),
            )
        ),
        Technique(
            "pomolargo", "Pomodoro largo", "50 min estudio · 10 descanso",
            "Para tareas profundas (programar, problemas largos, redactar) que necesitan calentar motores.",
            50, 10, 20, 3,
            listOf(
                Step(0, "Define el objetivo del bloque y deja a mano todo lo que necesitas (agua, apuntes, calculadora)."),
                Step(2, "Entra en la tarea. Los primeros 10 min cuestan: es normal, sigue."),
                Step(25, "Mitad del bloque: comprueba si sigues en el objetivo o te has ido por las ramas."),
                Step(45, "Cierra: deja escrita una nota de «por dónde voy» para retomar rápido."),
            )
        ),
        Technique(
            "5217", "52 / 17", "52 min estudio · 17 descanso",
            "Ritmo de los trabajadores más productivos en un estudio de DeskTime: trabajo intenso y descanso real, lejos de la pantalla.",
            52, 17, 17, 99,
            listOf(
                Step(0, "Tarea única durante 52 minutos. Nada de cambiar de asignatura."),
                Step(26, "Ecuador: respira hondo, endereza la espalda y sigue."),
                Step(48, "Recoge: resume en 3 líneas lo que has sacado de este bloque."),
            )
        ),
        Technique(
            "feynman", "Técnica Feynman", "30 min · explica para entender",
            "Entiendes algo de verdad cuando puedes explicarlo con palabras sencillas. Muy buena para teoría y conceptos difíciles.",
            30, 5, 15, 4,
            listOf(
                Step(0, "1 · Elige UN concepto y escribe su nombre arriba de una hoja en blanco."),
                Step(3, "2 · Explícalo por escrito como si se lo contaras a un niño de 12 años. Sin copiar de los apuntes."),
                Step(13, "3 · Señala dónde te has atascado o has usado palabras técnicas sin explicarlas: ahí están tus huecos."),
                Step(17, "4 · Vuelve a los apuntes SOLO para esos huecos y apréndelos."),
                Step(24, "5 · Reescribe la explicación más corta y simple, con un ejemplo o analogía propia."),
            )
        ),
        Technique(
            "recall", "Recuerdo activo", "30 min · leer, cerrar, recordar",
            "Sacar la información de la cabeza (en vez de releer) es de lo que más fija la memoria según la investigación en psicología del aprendizaje.",
            30, 5, 15, 4,
            listOf(
                Step(0, "1 · Lee o repasa una sección corta (máximo 2–3 páginas)."),
                Step(10, "2 · CIERRA los apuntes. Escribe o di en voz alta todo lo que recuerdes."),
                Step(22, "3 · Abre y compara: marca en otro color lo que faltaba o estaba mal."),
                Step(27, "4 · Convierte lo que fallaste en 2–3 preguntas para repasar otro día."),
            )
        ),
        Technique(
            "spaced", "Repetición espaciada", "25 min · repasa lo que toca hoy",
            "Repasar justo antes de olvidar: día 1, 3, 7, 14 y 30. La app te dice qué temas tocan hoy (pestaña Plan).",
            25, 5, 15, 4,
            listOf(
                Step(0, "Abre la lista «Repasos de hoy» del Plan y elige el primero."),
                Step(1, "Pregúntate sin mirar: ¿qué recuerdo de este tema? Escríbelo."),
                Step(10, "Comprueba con los apuntes y corrige."),
                Step(15, "Pasa al siguiente tema de la lista y repite."),
                Step(23, "Al terminar, marca los temas repasados en el Plan para que se reprogramen."),
            )
        ),
        Technique(
            "cornell", "Método Cornell", "40 min · apuntes en 3 zonas",
            "Divide la hoja: columna estrecha a la izquierda (preguntas), zona grande (notas) y franja abajo (resumen).",
            40, 10, 20, 3,
            listOf(
                Step(0, "Prepara la hoja: línea vertical a 6 cm del borde izquierdo y una franja de 5 cm abajo."),
                Step(2, "Zona derecha: toma notas del tema con frases cortas, esquemas y ejemplos."),
                Step(22, "Columna izquierda: escribe una pregunta o palabra clave junto a cada bloque de notas."),
                Step(30, "Tapa la zona derecha y responde las preguntas de la izquierda en voz alta."),
                Step(36, "Franja inferior: resume toda la hoja en 2–3 frases."),
            )
        ),
        Technique(
            "sq3r", "SQ3R", "45 min · para leer temas largos",
            "Explorar, Preguntar, Leer, Recitar, Repasar. Útil para capítulos de libro o temas muy densos.",
            45, 10, 20, 3,
            listOf(
                Step(0, "S · Explora: mira títulos, negritas, gráficos y resumen final (3 min)."),
                Step(4, "Q · Pregunta: convierte cada título en una pregunta."),
                Step(8, "R1 · Lee buscando la respuesta a cada pregunta."),
                Step(28, "R2 · Recita: sin mirar, responde cada pregunta con tus palabras."),
                Step(38, "R3 · Repasa: vuelve sobre lo que no sabías responder."),
            )
        ),
    )

    fun byId(id: String?) = all.firstOrNull { it.id == id } ?: all[0]
}

enum class Place(val label: String, val emoji: String) {
    CASA("En casa", ""),
    FUERA("Al aire libre", ""),
    UNI("Universidad", ""),
    BIBLIO("Biblioteca / poco espacio", "");
}

data class Exercise(val name: String, val how: String, val seconds: Int)

object Exercises {
    private val casa = listOf(
        Exercise("Sentadillas", "Pies al ancho de hombros, baja como si te sentaras en una silla, rodillas en línea con los pies. Ritmo tranquilo.", 45),
        Exercise("Plancha", "Antebrazos en el suelo, cuerpo recto de cabeza a talones. Aprieta abdomen y glúteos. Si cuesta, apoya rodillas.", 30),
        Exercise("Saltos de tijera", "Abre y cierra piernas y brazos a la vez. Sin impacto: da un paso a cada lado.", 40),
        Exercise("Flexiones", "Manos algo más abiertas que los hombros. Versión fácil: contra la pared o con rodillas apoyadas.", 30),
        Exercise("Zancadas alternas", "Paso largo adelante, baja la rodilla de atrás casi al suelo y vuelve. Alterna piernas.", 45),
        Exercise("Puente de glúteo", "Tumbada boca arriba, rodillas dobladas: sube la cadera, aprieta 2 s y baja.", 40),
        Exercise("Rodillas arriba", "Marcha en el sitio subiendo las rodillas a la altura de la cadera.", 40),
        Exercise("Gato-vaca", "A cuatro patas: arquea la espalda hacia arriba y luego hacia abajo, despacio, respirando.", 40),
        Exercise("Bailar una canción", "Pon una canción que te guste y muévete. Cuenta como cardio de verdad.", 60),
    )
    private val fuera = listOf(
        Exercise("Caminata rápida", "Camina a paso ligero, como si llegaras tarde. Brazos activos.", 120),
        Exercise("Subir escaleras / cuesta", "Si hay escalones o cuesta, súbelos a buen ritmo y baja andando.", 90),
        Exercise("Trote suave", "Corre muy suave, que puedas hablar sin ahogarte.", 90),
        Exercise("Sentadillas", "Junto a un banco: baja hasta casi tocarlo y sube.", 45),
        Exercise("Flexiones en banco", "Manos en el respaldo o borde del banco, cuerpo recto.", 30),
        Exercise("Estiramiento de gemelos", "Contra un árbol o pared, una pierna atrás con el talón en el suelo. 20 s cada una.", 40),
        Exercise("Respirar y mirar lejos", "Mira el horizonte o el cielo y haz 5 respiraciones lentas. Descansa la vista.", 30),
    )
    private val uni = listOf(
        Exercise("Subir escaleras", "Sube 2–3 pisos por la escalera y baja andando. Discreto y muy efectivo.", 120),
        Exercise("Paseo por el pasillo", "Da una vuelta por el edificio o el campus a paso vivo.", 120),
        Exercise("Sentadilla a la silla", "Levántate y siéntate de la silla sin usar las manos, despacio.", 40),
        Exercise("Estiramiento de cuello", "Oreja hacia el hombro, 15 s cada lado. Luego barbilla al pecho.", 40),
        Exercise("Apertura de pecho", "Manos entrelazadas detrás de la espalda, abre el pecho y mira al frente.", 30),
        Exercise("Elevación de talones", "De pie, sube de puntillas y baja despacio. Activa la circulación.", 40),
    )
    private val biblio = listOf(
        Exercise("Rotación de hombros", "Hombros hacia atrás en círculos grandes y lentos, luego hacia delante.", 30),
        Exercise("Estiramiento de cuello", "Oreja al hombro, 15 s por lado, sin forzar.", 30),
        Exercise("Muñecas y dedos", "Abre y cierra las manos, gira las muñecas. Ideal tras escribir.", 30),
        Exercise("Torsión sentada", "Sentada recta, gira el tronco agarrando el respaldo. 15 s por lado.", 30),
        Exercise("Talones arriba sentada", "Sube y baja los talones bajo la mesa. Nadie lo nota.", 40),
        Exercise("Paseo al baño / fuente", "Levántate y camina hasta la fuente para beber agua.", 90),
        Exercise("Respiración 4-4-4-4", "Inspira 4 s, aguanta 4, espira 4, aguanta 4. Repite. Calma el estrés.", 60),
    )

    fun forPlace(p: Place) = when (p) {
        Place.CASA -> casa; Place.FUERA -> fuera; Place.UNI -> uni; Place.BIBLIO -> biblio
    }

    /** Rutina aleatoria que cabe en el descanso (deja un minuto para agua). */
    fun routine(p: Place, breakMin: Int): List<Exercise> {
        val budget = ((breakMin - 1).coerceAtLeast(2)) * 60
        val pool = forPlace(p).shuffled()
        val out = mutableListOf<Exercise>()
        var t = 0
        for (e in pool) {
            if (t + e.seconds <= budget) { out += e; t += e.seconds + 15 }
            if (out.size >= 5) break
        }
        if (out.isEmpty()) out += pool.first()
        return out
    }
}

data class Snack(val name: String, val why: String)

object Snacks {
    private val day = listOf(
        Snack("Una pieza de fruta + un puñado de nueces", "Azúcar natural con fibra y grasas buenas: energía estable sin bajón."),
        Snack("Yogur natural con un poco de avena o fruta", "Proteína y fibra: sacia y no da sueño."),
        Snack("Palitos de zanahoria o pepino con hummus", "Crujiente, saciante y ligero. La legumbre da energía duradera."),
        Snack("Tostada integral con tomate y aceite de oliva", "Hidratos de absorción lenta: concentración durante más rato."),
        Snack("2 onzas de chocolate negro (+70 %) y una mandarina", "Un poco de cafeína y teobromina natural, sin pasarte de azúcar."),
        Snack("Un huevo duro y unos tomates cherry", "Proteína completa: te mantiene saciada hasta la comida."),
        Snack("Un puñado de almendras o pipas sin sal", "Grasas saludables y magnesio, prácticos para llevar a la uni."),
        Snack("Plátano pequeño", "Rápido, fácil y con potasio. Perfecto antes de un bloque largo."),
        Snack("Queso fresco con unas uvas", "Proteína ligera + algo dulce."),
        Snack("Edamame o garbanzos tostados", "Mucha proteína vegetal y fibra."),
    )
    private val night = listOf(
        Snack("Yogur natural o kéfir", "Ligero y fácil de digerir: no te quitará el sueño."),
        Snack("Una infusión (manzanilla, tila, rooibos) y una pieza de fruta", "Hidrata y relaja. Evita café o té negro a esta hora."),
        Snack("Unas nueces y un kiwi", "Ligero; ayuda a no llegar con hambre a la cama."),
        Snack("Tortita de arroz con un poco de queso fresco", "Poco volumen, sacia lo justo."),
    )

    fun pick(n: Int = 3): List<Snack> {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val pool = if (h >= 21 || h < 6) night else day
        return pool.shuffled().take(n)
    }

    const val TIPS =
        "• Bebe un vaso de agua: la deshidratación leve ya baja la concentración.\n" +
        "• Evita bollería, refrescos y bebidas energéticas: subidón y bajón en 1 hora.\n" +
        "• Café o té: mejor antes de las 17:00 para no estropear el sueño.\n" +
        "• Come sentada y sin pantalla: 5–10 minutos bastan."
}

object Tips {
    val posture = listOf(
        "Postura: espalda apoyada en el respaldo, pies planos en el suelo.",
        "Postura: hombros abajo y atrás, lejos de las orejas.",
        "Postura: pantalla o libro a la altura de los ojos, no mires hacia abajo mucho rato.",
        "Postura: codos a 90º y muñecas rectas al escribir.",
    )
    const val EYES = "Regla 20-20-20: mira algo a unos 6 metros (por la ventana, al fondo de la sala) durante 20 segundos. Parpadea varias veces."
    const val WATER = "Bebe un vaso de agua. Tenlo siempre a mano en la mesa."
    val stress = listOf(
        "Respiración cuadrada: inspira 4 s, aguanta 4, suelta 4, aguanta 4. Repite 4 veces.",
        "Escribe en un papel lo que te preocupa. Sacarlo de la cabeza libera memoria para estudiar.",
        "Divide: ¿cuál es el SIGUIENTE paso pequeño? Solo ese.",
        "Si el estrés dura días o te quita el sueño, habla con alguien de confianza. Pedir ayuda es de listas.",
    )
}
