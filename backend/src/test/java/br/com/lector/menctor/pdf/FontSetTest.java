package br.com.lector.menctor.pdf;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FontSetTest {

    /** Larguras esperadas obtidas com {@code pdfmetrics.stringWidth} do ReportLab. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Loghaus Logística | HELVETICA | 9 | 73.044",
            "Pesquisa de Clima Organizacional — | HELVETICA_BOLD | 22 | 386.36400000000003",
            "Café ≥ ✓ 😀 中 | HELVETICA | 10 | 60.5",
            "ÁÉÍÓÚ ãõç €™ | HELVETICA_OBLIQUE | 8 | 54.688"})
    void larguraIgualAoReportLab(String texto, StdFont fonte, double tamanho, double esperado) {
        assertEquals(esperado, new FontSet().stringWidth(texto, fonte, tamanho), 1e-9);
    }

    @Test
    void qualquerCaractereViraPdfSemErro() {
        String texto = "Emoji 😀 CJK 中文 cirílico Жж controle \u0000\u0007\t\n árabe عربى ZWSP\u200b fim";
        assertDoesNotThrow(() -> {
            try (PDDocument doc = new PDDocument()) {
                PDPage page = new PDPage();
                doc.addPage(page);
                FontSet fonts = new FontSet();
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    PdfCanvas canvas = new PdfCanvas(cs, fonts, 1);
                    canvas.setFont(StdFont.HELVETICA, 10);
                    canvas.drawString(10, 10, texto);
                }
                doc.save(new ByteArrayOutputStream());
            }
        });
    }
}
