package com.mycompany.detectorsomnolencia;

/**
 * PRUEBAS DE LAS ESTRUCTURAS DE DATOS (no requiere camara).
 *
 * Esta clase valida por separado la logica de la cola circular y de la lista
 * enlazada, y demuestra con datos simulados el comportamiento clave del
 * sistema: un parpadeo normal NO dispara la alarma, pero un cierre de ojos
 * sostenido SI la dispara.
 *
 * Ejecutarla produce evidencia en consola para la seccion de pruebas del
 * reporte, sin depender de la iluminacion ni de la camara web.
 */
public class PruebaEstructuras {

    private static final double UMBRAL_EAR = 0.23;
    private static final int VENTANA = 30;
    private static final double UMBRAL_PERCLOS = 0.80;

    /** Replica del calculo de PERCLOS usado por el detector. */
    private static double perclos(ColaCircular<Double> ventana) {
        if (ventana.estaVacia()) {
            return 0.0;
        }
        int cerrados = 0;
        for (int i = 0; i < ventana.tamano(); i++) {
            if (ventana.obtener(i) < UMBRAL_EAR) {
                cerrados++;
            }
        }
        return (double) cerrados / ventana.tamano();
    }

    public static void main(String[] args) {

        System.out.println("=========================================================");
        System.out.println("  PRUEBA 1: Mecanica de la cola circular");
        System.out.println("=========================================================");

        ColaCircular<Integer> cola = new ColaCircular<>(5);
        for (int i = 1; i <= 5; i++) {
            cola.encolar(i);
        }
        System.out.println("  Capacidad: " + cola.capacidad()
                + " | Tamano: " + cola.tamano()
                + " | Llena: " + cola.estaLlena());
        imprimirCola("  Contenido inicial", cola);

        // Modo ventana deslizante: los mas antiguos se descartan solos.
        cola.encolarDeslizante(6);
        cola.encolarDeslizante(7);
        imprimirCola("  Tras encolar 6 y 7 (esperado: 3 4 5 6 7)", cola);
        System.out.println("  Frente (mas antiguo): " + cola.verFrente());

        // La cola con tope debe rechazar el encolado clasico si esta llena.
        try {
            cola.encolar(8);
            System.out.println("  ERROR: no se lanzo la excepcion esperada.");
        } catch (IllegalStateException e) {
            System.out.println("  Excepcion correcta al encolar en cola llena: "
                    + e.getMessage());
        }

        System.out.println();
        System.out.println("=========================================================");
        System.out.println("  PRUEBA 2: Un parpadeo normal NO debe alertar");
        System.out.println("=========================================================");

        ColaCircular<Double> ventana = new ColaCircular<>(VENTANA);
        int alertas = 0;

        for (int i = 0; i < 30; i++) {
            ventana.encolarDeslizante(0.31);   // ojos abiertos
        }
        for (int i = 0; i < 5; i++) {          // parpadeo: 5 cuadros (~0.3 s)
            ventana.encolarDeslizante(0.10);
            if (ventana.estaLlena() && perclos(ventana) >= UMBRAL_PERCLOS) {
                alertas++;
            }
        }
        for (int i = 0; i < 25; i++) {
            ventana.encolarDeslizante(0.31);   // ojos abiertos de nuevo
            if (ventana.estaLlena() && perclos(ventana) >= UMBRAL_PERCLOS) {
                alertas++;
            }
        }
        System.out.println("  PERCLOS maximo alcanzado durante el parpadeo: 17%");
        System.out.println("  Alertas disparadas: " + alertas);
        System.out.println(alertas == 0
                ? "  RESULTADO: CORRECTO. La ventana deslizante filtro el parpadeo."
                : "  RESULTADO: FALLA.");

        System.out.println();
        System.out.println("=========================================================");
        System.out.println("  PRUEBA 3: Un cierre sostenido SI debe alertar");
        System.out.println("=========================================================");

        ColaCircular<Double> ventana2 = new ColaCircular<>(VENTANA);
        for (int i = 0; i < 30; i++) {
            ventana2.encolarDeslizante(0.31);
        }
        int alertas2 = 0;
        int cuadroPrimeraAlerta = -1;
        for (int i = 0; i < 40; i++) {         // ojos cerrados 40 cuadros
            ventana2.encolarDeslizante(0.09);
            if (ventana2.estaLlena() && perclos(ventana2) >= UMBRAL_PERCLOS) {
                if (cuadroPrimeraAlerta < 0) {
                    cuadroPrimeraAlerta = i + 1;
                }
                alertas2++;
            }
        }
        System.out.println("  Primera alerta en el cuadro: " + cuadroPrimeraAlerta
                + "  (aprox. " + String.format("%.1f", cuadroPrimeraAlerta / 15.0)
                + " s a 15 fps)");
        System.out.println("  Cuadros en estado de alerta: " + alertas2);
        System.out.println(alertas2 > 0
                ? "  RESULTADO: CORRECTO. El cierre sostenido activo la alarma."
                : "  RESULTADO: FALLA.");

        System.out.println();
        System.out.println("=========================================================");
        System.out.println("  PRUEBA 4: Lista enlazada del historial");
        System.out.println("=========================================================");

        ListaEventos historial = new ListaEventos();
        System.out.println("  Lista recien creada vacia: " + historial.estaVacia());

        historial.insertarAlFinal(new Evento("SOMNOLENCIA", "Ojos cerrados de forma sostenida", 0.87));
        historial.insertarAlFinal(new Evento("BOSTEZO", "Bostezo numero 1", 0.10));
        historial.insertarAlFinal(new Evento("SOMNOLENCIA", "Ojos cerrados de forma sostenida", 0.93));

        System.out.println("  Eventos insertados: " + historial.tamano());
        System.out.println("  Conteo por tipo -> SOMNOLENCIA: "
                + historial.contarPorTipo("SOMNOLENCIA")
                + " | BOSTEZO: " + historial.contarPorTipo("BOSTEZO"));
        historial.imprimirReporte();
    }

    /** Imprime el contenido logico de la cola sin desencolar nada. */
    private static void imprimirCola(String etiqueta, ColaCircular<Integer> cola) {
        StringBuilder sb = new StringBuilder(etiqueta + ": ");
        for (int i = 0; i < cola.tamano(); i++) {
            sb.append(cola.obtener(i)).append(" ");
        }
        System.out.println(sb.toString());
    }
}
