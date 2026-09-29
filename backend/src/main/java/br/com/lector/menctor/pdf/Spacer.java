package br.com.lector.menctor.pdf;

/** Espaço vertical em branco. Como no ReportLab, ocupa espaço mesmo no topo de uma página nova. */
public final class Spacer extends Flowable {

    private final double width;
    private final double height;

    public Spacer(double width, double height) {
        this.width = width;
        this.height = height;
    }

    @Override
    public Size wrap(double availWidth, double availHeight) {
        return new Size(width, height);
    }

    @Override
    public void draw(PdfCanvas canvas) {
    }
}
