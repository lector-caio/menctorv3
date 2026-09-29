package br.com.lector.menctor.pdf;

import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/** Fontes padrão (não embutidas) usadas pelo relatório, as mesmas do gerador ReportLab original. */
public enum StdFont {
    HELVETICA(Standard14Fonts.FontName.HELVETICA),
    HELVETICA_BOLD(Standard14Fonts.FontName.HELVETICA_BOLD),
    HELVETICA_OBLIQUE(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

    final Standard14Fonts.FontName pdfboxName;

    StdFont(Standard14Fonts.FontName pdfboxName) {
        this.pdfboxName = pdfboxName;
    }
}
