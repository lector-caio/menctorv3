package br.com.lector.menctor.pdf;

/** O conteúdo não pôde ser diagramado (equivalente ao {@code LayoutError} do ReportLab). */
public class LayoutException extends RuntimeException {

    public LayoutException(String message) {
        super(message);
    }
}
