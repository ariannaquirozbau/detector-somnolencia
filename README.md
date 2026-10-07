# Sistema de Detección de Somnolencia al Volante

Sistema en Java que detecta signos de somnolencia de un conductor en tiempo real usando visión por computadora, analizando el cierre de ojos y bostezos a través de 68 puntos faciales.

Proyecto de la materia Estructura de Datos — Equipo 3: Arianna Enriqueta Quiroz Bautista y Vania Mata Zarrabal.

## Qué hace

- Detecta el rostro con Haar Cascades y localiza 68 puntos faciales con el modelo FacemarkLBF.
- Calcula EAR (Eye Aspect Ratio) y MAR (Mouth Aspect Ratio) en cada cuadro de video.
- Usa PERCLOS (porcentaje de cierre de ojos) sobre una ventana de tiempo deslizante para decidir si hay somnolencia.
- Detecta bostezos por duración continua de boca abierta.
- Dispara una alarma sonora y registra cada evento con marca de tiempo.
- Visualización en vivo: malla facial, gráfica de la ventana deslizante, HUD con métricas.

## Estructuras de datos

- **Cola circular (`ColaCircular`)**: implementa la ventana deslizante temporal de muestras, con índices manejados por aritmética modular.
- **Lista enlazada (`ListaEventos`)**: registra cronológicamente cada evento detectado (somnolencia, bostezo, pérdida de rostro) durante la sesión, con referencia a la cola para inserción O(1).

## Tecnologías

- Java 17 + Maven
- JavaCV / OpenCV (Bytedeco) para visión por computadora

## Cómo correrlo

1. Clona el repositorio.
2. Descarga `haarcascade_frontalface_alt2.xml` y `lbfmodel.yaml` (no incluidos por su tamaño) y colócalos en la raíz del proyecto, junto a `pom.xml`.
3. Abre el proyecto en NetBeans (o cualquier IDE con soporte Maven).
4. Ejecuta `DetectorSomnolencia.java`.

## Controles

| Tecla | Acción |
|---|---|
| W / S | Sube / baja umbral de EAR |
| E / D | Sube / baja umbral de MAR |
| R | Restablece umbrales por defecto |
| V | Cambia modo de visualización de rasgos |
| T | Activa / desactiva estabilización de puntos |
| M | Activa / desactiva efecto espejo |
| ESC | Sale y muestra el reporte de la sesión |
