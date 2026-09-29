package br.com.lector.menctor.pdf;

/**
 * Elemento que nunca é desenhado: só executa uma ação no documento (troca de página, de modelo
 * de página, etc.), como o {@code ActionFlowable} do ReportLab.
 */
public abstract class ActionFlowable extends Flowable {

    abstract void apply(DocTemplate doc);

    @Override
    public final Size wrap(double availWidth, double availHeight) {
        throw new IllegalStateException(describe() + " não deve ser dimensionado");
    }

    @Override
    public final void draw(PdfCanvas canvas) {
        throw new IllegalStateException(describe() + " não deve ser desenhado");
    }

    /** Encerra a página atual. */
    public static ActionFlowable pageBreak() {
        return new PageBreak();
    }

    /** Usa o modelo de página {@code templateId} a partir da próxima página. */
    public static ActionFlowable nextPageTemplate(String templateId) {
        return new NextPageTemplate(templateId);
    }

    static ActionFlowable frameBreak() {
        return new FrameBreak();
    }

    static ActionFlowable nullAction() {
        return new NullAction();
    }

    static final class PageBreak extends ActionFlowable {
        @Override
        void apply(DocTemplate doc) {
            doc.handlePageBreak();
        }
    }

    static final class NextPageTemplate extends ActionFlowable {
        private final String templateId;

        NextPageTemplate(String templateId) {
            this.templateId = templateId;
        }

        @Override
        void apply(DocTemplate doc) {
            doc.handleNextPageTemplate(templateId);
        }
    }

    static final class FrameBreak extends ActionFlowable {
        @Override
        void apply(DocTemplate doc) {
            doc.handleFrameEnd();
        }
    }

    static final class NullAction extends ActionFlowable {
        @Override
        void apply(DocTemplate doc) {
        }
    }
}
