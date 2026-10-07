package com.mycompany.detectorsomnolencia;

/**
 * COLA CIRCULAR (implementacion propia).
 *
 * Estructura de datos lineal con politica FIFO implementada sobre un arreglo
 * de capacidad fija que se recorre "en circulo" mediante el operador modulo.
 *
 * POR QUE ESTA ESTRUCTURA Y NO OTRA
 * ---------------------------------
 * En este proyecto la cola circular funciona como VENTANA DESLIZANTE: guarda
 * los ultimos N valores de EAR (apertura ocular) calculados por el sistema.
 *
 * - Con un arreglo simple, insertar un valor nuevo obligaria a desplazar los N
 *   elementos una posicion hacia adelante en cada cuadro de video. Ese es
 *   exactamente el costo O(n) del metodo pop() de la cola con arreglo simple.
 *   A 30 cuadros por segundo ese desplazamiento se repetiria 30 veces por
 *   segundo y desperdiciaria tiempo de CPU que el procesamiento de imagen
 *   necesita.
 *
 * - Con la cola circular, insertar y extraer son O(1): no se mueve ningun dato,
 *   solo avanzan dos indices (frente y final) que dan la vuelta al llegar al
 *   final del arreglo gracias a la operacion (indice + 1) % CAPACIDAD.
 *
 * - Ademas reutiliza las casillas liberadas, por lo que la memoria usada es
 *   constante sin importar cuantas horas lleve corriendo el sistema.
 *
 * NOTA SOBRE frente == final
 * --------------------------
 * En una cola circular, la condicion frente == final es ambigua: puede
 * significar cola vacia o cola llena. Por eso se conserva la variable "tamano",
 * que distingue ambos casos sin sacrificar una casilla del arreglo.
 *
 * @param <E> tipo de dato que almacena la cola (aqui se usa con Double)
 */
public class ColaCircular<E> {

    /** Capacidad maxima de la cola (tamano fijo del arreglo). */
    private final int CAPACIDAD;

    /** Arreglo donde se almacenan fisicamente los elementos. */
    private final E[] data;

    /** Indice del elemento mas antiguo (el proximo en salir). */
    private int frente;

    /** Indice de la casilla donde entrara el proximo elemento. */
    private int finalCola;

    /** Cantidad de elementos actualmente en la cola. */
    private int tamano;

    /**
     * Crea una cola circular vacia con la capacidad indicada.
     *
     * @param capacidad numero maximo de elementos que puede contener
     */
    @SuppressWarnings("unchecked")
    public ColaCircular(int capacidad) {
        this.CAPACIDAD = capacidad;
        this.data = (E[]) new Object[capacidad];
        this.frente = 0;
        this.finalCola = 0;
        this.tamano = 0;
    }

    /**
     * Indica si la cola esta vacia.
     *
     * @return true si no contiene elementos
     */
    public boolean estaVacia() {
        return this.tamano == 0;
    }

    /**
     * Indica si la cola alcanzo su capacidad maxima.
     *
     * @return true si ya no caben mas elementos
     */
    public boolean estaLlena() {
        return this.tamano == this.CAPACIDAD;
    }

    /**
     * Numero de elementos actualmente almacenados.
     *
     * @return tamano actual de la cola
     */
    public int tamano() {
        return this.tamano;
    }

    /**
     * Capacidad maxima de la cola.
     *
     * @return tamano del arreglo interno
     */
    public int capacidad() {
        return this.CAPACIDAD;
    }

    /**
     * Agrega un elemento al final de la cola (comportamiento clasico de una
     * cola con tope: si esta llena, lanza excepcion).
     *
     * Complejidad: O(1)
     *
     * @param valor elemento a encolar
     * @throws IllegalStateException si la cola esta llena
     */
    public void encolar(E valor) {
        if (estaLlena()) {
            throw new IllegalStateException("La cola esta llena (capacidad " + CAPACIDAD + ").");
        }
        data[finalCola] = valor;
        // El indice avanza y "da la vuelta" al llegar al final del arreglo.
        finalCola = (finalCola + 1) % CAPACIDAD;
        tamano++;
    }

    /**
     * Agrega un elemento en modo VENTANA DESLIZANTE: si la cola esta llena,
     * descarta automaticamente el elemento mas antiguo para hacerle espacio al
     * nuevo. Es el metodo que usa el detector en cada cuadro de video.
     *
     * Complejidad: O(1) tanto si hay espacio como si hay que descartar.
     *
     * @param valor elemento a encolar
     */
    public void encolarDeslizante(E valor) {
        if (estaLlena()) {
            // Se descarta el mas viejo: basta avanzar el frente, no se mueve nada.
            desencolar();
        }
        encolar(valor);
    }

    /**
     * Extrae y devuelve el elemento mas antiguo de la cola (politica FIFO).
     *
     * Complejidad: O(1)
     *
     * @return el elemento del frente
     * @throws IllegalStateException si la cola esta vacia
     */
    public E desencolar() {
        if (estaVacia()) {
            throw new IllegalStateException("La cola esta vacia.");
        }
        E resultado = data[frente];
        data[frente] = null;                 // libera la referencia (garbage collector)
        frente = (frente + 1) % CAPACIDAD;   // el frente tambien da la vuelta
        tamano--;
        return resultado;
    }

    /**
     * Muestra el elemento del frente sin extraerlo.
     *
     * @return el elemento mas antiguo
     * @throws IllegalStateException si la cola esta vacia
     */
    public E verFrente() {
        if (estaVacia()) {
            throw new IllegalStateException("La cola esta vacia.");
        }
        return data[frente];
    }

    /**
     * Devuelve el elemento que ocupa la posicion logica i dentro de la ventana,
     * donde 0 es el mas antiguo y tamano()-1 el mas reciente.
     *
     * Traduce la posicion logica a la posicion fisica del arreglo con modulo,
     * lo que permite recorrer la ventana completa sin desencolar nada.
     *
     * @param i posicion logica (0 = mas antiguo)
     * @return el elemento en esa posicion
     * @throws IndexOutOfBoundsException si la posicion no existe
     */
    public E obtener(int i) {
        if (i < 0 || i >= tamano) {
            throw new IndexOutOfBoundsException("Posicion invalida: " + i);
        }
        return data[(frente + i) % CAPACIDAD];
    }

    /** Vacia la cola por completo. */
    public void vaciar() {
        while (!estaVacia()) {
            desencolar();
        }
    }
}
