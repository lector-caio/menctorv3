package br.com.lector.menctor.pdf;

/** Linha horizontal (equivalente ao {@code HRFlowable}: ponta arredondada, 1pt de espaço antes e depois). */
public final class HRFlowable extends Flowable {

    private final double widthPercent;
    private final double thickness;
    private final Rgb color;
    private double lineWidth;

    public HRFlowable(double widthPercent, double thickness, Rgb color) {
        this.widthPercent = widthPercent;
        this.thickness = thickness;
        this.color = color;
        this.hAlign = HAlign.CENTER;
    }

    @Override
    public Size wrap(double availWidth, double availHeight) {
        lineWidth = Math.min(availWidth * widthPercent * 0.01, availWidth);
        return new Size(lineWidth, thickness);
    }

    @Override
    public void draw(PdfCanvas canvas) {
        canvas.saveState();
        canvas.setLineWidth(thickness);
        canvas.setLineCap(1);
        canvas.setStrokeColor(color);
        canvas.line(0, 0, lineWidth, 0);
        canvas.restoreState();
    }

    @Override
    public double spaceBefore() {
        return 1;
    }

    @Override
    public double spaceAfter() {
        return 1;
    }
}
