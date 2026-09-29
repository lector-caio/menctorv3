package br.com.lector.menctor.pdf;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Parágrafo de texto puro com quebra de linha e justificação idênticas às do
 * {@code platypus.Paragraph} do ReportLab (caminho de fragmento único).
 *
 * <p>Diferente do ReportLab, o texto não é interpretado como marcação: {@code &}, {@code <} e
 * afins aparecem literalmente, o que evita que dados vindos da API sumam ou quebrem o PDF.
 */
public final class Paragraph extends Flowable {

    private static final double FUZZ = 1e-6;
    /** Encolhimento permitido dos espaços para uma linha caber ({@code rl_config.spaceShrinkage}). */
    private static final double SPACE_SHRINKAGE = 0.05;
    private static final char SOFT_HYPHEN = '\u00AD';

    private enum Kind { NORMAL, SPLIT, SPLIT_END }

    /** Palavra; SPLIT/SPLIT_END são pedaços de uma palavra longa demais para uma linha. */
    private record Word(String text, Kind kind) {
        boolean isSplit() {
            return kind != Kind.NORMAL;
        }
    }

    /** Linha quebrada: espaço que sobrou (negativo se precisou encolher) e as palavras. */
    private record Line(double extraSpace, List<Word> words) {
    }

    private final ParagraphStyle style;
    private final FontSet fonts;
    private final List<Word> words;
    private boolean justifyLast;
    private List<Line> lines;
    private double height;

    public Paragraph(String text, ParagraphStyle style, FontSet fonts) {
        this(wordsOf(text), style, fonts);
    }

    private Paragraph(List<Word> words, ParagraphStyle style, FontSet fonts) {
        this.words = words;
        this.style = style;
        this.fonts = fonts;
    }

    private static List<Word> wordsOf(String text) {
        String cleaned = RlText.strip(RlText.cleanBlockQuotedText(text));
        List<Word> result = new ArrayList<>();
        if (cleaned.isEmpty()) {
            return result;
        }
        for (String w : RlText.split(cleaned)) {
            // hífen opcional: o ReportLab não o desenha (a hifenização nesse ponto não é suportada)
            String visible = w.indexOf(SOFT_HYPHEN) >= 0 ? w.replace(String.valueOf(SOFT_HYPHEN), "") : w;
            if (!visible.isEmpty()) {
                result.add(new Word(visible, Kind.NORMAL));
            }
        }
        return result;
    }

    @Override
    public double spaceAfter() {
        return style.spaceAfter();
    }

    @Override
    public String describe() {
        StringBuilder sb = new StringBuilder("Parágrafo \"");
        for (Word w : words) {
            if (sb.length() > 60) {
                sb.append("...");
                break;
            }
            sb.append(w.text()).append(' ');
        }
        return sb.toString().strip() + "\"";
    }

    @Override
    public Size wrap(double availWidth, double availHeight) {
        if (availWidth < FUZZ) {
            return new Size(0, 0x7fffffff);
        }
        lines = breakLines(availWidth);
        height = lines.size() * style.leading();
        return new Size(availWidth, height);
    }

    private List<Line> breakLines(double maxWidth) {
        List<Line> result = new ArrayList<>();
        if (words.isEmpty()) {
            return result;
        }
        StdFont font = style.font();
        double fontSize = style.fontSize();
        double spaceWidth = fonts.stringWidth(" ", font, fontSize);
        double dSpaceShrink = SPACE_SHRINKAGE * spaceWidth;
        Deque<Word> pending = new ArrayDeque<>(words);
        List<Word> cLine = new ArrayList<>();
        double currentWidth = -spaceWidth; // compensa o espaço antes da primeira palavra
        boolean forcedSplit = false;
        while (!pending.isEmpty()) {
            Word word = pending.pollFirst();
            if (word.text().isEmpty() && word.isSplit()) {
                forcedSplit = true;
            }
            double wordWidth = fonts.stringWidth(word.text(), font, fontSize);
            double newWidth = currentWidth + spaceWidth + wordWidth;
            double limWidth = maxWidth + dSpaceShrink * cLine.size();
            if (newWidth > limWidth && !forcedSplit && !word.isSplit() && wordWidth > maxWidth) {
                // palavra maior que a linha inteira: quebra por caractere
                List<Word> pieces = splitWord(word.text(), currentWidth + spaceWidth, maxWidth);
                for (int i = pieces.size() - 1; i >= 0; i--) {
                    pending.addFirst(pieces.get(i));
                }
                forcedSplit = true;
                continue;
            }
            if (newWidth <= limWidth || cLine.isEmpty() || forcedSplit) {
                if (!word.text().isEmpty()) {
                    cLine.add(word);
                }
                if (forcedSplit) {
                    forcedSplit = false;
                    result.add(new Line(maxWidth - newWidth, cLine));
                    cLine = new ArrayList<>();
                    currentWidth = -spaceWidth;
                } else {
                    currentWidth = newWidth;
                }
            } else {
                result.add(new Line(maxWidth - currentWidth, cLine));
                cLine = new ArrayList<>();
                cLine.add(word);
                currentWidth = wordWidth;
            }
        }
        if (!cLine.isEmpty()) {
            result.add(new Line(maxWidth - currentWidth, cLine));
        }
        return result;
    }

    /** {@code _splitWord}: pedaços que cabem na linha atual e nas seguintes. */
    private List<Word> splitWord(String word, double lineWidth, double maxWidth) {
        List<Word> pieces = new ArrayList<>();
        StringBuilder piece = new StringBuilder();
        int i = 0;
        while (i < word.length()) {
            int cp = word.codePointAt(i);
            i += Character.charCount(cp);
            String c = new String(Character.toChars(cp));
            double cw = fonts.stringWidth(c, style.font(), style.fontSize());
            double newLineWidth = lineWidth + cw;
            if (newLineWidth > maxWidth) {
                pieces.add(new Word(piece.toString(), Kind.SPLIT));
                newLineWidth = cw;
                piece.setLength(0);
            }
            piece.append(c);
            lineWidth = newLineWidth;
        }
        pieces.add(new Word(piece.toString(), Kind.SPLIT_END));
        return pieces;
    }

    @Override
    public List<Flowable> split(double availWidth, double availHeight, LayoutContext context) {
        if (words.isEmpty() || availWidth < FUZZ || availHeight < FUZZ) {
            return List.of();
        }
        if (lines == null) {
            wrap(availWidth, availHeight);
        }
        int s = (int) (availHeight / style.leading());
        if (s <= 1) {
            // não deixa uma linha órfã no fim da página
            lines = null;
            return List.of();
        }
        int n = lines.size();
        if (n <= s) {
            return List.of(this);
        }
        Paragraph first = new Paragraph(wordsOfLines(0, s), style, fonts);
        first.justifyLast = true;
        Paragraph rest = new Paragraph(wordsOfLines(s, n), style, fonts);
        return List.of(first, rest);
    }

    /** {@code _yieldBLParaWords}: palavras das linhas, recompondo pedaços de palavras quebradas. */
    private List<Word> wordsOfLines(int start, int stop) {
        List<Word> result = new ArrayList<>();
        StringBuilder pieces = new StringBuilder();
        boolean inPieces = false;
        for (Line line : lines.subList(start, stop)) {
            for (Word w : line.words()) {
                if (w.isSplit()) {
                    pieces.append(w.text());
                    inPieces = true;
                    if (w.kind() == Kind.SPLIT_END) {
                        result.add(new Word(pieces.toString(), Kind.NORMAL));
                        pieces.setLength(0);
                        inPieces = false;
                    }
                    continue;
                }
                if (inPieces) {
                    result.add(new Word(pieces.toString(), Kind.NORMAL));
                    pieces.setLength(0);
                    inPieces = false;
                }
                result.add(w);
            }
        }
        if (inPieces) {
            result.add(new Word(pieces.toString(), Kind.NORMAL));
        }
        return result;
    }

    @Override
    public void draw(PdfCanvas canvas) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        canvas.saveState();
        canvas.setFillColor(style.textColor());
        // rl_config.paraFontSizeHeightOffset: a primeira linha começa em altura - tamanho da fonte
        PdfCanvas.TextObject tx = canvas.beginText(0, height - style.fontSize(),
                style.font(), style.fontSize(), style.leading());
        int last = lines.size() - 1;
        for (int i = 0; i <= last; i++) {
            Line line = lines.get(i);
            boolean lastLine = !justifyLast && i == last;
            drawLine(tx, line, lastLine);
        }
        tx.end();
        canvas.restoreState();
    }

    private void drawLine(PdfCanvas.TextObject tx, Line line, boolean lastLine) {
        List<String> texts = new ArrayList<>(line.words().size());
        for (Word w : line.words()) {
            texts.add(w.text());
        }
        String text = String.join(" ", texts);
        double extra = line.extraSpace();
        boolean simple = switch (style.alignment()) {
            case JUSTIFY -> (-1e-8 < extra && extra <= 1e-8) || (lastLine && extra > -1e-8);
            default -> extra > -1e-8;
        };
        int nSpaces = 0;
        if (!simple) {
            nSpaces = line.words().size() + nbspCount(text) - 1;
            simple = nSpaces <= 0;
        }
        if (simple) {
            // CENTER/RIGHT não são usados pelo relatório; alinhados como LEFT
            tx.textLine(text);
        } else {
            tx.setWordSpace(extra / nSpaces);
            tx.textLine(text);
            tx.setWordSpace(0);
        }
    }

    private static int nbspCount(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\u00A0') {
                count++;
            }
        }
        return count;
    }
}
