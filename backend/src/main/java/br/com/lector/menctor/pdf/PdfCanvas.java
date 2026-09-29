package br.com.lector.menctor.pdf;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.util.Matrix;

/**
 * Canvas de uma página, com a mesma API (e as mesmas primitivas geométricas) do
 * {@code reportlab.pdfgen.canvas.Canvas} usado pelo gerador original. Coordenadas em pontos,
 * origem no canto inferior esquerdo.
 */
public final class PdfCanvas {

    /** Constante de Bézier para arcos de 90° (a mesma de {@code pdfgeom.bezierArc}). */
    private static final double KAPPA = Math.abs(4.0 / 3.0 * (1.0 - Math.cos(Math.PI / 4)) / Math.sin(Math.PI / 4));
    /** Multiplicador de raio de {@code PDFPathObject.roundRect}. */
    private static final double ROUND_RECT_M = 0.4472;

    private final PDPageContentStream cs;
    private final FontSet fonts;
    private final int pageNumber;
    private StdFont font = StdFont.HELVETICA;
    private double fontSize = 12;

    public PdfCanvas(PDPageContentStream cs, FontSet fonts, int pageNumber) {
        this.cs = cs;
        this.fonts = fonts;
        this.pageNumber = pageNumber;
    }

    public int pageNumber() {
        return pageNumber;
    }

    public FontSet fonts() {
        return fonts;
    }

    // ── estado gráfico ────────────────────────────────────────────

    public void saveState() {
        io(cs::saveGraphicsState);
    }

    public void restoreState() {
        io(cs::restoreGraphicsState);
    }

    public void translate(double dx, double dy) {
        io(() -> cs.transform(Matrix.getTranslateInstance((float) dx, (float) dy)));
    }

    public void setFillColor(Rgb c) {
        io(() -> cs.setNonStrokingColor(c.r(), c.g(), c.b()));
    }

    public void setStrokeColor(Rgb c) {
        io(() -> cs.setStrokingColor(c.r(), c.g(), c.b()));
    }

    public void setLineWidth(double w) {
        io(() -> cs.setLineWidth((float) w));
    }

    /** 0 = butt, 1 = round, 2 = square. */
    public void setLineCap(int cap) {
        io(() -> cs.setLineCapStyle(cap));
    }

    /** 0 = miter, 1 = round, 2 = bevel. */
    public void setLineJoin(int join) {
        io(() -> cs.setLineJoinStyle(join));
    }

    public void setFont(StdFont font, double size) {
        this.font = font;
        this.fontSize = size;
    }

    public double stringWidth(String text, StdFont font, double size) {
        return fonts.stringWidth(text, font, size);
    }

    // ── texto ─────────────────────────────────────────────────────

    public void drawString(double x, double y, String text) {
        if (text.isEmpty()) {
            return;
        }
        io(() -> {
            cs.beginText();
            cs.newLineAtOffset((float) x, (float) y);
            showRuns(text, font, fontSize, null);
            cs.endText();
        });
    }

    public void drawRightString(double x, double y, String text) {
        drawString(x - stringWidth(text, font, fontSize), y, text);
    }

    public void drawCentredString(double x, double y, String text) {
        drawString(x - 0.5 * stringWidth(text, font, fontSize), y, text);
    }

    /** Objeto de texto de várias linhas (BT ... ET), usado pelos parágrafos. */
    public TextObject beginText(double x, double y, StdFont font, double size, double leading) {
        io(() -> {
            cs.beginText();
            cs.newLineAtOffset((float) x, (float) y);
            cs.setLeading((float) leading);
        });
        return new TextObject(font, size);
    }

    public final class TextObject {
        private final StdFont font;
        private final double size;
        private PDType1Font current;

        private TextObject(StdFont font, double size) {
            this.font = font;
            this.size = size;
        }

        /** Operador Tw: espaço extra aplicado a cada caractere de espaço (justificação). */
        public void setWordSpace(double wordSpace) {
            io(() -> cs.setWordSpacing((float) wordSpace));
        }

        /** Escreve a linha e desce para a próxima (Tj + T*). */
        public void textLine(String text) {
            io(() -> {
                current = showRuns(text, font, size, current);
                cs.newLine();
            });
        }

        public void end() {
            io(cs::endText);
        }
    }

    /** Escreve o texto trocando de fonte quando necessário; devolve a última fonte selecionada. */
    private PDType1Font showRuns(String text, StdFont font, double size, PDType1Font current) throws IOException {
        for (FontSet.Run run : fonts.runs(text, font)) {
            if (run.font() != current) {
                cs.setFont(run.font(), (float) size);
                current = run.font();
            }
            cs.showText(run.text());
        }
        return current;
    }

    // ── formas ────────────────────────────────────────────────────

    public void line(double x1, double y1, double x2, double y2) {
        io(() -> {
            cs.moveTo((float) x1, (float) y1);
            cs.lineTo((float) x2, (float) y2);
            cs.stroke();
        });
    }

    public void rect(double x, double y, double w, double h, boolean fill, boolean stroke) {
        io(() -> {
            cs.addRect((float) x, (float) y, (float) w, (float) h);
            paint(fill, stroke);
        });
    }

    public void roundRect(double x, double y, double width, double height, double radius, boolean fill, boolean stroke) {
        double xlo = Math.min(x, x + width), xhi = Math.max(x, x + width);
        double ylo = Math.min(y, y + height), yhi = Math.max(y, y + height);
        double t = ROUND_RECT_M * radius;
        io(() -> {
            cs.moveTo(f(xlo + radius), f(ylo));
            cs.lineTo(f(xhi - radius), f(ylo));
            cs.curveTo(f(xhi - t), f(ylo), f(xhi), f(ylo + t), f(xhi), f(ylo + radius));
            cs.lineTo(f(xhi), f(yhi - radius));
            cs.curveTo(f(xhi), f(yhi - t), f(xhi - t), f(yhi), f(xhi - radius), f(yhi));
            cs.lineTo(f(xlo + radius), f(yhi));
            cs.curveTo(f(xlo + t), f(yhi), f(xlo), f(yhi - t), f(xlo), f(yhi - radius));
            cs.lineTo(f(xlo), f(ylo + radius));
            cs.curveTo(f(xlo), f(ylo + t), f(xlo + t), f(ylo), f(xlo + radius), f(ylo));
            cs.closePath();
            paint(fill, stroke);
        });
    }

    public void circle(double cx, double cy, double r, boolean fill, boolean stroke) {
        double k = KAPPA * r;
        io(() -> {
            cs.moveTo(f(cx + r), f(cy));
            cs.curveTo(f(cx + r), f(cy + k), f(cx + k), f(cy + r), f(cx), f(cy + r));
            cs.curveTo(f(cx - k), f(cy + r), f(cx - r), f(cy + k), f(cx - r), f(cy));
            cs.curveTo(f(cx - r), f(cy - k), f(cx - k), f(cy - r), f(cx), f(cy - r));
            cs.curveTo(f(cx + k), f(cy - r), f(cx + r), f(cy - k), f(cx + r), f(cy));
            paint(fill, stroke);
        });
    }

    /** O ReportLab usa a regra par-ímpar por padrão (operadores f* / B*). */
    private void paint(boolean fill, boolean stroke) throws IOException {
        if (fill && stroke) {
            cs.fillAndStrokeEvenOdd();
        } else if (fill) {
            cs.fillEvenOdd();
        } else if (stroke) {
            cs.stroke();
        }
    }

    private static float f(double v) {
        return (float) v;
    }

    @FunctionalInterface
    private interface IoAction {
        void run() throws IOException;
    }

    private static void io(IoAction action) {
        try {
            action.run();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
