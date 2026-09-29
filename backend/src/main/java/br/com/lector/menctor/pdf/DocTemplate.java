package br.com.lector.menctor.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

/**
 * Monta o documento a partir de uma lista de elementos, com o mesmo algoritmo de paginação do
 * {@code BaseDocTemplate} do ReportLab (páginas abertas sob demanda, divisão de elementos,
 * adiamento para a próxima página, troca de modelo de página).
 *
 * <p>O desenho fixo de cada página ({@link PageTemplate.Decorator}) é aplicado no fim, por baixo
 * do conteúdo, quando o total de páginas já é conhecido. É o que o gerador Python fazia rodando o
 * build duas vezes. Uma instância gera um único documento.
 */
public final class DocTemplate implements LayoutContext {

    /** O ReportLab desiste após 10 páginas seguidas sem conteúdo (loop de diagramação). */
    private static final int EMPTY_PAGES_ALLOWED = 10;

    private final PDRectangle pageSize;
    private final List<PageTemplate> templates;
    private final PDDocument document = new PDDocument();
    private final FontSet fonts = new FontSet();
    private final List<PageTemplate> templatePerPage = new ArrayList<>();

    private PageTemplate pageTemplate;
    private Integer nextTemplateIndex;
    private Frame frame;
    private int page;
    private int pendingPageBegins;
    private int flowablesOnPage;
    private int emptyPages;
    private PDPageContentStream contentStream;
    private PdfCanvas canvas;

    public DocTemplate(PDRectangle pageSize, List<PageTemplate> templates) {
        this.pageSize = pageSize;
        this.templates = List.copyOf(templates);
    }

    public FontSet fonts() {
        return fonts;
    }

    /** Diagrama os elementos e devolve o PDF. */
    public byte[] build(List<Flowable> story, Consumer<PDDocumentInformation> metadata) {
        try {
            Deque<Flowable> flowables = new ArrayDeque<>(story);
            pendingPageBegins = 1;
            pageTemplate = templates.get(0);
            page = 0;
            while (!flowables.isEmpty()) {
                beginPendingPages();
                handleFlowable(flowables);
            }
            if (pendingPageBegins > 0) {
                // a última página já foi encerrada: não abre uma página em branco no fim
                pendingPageBegins--;
                beginPendingPages();
            } else {
                handlePageBreak();
            }
            decoratePages();
            metadata.accept(document.getDocumentInformation());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            // fecha sem lançar, para não esconder o erro original de diagramação
            try {
                closeContentStream();
            } catch (UncheckedIOException ignored) {
                // o documento é descartado de qualquer forma
            }
            try {
                document.close();
            } catch (IOException ignored) {
                // idem
            }
        }
    }

    @Override
    public Frame currentFrame() {
        return frame;
    }

    @Override
    public Frame peekNextFrame() {
        PageTemplate next = nextTemplateIndex != null ? templates.get(nextTemplateIndex) : pageTemplate;
        return next.frame();
    }

    // ── ações (chamadas pelos ActionFlowable) ─────────────────────

    void handlePageBreak() {
        handlePageEnd();
    }

    void handleFrameEnd() {
        // cada modelo tem um único quadro: fim do quadro = fim da página
        handlePageEnd();
        frame = null;
    }

    void handleNextPageTemplate(String templateId) {
        for (int i = 0; i < templates.size(); i++) {
            if (templates.get(i).id().equals(templateId)) {
                nextTemplateIndex = i;
                return;
            }
        }
        throw new IllegalArgumentException("Modelo de página inexistente: " + templateId);
    }

    // ── núcleo da paginação ───────────────────────────────────────

    private void handleFlowable(Deque<Flowable> flowables) {
        Flowable f = flowables.pollFirst();
        if (f instanceof ActionFlowable action) {
            action.apply(this);
            return;
        }
        if (frame.add(f, canvas)) {
            flowablesOnPage++;
            return;
        }
        List<Flowable> parts = frame.split(f, this);
        if (!parts.isEmpty()) {
            Flowable first = parts.get(0);
            if (first instanceof ActionFlowable) {
                pushFront(flowables, parts);
            } else {
                if (!frame.add(first, canvas)) {
                    throw new LayoutException("Erro ao dividir " + f.describe() + " na página " + page);
                }
                flowablesOnPage++;
                pushFront(flowables, parts.subList(1, parts.size()));
            }
            return;
        }
        if (f.postponed) {
            throw new LayoutException(f.describe() + " é grande demais para caber em uma página (página " + page + ")");
        }
        f.postponed = true;
        flowables.addFirst(f);
        handleFrameEnd();
    }

    private void beginPendingPages() {
        while (pendingPageBegins > 0) {
            pendingPageBegins--;
            handlePageBegin();
        }
    }

    private void handlePageBegin() {
        page++;
        templatePerPage.add(pageTemplate);
        frame = pageTemplate.frame();
        frame.reset();
        flowablesOnPage = 0;
        PDPage pdPage = new PDPage(pageSize);
        document.addPage(pdPage);
        try {
            contentStream = new PDPageContentStream(document, pdPage);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        canvas = new PdfCanvas(contentStream, fonts, page);
    }

    private void handlePageEnd() {
        emptyPages = flowablesOnPage == 0 ? emptyPages + 1 : 0;
        if (emptyPages >= EMPTY_PAGES_ALLOWED) {
            throw new LayoutException("Mais de " + EMPTY_PAGES_ALLOWED
                    + " páginas geradas sem conteúdo: algum elemento é grande demais para a página");
        }
        closeContentStream();
        if (nextTemplateIndex != null) {
            pageTemplate = templates.get(nextTemplateIndex);
            nextTemplateIndex = null;
        }
        pendingPageBegins++;
    }

    private void closeContentStream() {
        if (contentStream != null) {
            try {
                contentStream.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                contentStream = null;
                canvas = null;
            }
        }
    }

    private void decoratePages() throws IOException {
        int total = page;
        for (int i = 0; i < total; i++) {
            PDPage pdPage = document.getPage(i);
            try (PDPageContentStream under = new PDPageContentStream(
                    document, pdPage, PDPageContentStream.AppendMode.PREPEND, true)) {
                PdfCanvas c = new PdfCanvas(under, fonts, i + 1);
                c.saveState();
                templatePerPage.get(i).decorator().decorate(c, i + 1, total);
                c.restoreState();
            }
        }
    }

    private static void pushFront(Deque<Flowable> deque, List<Flowable> items) {
        for (int i = items.size() - 1; i >= 0; i--) {
            deque.addFirst(items.get(i));
        }
    }
}
