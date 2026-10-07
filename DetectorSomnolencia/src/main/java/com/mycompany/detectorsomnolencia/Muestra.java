package com.mycompany.detectorsomnolencia;

/**
 * Muestra individual del analisis: un valor de EAR junto con el instante
 * exacto en que fue medido.
 *
 * POR QUE SE GUARDA EL TIEMPO
 * ---------------------------
 * La primera version del sistema usaba una ventana de N cuadros fijos. El
 * problema es que la velocidad de procesamiento (fps) cambia de una
 * computadora a otra e incluso de un momento a otro, porque el modelo LBF
 * consume CPU. Una ventana de 30 cuadros puede representar 1 segundo en un
 * equipo rapido y 4 segundos en uno lento, asi que el sistema alertaba en
 * tiempos distintos segun la maquina.
 *
 * Al guardar la marca de tiempo de cada muestra, la ventana representa
 * SIEMPRE el mismo intervalo real (por ejemplo, 1.5 segundos), sin importar
 * cuantos cuadros quepan en el. Es un POJO igual que los nodos del curso.
 */
public class Muestra {

    /** Instante de la medicion, en milisegundos. */
    private final long tiempo;

    /** Valor de EAR medido en ese instante. */
    private final double ear;

    public Muestra(long tiempo, double ear) {
        this.tiempo = tiempo;
        this.ear = ear;
    }

    public long getTiempo() {
        return tiempo;
    }

    public double getEar() {
        return ear;
    }
}
