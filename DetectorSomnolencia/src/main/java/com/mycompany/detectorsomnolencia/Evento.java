package com.mycompany.detectorsomnolencia;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Evento registrado por el sistema (alerta de somnolencia o bostezo detectado).
 *
 * Es un POJO (Plain Old Java Object): solo guarda datos, con su constructor y
 * sus getters. Es el "dato" que viajara dentro de los nodos de la lista
 * enlazada del historial de la sesion.
 */
public class Evento {

    /** Tipo de evento: SOMNOLENCIA o BOSTEZO. */
    private final String tipo;

    /** Descripcion legible del evento. */
    private final String descripcion;

    /** Momento exacto en que ocurrio. */
    private final LocalDateTime momento;

    /** Valor de PERCLOS al momento del evento (0.0 a 1.0). */
    private final double perclos;

    public Evento(String tipo, String descripcion, double perclos) {
        this.tipo = tipo;
        this.descripcion = descripcion;
        this.perclos = perclos;
        this.momento = LocalDateTime.now();
    }

    public String getTipo() {
        return tipo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public LocalDateTime getMomento() {
        return momento;
    }

    public double getPerclos() {
        return perclos;
    }

    /** Hora formateada como HH:mm:ss para mostrarla en el reporte. */
    public String getHora() {
        return momento.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    @Override
    public String toString() {
        return "[" + getHora() + "] " + tipo + " - " + descripcion
                + String.format(" (PERCLOS: %.0f%%)", perclos * 100);
    }
}
