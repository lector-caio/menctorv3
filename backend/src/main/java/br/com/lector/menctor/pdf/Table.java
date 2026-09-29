package br.com.lector.menctor.pdf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Tabela com a mesma geometria do {@code platypus.Table} do ReportLab: altura das linhas pelo
 * conteúdo, alinhamento vertical inferior por padrão, fundos, linhas de grade e divisão por linhas
 * entre páginas (incluindo o ajuste dos comandos de borda na divisão).
 *
 * <p>Uma célula é uma {@link String} (texto numa linha só, na fonte da célula) ou uma lista de
 * {@link Flowable}.
 */
public final class Table extends Flowable {

    /** Estilo de uma célula ({@code CellStyle}), com os padrões do ReportLab. */
    public static final class CellStyle {
        StdFont fontName = StdFont.HELVETICA;
        double fontSize = 10;
        double leading = 12;
        double leftPadding = 6;
        double rightPadding = 6;
        double topPadding = 3;
        double bottomPadding = 3;
        Rgb color = Rgb.BLACK;
        String alignment = "LEFT";
        String valign = "BOTTOM";
    }

    /** Comando de estilo ({@code TableStyle}): operação, célula inicial, célula final e argumentos. */
    public record Cmd(String op, int sc, int sr, int ec, int er, List<Object> args) {
        Cmd withRange(int sc, int sr, int ec, int er) {
            return new Cmd(op, sc, sr, ec, er, args);
        }

        Cmd withOp(String op, int sc, int sr, int ec, int er) {
            return new Cmd(op, sc, sr, ec, er, args);
        }
    }

    private static final Set<String> LINE_OPS = Set.of(
            "GRID", "BOX", "OUTLINE", "INNERGRID", "LINEBELOW", "LINEABOVE", "LINEBEFORE", "LINEAFTER");
    private static final Set<String> BACKGROUND_OPS = Set.of("BACKGROUND", "ROWBACKGROUNDS");

    public static Cmd background(int sc, int sr, int ec, int er, Rgb color) {
        return new Cmd("BACKGROUND", sc, sr, ec, er, List.of(color));
    }

    public static Cmd rowBackgrounds(int sc, int sr, int ec, int er, Rgb... colors) {
        return new Cmd("ROWBACKGROUNDS", sc, sr, ec, er, List.of((Object[]) colors));
    }

    /** Linha (LINEBELOW, BOX, ...) com ponta e junção arredondadas, padrão das tabelas do ReportLab. */
    public static Cmd line(String op, int sc, int sr, int ec, int er, double weight, Rgb color) {
        if (!LINE_OPS.contains(op)) {
            throw new IllegalArgumentException("Comando de linha desconhecido: " + op);
        }
        return new Cmd(op, sc, sr, ec, er, List.of(weight, color));
    }

    /** Estilo de célula: TOPPADDING, BOTTOMPADDING, LEFTPADDING, RIGHTPADDING, FONTNAME, FONTSIZE, ... */
    public static Cmd cell(String op, int sc, int sr, int ec, int er, Object value) {
        return new Cmd(op, sc, sr, ec, er, List.of(value));
    }

    private final List<List<Object>> cells;
    private final double[] colWidths;
    private final Double[] argRowHeights;
    private final CellStyle[][] cellStyles;
    private final int nrows;
    private final int ncols;
    private final List<Cmd> bkgrndCmds = new ArrayList<>();
    private final List<Cmd> lineCmds = new ArrayList<>();

    private double[] rowHeights;
    private double[] rowPositions;
    private double[] colPositions;
    private double tableWidth;
    private double tableHeight;

    // estado de desenho das linhas (_curcolor, _curweight)
    private Rgb curColor;
    private double curWeight;

    public Table(List<List<Object>> cells, double[] colWidths, List<Cmd> style) {
        this(cells, colWidths, new Double[cells.size()], null);
        for (Cmd cmd : style) {
            addCommand(cmd);
        }
    }

    private Table(List<List<Object>> cells, double[] colWidths, Double[] rowHeights, CellStyle[][] cellStyles) {
        this.cells = cells;
        this.nrows = cells.size();
        this.ncols = colWidths.length;
        if (nrows == 0) {
            throw new IllegalArgumentException("A tabela precisa de pelo menos uma linha");
        }
        this.colWidths = colWidths;
        this.argRowHeights = rowHeights;
        if (cellStyles == null) {
            cellStyles = new CellStyle[nrows][ncols];
            for (CellStyle[] row : cellStyles) {
                for (int j = 0; j < ncols; j++) {
                    row[j] = new CellStyle();
                }
            }
        }
        this.cellStyles = cellStyles;
        this.hAlign = HAlign.CENTER;
    }

    @Override
    public String describe() {
        return "Tabela";
    }

    // ── comandos de estilo ────────────────────────────────────────

    private void addCommand(Cmd cmd) {
        if (BACKGROUND_OPS.contains(cmd.op())) {
            bkgrndCmds.add(cmd);
        } else if (LINE_OPS.contains(cmd.op())) {
            lineCmds.add(cmd);
        } else {
            int[] r = normCellRange(cmd.sc(), cmd.ec(), cmd.sr(), cmd.er());
            for (int i = r[2]; i <= r[3]; i++) {
                for (int j = r[0]; j <= r[1]; j++) {
                    setCellStyle(cellStyles[i][j], cmd.op(), cmd.args().get(0));
                }
            }
        }
    }

    private static void setCellStyle(CellStyle s, String op, Object value) {
        switch (op) {
            case "FONTNAME" -> s.fontName = (StdFont) value;
            case "FONTSIZE" -> s.fontSize = ((Number) value).doubleValue();
            case "LEADING" -> s.leading = ((Number) value).doubleValue();
            case "TEXTCOLOR" -> s.color = (Rgb) value;
            case "ALIGN" -> s.alignment = (String) value;
            case "VALIGN" -> s.valign = (String) value;
            case "LEFTPADDING" -> s.leftPadding = ((Number) value).doubleValue();
            case "RIGHTPADDING" -> s.rightPadding = ((Number) value).doubleValue();
            case "TOPPADDING" -> s.topPadding = ((Number) value).doubleValue();
            case "BOTTOMPADDING" -> s.bottomPadding = ((Number) value).doubleValue();
            default -> throw new IllegalArgumentException("Comando de tabela desconhecido: " + op);
        }
    }

    /** Devolve {sc, ec, sr, er} com índices negativos resolvidos e limitados à tabela. */
    private int[] normCellRange(int sc, int ec, int sr, int er) {
        if (sc < 0) sc += ncols;
        if (ec < 0) ec += ncols;
        if (sr < 0) sr += nrows;
        if (er < 0) er += nrows;
        return new int[] {Math.max(0, sc), Math.min(ncols - 1, ec), Math.max(0, sr), Math.min(nrows - 1, er)};
    }

    // ── dimensões ─────────────────────────────────────────────────

    @Override
    public Size wrap(double availWidth, double availHeight) {
        calc();
        return new Size(tableWidth, tableHeight);
    }

    private void calc() {
        calcHeights();
        if (colPositions == null) {
            colPositions = new double[ncols + 1];
            double width = 0;
            for (int j = 0; j < ncols; j++) {
                width = width + colWidths[j];
                colPositions[j + 1] = width;
            }
            tableWidth = width;
        }
    }

    private void calcHeights() {
        double[] h = new double[nrows];
        for (int i = 0; i < nrows; i++) {
            if (argRowHeights[i] != null) {
                h[i] = argRowHeights[i];
                continue;
            }
            double max = 0;
            for (int j = 0; j < ncols; j++) {
                CellStyle s = cellStyles[i][j];
                Object v = cells.get(i).get(j);
                double t;
                if (v instanceof String text) {
                    t = s.leading * text.split("\n", -1).length;
                } else {
                    t = listCellGeom(flowables(v), colWidths[j], s, 72000, null, null)[1];
                }
                t += s.bottomPadding + s.topPadding;
                if (t > max) {
                    max = t;
                }
            }
            h[i] = max;
        }
        rowHeights = h;
        // posições de baixo para cima com soma compensada, exatamente como o ReportLab
        double[] reversed = new double[nrows + 1];
        double height = 0;
        double c = 0;
        int k = 0;
        for (int i = nrows - 1; i >= 0; i--) {
            reversed[k++] = height;
            double y = h[i] - c;
            double t = height + y;
            c = (t - height) - y;
            height = t;
        }
        reversed[k] = height;
        tableHeight = height;
        rowPositions = new double[nrows + 1];
        for (int i = 0; i <= nrows; i++) {
            rowPositions[i] = reversed[nrows - i];
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Flowable> flowables(Object cell) {
        return (List<Flowable>) cell;
    }

    /** {@code _listCellGeom}: largura e altura do conteúdo de uma célula. */
    private static double[] listCellGeom(List<Flowable> content, double colWidth, CellStyle s, double aH,
                                         List<Double> widths, List<Double> heights) {
        if (content.isEmpty()) {
            return new double[] {0, 0};
        }
        double aW = colWidth - s.leftPadding - s.rightPadding;
        double availH = aH - s.topPadding - s.bottomPadding;
        double t = 0;
        double w = 0;
        Double sb0 = null;
        double sa = 0;
        for (Flowable v : content) {
            Size size = v.wrap(aW, availH);
            double sb = v.spaceBefore();
            sa = v.spaceAfter();
            if (widths != null) widths.add(size.width());
            if (heights != null) heights.add(size.height());
            w = Math.max(w, size.width());
            t += size.height() + sa + sb;
            if (sb0 == null) {
                sb0 = sb;
            }
        }
        return new double[] {w, t - sb0 - sa};
    }

    // ── divisão entre páginas ─────────────────────────────────────

    @Override
    public List<Flowable> split(double availWidth, double availHeight, LayoutContext context) {
        calc();
        int n = firstPossibleSplitRowPosition(availHeight);
        if (n <= 0) {
            return List.of();
        }
        if (n == nrows) {
            return List.of(this);
        }
        Double[] heights = Arrays.stream(rowHeights).boxed().toArray(Double[]::new);
        Table r0 = new Table(cells.subList(0, n), colWidths, Arrays.copyOfRange(heights, 0, n),
                Arrays.copyOfRange(cellStyles, 0, n));
        List<Cmd> splitLines = splitLineCmds(n);
        r0.cr0(n, splitLines, nrows);
        r0.cr0(n, bkgrndCmds, nrows);
        Table r1 = new Table(cells.subList(n, nrows), colWidths, Arrays.copyOfRange(heights, n, nrows),
                Arrays.copyOfRange(cellStyles, n, nrows));
        r1.cr10(n, splitLines);
        r1.cr10(n, bkgrndCmds);
        r0.hAlign = r1.hAlign = hAlign;
        return List.of(r0, r1);
    }

    private int firstPossibleSplitRowPosition(double availHeight) {
        double h = 0;
        int n = 1;
        int splitAt = 0;
        for (double rh : rowHeights) {
            if (h + rh > availHeight) {
                break;
            }
            splitAt = n;
            h = h + rh;
            n++;
        }
        return splitAt;
    }

    /** {@code _splitLineCmds}: caixas que atravessam a divisão viram linhas avulsas em cada parte. */
    private List<Cmd> splitLineCmds(int n) {
        List<Cmd> result = new ArrayList<>();
        for (Cmd c : lineCmds) {
            int sc = c.sc() < 0 ? c.sc() + ncols : c.sc();
            int ec = c.ec() < 0 ? c.ec() + ncols : c.ec();
            int sr = c.sr() < 0 ? c.sr() + nrows : c.sr();
            int er = c.er() < 0 ? c.er() + nrows : c.er();
            switch (c.op()) {
                case "BOX", "OUTLINE", "GRID" -> {
                    if (sr < n && er >= n) {
                        result.add(c.withOp("LINEABOVE", sc, sr, ec, sr));
                        result.add(c.withOp("LINEBEFORE", sc, sr, sc, er));
                        result.add(c.withOp("LINEAFTER", ec, sr, ec, er));
                        result.add(c.withOp("LINEBELOW", sc, er, ec, er));
                        if (c.op().equals("GRID")) {
                            result.add(c.withOp("LINEBELOW", sc, n - 1, ec, n - 1));
                            result.add(c.withOp("LINEABOVE", sc, n, ec, n));
                            result.add(c.withOp("INNERGRID", sc, sr, ec, er));
                        }
                    } else {
                        result.add(c.withRange(sc, sr, ec, er));
                    }
                }
                case "INNERGRID" -> {
                    if (sr < n && er >= n) {
                        result.add(c.withOp("LINEBELOW", sc, n - 1, ec, n - 1));
                        result.add(c.withOp("LINEABOVE", sc, n, ec, n));
                    }
                    result.add(c.withRange(sc, sr, ec, er));
                }
                case "LINEBELOW" -> {
                    if (sr < n && er >= n - 1) {
                        result.add(c.withOp("LINEABOVE", sc, n, ec, n));
                    }
                    result.add(c.withRange(sc, sr, ec, er));
                }
                case "LINEABOVE" -> {
                    if (sr <= n && er >= n) {
                        result.add(c.withOp("LINEBELOW", sc, n - 1, ec, n - 1));
                    }
                    result.add(c.withRange(sc, sr, ec, er));
                }
                default -> result.add(c.withRange(sc, sr, ec, er));
            }
        }
        return result;
    }

    /** {@code _cr_0}: comandos que valem para a primeira parte (linhas 0..n-1). */
    private void cr0(int n, List<Cmd> cmds, int originalRows) {
        for (Cmd c : cmds) {
            int sr = c.sr();
            int er = c.er();
            if (sr < 0) sr += originalRows;
            if (sr >= n) continue;
            if (er >= n) er = n - 1;
            addCommand(c.withRange(c.sc(), sr, c.ec(), er));
        }
    }

    /** {@code _cr_1_0}: comandos que valem para a segunda parte (linhas n.., renumeradas). */
    private void cr10(int n, List<Cmd> cmds) {
        for (Cmd c : cmds) {
            int sr = c.sr();
            int er = c.er();
            if (er >= 0 && er < n) continue;
            if (sr >= 0 && sr < n) sr = 0;
            if (sr >= n) sr -= n;
            if (er >= n) er -= n;
            addCommand(c.withRange(c.sc(), sr, c.ec(), er));
        }
    }

    // ── desenho ───────────────────────────────────────────────────

    @Override
    public void draw(PdfCanvas canvas) {
        canvas.saveState();
        curColor = null;
        curWeight = 0;
        drawBackgrounds(canvas);
        for (int i = 0; i < nrows; i++) {
            for (int j = 0; j < ncols; j++) {
                drawCell(canvas, cells.get(i).get(j), cellStyles[i][j],
                        colPositions[j], rowPositions[i + 1], colWidths[j], rowHeights[i]);
            }
        }
        drawLines(canvas);
        canvas.restoreState();
    }

    private void drawBackgrounds(PdfCanvas canvas) {
        for (Cmd cmd : bkgrndCmds) {
            int sc = cmd.sc() < 0 ? cmd.sc() + ncols : cmd.sc();
            int ec = cmd.ec() < 0 ? cmd.ec() + ncols : cmd.ec();
            int sr = cmd.sr() < 0 ? cmd.sr() + nrows : cmd.sr();
            int er = cmd.er() < 0 ? cmd.er() + nrows : cmd.er();
            double x0 = colPositions[sc];
            double y0 = rowPositions[sr];
            double x1 = colPositions[Math.min(ec + 1, ncols)];
            double y1 = rowPositions[Math.min(er + 1, nrows)];
            double w = x1 - x0;
            double h = y1 - y0;
            if (cmd.op().equals("ROWBACKGROUNDS")) {
                List<Object> cycle = cmd.args();
                for (int i = 0; i < er - sr + 1; i++) {
                    Rgb color = (Rgb) cycle.get(i % cycle.size());
                    double rh = rowHeights[sr + i];
                    if (color != null) {
                        canvas.setFillColor(color);
                        canvas.rect(x0, y0, w, -rh, true, false);
                    }
                    y0 = y0 - rh;
                }
            } else {
                canvas.setFillColor((Rgb) cmd.args().get(0));
                canvas.rect(x0, y0, w, h, true, false);
            }
        }
    }

    private void drawCell(PdfCanvas canvas, Object value, CellStyle s,
                          double colpos, double rowpos, double colwidth, double rowheight) {
        canvas.setFillColor(s.color);
        canvas.setFont(s.fontName, s.fontSize);
        if (value instanceof String text) {
            String[] vals = text.split("\n", -1);
            int n = vals.length;
            double y = switch (s.valign) {
                case "TOP" -> rowpos + rowheight - s.topPadding - s.fontSize;
                case "MIDDLE" -> rowpos + (s.bottomPadding + rowheight - s.topPadding + n * s.leading) / 2.0 - s.fontSize;
                default -> rowpos + s.bottomPadding + n * s.leading - s.fontSize;
            };
            for (String v : vals) {
                switch (s.alignment) {
                    case "CENTER", "CENTRE" -> canvas.drawCentredString(
                            colpos + (colwidth + s.leftPadding - s.rightPadding) * 0.5, y, v);
                    case "RIGHT" -> canvas.drawRightString(colpos + colwidth - s.rightPadding, y, v);
                    default -> canvas.drawString(colpos + s.leftPadding, y, v);
                }
                y -= s.leading;
            }
            return;
        }
        List<Flowable> content = flowables(value);
        List<Double> widths = new ArrayList<>();
        List<Double> heights = new ArrayList<>();
        double h = listCellGeom(content, colwidth, s, rowheight, widths, heights)[1];
        double y = switch (s.valign) {
            case "TOP" -> rowpos + rowheight - s.topPadding;
            case "MIDDLE" -> rowpos + (rowheight + s.bottomPadding - s.topPadding + h) / 2.0;
            default -> rowpos + s.bottomPadding + h;
        };
        if (!content.isEmpty()) {
            y += content.get(0).spaceBefore();
        }
        for (int k = 0; k < content.size(); k++) {
            Flowable v = content.get(k);
            double w = widths.get(k);
            double x = switch (s.alignment) {
                case "RIGHT" -> colpos + colwidth - s.rightPadding - w;
                case "CENTER", "CENTRE" -> colpos + (colwidth + s.leftPadding - s.rightPadding - w) / 2.0;
                default -> colpos + s.leftPadding;
            };
            y -= v.spaceBefore();
            y -= heights.get(k);
            v.drawOn(canvas, x, y, 0);
            y -= v.spaceAfter();
        }
    }

    private void drawLines(PdfCanvas canvas) {
        canvas.saveState();
        boolean capAndJoinSet = false;
        for (Cmd cmd : lineCmds) {
            if (!capAndJoinSet) {
                // padrão dos comandos de linha do ReportLab: ponta e junção arredondadas
                canvas.setLineCap(1);
                canvas.setLineJoin(1);
                capAndJoinSet = true;
            }
            int[] r = normCellRange(cmd.sc(), cmd.ec(), cmd.sr(), cmd.er());
            int sc = r[0], ec = r[1], sr = r[2], er = r[3];
            double weight = ((Number) cmd.args().get(0)).doubleValue();
            Rgb color = (Rgb) cmd.args().get(1);
            switch (cmd.op()) {
                case "GRID" -> {
                    drawBox(canvas, sc, sr, ec, er, weight, color);
                    drawInnerGrid(canvas, sc, sr, ec, er, weight, color);
                }
                case "BOX", "OUTLINE" -> drawBox(canvas, sc, sr, ec, er, weight, color);
                case "INNERGRID" -> drawInnerGrid(canvas, sc, sr, ec, er, weight, color);
                case "LINEBELOW" -> drawHLines(canvas, sc, sr + 1, ec, er + 1, weight, color);
                case "LINEABOVE" -> drawHLines(canvas, sc, sr, ec, er, weight, color);
                case "LINEBEFORE" -> drawVLines(canvas, sc, sr, ec, er, weight, color);
                case "LINEAFTER" -> drawVLines(canvas, sc + 1, sr, ec + 1, er, weight, color);
                default -> throw new IllegalStateException(cmd.op());
            }
        }
        canvas.restoreState();
        curColor = null;
    }

    private void drawBox(PdfCanvas canvas, int sc, int sr, int ec, int er, double weight, Rgb color) {
        drawHLines(canvas, sc, sr, ec, sr, weight, color);
        drawHLines(canvas, sc, er + 1, ec, er + 1, weight, color);
        drawVLines(canvas, sc, sr, sc, er, weight, color);
        drawVLines(canvas, ec + 1, sr, ec + 1, er, weight, color);
    }

    private void drawInnerGrid(PdfCanvas canvas, int sc, int sr, int ec, int er, double weight, Rgb color) {
        drawHLines(canvas, sc, sr + 1, ec, er, weight, color);
        drawVLines(canvas, sc + 1, sr, ec, er, weight, color);
    }

    private void drawHLines(PdfCanvas canvas, int sc, int sr, int ec, int er, double weight, Rgb color) {
        double[] ecp = slice(colPositions, sc, ec + 2);
        double[] rp = slice(rowPositions, sr, er + 1);
        if (ecp.length <= 1 || rp.length < 1) {
            return;
        }
        prepLine(canvas, weight, color);
        for (double y : rp) {
            canvas.line(ecp[0], y, ecp[ecp.length - 1], y);
        }
    }

    private void drawVLines(PdfCanvas canvas, int sc, int sr, int ec, int er, double weight, Rgb color) {
        double[] erp = slice(rowPositions, sr, er + 2);
        double[] cp = slice(colPositions, sc, ec + 1);
        if (erp.length <= 1 || cp.length < 1) {
            return;
        }
        prepLine(canvas, weight, color);
        for (double x : cp) {
            canvas.line(x, erp[erp.length - 1], x, erp[0]);
        }
    }

    private void prepLine(PdfCanvas canvas, double weight, Rgb color) {
        if (color != null && !color.equals(curColor)) {
            canvas.setStrokeColor(color);
            curColor = color;
        }
        if (weight != 0 && weight != curWeight) {
            canvas.setLineWidth(weight);
            curWeight = weight;
        }
    }

    /** Fatia com a semântica de {@code lista[start:stop]} do Python para índices não negativos. */
    private static double[] slice(double[] a, int start, int stop) {
        int s = Math.min(Math.max(start, 0), a.length);
        int e = Math.min(Math.max(stop, 0), a.length);
        return s >= e ? new double[0] : Arrays.copyOfRange(a, s, e);
    }
}
