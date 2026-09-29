package br.com.lector.menctor.pdf;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * Fontes de um documento e medição de texto.
 *
 * <p>Reproduz o comportamento do ReportLab para fontes padrão: cada caractere é escrito na fonte
 * principal (codificação WinAnsi); se não existir nela, tenta Symbol, depois ZapfDingbats e, em
 * último caso, desenha o glifo "■" do ZapfDingbats. Assim nenhum texto vindo da API derruba a
 * geração do PDF, e as larguras (que definem as quebras de linha e de página) ficam idênticas às
 * do gerador Python original.
 *
 * <p>Instâncias não são thread-safe: crie uma por documento.
 */
public final class FontSet {

    /** Trecho de texto que pode ser escrito numa única fonte. */
    public record Run(PDType1Font font, String text) {
    }

    /** Caracteres fora de Latin-1 que o codec WinAnsi do ReportLab aceita. */
    private static final Set<Integer> WIN_ANSI_EXTRAS = Set.of(
            0x152, 0x153, 0x160, 0x161, 0x178, 0x17D, 0x17E, 0x192, 0x2C6, 0x2DC,
            0x2013, 0x2014, 0x2018, 0x2019, 0x201A, 0x201C, 0x201D, 0x201E, 0x2020, 0x2021,
            0x2022, 0x2026, 0x2030, 0x2039, 0x203A, 0x20AC, 0x2122);

    /**
     * Letras gregas que o PDFBox procura com nomes "...greek", ausentes na fonte Symbol. O ReportLab
     * as escreve com os glifos equivalentes da Symbol.
     */
    private static final Map<Integer, String> SYMBOL_ALIASES = Map.of(
            0x0394, "\u2206",  // Δ grego -> Delta (U+2206)
            0x03A9, "\u2126",  // Ω grego -> Omega (U+2126)
            0x03BC, "\u00B5"); // μ grego -> mu (U+00B5)

    /** Glifo usado pelo ReportLab para caracteres sem representação ("n" do ZapfDingbats). */
    private static final String NOTDEF = "\u25A0";

    private enum Target { PRIMARY, SYMBOL, ZAPF, NOTDEF }

    private final EnumMap<StdFont, PDType1Font> primary = new EnumMap<>(StdFont.class);
    private final PDType1Font symbol = new PDType1Font(Standard14Fonts.FontName.SYMBOL);
    private final PDType1Font zapf = new PDType1Font(Standard14Fonts.FontName.ZAPF_DINGBATS);
    private final Map<Integer, Target> fallbackCache = new HashMap<>();

    public FontSet() {
        for (StdFont f : StdFont.values()) {
            primary.put(f, new PDType1Font(f.pdfboxName));
        }
    }

    public PDType1Font pdfFont(StdFont font) {
        return primary.get(font);
    }

    /** Largura do texto em pontos, como {@code pdfmetrics.stringWidth}. */
    public double stringWidth(String text, StdFont font, double size) {
        long units = 0;
        for (Run run : runs(text, font)) {
            units += widthUnits(run);
        }
        // mesma ordem de operações do ReportLab: soma_inteira * 0.001 * tamanho
        return units * 0.001 * size;
    }

    /** Quebra o texto em trechos por fonte (equivalente a {@code pdfmetrics.unicode2T1}). */
    public List<Run> runs(String text, StdFont font) {
        List<Run> runs = new ArrayList<>(1);
        PDType1Font current = null;
        StringBuilder buf = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            PDType1Font target;
            String piece;
            switch (targetFor(cp)) {
                case PRIMARY -> {
                    target = primary.get(font);
                    // o ReportLab escreve NBSP como espaço e o hífen opcional com o glifo "hyphen"
                    piece = cp == 0xA0 ? " " : cp == 0xAD ? "-" : new String(Character.toChars(cp));
                }
                case SYMBOL -> {
                    target = symbol;
                    piece = SYMBOL_ALIASES.getOrDefault(cp, new String(Character.toChars(cp)));
                }
                case ZAPF -> {
                    target = zapf;
                    piece = new String(Character.toChars(cp));
                }
                default -> {
                    target = zapf;
                    piece = NOTDEF;
                }
            }
            if (target != current && buf.length() > 0) {
                runs.add(new Run(current, buf.toString()));
                buf.setLength(0);
            }
            current = target;
            buf.append(piece);
        }
        if (buf.length() > 0) {
            runs.add(new Run(current, buf.toString()));
        }
        return runs;
    }

    private static long widthUnits(Run run) {
        try {
            // fontes padrão têm larguras inteiras no AFM
            return Math.round(run.font().getStringWidth(run.text()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Target targetFor(int cp) {
        if ((cp >= 0x20 && cp <= 0x7E) || (cp >= 0xA0 && cp <= 0xFF) || WIN_ANSI_EXTRAS.contains(cp)) {
            return Target.PRIMARY;
        }
        return fallbackCache.computeIfAbsent(cp, this::fallbackFor);
    }

    private Target fallbackFor(int cp) {
        String s = SYMBOL_ALIASES.getOrDefault(cp, new String(Character.toChars(cp)));
        if (canEncode(symbol, s)) {
            return Target.SYMBOL;
        }
        if (canEncode(zapf, new String(Character.toChars(cp)))) {
            return Target.ZAPF;
        }
        return Target.NOTDEF;
    }

    private static boolean canEncode(PDType1Font font, String s) {
        try {
            font.encode(s);
            return true;
        } catch (IllegalArgumentException | IOException e) {
            return false;
        }
    }
}
