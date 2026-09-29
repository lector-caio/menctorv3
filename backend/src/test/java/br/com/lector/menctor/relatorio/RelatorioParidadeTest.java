package br.com.lector.menctor.relatorio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * O relatório Java deve ser idêntico ao do gerador Python original (ver
 * {@code src/test/resources/referencia-python/README.md}).
 */
class RelatorioParidadeTest {

    private static final double TOLERANCIA_POSICAO_PT = 0.01;
    private static final double TOLERANCIA_PIXELS_PCT = 0.05;

    private record Glifo(String texto, float x, float y, float tamanho, String fonte) {
    }

    @ParameterizedTest
    @ValueSource(strings = {"exemplo", "test_api", "dims_000", "dims_013", "dims_030", "long_texts", "unicode", "scores_edge"})
    void relatorioIgualAoDoPython(String caso) throws IOException {
        byte[] java = RelatorioPsicossocialPdf.gerar(config(caso));
        byte[] python = recurso(caso + ".pdf");

        try (PDDocument ref = Loader.loadPDF(python); PDDocument gerado = Loader.loadPDF(java)) {
            assertEquals(ref.getNumberOfPages(), gerado.getNumberOfPages(), "número de páginas");
            PDFRenderer rRef = new PDFRenderer(ref);
            PDFRenderer rGerado = new PDFRenderer(gerado);
            for (int p = 1; p <= ref.getNumberOfPages(); p++) {
                assertEquals(texto(ref, p), texto(gerado, p), "texto da página " + p);
                compararGlifos(glifos(ref, p), glifos(gerado, p), p);
                double pct = pixelsDiferentes(rRef.renderImageWithDPI(p - 1, 50, ImageType.RGB),
                        rGerado.renderImageWithDPI(p - 1, 50, ImageType.RGB));
                assertTrue(pct <= TOLERANCIA_PIXELS_PCT, "página " + p + ": " + pct + "% dos pixels diferentes");
            }
        }
    }

    private static RelatorioConfig config(String caso) throws IOException {
        if (caso.equals("exemplo")) {
            return RelatorioConfig.EXEMPLO;
        }
        return RelatorioPayload.parse(new ObjectMapper().readTree(recurso(caso + ".json")));
    }

    private static byte[] recurso(String nome) throws IOException {
        try (InputStream in = RelatorioParidadeTest.class.getResourceAsStream("/referencia-python/" + nome)) {
            if (in == null) {
                throw new IOException("recurso não encontrado: " + nome);
            }
            return in.readAllBytes();
        }
    }

    private static String texto(PDDocument doc, int pagina) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(pagina);
        stripper.setEndPage(pagina);
        return stripper.getText(doc);
    }

    private static List<Glifo> glifos(PDDocument doc, int pagina) throws IOException {
        List<Glifo> glifos = new ArrayList<>();
        PDFTextStripper stripper = new PDFTextStripper() {
            @Override
            protected void processTextPosition(TextPosition t) {
                glifos.add(new Glifo(t.getUnicode(), t.getXDirAdj(), t.getYDirAdj(), t.getFontSizeInPt(),
                        t.getFont() == null ? "?" : t.getFont().getName()));
            }
        };
        stripper.setStartPage(pagina);
        stripper.setEndPage(pagina);
        stripper.getText(doc);
        return glifos;
    }

    private static void compararGlifos(List<Glifo> ref, List<Glifo> gerado, int pagina) {
        assertEquals(ref.size(), gerado.size(), "quantidade de glifos na página " + pagina);
        for (int i = 0; i < ref.size(); i++) {
            Glifo a = ref.get(i);
            Glifo b = gerado.get(i);
            String onde = "página " + pagina + ", glifo " + i + " (" + a + " x " + b + ")";
            assertEquals(a.texto(), b.texto(), onde);
            assertEquals(a.fonte(), b.fonte(), onde);
            assertEquals(a.tamanho(), b.tamanho(), 0.01, onde);
            assertEquals(a.x(), b.x(), TOLERANCIA_POSICAO_PT, onde);
            assertEquals(a.y(), b.y(), TOLERANCIA_POSICAO_PT, onde);
        }
    }

    private static double pixelsDiferentes(BufferedImage a, BufferedImage b) {
        assertEquals(a.getWidth(), b.getWidth());
        assertEquals(a.getHeight(), b.getHeight());
        long diferentes = 0;
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                int c1 = a.getRGB(x, y);
                int c2 = b.getRGB(x, y);
                int d = Math.max(Math.abs(((c1 >> 16) & 255) - ((c2 >> 16) & 255)),
                        Math.max(Math.abs(((c1 >> 8) & 255) - ((c2 >> 8) & 255)), Math.abs((c1 & 255) - (c2 & 255))));
                if (d > 24) {
                    diferentes++;
                }
            }
        }
        return 100.0 * diferentes / ((double) a.getWidth() * a.getHeight());
    }
}
