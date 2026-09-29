package br.com.lector.menctor.pdf;

import java.util.ArrayList;
import java.util.List;

/**
 * Mantém os elementos na mesma página: se o conjunto não couber no espaço restante, pula para a
 * próxima página antes de começar (mesma regra do {@code KeepTogether} do ReportLab 4).
 */
public final class KeepTogether extends Flowable {

    private static final double FUZZ = 1e-6;
    /** Altura "infinita" que o ReportLab devolve para forçar a divisão. */
    private static final double FORCE_SPLIT_HEIGHT = 0xffffff;
    private static final double UNBOUNDED = 0xfffffff;

    private final List<Flowable> content;
    private double totalHeight;
    private double firstHeight;
    private double wrappedWidth = Double.NaN;
    private double wrappedHeight = Double.NaN;

    public KeepTogether(List<Flowable> content) {
        this.content = content.isEmpty() ? List.of(ActionFlowable.nullAction()) : List.copyOf(content);
    }

    @Override
    public Size wrap(double availWidth, double availHeight) {
        double maxWidth = 0;
        double height = 0;
        double prevSpaceAfter = 0;
        boolean atTop = true;
        firstHeight = 0;
        boolean first = true;
        for (Flowable f : content) {
            // ações têm tamanho zero (e são puladas, como no _listWrapOn)
            Size s = f instanceof ActionFlowable ? new Size(0, 0) : f.wrap(availWidth, UNBOUNDED);
            if (first) {
                firstHeight = s.height();
                first = false;
            }
            if (s.height() <= FUZZ) {
                continue;
            }
            maxWidth = Math.max(maxWidth, Math.min(s.width(), availWidth));
            height += s.height();
            if (!atTop) {
                height += Math.max(f.spaceBefore() - prevSpaceAfter, 0);
            } else {
                atTop = false;
            }
            prevSpaceAfter = f.spaceAfter();
            height += prevSpaceAfter;
        }
        totalHeight = height - prevSpaceAfter;
        wrappedWidth = availWidth;
        wrappedHeight = availHeight;
        return new Size(maxWidth, FORCE_SPLIT_HEIGHT);
    }

    @Override
    public List<Flowable> split(double availWidth, double availHeight, LayoutContext context) {
        if (wrappedWidth != availWidth || wrappedHeight != availHeight) {
            wrap(availWidth, availHeight);
        }
        List<Flowable> parts = new ArrayList<>(content);
        Frame current = context.currentFrame();
        boolean atTop = current.atTop();
        boolean c0 = totalHeight > availHeight;
        boolean c1 = firstHeight > availHeight || (c0 && atTop);
        if (c0 || c1) {
            Frame next = context.peekNextFrame();
            boolean frameBreak;
            if (c0) {
                // no topo de um quadro que não é menor que o próximo, não adianta pular
                frameBreak = !(atTop && current.width() >= next.width() && current.height() >= next.height());
            } else {
                frameBreak = next.width() >= current.width() && next.height() >= totalHeight;
            }
            parts.add(0, frameBreak ? ActionFlowable.frameBreak() : ActionFlowable.nullAction());
        }
        return parts;
    }

    @Override
    public double spaceBefore() {
        return content.get(0).spaceBefore();
    }

    @Override
    public double spaceAfter() {
        return content.get(content.size() - 1).spaceAfter();
    }

    @Override
    public void draw(PdfCanvas canvas) {
        throw new IllegalStateException("KeepTogether é sempre dividido antes de ser desenhado");
    }
}
