package com.mycompany.detectorsomnolencia;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.awt.Toolkit;

/**
 * Generador de la alarma sonora del sistema.
 *
 * Sintetiza un tono audible en lugar de usar el "beep" del sistema operativo,
 * porque ese beep depende de la configuracion de sonidos de Windows y en
 * muchos equipos esta desactivado (y entonces la alerta pasa desapercibida).
 *
 * El tono se reproduce en un hilo aparte para no congelar el procesamiento de
 * video mientras suena.
 */
public class Alarma {

    /** Frecuencia del tono en Hz (agudo, para que llame la atencion). */
    private static final float FRECUENCIA = 880f;

    /** Duracion del tono en segundos. */
    private static final double DURACION = 0.35;

    /** Frecuencia de muestreo del audio. */
    private static final float MUESTREO = 8000f;

    /**
     * Reproduce el tono de alarma sin bloquear el bucle de video.
     */
    public static void sonar() {
        Thread hilo = new Thread(() -> {
            try {
                byte[] buffer = new byte[(int) (MUESTREO * DURACION)];
                for (int i = 0; i < buffer.length; i++) {
                    double angulo = (i / (MUESTREO / FRECUENCIA)) * 2.0 * Math.PI;
                    buffer[i] = (byte) (Math.sin(angulo) * 110);
                }

                AudioFormat formato = new AudioFormat(MUESTREO, 8, 1, true, false);
                SourceDataLine linea = AudioSystem.getSourceDataLine(formato);
                linea.open(formato);
                linea.start();
                linea.write(buffer, 0, buffer.length);
                linea.drain();
                linea.close();

            } catch (Exception e) {
                // Si el equipo no tiene salida de audio disponible, se recurre
                // al beep del sistema como respaldo.
                Toolkit.getDefaultToolkit().beep();
            }
        });
        hilo.setDaemon(true);
        hilo.start();
    }
}
