package br.com.lector.menctor.pdf;

/** Estilo de parágrafo (subconjunto do {@code ParagraphStyle} do ReportLab usado pelo relatório). */
public record ParagraphStyle(
        StdFont font,
        double fontSize,
        double leading,
        Rgb textColor,
        Alignment alignment,
        double spaceAfter) {

    public enum Alignment { LEFT, CENTER, RIGHT, JUSTIFY }

    public ParagraphStyle(StdFont font, double fontSize, double leading, Rgb textColor, Alignment alignment) {
        this(font, fontSize, leading, textColor, alignment, 0);
    }
}
