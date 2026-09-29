package br.com.lector.menctor.pdf;

import java.util.List;

/**
 * Área de uma página onde os elementos são empilhados de cima para baixo (equivalente ao
 * {@code platypus.Frame}, com {@code overlapAttachedSpace} ligado: o espaço depois de um elemento
 * e o espaço antes do seguinte se sobrepõem).
 */
public final class Frame {

    private static final double FUZZ = 1e-6;

    private final double x1;
    private final double y1;
    private final double width;
    private final double height;
    private final double leftPadding;
    private final double topPadding;
    private final double y1p;
    private final double availableWidth;

    private double x;
    private double y;
    private boolean atTop;
    private double prevSpaceAfter;

    public Frame(double x1, double y1, double width, double height) {
        this(x1, y1, width, height, 0, 0, 0, 0);
    }

    public Frame(double x1, double y1, double width, double height,
                 double leftPadding, double bottomPadding, double rightPadding, double topPadding) {
        this.x1 = x1;
        this.y1 = y1;
        this.width = width;
        this.height = height;
        this.leftPadding = leftPadding;
        this.topPadding = topPadding;
        this.y1p = y1 + bottomPadding;
        this.availableWidth = (x1 + width) - x1 - leftPadding - rightPadding;
        reset();
    }

    void reset() {
        x = x1 + leftPadding;
        y = y1 + height - topPadding;
        atTop = true;
        prevSpaceAfter = 0;
    }

    public double width() {
        return width;
    }

    public double height() {
        return height;
    }

    public boolean atTop() {
        return atTop;
    }

    /** Desenha o elemento na posição atual; devolve false se ele não couber. */
    boolean add(Flowable f, PdfCanvas canvas) {
        double s = spaceBeforeFor(f);
        double h = y - y1p - s;
        if (h <= 0) {
            return false;
        }
        Size size = f.wrap(availableWidth, h);
        double newY = y - (size.height() + s);
        if (newY < y1p - FUZZ) {
            return false;
        }
        double spaceAfter = f.spaceAfter();
        f.drawOn(canvas, x, newY, availableWidth - size.width());
        newY -= spaceAfter;
        prevSpaceAfter = spaceAfter;
        if (newY != y) {
            atTop = false;
        }
        y = newY;
        return true;
    }

    /** Pede ao elemento para se dividir no espaço que resta. */
    List<Flowable> split(Flowable f, LayoutContext context) {
        double h = y - y1p - spaceBeforeFor(f);
        if (h <= 0) {
            return List.of();
        }
        return f.split(availableWidth, h, context);
    }

    private double spaceBeforeFor(Flowable f) {
        if (atTop) {
            return 0;
        }
        return Math.max(f.spaceBefore() - prevSpaceAfter, 0);
    }
}
