package br.com.lector.menctor.pdf;

/** Cor RGB com componentes entre 0 e 1 (equivalente a {@code reportlab.lib.colors.Color}). */
public record Rgb(float r, float g, float b) {

    public static final Rgb WHITE = new Rgb(1f, 1f, 1f);
    public static final Rgb BLACK = new Rgb(0f, 0f, 0f);

    /** Converte "#RRGGBB" como o {@code colors.HexColor} do ReportLab. */
    public static Rgb hex(String hex) {
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        if (h.length() != 6) {
            throw new IllegalArgumentException("Cor inválida: " + hex);
        }
        int v = Integer.parseInt(h, 16);
        return new Rgb(((v >> 16) & 0xFF) / 255f, ((v >> 8) & 0xFF) / 255f, (v & 0xFF) / 255f);
    }
}
