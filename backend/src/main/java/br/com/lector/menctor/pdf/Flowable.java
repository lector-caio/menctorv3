package br.com.lector.menctor.pdf;

import java.util.List;

/**
 * Elemento que flui pelas páginas (equivalente ao {@code platypus.Flowable}). O documento
 * pergunta o tamanho com {@link #wrap}, tenta dividir com {@link #split} quando não cabe e
 * desenha com {@link #drawOn}, sempre no sistema de coordenadas do próprio elemento.
 */
public abstract class Flowable {

    public enum HAlign { LEFT, CENTER, RIGHT }

    protected HAlign hAlign = HAlign.LEFT;

    /** Já foi adiado para a próxima página uma vez ({@code _postponed}). */
    boolean postponed;

    public abstract Size wrap(double availWidth, double availHeight);

    public abstract void draw(PdfCanvas canvas);

    /** Divide o elemento no espaço disponível; lista vazia quando não é possível. */
    public List<Flowable> split(double availWidth, double availHeight, LayoutContext context) {
        return List.of();
    }

    public double spaceBefore() {
        return 0;
    }

    public double spaceAfter() {
        return 0;
    }

    /** Descrição curta para mensagens de erro de diagramação. */
    public String describe() {
        return getClass().getSimpleName();
    }

    public final void drawOn(PdfCanvas canvas, double x, double y, double spareWidth) {
        if (spareWidth != 0) {
            if (hAlign == HAlign.CENTER) {
                x += 0.5 * spareWidth;
            } else if (hAlign == HAlign.RIGHT) {
                x += spareWidth;
            }
        }
        canvas.saveState();
        canvas.translate(x, y);
        draw(canvas);
        canvas.restoreState();
    }
}
