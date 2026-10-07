package com.mycompany.detectorsomnolencia;

import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.opencv_face.*;
import org.bytedeco.opencv.opencv_objdetect.*;
import org.bytedeco.opencv.opencv_videoio.*;

import static org.bytedeco.opencv.global.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_imgproc.*;
import static org.bytedeco.opencv.global.opencv_highgui.*;
import static org.bytedeco.opencv.global.opencv_imgcodecs.*;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * SISTEMA DE DETECCION DE SOMNOLENCIA AL VOLANTE
 * Prototipo funcional - Proyecto Final de Estructuras de Datos (Opcion B)
 *
 * QUE HACE
 * --------
 * Monitorea el rostro del conductor con la camara web y dispara una alerta
 * cuando detecta signos de fatiga: ojos cerrados de forma sostenida o bostezos.
 *
 * COMO LO HACE
 * ------------
 * 1. Haar Cascades (Viola-Jones) localiza el cuadro del rostro.
 * 2. El modelo LBF ubica 68 puntos faciales dentro de ese cuadro.
 * 3. Con distancias euclidianas entre esos puntos se calculan dos metricas:
 *    - EAR (Eye Aspect Ratio): que tan abiertos estan los ojos.
 *    - MAR (Mouth Aspect Ratio): que tan abierta esta la boca.
 * 4. Las muestras de EAR de los ultimos milisegundos se guardan en una COLA
 *    CIRCULAR que funciona como ventana deslizante, y sobre ella se calcula
 *    PERCLOS (porcentaje de tiempo con el ojo cerrado).
 * 5. Cada alerta se registra en una LISTA ENLAZADA que produce el reporte final.
 *
 * POR QUE LA VENTANA ES POR TIEMPO Y NO POR NUMERO DE CUADROS
 * -----------------------------------------------------------
 * La primera version usaba una ventana de 30 cuadros fijos. Al probarla en
 * equipos distintos se detecto que la velocidad de procesamiento varia (el
 * modelo LBF consume CPU), por lo que 30 cuadros equivalian a 1 segundo en una
 * maquina y a casi 4 en otra: el sistema alertaba a destiempo segun el equipo.
 * La solucion fue guardar la marca de tiempo de cada muestra y descartar por
 * el frente todas las que salen del intervalo de la ventana. Asi la ventana
 * siempre representa el mismo tiempo real. La cola circular sigue siendo la
 * estructura adecuada: se encola por el final y se descarta por el frente, en
 * O(1), sin desplazar datos.
 *
 * ================================================================
 * MEJORAS DE ESTA VERSION (identificador de rasgos faciales)
 * ================================================================
 * La logica de decision NO cambio: siguen siendo los mismos umbrales, la misma
 * ventana de 1500 ms y el mismo criterio de PERCLOS al 80%. Lo que se mejoro es
 * la etapa previa, es decir COMO se obtienen y se muestran los 68 puntos:
 *
 * 1. PREPROCESAMIENTO DE LA IMAGEN
 *    Antes se buscaba el rostro directamente sobre la imagen a color. Ahora se
 *    convierte a escala de grises y se ecualiza el histograma. La ecualizacion
 *    redistribuye los niveles de gris para aprovechar todo el rango disponible,
 *    de modo que un rostro mal iluminado (a contraluz o en penumbra) gana
 *    contraste y Haar Cascades lo localiza de forma mas confiable. El ajuste de
 *    los 68 puntos se sigue haciendo sobre la imagen original.
 *
 * 2. DETECCION DE ROSTRO CON PARAMETROS EXPLICITOS
 *    detectMultiScale se llamaba sin argumentos, con los valores por defecto.
 *    Ahora recibe factor de escala, vecinos minimos y tamano minimo de rostro.
 *    Descartar los candidatos demasiado pequenos elimina falsos positivos y
 *    ademas acelera la busqueda, porque se recorren menos escalas.
 *
 * 3. SELECCION DEL ROSTRO PRINCIPAL
 *    Antes se tomaba el rostro del indice 0, que es arbitrario. Ahora se elige
 *    el de mayor area: si alguien pasa por atras, el sistema sigue midiendo al
 *    conductor, que es quien esta mas cerca de la camara.
 *
 * 4. ESTABILIZACION TEMPORAL DE LOS PUNTOS
 *    El modelo LBF estima cada punto de forma independiente en cada cuadro, asi
 *    que las posiciones oscilan algunos pixeles aunque el rostro este quieto.
 *    La clase RasgosFaciales aplica un filtro exponencial que promedia cada
 *    punto con su posicion anterior, lo que elimina ese temblor. Se activa y se
 *    desactiva con la tecla T para poder comparar el efecto en vivo.
 *
 * 5. TRES MODOS DE VISUALIZACION (tecla V)
 *    Minimo (como la version original), malla facial completa, y los 68 puntos
 *    coloreados por region.
 *
 * CONTROLES (dentro de la ventana de video)
 * -----------------------------------------
 *   W / S  -> sube / baja el umbral de EAR (ojo cerrado)
 *   E / D  -> sube / baja el umbral de MAR (boca abierta)
 *   R      -> restablece los umbrales por defecto
 *   V      -> cambia el modo de visualizacion de los rasgos
 *   T      -> activa / desactiva la estabilizacion de los puntos
 *   M      -> activa / desactiva el efecto espejo
 *   ESC    -> salir y mostrar el reporte
 *
 * ARCHIVOS NECESARIOS EN LA RAIZ DEL PROYECTO
 * -------------------------------------------
 * - haarcascade_frontalface_alt2.xml
 * - lbfmodel.yaml
 */
public class DetectorSomnolencia {

    // ====================== CONSTANTES DE DECISION ======================
    // (sin cambios respecto a la version documentada en el reporte)

    /** Valores por defecto de los umbrales (ajustables en vivo). */
    private static final double EAR_POR_DEFECTO = 0.23;
    private static final double MAR_POR_DEFECTO = 0.35;

    /** Duracion de la ventana deslizante, en milisegundos. */
    private static final long VENTANA_MS = 1500;

    /** Capacidad del arreglo interno de la cola (holgada para cualquier fps). */
    private static final int CAPACIDAD_VENTANA = 300;

    /** Muestras minimas para poder decidir (evita alertar al arrancar). */
    private static final int MIN_MUESTRAS = 5;

    /** Porcentaje de la ventana con ojo cerrado que dispara la alerta. */
    private static final double UMBRAL_PERCLOS = 0.80;

    /** Tiempo con la boca abierta para contar un bostezo, en milisegundos. */
    private static final long MS_BOSTEZO = 700;

    /** Tiempo minimo entre capturas de evidencia, en milisegundos. */
    private static final long ENFRIAMIENTO_MS = 5000;

    /** Tiempo minimo entre repeticiones del tono de alarma. */
    private static final long ENFRIAMIENTO_SONIDO_MS = 1200;

    // ====================== CONSTANTES DEL IDENTIFICADOR DE RASGOS ======================

    /**
     * Peso de la posicion anterior en el filtro de estabilizacion.
     * 0.0 = sin filtro (puntos crudos) | 0.9 = muy suave pero con retraso.
     */
    private static final double ALFA_SUAVIZADO = 0.55;

    /** Cuanto crece la ventana de busqueda entre escalas sucesivas (10%). */
    private static final double ESCALA_BUSQUEDA = 1.1;

    /**
     * Cuantas detecciones vecinas debe acumular una region para aceptarse como
     * rostro. Mas alto = menos falsos positivos, pero pierde rostros dificiles.
     */
    private static final int VECINOS_MINIMOS = 5;

    /** Fraccion del alto de la imagen que debe medir el rostro como minimo. */
    private static final double FRACCION_ROSTRO_MINIMO = 0.20;

    /** Segundos sin rostro tras los cuales se registra la perdida de vision. */
    private static final long MS_SIN_ROSTRO = 3000;

    // ====================== ESTADO AJUSTABLE ======================

    private static double umbralEAR = EAR_POR_DEFECTO;
    private static double umbralMAR = MAR_POR_DEFECTO;

    /** 0 = minimo, 1 = malla completa, 2 = los 68 puntos. */
    private static int modoVista = 1;
    private static final String[] NOMBRE_VISTA = {"MINIMO", "MALLA", "68 PUNTOS"};

    private static boolean suavizadoActivo = true;
    private static boolean espejoActivo = true;

    // ====================== PROGRAMA PRINCIPAL ======================

    public static void main(String[] args) {

        // --- 1. Cargar el detector del cuadro del rostro (Haar Cascades) ---
        CascadeClassifier detectorRostros = new CascadeClassifier("haarcascade_frontalface_alt2.xml");
        if (detectorRostros.empty()) {
            System.out.println("Error: no se encontro haarcascade_frontalface_alt2.xml en la raiz del proyecto.");
            return;
        }

        // --- 2. Cargar el modelo LBF de 68 puntos faciales ---
        Facemark facemark = FacemarkLBF.create();
        facemark.loadModel("lbfmodel.yaml");

        // --- 3. Conectar la camara (0 = integrada, 1 o 2 = USB externa) ---
        VideoCapture camara = new VideoCapture(0);
        if (!camara.isOpened()) {
            System.out.println("Error: no se detecto ninguna camara web.");
            return;
        }

        // --- 4. Estructuras de datos del sistema ---
        // Ventana deslizante con las muestras de EAR del ultimo intervalo.
        ColaCircular<Muestra> ventana = new ColaCircular<>(CAPACIDAD_VENTANA);
        // Historial cronologico de alertas de toda la sesion.
        ListaEventos historial = new ListaEventos();
        // Identificador de rasgos: guarda y estabiliza los 68 puntos.
        RasgosFaciales rasgos = new RasgosFaciales();

        // Objetos reutilizados en todos los cuadros. Crearlos una sola vez evita
        // reservar memoria nativa en cada iteracion del bucle.
        Mat frame = new Mat();
        Mat gris = new Mat();
        RectVector rostrosDetectados = new RectVector();
        Point2fVectorVector puntosFaciales = new Point2fVectorVector();

        int contadorBostezos = 0;
        long inicioBocaAbierta = 0;
        boolean bostezoYaContado = false;
        long tiempoUltimaCaptura = 0;
        long tiempoUltimoSonido = 0;
        boolean alarmaActivaAnterior = false;

        long tiempoUltimoRostro = System.currentTimeMillis();
        boolean perdidaRegistrada = false;

        // Medicion de fps para mostrarla en pantalla.
        long tiempoUltimoFrame = System.currentTimeMillis();
        double fps = 0;

        System.out.println("=========================================================");
        System.out.println("  DETECTOR DE SOMNOLENCIA AL VOLANTE");
        System.out.println("  Ventana deslizante: " + VENTANA_MS + " ms");
        System.out.println("  Umbral EAR inicial: " + umbralEAR
                + " | Umbral MAR inicial: " + umbralMAR);
        System.out.println("  Identificador de rasgos: modelo LBF de 68 puntos,");
        System.out.println("  con ecualizacion de histograma y estabilizacion temporal.");
        System.out.println("  Teclas: W/S umbral ojos, E/D umbral boca, R reinicia,");
        System.out.println("          V vista, T estabilizacion, M espejo, ESC salir.");
        System.out.println("=========================================================");

        // ====================== BUCLE DE VIDEO ======================
        while (true) {
            camara.read(frame);
            if (frame.empty()) {
                break;
            }

            // Efecto espejo: la imagen se comporta como un espejo real, que es
            // como una persona espera verse. No afecta ninguna medicion, porque
            // el EAR promedia ambos ojos y el MAR es simetrico.
            if (espejoActivo) {
                flip(frame, frame, 1);
            }

            long ahora = System.currentTimeMillis();

            // Calculo de fps suavizado (promedio movil simple).
            long deltaMs = ahora - tiempoUltimoFrame;
            if (deltaMs > 0) {
                double fpsInstantaneo = 1000.0 / deltaMs;
                fps = (fps == 0) ? fpsInstantaneo : (fps * 0.9 + fpsInstantaneo * 0.1);
            }
            tiempoUltimoFrame = ahora;

            // --- PREPROCESAMIENTO ---
            // 1) Escala de grises: el color no aporta nada a Haar Cascades ni a
            //    LBF, que trabajan con patrones de intensidad.
            // 2) Ecualizacion del histograma: redistribuye los niveles de gris
            //    para ocupar todo el rango, lo que compensa la mala iluminacion.
            cvtColor(frame, gris, COLOR_BGR2GRAY);
            equalizeHist(gris, gris);

            // --- DETECCION DEL ROSTRO con parametros explicitos ---
            int ladoMinimo = (int) (frame.rows() * FRACCION_ROSTRO_MINIMO);
            detectorRostros.detectMultiScale(
                    gris,
                    rostrosDetectados,
                    ESCALA_BUSQUEDA,
                    VECINOS_MINIMOS,
                    0,
                    new Size(ladoMinimo, ladoMinimo),
                    new Size(frame.cols(), frame.rows()));

            boolean hayRostro = rostrosDetectados.size() > 0;
            double earActual = 0.0;
            double marActual = 0.0;
            boolean ojosCerrados = false;
            boolean bocaAbierta = false;
            boolean rasgosValidos = false;

            if (hayRostro) {
                tiempoUltimoRostro = ahora;
                perdidaRegistrada = false;

                // El rostro principal es el mas grande: el conductor es quien
                // esta mas cerca de la camara.
                int principal = indiceRostroMasGrande(rostrosDetectados);

                // El ajuste de los 68 puntos se hace sobre la imagen original,
                // igual que en la version anterior. La imagen ecualizada se usa
                // unicamente para localizar el rostro, que es donde la mejora
                // de contraste rinde mas y donde no hay riesgo de alterar la
                // precision del modelo.
                boolean exito = facemark.fit(frame, rostrosDetectados, puntosFaciales);

                if (exito && puntosFaciales.size() > principal) {
                    // El identificador de rasgos guarda los puntos y, si el
                    // filtro esta activo, los promedia con los del cuadro previo.
                    rasgosValidos = rasgos.actualizar(
                            puntosFaciales.get(principal), suavizadoActivo, ALFA_SUAVIZADO);
                }

                if (rasgosValidos) {
                    // --- Metrica 1: EAR promedio de ambos ojos ---
                    earActual = rasgos.calcularEARPromedio();
                    ojosCerrados = earActual < umbralEAR;

                    // --- La cola circular recibe la muestra del cuadro actual ---
                    ventana.encolarDeslizante(new Muestra(ahora, earActual));

                    // --- Metrica 2: MAR para deteccion de bostezos ---
                    marActual = rasgos.calcularMAR();
                    bocaAbierta = marActual > umbralMAR;

                    if (bocaAbierta) {
                        if (inicioBocaAbierta == 0) {
                            inicioBocaAbierta = ahora;       // empieza a contar
                        } else if (!bostezoYaContado
                                && (ahora - inicioBocaAbierta) >= MS_BOSTEZO) {
                            contadorBostezos++;
                            bostezoYaContado = true;
                            Evento ev = new Evento("BOSTEZO",
                                    "Bostezo numero " + contadorBostezos, 0.0);
                            historial.insertarAlFinal(ev);
                            System.out.println("-> " + ev);
                            Alarma.sonar();
                        }
                    } else {
                        inicioBocaAbierta = 0;
                        bostezoYaContado = false;
                    }

                    // --- Dibujo de los rasgos segun el modo elegido ---
                    Scalar colorOjos = ojosCerrados
                            ? new Scalar(0, 165, 255, 0)   // naranja si estan cerrados
                            : new Scalar(0, 255, 0, 0);    // verde si estan abiertos
                    Scalar colorBoca = bocaAbierta
                            ? new Scalar(0, 165, 255, 0)
                            : new Scalar(0, 255, 0, 0);

                    if (modoVista == 0) {
                        rasgos.dibujarMinimo(frame, colorOjos, colorBoca);
                    } else if (modoVista == 1) {
                        rasgos.dibujarMalla(frame, colorOjos, colorBoca);
                    } else {
                        rasgos.dibujarTodosLosPuntos(frame, colorOjos, colorBoca);
                        // En esta vista se muestran ademas las tres distancias
                        // con las que se calcula el EAR de cada ojo.
                        Scalar cian = new Scalar(255, 255, 0, 0);
                        rasgos.dibujarMedicionesEAR(frame, RasgosFaciales.OJO_DERECHO, cian);
                        rasgos.dibujarMedicionesEAR(frame, RasgosFaciales.OJO_IZQUIERDO, cian);
                    }

                    // --- Recuadro del rostro principal ---
                    rectangle(frame, rostrosDetectados.get(principal),
                            new Scalar(200, 200, 200, 0), 1, 8, 0);
                }
            } else {
                // Sin rostro no se agregan muestras nuevas; las viejas caducan solas.
                inicioBocaAbierta = 0;
                bostezoYaContado = false;
                // Se descarta el estado del filtro: al reaparecer el rostro, los
                // puntos deben partir de su posicion real y no arrastrarse.
                rasgos.reiniciar();

                // Un conductor que desaparece del campo de vision durante varios
                // segundos tambien es una condicion que vale la pena registrar.
                if (!perdidaRegistrada && (ahora - tiempoUltimoRostro) > MS_SIN_ROSTRO) {
                    Evento ev = new Evento("SIN ROSTRO",
                            "Conductor fuera del campo de vision", 0.0);
                    historial.insertarAlFinal(ev);
                    System.out.println("-> " + ev);
                    perdidaRegistrada = true;
                }
            }

            // --- Purga por tiempo: se descartan por el FRENTE las muestras que
            //     salieron del intervalo de la ventana. Cada desencolado es O(1). ---
            while (!ventana.estaVacia()
                    && (ahora - ventana.verFrente().getTiempo()) > VENTANA_MS) {
                ventana.desencolar();
            }

            // --- PERCLOS sobre la ventana vigente ---
            double perclos = calcularPerclos(ventana);

            // Solo se decide cuando la ventana tiene suficientes muestras y cubre
            // al menos el 60% del intervalo, para no alertar con datos parciales.
            boolean ventanaValida = ventana.tamano() >= MIN_MUESTRAS
                    && abarcaIntervalo(ventana);
            boolean alarmaActiva = ventanaValida && perclos >= UMBRAL_PERCLOS;

            // --- Panel de informacion (HUD) ---
            dibujarHUD(frame, hayRostro && rasgosValidos, earActual, marActual, perclos,
                    ventana.tamano(), contadorBostezos, historial.tamano(),
                    fps, ojosCerrados, bocaAbierta, rasgos);

            // --- Grafica en vivo del contenido de la cola circular ---
            dibujarGraficaVentana(frame, ventana);

            // --- Alerta visual y sonora ---
            if (alarmaActiva) {
                dibujarAlerta(frame);

                // Registrar el evento solo en el flanco de subida: una sola vez
                // por episodio, no en cada cuadro mientras dure la alerta.
                if (!alarmaActivaAnterior) {
                    Evento ev = new Evento("SOMNOLENCIA",
                            "Ojos cerrados de forma sostenida", perclos);
                    historial.insertarAlFinal(ev);
                    System.out.println("-> " + ev);
                }

                // El tono se repite mientras dure la alerta, con enfriamiento.
                if (ahora - tiempoUltimoSonido > ENFRIAMIENTO_SONIDO_MS) {
                    Alarma.sonar();
                    tiempoUltimoSonido = ahora;
                }

                // Captura de evidencia con enfriamiento para no llenar el disco.
                if (ahora - tiempoUltimaCaptura > ENFRIAMIENTO_MS) {
                    String marca = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                    String nombreArchivo = "Evidencia_" + marca + ".jpg";
                    imwrite(nombreArchivo, frame);
                    System.out.println("   Captura guardada: " + nombreArchivo);
                    tiempoUltimaCaptura = ahora;
                }
            }
            alarmaActivaAnterior = alarmaActiva;

            imshow("Detector de Somnolencia - Prototipo", frame);

            // --- Teclado: salida, calibracion y modos de vista ---
            int tecla = waitKey(1);
            if (tecla != -1) {
                int c = tecla & 0xFF;
                if (c == 27) {                       // ESC
                    break;
                } else if (c == 'w' || c == 'W') {
                    umbralEAR = Math.min(0.45, umbralEAR + 0.01);
                    System.out.println("   Umbral EAR = " + String.format("%.2f", umbralEAR));
                } else if (c == 's' || c == 'S') {
                    umbralEAR = Math.max(0.05, umbralEAR - 0.01);
                    System.out.println("   Umbral EAR = " + String.format("%.2f", umbralEAR));
                } else if (c == 'e' || c == 'E') {
                    umbralMAR = Math.min(1.20, umbralMAR + 0.05);
                    System.out.println("   Umbral MAR = " + String.format("%.2f", umbralMAR));
                } else if (c == 'd' || c == 'D') {
                    umbralMAR = Math.max(0.10, umbralMAR - 0.05);
                    System.out.println("   Umbral MAR = " + String.format("%.2f", umbralMAR));
                } else if (c == 'r' || c == 'R') {
                    umbralEAR = EAR_POR_DEFECTO;
                    umbralMAR = MAR_POR_DEFECTO;
                    System.out.println("   Umbrales restablecidos.");
                } else if (c == 'v' || c == 'V') {
                    modoVista = (modoVista + 1) % 3;
                    System.out.println("   Vista de rasgos: " + NOMBRE_VISTA[modoVista]);
                } else if (c == 't' || c == 'T') {
                    suavizadoActivo = !suavizadoActivo;
                    rasgos.reiniciar();
                    System.out.println("   Estabilizacion de puntos: "
                            + (suavizadoActivo ? "ACTIVADA" : "DESACTIVADA"));
                } else if (c == 'm' || c == 'M') {
                    espejoActivo = !espejoActivo;
                    System.out.println("   Efecto espejo: "
                            + (espejoActivo ? "ACTIVADO" : "DESACTIVADO"));
                }
            }
        }

        // ====================== CIERRE Y REPORTE ======================
        camara.release();
        destroyAllWindows();

        System.out.println();
        System.out.println("Umbrales finales usados -> EAR: "
                + String.format("%.2f", umbralEAR)
                + " | MAR: " + String.format("%.2f", umbralMAR));
        historial.imprimirReporte();
    }

    // ====================== METODOS DE APOYO ======================

    /**
     * Devuelve la posicion del rostro de mayor area dentro de los detectados.
     * El conductor es quien esta mas cerca de la camara, por lo tanto el mas
     * grande en la imagen.
     */
    private static int indiceRostroMasGrande(RectVector rostros) {
        int mejor = 0;
        long areaMayor = -1;
        for (int i = 0; i < rostros.size(); i++) {
            Rect r = rostros.get(i);
            long area = (long) r.width() * (long) r.height();
            if (area > areaMayor) {
                areaMayor = area;
                mejor = i;
            }
        }
        return mejor;
    }

    /**
     * Calcula PERCLOS: proporcion de muestras de la ventana en las que el ojo
     * estuvo cerrado.
     *
     * Recorre la cola circular con obtener(i), que traduce la posicion logica a
     * la fisica mediante modulo, sin desencolar ni mover ningun dato.
     */
    private static double calcularPerclos(ColaCircular<Muestra> ventana) {
        if (ventana.estaVacia()) {
            return 0.0;
        }
        int cerrados = 0;
        for (int i = 0; i < ventana.tamano(); i++) {
            if (ventana.obtener(i).getEar() < umbralEAR) {
                cerrados++;
            }
        }
        return (double) cerrados / ventana.tamano();
    }

    /**
     * Verifica que la ventana cubra al menos el 60% de su intervalo de tiempo,
     * comparando la muestra mas antigua con la mas reciente.
     */
    private static boolean abarcaIntervalo(ColaCircular<Muestra> ventana) {
        if (ventana.tamano() < 2) {
            return false;
        }
        long primera = ventana.obtener(0).getTiempo();
        long ultima = ventana.obtener(ventana.tamano() - 1).getTiempo();
        return (ultima - primera) >= (VENTANA_MS * 0.6);
    }

    /**
     * Dibuja el contenido de la COLA CIRCULAR como una grafica en vivo.
     *
     * Cada muestra almacenada se convierte en un punto de la curva: el eje
     * horizontal es la posicion dentro de la ventana (de la mas antigua a la
     * mas reciente) y el vertical es el valor de EAR. La linea punteada marca
     * el umbral. Cuando el ojo se cierra, la curva cae por debajo de esa linea.
     *
     * Sirve para VER la estructura de datos trabajando: la curva se desplaza
     * hacia la izquierda conforme entran muestras nuevas por el final y salen
     * las viejas por el frente.
     */
    private static void dibujarGraficaVentana(Mat frame, ColaCircular<Muestra> ventana) {
        int x0 = frame.cols() - 275;
        int y0 = 18;
        int ancho = 255;
        int alto = 90;

        // Si la resolucion de la camara es muy baja, la grafica no cabe y se
        // omite en lugar de dibujarse encima del panel principal.
        if (x0 < 320 || frame.rows() < y0 + alto + 10) {
            return;
        }

        Scalar blanco = new Scalar(255, 255, 255, 0);
        Scalar gris = new Scalar(140, 140, 140, 0);
        Scalar verde = new Scalar(0, 255, 0, 0);
        Scalar rojo = new Scalar(0, 0, 255, 0);

        rectangle(frame, new Point(x0, y0), new Point(x0 + ancho, y0 + alto),
                gris, 1, 8, 0);

        putText(frame, "COLA CIRCULAR - EAR", new Point(x0 + 6, y0 + 15),
                FONT_HERSHEY_SIMPLEX, 0.38, blanco, 1, 8, false);

        // El eje vertical cubre de EAR = 0.00 a EAR = 0.45.
        double earMaximo = 0.45;

        // Linea del umbral vigente.
        int yUmbral = y0 + alto - (int) ((umbralEAR / earMaximo) * (alto - 22));
        for (int x = x0 + 4; x < x0 + ancho - 4; x += 8) {
            line(frame, new Point(x, yUmbral), new Point(x + 4, yUmbral), rojo, 1, 8, 0);
        }

        int n = ventana.tamano();
        if (n < 2) {
            return;
        }

        // Cada muestra de la cola se convierte en un punto de la curva.
        int xAnterior = 0;
        int yAnterior = 0;
        for (int i = 0; i < n; i++) {
            double ear = Math.min(ventana.obtener(i).getEar(), earMaximo);
            int x = x0 + 4 + (int) ((ancho - 8) * (i / (double) (n - 1)));
            int y = y0 + alto - (int) ((ear / earMaximo) * (alto - 22));

            if (i > 0) {
                line(frame, new Point(xAnterior, yAnterior), new Point(x, y), verde, 1, 8, 0);
            }
            xAnterior = x;
            yAnterior = y;
        }

        putText(frame, n + " muestras", new Point(x0 + ancho - 82, y0 + alto - 5),
                FONT_HERSHEY_SIMPLEX, 0.34, blanco, 1, 8, false);
    }

    /** Dibuja el panel de informacion en la esquina superior izquierda. */
    private static void dibujarHUD(Mat frame, boolean hayRostro, double ear, double mar,
                                   double perclos, int enVentana, int bostezos, int eventos,
                                   double fps, boolean ojosCerrados, boolean bocaAbierta,
                                   RasgosFaciales rasgos) {

        // Fondo semitransparente para que el texto se lea sobre el video.
        // Solo se dibuja si la zona cabe completa dentro del cuadro.
        if (frame.cols() > 320 && frame.rows() > 225) {
            Rect zona = new Rect(10, 10, 300, 205);
            Mat roi = new Mat(frame, zona);
            Mat tinte = new Mat(roi.size(), roi.type(), new Scalar(0, 0, 0, 0));
            addWeighted(roi, 0.35, tinte, 0.65, 0, roi);
            // Se liberan de inmediato: son objetos nativos que se creaban en
            // cada cuadro y antes quedaban a la espera del recolector.
            tinte.release();
            roi.release();
        }

        Scalar blanco = new Scalar(255, 255, 255, 0);
        Scalar verde = new Scalar(0, 255, 0, 0);
        Scalar naranja = new Scalar(0, 165, 255, 0);
        Scalar amarillo = new Scalar(0, 255, 255, 0);

        putText(frame, "MONITOREO DE FATIGA", new Point(20, 32),
                FONT_HERSHEY_SIMPLEX, 0.52, blanco, 2, 8, false);

        putText(frame, String.format("FPS: %.1f", fps), new Point(215, 32),
                FONT_HERSHEY_SIMPLEX, 0.42, amarillo, 1, 8, false);

        if (!hayRostro) {
            putText(frame, "Buscando rostro...", new Point(20, 58),
                    FONT_HERSHEY_SIMPLEX, 0.5, amarillo, 1, 8, false);
        } else {
            putText(frame, String.format("EAR %.3f  (umbral %.2f)", ear, umbralEAR),
                    new Point(20, 58), FONT_HERSHEY_SIMPLEX, 0.46,
                    ojosCerrados ? naranja : verde, 1, 8, false);

            putText(frame, ojosCerrados ? "OJOS CERRADOS" : "OJOS ABIERTOS",
                    new Point(20, 78), FONT_HERSHEY_SIMPLEX, 0.46,
                    ojosCerrados ? naranja : verde, 1, 8, false);

            putText(frame, String.format("MAR %.3f  (umbral %.2f)", mar, umbralMAR),
                    new Point(20, 100), FONT_HERSHEY_SIMPLEX, 0.46,
                    bocaAbierta ? naranja : verde, 1, 8, false);

            putText(frame, bocaAbierta ? "BOCA ABIERTA" : "BOCA CERRADA",
                    new Point(20, 120), FONT_HERSHEY_SIMPLEX, 0.46,
                    bocaAbierta ? naranja : verde, 1, 8, false);
        }

        putText(frame, String.format("PERCLOS: %.0f%%", perclos * 100), new Point(20, 143),
                FONT_HERSHEY_SIMPLEX, 0.48, blanco, 1, 8, false);

        // Barra de progreso de PERCLOS: hace visible la ventana deslizante.
        rectangle(frame, new Point(20, 150), new Point(285, 163), blanco, 1, 8, 0);
        int ancho = (int) (263 * Math.min(perclos, 1.0));
        if (ancho > 0) {
            Scalar colorBarra = (perclos >= UMBRAL_PERCLOS)
                    ? new Scalar(0, 0, 255, 0) : verde;
            rectangle(frame, new Point(21, 151), new Point(21 + ancho, 162),
                    colorBarra, -1, 8, 0);
        }

        putText(frame, "Muestras: " + enVentana + "   Bostezos: " + bostezos
                        + "   Eventos: " + eventos,
                new Point(20, 182), FONT_HERSHEY_SIMPLEX, 0.42, blanco, 1, 8, false);

        // Estado del identificador de rasgos.
        String estadoRasgos = "Rasgos: " + (hayRostro ? "68 pts" : "---")
                + "  Vista: " + NOMBRE_VISTA[modoVista]
                + "  Filtro: " + (suavizadoActivo ? "ON" : "OFF");
        putText(frame, estadoRasgos, new Point(20, 203),
                FONT_HERSHEY_SIMPLEX, 0.38, amarillo, 1, 8, false);

        // Ayuda de controles en la parte inferior.
        putText(frame, "W/S ojos  E/D boca  R reset  V vista  T filtro  M espejo  ESC salir",
                new Point(15, frame.rows() - 12), FONT_HERSHEY_SIMPLEX, 0.40,
                new Scalar(200, 200, 200, 0), 1, 8, false);
    }

    /** Dibuja el aviso de alerta sobre el cuadro de video. */
    private static void dibujarAlerta(Mat frame) {
        Scalar rojo = new Scalar(0, 0, 255, 0);

        rectangle(frame, new Point(0, 0), new Point(frame.cols() - 1, frame.rows() - 1),
                rojo, 12, 8, 0);

        putText(frame, "ALERTA: SOMNOLENCIA", new Point(55, frame.rows() - 72),
                FONT_HERSHEY_DUPLEX, 0.95, rojo, 2, 8, false);
        putText(frame, "DETENGASE Y DESCANSE", new Point(55, frame.rows() - 42),
                FONT_HERSHEY_SIMPLEX, 0.65, rojo, 2, 8, false);
    }
}
