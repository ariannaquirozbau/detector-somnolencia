package com.mycompany.detectorsomnolencia;

import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Point;
import org.bytedeco.opencv.opencv_core.Point2f;
import org.bytedeco.opencv.opencv_core.Point2fVector;
import org.bytedeco.opencv.opencv_core.Scalar;

import static org.bytedeco.opencv.global.opencv_imgproc.circle;
import static org.bytedeco.opencv.global.opencv_imgproc.line;

/**
 * IDENTIFICADOR DE RASGOS FACIALES
 *
 * QUE ES ESTA CLASE
 * -----------------
 * Encapsula los 68 puntos faciales que devuelve el modelo LBF y se encarga de
 * dos cosas que antes estaban mezcladas dentro del programa principal:
 *
 *   1. ESTABILIZAR los puntos (filtro temporal).
 *   2. DIBUJAR los rasgos agrupados por region del rostro.
 *
 * POR QUE HACE FALTA ESTABILIZARLOS
 * ---------------------------------
 * El modelo LBF estima la posicion de cada punto de forma INDEPENDIENTE en
 * cada cuadro de video. Como la imagen de una camara web siempre trae algo de
 * ruido (variaciones de iluminacion, compresion, grano del sensor), la posicion
 * estimada de un mismo punto oscila unos pocos pixeles de un cuadro a otro
 * aunque la persona este completamente inmovil. Ese temblor tiene dos efectos:
 *
 *   - Visual: los puntos "vibran" sobre el rostro y se ven poco confiables.
 *   - Numerico: el EAR se calcula con distancias entre esos puntos, asi que el
 *     temblor se traslada a la metrica y agrega ruido a la senal.
 *
 * COMO SE ESTABILIZAN: filtro exponencial (media movil ponderada)
 * ---------------------------------------------------------------
 * Para cada punto se guarda su posicion suavizada del cuadro anterior y se
 * combina con la nueva medicion:
 *
 *     posicion_suave = alfa * posicion_anterior + (1 - alfa) * posicion_nueva
 *
 * Con alfa = 0.55, cada punto conserva el 55% de su posicion previa y adopta el
 * 45% de la nueva. El resultado es una posicion que responde al movimiento real
 * del rostro pero ignora las oscilaciones de un solo cuadro.
 *
 * Es el mismo principio que ya usaba el programa para suavizar los fps. La
 * diferencia es que aqui se aplica a 68 pares de coordenadas en lugar de a un
 * solo numero.
 *
 * COSTO COMPUTACIONAL
 * -------------------
 * O(1) por punto y O(68) por cuadro: una multiplicacion y una suma por
 * coordenada, sin reservar memoria nueva (los arreglos se crean una sola vez).
 *
 * INDICES DEL MODELO DE 68 PUNTOS (estandar iBUG 300-W)
 * -----------------------------------------------------
 *    0-16  contorno de la mandibula
 *   17-21  ceja derecha          22-26  ceja izquierda
 *   27-30  puente de la nariz    31-35  base de la nariz
 *   36-41  ojo derecho           42-47  ojo izquierdo
 *   48-59  contorno exterior de la boca
 *   60-67  contorno interior de la boca
 */
public class RasgosFaciales {

    /** Cantidad de puntos que entrega el modelo LBF. */
    public static final int TOTAL = 68;

    // ====================== REGIONES DEL ROSTRO ======================

    public static final int[] MANDIBULA      = secuencia(0, 16);
    public static final int[] CEJA_DERECHA   = secuencia(17, 21);
    public static final int[] CEJA_IZQUIERDA = secuencia(22, 26);
    public static final int[] NARIZ_PUENTE   = secuencia(27, 30);
    public static final int[] NARIZ_BASE     = secuencia(31, 35);
    public static final int[] OJO_DERECHO    = secuencia(36, 41);
    public static final int[] OJO_IZQUIERDO  = secuencia(42, 47);
    public static final int[] BOCA_EXTERIOR  = secuencia(48, 59);
    public static final int[] BOCA_INTERIOR  = secuencia(60, 67);

    // ====================== ESTADO ======================

    /** Coordenadas suavizadas del cuadro actual. */
    private final double[] x = new double[TOTAL];
    private final double[] y = new double[TOTAL];

    /** Indica si ya hay un cuadro previo con el cual promediar. */
    private boolean hayEstadoPrevio = false;

    /** Cuadros consecutivos con rostro detectado (indicador de estabilidad). */
    private int cuadrosSeguidos = 0;

    // ====================== CONSTRUCCION DE RANGOS ======================

    /** Devuelve el arreglo {desde, desde+1, ..., hasta}. */
    private static int[] secuencia(int desde, int hasta) {
        int[] r = new int[hasta - desde + 1];
        for (int i = 0; i < r.length; i++) {
            r[i] = desde + i;
        }
        return r;
    }

    // ====================== ACTUALIZACION ======================

    /**
     * Toma los 68 puntos del cuadro actual y los incorpora al estado interno.
     *
     * @param puntos   salida del modelo LBF para el rostro principal
     * @param suavizar true aplica el filtro temporal; false usa el valor crudo
     * @param alfa     peso de la posicion anterior (0 = sin filtro, 0.9 = muy lento)
     * @return true si los puntos eran validos y se guardaron
     */
    public boolean actualizar(Point2fVector puntos, boolean suavizar, double alfa) {
        if (puntos == null || puntos.size() < TOTAL) {
            return false;
        }

        for (int i = 0; i < TOTAL; i++) {
            Point2f p = puntos.get(i);
            double nuevoX = p.x();
            double nuevoY = p.y();

            if (suavizar && hayEstadoPrevio) {
                // Filtro exponencial: conserva parte de la posicion anterior.
                x[i] = alfa * x[i] + (1.0 - alfa) * nuevoX;
                y[i] = alfa * y[i] + (1.0 - alfa) * nuevoY;
            } else {
                // Primer cuadro (o filtro desactivado): se toma la medicion tal cual.
                x[i] = nuevoX;
                y[i] = nuevoY;
            }
        }

        hayEstadoPrevio = true;
        cuadrosSeguidos++;
        return true;
    }

    /**
     * Se llama cuando se pierde el rostro. Descarta el estado previo para que
     * al reaparecer la persona los puntos no se arrastren desde la posicion
     * vieja (lo que produciria un "salto" visible).
     */
    public void reiniciar() {
        hayEstadoPrevio = false;
        cuadrosSeguidos = 0;
    }

    public boolean hayDatos() {
        return hayEstadoPrevio;
    }

    public int getCuadrosSeguidos() {
        return cuadrosSeguidos;
    }

    // ====================== CONSULTA ======================

    public double x(int i) {
        return x[i];
    }

    public double y(int i) {
        return y[i];
    }

    /**
     * Distancia euclidiana entre dos puntos faciales:
     * d = raiz( (x2-x1)^2 + (y2-y1)^2 )
     */
    public double distancia(int a, int b) {
        double dx = x[a] - x[b];
        double dy = y[a] - y[b];
        return Math.sqrt(dx * dx + dy * dy);
    }

    /** Convierte un punto suavizado a coordenada entera para dibujarlo. */
    private Point aPunto(int i) {
        return new Point((int) Math.round(x[i]), (int) Math.round(y[i]));
    }

    // ====================== METRICAS ======================

    /**
     * EAR (Eye Aspect Ratio) de un ojo.
     *
     *   EAR = ( |p2-p6| + |p3-p5| ) / ( 2 * |p1-p4| )
     *
     * Numerador: las dos distancias verticales (alto del ojo).
     * Denominador: el doble de la distancia horizontal (ancho del ojo).
     *
     * Al ser una PROPORCION y no una medida en pixeles, el valor no cambia si
     * el conductor se acerca o se aleja de la camara.
     */
    public double calcularEAR(int[] ojo) {
        double vertical1 = distancia(ojo[1], ojo[5]);
        double vertical2 = distancia(ojo[2], ojo[4]);
        double horizontal = distancia(ojo[0], ojo[3]);

        if (horizontal == 0) {
            return 0.0;   // proteccion contra division entre cero
        }
        return (vertical1 + vertical2) / (2.0 * horizontal);
    }

    /** EAR promedio de ambos ojos. */
    public double calcularEARPromedio() {
        return (calcularEAR(OJO_DERECHO) + calcularEAR(OJO_IZQUIERDO)) / 2.0;
    }

    /**
     * MAR (Mouth Aspect Ratio): alto de la boca entre ancho de la boca,
     * medido sobre el contorno interior (puntos 60, 62, 64 y 66).
     */
    public double calcularMAR() {
        double alto = distancia(62, 66);
        double ancho = distancia(60, 64);

        if (ancho == 0) {
            return 0.0;
        }
        return alto / ancho;
    }

    /**
     * Inclinacion de la cabeza en grados, calculada con la linea que une los
     * extremos externos de ambos ojos (punto 36 y punto 45).
     *
     * 0 grados = cabeza derecha. Valores grandes = cabeza ladeada.
     * Es un indicador complementario: la cabeza ladeada acompana al cabeceo.
     */
    public double calcularInclinacion() {
        double dx = x[45] - x[36];
        double dy = y[45] - y[36];
        return Math.toDegrees(Math.atan2(dy, dx));
    }

    // ====================== DIBUJO ======================

    /** Dibuja los puntos de una region como circulos rellenos. */
    public void dibujarPuntos(Mat frame, int[] region, Scalar color, int radio) {
        for (int i : region) {
            circle(frame, aPunto(i), radio, color, -1, 8, 0);
        }
    }

    /**
     * Une los puntos de una region con segmentos de recta.
     *
     * @param cerrado true une tambien el ultimo punto con el primero
     *                (ojos y boca son contornos cerrados; la mandibula, las
     *                cejas y la nariz son abiertos)
     */
    public void dibujarContorno(Mat frame, int[] region, Scalar color,
                                boolean cerrado, int grosor) {
        for (int k = 0; k < region.length - 1; k++) {
            line(frame, aPunto(region[k]), aPunto(region[k + 1]), color, grosor, 8, 0);
        }
        if (cerrado && region.length > 2) {
            line(frame, aPunto(region[region.length - 1]), aPunto(region[0]),
                    color, grosor, 8, 0);
        }
    }

    /**
     * VISTA 1 - MALLA COMPLETA.
     * Dibuja el contorno de las nueve regiones del rostro. Es la vista que
     * muestra todo lo que el modelo LBF esta reconociendo.
     */
    public void dibujarMalla(Mat frame, Scalar colorOjos, Scalar colorBoca) {
        Scalar gris = new Scalar(180, 180, 180, 0);
        Scalar azul = new Scalar(255, 200, 80, 0);

        dibujarContorno(frame, MANDIBULA, gris, false, 1);
        dibujarContorno(frame, CEJA_DERECHA, azul, false, 1);
        dibujarContorno(frame, CEJA_IZQUIERDA, azul, false, 1);
        dibujarContorno(frame, NARIZ_PUENTE, azul, false, 1);
        dibujarContorno(frame, NARIZ_BASE, azul, false, 1);

        // Ojos y boca son los que alimentan las metricas: van resaltados.
        dibujarContorno(frame, OJO_DERECHO, colorOjos, true, 2);
        dibujarContorno(frame, OJO_IZQUIERDO, colorOjos, true, 2);
        dibujarContorno(frame, BOCA_EXTERIOR, colorBoca, true, 1);
        dibujarContorno(frame, BOCA_INTERIOR, colorBoca, true, 2);

        dibujarPuntos(frame, OJO_DERECHO, colorOjos, 2);
        dibujarPuntos(frame, OJO_IZQUIERDO, colorOjos, 2);
        dibujarPuntos(frame, BOCA_INTERIOR, colorBoca, 2);
    }

    /**
     * VISTA 2 - LOS 68 PUNTOS.
     * Cada region con su propio color, sin lineas. Sirve para explicar que el
     * modelo entrega 68 coordenadas y que el sistema solo usa 16 de ellas.
     */
    public void dibujarTodosLosPuntos(Mat frame, Scalar colorOjos, Scalar colorBoca) {
        Scalar gris = new Scalar(150, 150, 150, 0);
        Scalar azul = new Scalar(255, 200, 80, 0);

        dibujarPuntos(frame, MANDIBULA, gris, 2);
        dibujarPuntos(frame, CEJA_DERECHA, azul, 2);
        dibujarPuntos(frame, CEJA_IZQUIERDA, azul, 2);
        dibujarPuntos(frame, NARIZ_PUENTE, azul, 2);
        dibujarPuntos(frame, NARIZ_BASE, azul, 2);
        dibujarPuntos(frame, BOCA_EXTERIOR, colorBoca, 2);

        // Los 16 puntos que realmente alimentan EAR y MAR, mas grandes.
        dibujarPuntos(frame, OJO_DERECHO, colorOjos, 3);
        dibujarPuntos(frame, OJO_IZQUIERDO, colorOjos, 3);
        dibujarPuntos(frame, BOCA_INTERIOR, colorBoca, 3);
    }

    /**
     * VISTA 0 - MINIMA.
     * Solo los puntos usados por las metricas, como en la version original.
     */
    public void dibujarMinimo(Mat frame, Scalar colorOjos, Scalar colorBoca) {
        dibujarPuntos(frame, OJO_DERECHO, colorOjos, 2);
        dibujarPuntos(frame, OJO_IZQUIERDO, colorOjos, 2);
        dibujarPuntos(frame, BOCA_INTERIOR, colorBoca, 2);
    }

    /**
     * Dibuja los segmentos que se estan midiendo para el EAR de un ojo:
     * las dos distancias verticales y la horizontal. Hace visible de donde
     * sale el numero que aparece en el panel.
     */
    public void dibujarMedicionesEAR(Mat frame, int[] ojo, Scalar color) {
        line(frame, aPunto(ojo[1]), aPunto(ojo[5]), color, 1, 8, 0);
        line(frame, aPunto(ojo[2]), aPunto(ojo[4]), color, 1, 8, 0);
        line(frame, aPunto(ojo[0]), aPunto(ojo[3]), color, 1, 8, 0);
    }
}
