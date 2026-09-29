package br.com.lector.menctor.pdf;

import java.util.ArrayList;
import java.util.List;

/** Normalização de espaços em branco idêntica à de {@code reportlab.platypus.paragraph}. */
final class RlText {

    private static final char NBSP = '\u00A0';

    private RlText() {
    }

    /** Conjunto {@code _wsc} do ReportLab (não inclui o NBSP). */
    static boolean isWsc(int cp) {
        return cp == 0x09 || cp == 0x0A || cp == 0x0B || cp == 0x0C || cp == 0x0D
                || (cp >= 0x1C && cp <= 0x20) || cp == 0x85 || cp == 0x1680
                || (cp >= 0x2000 && cp <= 0x200B) || cp == 0x2028 || cp == 0x2029
                || cp == 0x202F || cp == 0x205F || cp == 0x3000;
    }

    /** {@code str.isspace()} do Python: igual ao {@code _wsc}, mas com NBSP e sem o U+200B. */
    private static boolean isPySpace(int cp) {
        return cp == NBSP || (cp != 0x200B && isWsc(cp));
    }

    /** {@code text.strip(_wsc)}. */
    static String strip(String text) {
        int start = 0;
        int end = text.length();
        while (start < end && isWsc(text.codePointAt(start))) {
            start += Character.charCount(text.codePointAt(start));
        }
        while (end > start && isWsc(text.codePointBefore(end))) {
            end -= Character.charCount(text.codePointBefore(end));
        }
        return text.substring(start, end);
    }

    /**
     * {@code paragraph.split(text)}: com NBSP no texto, divide nos caracteres {@code _wsc} (o NBSP
     * fica dentro da palavra); sem NBSP, usa o {@code str.split()} do Python.
     */
    static List<String> split(String text) {
        boolean hasNbsp = text.indexOf(NBSP) >= 0;
        List<String> words = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        boolean keepEmpty = hasNbsp; // re.split preserva vazios nas pontas
        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            boolean sep = hasNbsp ? isWsc(cp) : isPySpace(cp);
            if (sep) {
                if (word.length() > 0 || keepEmpty) {
                    words.add(word.toString());
                    word.setLength(0);
                }
                // consome o restante da sequência de separadores
                while (i < text.length()) {
                    int next = text.codePointAt(i);
                    if (!(hasNbsp ? isWsc(next) : isPySpace(next))) {
                        break;
                    }
                    i += Character.charCount(next);
                }
                if (hasNbsp && i >= text.length()) {
                    words.add("");
                }
            } else {
                word.appendCodePoint(cp);
            }
        }
        if (word.length() > 0) {
            words.add(word.toString());
        }
        return words;
    }

    /** {@code cleanBlockQuotedText}: une as linhas e reduz qualquer sequência de espaços a um. */
    static String cleanBlockQuotedText(String text) {
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\n", -1)) {
            List<String> words = new ArrayList<>();
            for (String w : split(strip(line))) {
                if (!w.isEmpty()) {
                    words.add(w);
                }
            }
            String cleaned = String.join(" ", words);
            if (!cleaned.isEmpty()) {
                lines.add(cleaned);
            }
        }
        return String.join(" ", lines);
    }
}
