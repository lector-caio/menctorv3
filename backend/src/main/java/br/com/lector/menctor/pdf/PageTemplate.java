package br.com.lector.menctor.pdf;

/**
 * Modelo de página: o quadro onde o conteúdo flui e o desenho fixo da página (fundo, cabeçalho,
 * rodapé). O desenho fixo fica por baixo do conteúdo, como o {@code onPage} do ReportLab.
 */
public record PageTemplate(String id, Frame frame, Decorator decorator) {

    @FunctionalInterface
    public interface Decorator {
        void decorate(PdfCanvas canvas, int pageNumber, int totalPages);
    }
}
