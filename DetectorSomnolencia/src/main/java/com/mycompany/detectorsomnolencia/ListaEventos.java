package com.mycompany.detectorsomnolencia;

/**
 * LISTA ENLAZADA SIMPLE (implementacion propia) para el historial de la sesion.
 *
 * POR QUE ESTA ESTRUCTURA Y NO OTRA
 * ---------------------------------
 * Al iniciar el monitoreo es IMPOSIBLE saber cuantas alertas se van a generar:
 * pueden ser cero en un viaje corto o cientos en una jornada larga. Un arreglo
 * exige declarar el tamano de antemano, asi que sobraria memoria o se quedaria
 * corto. La lista enlazada crece dinamicamente: cada alerta reserva su propio
 * nodo en el momento en que ocurre.
 *
 * Ademas, el reporte final debe leerse en ORDEN CRONOLOGICO, por lo que los
 * eventos se insertan al final y se recorren desde la cabeza.
 *
 * Se conserva una referencia al ultimo nodo (ultimo) para que la insercion al
 * final sea O(1) en vez de O(n): sin ella habria que recorrer toda la lista
 * cada vez que se registra una alerta.
 */
public class ListaEventos {

    /**
     * Nodo de la lista: sus dos partes son el dato y el enlace al siguiente.
     */
    private static class Nodo {
        Evento dato;
        Nodo siguiente;

        Nodo(Evento dato) {
            this.dato = dato;
            this.siguiente = null;
        }
    }

    /** Cabeza de la lista (primer nodo). Si es null, la lista esta vacia. */
    private Nodo cabeza;

    /** Referencia al ultimo nodo, para insertar al final en O(1). */
    private Nodo ultimo;

    /** Cantidad de eventos almacenados. */
    private int tamano;

    public ListaEventos() {
        this.cabeza = null;
        this.ultimo = null;
        this.tamano = 0;
    }

    /**
     * Indica si la lista no tiene elementos.
     *
     * @return true si esta vacia
     */
    public boolean estaVacia() {
        return cabeza == null;
    }

    /**
     * Numero de eventos registrados.
     *
     * @return tamano de la lista
     */
    public int tamano() {
        return tamano;
    }

    /**
     * Inserta un evento al final de la lista (orden cronologico).
     *
     * Complejidad: O(1) gracias a la referencia al ultimo nodo.
     *
     * @param evento evento a registrar
     */
    public void insertarAlFinal(Evento evento) {
        Nodo nuevo = new Nodo(evento);

        if (estaVacia()) {
            // Lista vacia: el nuevo nodo es cabeza y ultimo a la vez.
            cabeza = nuevo;
            ultimo = nuevo;
        } else {
            // El que era ultimo ahora apunta al nuevo, y el nuevo pasa a ser ultimo.
            ultimo.siguiente = nuevo;
            ultimo = nuevo;
        }
        tamano++;
    }

    /**
     * Devuelve el evento mas reciente sin quitarlo de la lista.
     *
     * @return ultimo evento registrado, o null si la lista esta vacia
     */
    public Evento verUltimo() {
        return (ultimo == null) ? null : ultimo.dato;
    }

    /**
     * Cuenta cuantos eventos hay de un tipo determinado recorriendo la lista.
     *
     * Complejidad: O(n)
     *
     * @param tipo tipo de evento a contar (por ejemplo "SOMNOLENCIA")
     * @return numero de coincidencias
     */
    public int contarPorTipo(String tipo) {
        int contador = 0;
        Nodo actual = cabeza;
        // Patron de recorrido: avanzar mientras el nodo no sea null.
        while (actual != null) {
            if (actual.dato.getTipo().equals(tipo)) {
                contador++;
            }
            actual = actual.siguiente;
        }
        return contador;
    }

    /**
     * Imprime en consola el reporte completo de la sesion recorriendo la lista
     * desde la cabeza, es decir, en el mismo orden en que ocurrieron.
     */
    public void imprimirReporte() {
        System.out.println();
        System.out.println("==============================================");
        System.out.println("   REPORTE DE SESION - MONITOREO DE FATIGA");
        System.out.println("==============================================");

        if (estaVacia()) {
            System.out.println("Sin incidencias registradas. Conductor alerta durante toda la sesion.");
            System.out.println("==============================================");
            return;
        }

        Nodo actual = cabeza;
        int n = 1;
        while (actual != null) {
            System.out.println("  " + n + ". " + actual.dato.toString());
            actual = actual.siguiente;
            n++;
        }

        System.out.println("----------------------------------------------");
        System.out.println("  Total de eventos:      " + tamano);
        System.out.println("  Alertas de somnolencia: " + contarPorTipo("SOMNOLENCIA"));
        System.out.println("  Bostezos detectados:    " + contarPorTipo("BOSTEZO"));
        System.out.println("==============================================");
    }
}
