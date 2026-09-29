package br.com.lector.menctor.relatorio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.pdfbox.pdmodel.common.PDRectangle;

import br.com.lector.menctor.pdf.ActionFlowable;
import br.com.lector.menctor.pdf.DocTemplate;
import br.com.lector.menctor.pdf.Flowable;
import br.com.lector.menctor.pdf.FontSet;
import br.com.lector.menctor.pdf.Frame;
import br.com.lector.menctor.pdf.HRFlowable;
import br.com.lector.menctor.pdf.KeepTogether;
import br.com.lector.menctor.pdf.PageTemplate;
import br.com.lector.menctor.pdf.Paragraph;
import br.com.lector.menctor.pdf.ParagraphStyle;
import br.com.lector.menctor.pdf.ParagraphStyle.Alignment;
import br.com.lector.menctor.pdf.PdfCanvas;
import br.com.lector.menctor.pdf.Rgb;
import br.com.lector.menctor.pdf.Size;
import br.com.lector.menctor.pdf.Spacer;
import br.com.lector.menctor.pdf.StdFont;
import br.com.lector.menctor.pdf.Table;

/**
 * Relatório psicossocial em PDF (capa + 11 seções). Port fiel do antigo
 * {@code gerar_relatorio_psicossocial.py}: mesmos textos, cores, medidas e regras de paginação.
 * Cada instância gera um documento.
 */
public final class RelatorioPsicossocialPdf {

    // ── cores ─────────────────────────────────────────────────────
    static final Rgb DARK_BLUE = Rgb.hex("#1C2B4B");
    static final Rgb ORANGE = Rgb.hex("#E87722");
    static final Rgb AMBER = Rgb.hex("#F5A623");
    static final Rgb GREEN = Rgb.hex("#27AE60");
    static final Rgb RED = Rgb.hex("#E74C3C");
    static final Rgb LIGHT_GRAY = Rgb.hex("#F5F6FA");
    static final Rgb TEXT_BODY = Rgb.hex("#2C3E50");
    static final Rgb TEXT_MUTED = Rgb.hex("#7F8C8D");
    static final Rgb WHITE = Rgb.WHITE;
    static final Rgb COVER_BOX = Rgb.hex("#243460");

    // ── página A4 e margens (mesmas fórmulas de reportlab.lib.units) ──
    private static final double CM = 72.0 / 2.54;
    private static final double MM = CM * 0.1;
    static final double PAGE_W = 210 * MM;
    static final double PAGE_H = 297 * MM;
    static final double MARGIN = 2.2 * CM;
    private static final double AVAIL_W = PAGE_W - 2 * MARGIN;

    // ── estilos de parágrafo ──────────────────────────────────────
    private static final ParagraphStyle BODY = new ParagraphStyle(StdFont.HELVETICA, 9, 14, TEXT_BODY, Alignment.JUSTIFY);
    private static final ParagraphStyle BODY_L = new ParagraphStyle(StdFont.HELVETICA, 9, 14, TEXT_BODY, Alignment.LEFT);
    private static final ParagraphStyle MUTED = new ParagraphStyle(StdFont.HELVETICA, 8, 12, TEXT_MUTED, Alignment.LEFT);
    private static final ParagraphStyle BOLD9 = new ParagraphStyle(StdFont.HELVETICA_BOLD, 9, 13, TEXT_BODY, Alignment.LEFT);
    private static final ParagraphStyle BOLD10 = new ParagraphStyle(StdFont.HELVETICA_BOLD, 10, 14, TEXT_BODY, Alignment.LEFT);
    private static final ParagraphStyle SECTION = new ParagraphStyle(StdFont.HELVETICA_BOLD, 12, 16, DARK_BLUE, Alignment.LEFT, 2);
    private static final ParagraphStyle ITALIC_M = new ParagraphStyle(StdFont.HELVETICA_OBLIQUE, 8, 12, TEXT_MUTED, Alignment.LEFT);
    private static final ParagraphStyle WHITE_B = new ParagraphStyle(StdFont.HELVETICA_BOLD, 9, 13, WHITE, Alignment.LEFT);

    private final RelatorioConfig cfg;
    private final DocTemplate doc;
    private final FontSet fonts;
    /** Página em que cada seção começa (preenchido durante a diagramação). */
    private final Map<Integer, String> sectionPageMap = new HashMap<>();
    private final List<String> sectionTitles = new ArrayList<>();

    private RelatorioPsicossocialPdf(RelatorioConfig cfg) {
        this.cfg = cfg;
        PageTemplate cover = new PageTemplate("cover", new Frame(0, 0, PAGE_W, PAGE_H), this::drawCover);
        PageTemplate inner = new PageTemplate("inner",
                new Frame(MARGIN, MARGIN + 10, PAGE_W - 2 * MARGIN, PAGE_H - 2 * MARGIN - 30), this::drawInner);
        this.doc = new DocTemplate(new PDRectangle((float) PAGE_W, (float) PAGE_H), List.of(cover, inner));
        this.fonts = doc.fonts();
    }

    /** Gera o PDF do relatório. */
    public static byte[] gerar(RelatorioConfig cfg) {
        return new RelatorioPsicossocialPdf(cfg).build();
    }

    private byte[] build() {
        record Section(String title, List<Flowable> content) {
        }
        List<Section> sections = List.of(
                new Section("Introducao", p2()),
                new Section("Identificacao da Empresa e Resp. Tecnico", p3()),
                new Section("Metodologia", p4()),
                new Section("Resumo Executivo", p5()),
                new Section("Fatores Psicossociais — Ranking", p6()),
                new Section("Interpretacao Tecnica por Dimensao", p7()),
                new Section("Plano de Acao Recomendado", p8p9()),
                new Section("Metodologia e Referencias", p10()),
                new Section("Analise Global dos Resultados", p11()),
                new Section("Recomendacoes Tecnicas por Dominio", p12()),
                new Section("Conclusao", p13()));

        List<Flowable> story = new ArrayList<>();
        // página 1: capa, desenhada inteira pelo modelo "cover"
        story.add(ActionFlowable.nextPageTemplate("inner"));
        story.add(new Spacer(1, PAGE_H * 0.01));
        story.add(ActionFlowable.pageBreak());
        for (int i = 0; i < sections.size(); i++) {
            Section s = sections.get(i);
            sectionTitles.add(s.title());
            if (i > 0) {
                story.add(ActionFlowable.pageBreak());
            }
            story.add(new SectionMarker(s.title()));
            story.addAll(s.content());
        }
        return doc.build(story, info -> {
            info.setTitle("Relatório Psicossocial - " + cfg.empresa());
            info.setProducer("Menctor Backend (Quarkus + PDFBox)");
            info.setCreator("Menctor");
        });
    }

    // ── desenho fixo das páginas ──────────────────────────────────

    private void drawCover(PdfCanvas c, int page, int total) {
        double w = PAGE_W, h = PAGE_H, m = MARGIN;
        c.setFillColor(DARK_BLUE);
        c.rect(0, 0, w, h, true, false);

        c.setFont(StdFont.HELVETICA_BOLD, 13);
        c.setFillColor(WHITE);
        c.drawString(m, h - m - 4, "PSICOSSOCIAL ANALYTICS");
        c.setStrokeColor(ORANGE);
        c.setLineWidth(4);
        c.line(m, h - m - 14, m + 200, h - m - 14);
        c.setFont(StdFont.HELVETICA, 9);
        c.setFillColor(WHITE);
        c.drawString(m, h - m - 30, "Relatorio Executivo");

        double midY = h * 0.52;
        c.setFont(StdFont.HELVETICA_BOLD, 14);
        c.setFillColor(ORANGE);
        c.drawString(m, midY + 70, cfg.codigo());
        c.setFont(StdFont.HELVETICA_BOLD, 22);
        c.setFillColor(WHITE);
        c.drawString(m, midY + 38, cfg.tituloLinha1());
        c.drawString(m, midY + 12, cfg.tituloLinha2());
        c.setFont(StdFont.HELVETICA, 10);
        c.setFillColor(WHITE);
        c.drawString(m, midY - 12, cfg.descricao());

        double boxY = midY - 110;
        double boxH = 82;
        c.setFillColor(COVER_BOX);
        c.roundRect(m, boxY, w - 2 * m, boxH, 6, true, false);

        double col1X = m + 16;
        double col2X = m + (w - 2 * m) / 2 + 8;
        String[] labelsC1 = {"PERIODO", "APLICACAO", "RESPONSAVEL"};
        String[] valsC1 = {cfg.periodo(), cfg.aplicacao(), cfg.responsavel()};
        String[] labelsC2 = {"RESPONDENTES", "TAXA DE ADESAO", "FOCO"};
        String[] valsC2 = {cfg.respondentes() + " de " + cfg.totalColaboradores(), cfg.taxaAdesao(), cfg.foco()};
        double rowHBox = (boxH - 16) / 3;
        for (int i = 0; i < 3; i++) {
            double ry = boxY + boxH - 14 - i * rowHBox;
            c.setFont(StdFont.HELVETICA_BOLD, 7);
            c.setFillColor(TEXT_MUTED);
            c.drawString(col1X, ry, labelsC1[i]);
            c.setFont(StdFont.HELVETICA, 8.5);
            c.setFillColor(WHITE);
            c.drawString(col1X, ry - 11, valsC1[i]);
            c.setFont(StdFont.HELVETICA_BOLD, 7);
            c.setFillColor(TEXT_MUTED);
            c.drawString(col2X, ry, labelsC2[i]);
            c.setFont(StdFont.HELVETICA, 8.5);
            c.setFillColor(WHITE);
            c.drawString(col2X, ry - 11, valsC2[i]);
        }

        c.setFillColor(ORANGE);
        c.rect(0, 0, w, 28, true, false);
        c.setFont(StdFont.HELVETICA, 8);
        c.setFillColor(WHITE);
        c.drawString(m, 10, "Emitido em " + cfg.emissao());
        c.drawRightString(w - m, 10, "Confidencial — Uso Interno");
    }

    private void drawInner(PdfCanvas c, int page, int total) {
        double w = PAGE_W, h = PAGE_H, m = MARGIN;
        c.setFont(StdFont.HELVETICA_BOLD, 11);
        c.setFillColor(DARK_BLUE);
        c.drawString(m, h - m + 8, sectionTitleFor(page));
        c.setFont(StdFont.HELVETICA, 8);
        c.setFillColor(TEXT_MUTED);
        c.drawRightString(w - m, h - m + 8, cfg.codigo() + " • " + cfg.periodo());
        c.setStrokeColor(ORANGE);
        c.setLineWidth(2);
        c.line(m, h - m, w - m, h - m);

        c.setStrokeColor(TEXT_MUTED);
        c.setLineWidth(0.5);
        c.line(m, 28, w - m, 28);
        c.setFont(StdFont.HELVETICA, 7);
        c.setFillColor(TEXT_MUTED);
        c.drawString(m, 16, cfg.codigo() + " • " + cfg.periodo() + " • Psicossocial Analytics");
        c.drawRightString(w - m, 16, "Pagina " + page + " de " + total);
    }

    /** Título da seção em curso: a última que começou nesta página ou antes dela. */
    private String sectionTitleFor(int page) {
        if (sectionPageMap.containsKey(page)) {
            return sectionPageMap.get(page);
        }
        for (int p = page - 1; p > 1; p--) {
            if (sectionPageMap.containsKey(p)) {
                return sectionPageMap.get(p);
            }
        }
        return sectionTitles.get(0);
    }

    // ── seções ────────────────────────────────────────────────────

    private Paragraph para(String text, ParagraphStyle style) {
        return new Paragraph(text, style, fonts);
    }

    private void paragraphs(List<Flowable> story, double spaceAfter, String... texts) {
        for (String t : texts) {
            story.add(para(t, BODY));
            story.add(new Spacer(1, spaceAfter));
        }
    }

    private void sectionTitle(List<Flowable> story, String title) {
        story.add(para(title, SECTION));
        story.add(new OrangeLine());
    }

    private List<Flowable> p2() {
        List<Flowable> story = new ArrayList<>();
        paragraphs(story, 7,
                "O COPSOQ II (Copenhagen Psychosocial Questionnaire II) e um instrumento internacional de avaliacao "
                + "de riscos psicossociais no trabalho, amplamente utilizado em pesquisas organizacionais e processos "
                + "de gestao de saude ocupacional. Desenvolvido na Dinamarca e adaptado para inumeros contextos "
                + "culturais, o instrumento avalia multiplas dimensoes que impactam o bem-estar dos trabalhadores.",
                "A aplicacao do COPSOQ II possibilita identificar fatores de risco e protecao no ambiente de trabalho, "
                + "permitindo a elaboracao de planos de acao baseados em evidencias cientificas. Sua estrutura modular "
                + "facilita a adaptacao as especificidades de cada organizacao, mantendo o rigor metodologico necessario.",
                "No contexto brasileiro, a avaliacao psicossocial ganhou relevancia regulatoria com a publicacao da "
                + "NR-01 atualizada, que estabelece a obrigatoriedade do Gerenciamento de Riscos Ocupacionais (GRO) "
                + "incluindo os fatores psicossociais como parte integrante do Programa de Gerenciamento de Riscos (PGR).",
                "A presente pesquisa utiliza uma versao adaptada do COPSOQ II, combinada com indicadores proprios "
                + "de clima organizacional e engajamento, proporcionando uma visao abrangente e multidimensional do "
                + "ambiente psicossocial da organizacao avaliada.");

        story.add(new Spacer(1, 6));
        story.add(new OrangeUnderlineTitle("Objetivo do Relatorio Tecnico"));
        story.add(new Spacer(1, 8));
        paragraphs(story, 7,
                "O presente relatorio tem por objetivo apresentar os resultados obtidos na Pesquisa de Clima "
                + "Organizacional — 1° Trimestre/2026, realizada na organizacao " + cfg.empresa() + ". "
                + "O documento foi elaborado com base nos principios da psicologia organizacional e do trabalho, "
                + "seguindo as diretrizes metodologicas do COPSOQ II e as exigencias da NR-01.",
                "As informacoes aqui contidas destinam-se exclusivamente ao uso interno da organizacao, "
                + "subsidiando decisoes estrategicas de gestao de pessoas, saude ocupacional e desenvolvimento "
                + "organizacional. A confidencialidade dos dados individuais e garantida em todas as etapas do processo.");

        story.add(new Spacer(1, 6));
        sectionTitle(story, "Componentes Essenciais do Relatorio");
        story.add(new Spacer(1, 8));

        String[][] items = {
                {"1. Identificacao e Metodologia",
                        "Apresentacao da empresa avaliada, do responsavel tecnico e da metodologia utilizada na coleta e analise dos dados."},
                {"2. Resumo Executivo",
                        "Panorama geral dos resultados com indicadores-chave de desempenho (KPIs) e classificacao geral do risco psicossocial."},
                {"3. Analise Detalhada por Dimensao",
                        "Ranking das dimensoes psicossociais avaliadas, interpretacao tecnica individualizada e correlacoes entre fatores."},
                {"4. Plano de Acao e Recomendacoes",
                        "Diretrizes praticas para intervencao, organizadas por prioridade de risco, com prazos e impactos esperados."},
        };
        for (String[] item : items) {
            story.add(para(item[0], BOLD9));
            story.add(para(item[1], BODY));
            story.add(new Spacer(1, 6));
        }
        return story;
    }

    private Table keyValueTable(String[][] rows, double keyWidth, double padding) {
        List<List<Object>> cells = new ArrayList<>();
        for (String[] row : rows) {
            cells.add(List.of(List.of(para(row[0], MUTED)), List.of(para(row[1], BOLD9))));
        }
        return new Table(cells, new double[] {keyWidth, AVAIL_W - keyWidth}, List.of(
                Table.background(0, 0, 0, -1, LIGHT_GRAY),
                Table.cell("TOPPADDING", 0, 0, -1, -1, padding),
                Table.cell("BOTTOMPADDING", 0, 0, -1, -1, padding),
                Table.cell("LEFTPADDING", 0, 0, -1, -1, 8),
                Table.line("LINEBELOW", 0, 0, -1, -2, 0.5, LIGHT_GRAY),
                Table.line("BOX", 0, 0, -1, -1, 0.5, TEXT_MUTED)));
    }

    private List<Flowable> p3() {
        List<Flowable> story = new ArrayList<>();
        sectionTitle(story, "Dados da Empresa Avaliada");
        story.add(new Spacer(1, 8));
        story.add(keyValueTable(new String[][] {
                {"RAZAO SOCIAL", cfg.empresa()},
                {"CNPJ", cfg.cnpj()},
                {"ENDERECO", cfg.endereco()},
                {"DATA DA AVALIACAO", cfg.dataAvaliacao()},
                {"SETORES AVALIADOS", cfg.foco()},
        }, 130, 5));
        story.add(new Spacer(1, 10));
        paragraphs(story, 7,
                "A empresa acima identificada contratou a realizacao da Pesquisa de Clima Organizacional com foco "
                + "na avaliacao psicossocial de seus colaboradores. O processo foi conduzido em conformidade com os "
                + "principios eticos da psicologia e as normas regulamentadoras vigentes.",
                "A participacao dos colaboradores foi voluntaria e anonima, garantindo a confidencialidade das "
                + "respostas individuais. Os dados foram tratados de forma agregada, impossibilitando a identificacao "
                + "de respondentes especificos.");

        story.add(new Spacer(1, 10));
        sectionTitle(story, "Responsavel Tecnico pela Avaliacao");
        story.add(new Spacer(1, 8));
        story.add(keyValueTable(new String[][] {
                {"NOME", cfg.rtNome()},
                {"REGISTRO", cfg.rtRegistro()},
                {"ESPECIALIDADE", cfg.rtEspecialidade()},
                {"CONTATO", cfg.rtContato()},
        }, 130, 5));
        story.add(new Spacer(1, 10));
        paragraphs(story, 7,
                "O responsavel tecnico listado acima assume a responsabilidade pela conducao metodologica da pesquisa, "
                + "pela analise dos dados coletados e pela elaboracao do presente relatorio. Sua atuacao esta em "
                + "conformidade com o Codigo de Etica Profissional do Psicologo e as diretrizes do CFP.",
                "Quaisquer duvidas sobre os resultados, metodologia ou plano de acao podem ser encaminhadas "
                + "diretamente ao responsavel tecnico atraves dos contatos informados, respeitando os prazos "
                + "estabelecidos no cronograma de devolutiva.");
        return story;
    }

    private List<Flowable> p4() {
        List<Flowable> story = new ArrayList<>();
        paragraphs(story, 8,
                "A metodologia empregada nesta pesquisa baseia-se no Copenhagen Psychosocial Questionnaire II "
                + "(COPSOQ II), instrumento desenvolvido pelo National Research Centre for the Working Environment "
                + "da Dinamarca. O questionario foi aplicado de forma digital, garantindo anonimato e facilidade de "
                + "acesso para todos os colaboradores.",
                "O instrumento utilizado contempla dimensoes relacionadas as exigencias do trabalho, a organizacao "
                + "e ao conteudo das tarefas, as relacoes interpessoais e de lideranca, as interfaces trabalho-individuo "
                + "e aos valores no local de trabalho. Cada dimensao e avaliada por meio de uma escala Likert de 5 pontos, "
                + "convertida para uma escala de 0 a 4 para analise e interpretacao.",
                "A classificacao dos resultados segue os parametros estabelecidos pelo COPSOQ II: scores de 0 a 1.66 "
                + "indicam baixo risco (fator protetor); scores de 1.67 a 2.66 indicam risco moderado (zona de atencao); "
                + "e scores de 2.67 a 4.00 indicam alto risco (requer intervencao prioritaria).");

        story.add(new Spacer(1, 6));
        sectionTitle(story, "Dimensoes Contempladas no Instrumento");
        story.add(new Spacer(1, 8));
        for (String d : new String[] {
                "Exigencias quantitativas e ritmo de trabalho",
                "Exigencias emocionais e cognitivas",
                "Influencia, desenvolvimento e significado do trabalho",
                "Qualidade da lideranca e suporte social",
                "Reconhecimento, justica e previsibilidade",
                "Comunidade social e confianca organizacional",
                "Equilibrio trabalho-familia e saude geral"}) {
            story.add(new OrangeBullet(d));
            story.add(new Spacer(1, 4));
        }

        story.add(new Spacer(1, 10));
        sectionTitle(story, "Perfil dos Participantes");
        story.add(new Spacer(1, 8));
        List<List<Object>> cells = new ArrayList<>();
        String[][] perfil = {
                {"TOTAL DE RESPONDENTES", cfg.respondentes() + " de " + cfg.totalColaboradores()},
                {"PERIODO DE APLICACAO", cfg.aplicacao()},
                {"FOCO", cfg.foco()},
                {"RESPONSAVEL", cfg.responsavel()},
        };
        for (String[] row : perfil) {
            cells.add(List.of(List.of(para(row[0], MUTED)), List.of(para(row[1], BOLD9))));
        }
        story.add(new Table(cells, new double[] {160, AVAIL_W - 160}, List.of(
                Table.background(0, 0, 0, -1, LIGHT_GRAY),
                Table.cell("TOPPADDING", 0, 0, -1, -1, 6),
                Table.cell("BOTTOMPADDING", 0, 0, -1, -1, 6),
                Table.cell("LEFTPADDING", 0, 0, -1, -1, 8),
                Table.line("LINEBELOW", 0, 0, -1, -2, 0.5, LIGHT_GRAY),
                Table.line("BOX", 0, 0, -1, -1, 0.5, TEXT_MUTED))));
        return story;
    }

    private List<Flowable> p5() {
        List<Flowable> story = new ArrayList<>();
        double kpiW = (AVAIL_W - 18) / 4;
        List<Object> kpis = List.of(
                List.of(new KpiBox("RISCO GERAL", "2.25", "MODERADO", DARK_BLUE, AMBER, kpiW)),
                List.of(new KpiBox("ADESAO", "92%", "BOA", DARK_BLUE, GREEN, kpiW)),
                List.of(new KpiBox("RESPONDENTES", "312", "TOTAL", DARK_BLUE, TEXT_MUTED, kpiW)),
                List.of(new KpiBox("DIMENSOES CRITICAS", "1", "ALTO RISCO", RED, RED, kpiW)));
        story.add(new Table(List.of(kpis), new double[] {kpiW, kpiW, kpiW, kpiW}, List.of(
                Table.cell("LEFTPADDING", 0, 0, -1, -1, 3),
                Table.cell("RIGHTPADDING", 0, 0, -1, -1, 3))));
        story.add(new Spacer(1, 14));

        sectionTitle(story, "Analise Executiva");
        story.add(new Spacer(1, 6));
        story.add(para(
                "Os resultados da Pesquisa de Clima Organizacional — 1° Trimestre/2026 revelam um cenario de risco "
                + "psicossocial moderado na organizacao, com score geral de 2.25/4. A analise das doze dimensoes "
                + "avaliadas indica concentracao de fatores de risco nas areas de exigencias quantitativas, saude "
                + "mental e equilibrio trabalho-familia, demandando atencao prioritaria da gestao.", BODY));
        story.add(new Spacer(1, 10));

        sectionTitle(story, "Contexto Organizacional");
        story.add(new Spacer(1, 6));
        story.add(para(
                "A taxa de adesao de 91.8% (312 de 340 colaboradores) confere elevada representatividade estatistica "
                + "aos resultados, garantindo que as conclusoes refletem fidedignamente o estado psicossocial da "
                + "organizacao. A ausencia de respostas (8.2%) e considerada dentro dos parametros aceitaveis para "
                + "pesquisas deste tipo, nao comprometendo a validade do instrumento.", BODY));
        story.add(new Spacer(1, 10));

        sectionTitle(story, "Classificacao Geral");
        story.add(new Spacer(1, 10));
        story.add(new ClassifBox(AVAIL_W));
        return story;
    }

    /** Dimensões da maior para a menor (ordenação estável, como o sorted do Python). */
    private List<RelatorioConfig.Dimensao> dimensoesOrdenadas() {
        List<RelatorioConfig.Dimensao> dims = new ArrayList<>(cfg.dimensoes());
        dims.sort(Comparator.comparingDouble(RelatorioConfig.Dimensao::score).reversed());
        return dims;
    }

    private List<Flowable> p6() {
        List<Flowable> story = new ArrayList<>();
        story.add(para(
                "O ranking abaixo apresenta as doze dimensoes psicossociais avaliadas, ordenadas do maior para o "
                + "menor score de risco. Dimensoes com score acima de 2.67 requerem intervencao prioritaria; entre "
                + "1.67 e 2.66 demandam atencao preventiva; abaixo de 1.67 sao consideradas fatores protetivos.", BODY));
        story.add(new Spacer(1, 12));
        for (RelatorioConfig.Dimensao d : dimensoesOrdenadas()) {
            story.add(new BarChart(d.nome(), d.score(), AVAIL_W));
            story.add(new Spacer(1, 6));
        }
        return story;
    }

    private List<Flowable> p7() {
        List<Flowable> story = new ArrayList<>();
        story.add(para(
                "A interpretacao tecnica a seguir apresenta analise individualizada de cada dimensao psicossocial "
                + "avaliada, contextualizando os scores obtidos e indicando direcionamentos para intervencao. "
                + "As analises foram elaboradas com base no referencial teorico do COPSOQ II e na literatura "
                + "cientifica sobre saude mental no trabalho.", BODY));
        story.add(new Spacer(1, 12));
        for (RelatorioConfig.Dimensao d : dimensoesOrdenadas()) {
            String texto = ConteudoTecnico.INTERPRETACOES.getOrDefault(d.nome(), ConteudoTecnico.INTERPRETACAO_PADRAO);
            story.add(new KeepTogether(List.of(new DimensionCard(d.nome(), d.score(), texto, AVAIL_W), new Spacer(1, 8))));
        }
        return story;
    }

    private List<Flowable> p8p9() {
        List<Flowable> story = new ArrayList<>();
        story.add(para(
                "O plano de acao a seguir foi elaborado com base nas dimensoes que apresentaram score igual ou "
                + "superior a 2.40, indicando necessidade de intervencao preventiva ou corretiva. As acoes foram "
                + "priorizadas por nivel de risco e potencial de impacto na saude e bem-estar dos colaboradores.", BODY));
        story.add(new Spacer(1, 12));
        for (RelatorioConfig.Dimensao d : dimensoesOrdenadas()) {
            if (d.score() < 2.4) {
                continue;
            }
            ConteudoTecnico.Plano plano = ConteudoTecnico.PLANOS.getOrDefault(d.nome(), ConteudoTecnico.PLANO_PADRAO);
            story.add(new KeepTogether(List.of(
                    new ActionCard(d.nome(), d.score(), plano.acoes(), plano.prazo(), plano.impacto(), AVAIL_W),
                    new Spacer(1, 10))));
        }
        return story;
    }

    private List<Flowable> p10() {
        List<Flowable> story = new ArrayList<>();
        sectionTitle(story, "Instrumento Utilizado");
        story.add(new Spacer(1, 6));
        story.add(para(
                "O Copenhagen Psychosocial Questionnaire II (COPSOQ II) e um instrumento de dominio publico, "
                + "desenvolvido e validado pelo National Research Centre for the Working Environment (NFA) da "
                + "Dinamarca. A versao utilizada nesta pesquisa foi adaptada para o contexto brasileiro, mantendo "
                + "a equivalencia semantica e conceitual dos itens originais.", BODY));
        story.add(new Spacer(1, 10));

        sectionTitle(story, "Escala COPSOQ II");
        story.add(new Spacer(1, 6));
        story.add(para(
                "Os scores sao calculados como medias das respostas em escala Likert de 5 pontos, "
                + "convertidas para a escala 0-4. A classificacao segue os parametros internacionais:", BODY));
        story.add(new Spacer(1, 8));

        List<List<Object>> escala = List.of(
                List.of(List.of(para("FAIXA", WHITE_B)), List.of(para("CLASSIFICACAO", WHITE_B)),
                        List.of(para("DESCRICAO", WHITE_B))),
                List.of("0.00 – 1.66", "BAIXO RISCO", "Fator protetor. Manter e potencializar."),
                List.of("1.67 – 2.66", "RISCO MODERADO", "Zona de atencao. Monitorar e agir preventivamente."),
                List.of("2.67 – 4.00", "ALTO RISCO", "Requer intervencao prioritaria e urgente."));
        story.add(new Table(escala, new double[] {100, 120, AVAIL_W - 220}, List.of(
                Table.background(0, 0, -1, 0, DARK_BLUE),
                Table.cell("FONTNAME", 0, 1, -1, -1, StdFont.HELVETICA),
                Table.cell("FONTSIZE", 0, 0, -1, -1, 8),
                Table.background(0, 1, -1, 1, Rgb.hex("#EAF9F0")),
                Table.background(0, 2, -1, 2, Rgb.hex("#FFF8E7")),
                Table.background(0, 3, -1, 3, Rgb.hex("#FEECEC")),
                Table.cell("TOPPADDING", 0, 0, -1, -1, 6),
                Table.cell("BOTTOMPADDING", 0, 0, -1, -1, 6),
                Table.cell("LEFTPADDING", 0, 0, -1, -1, 8),
                Table.line("BOX", 0, 0, -1, -1, 0.5, TEXT_MUTED),
                Table.line("LINEBELOW", 0, 0, -1, -1, 0.3, TEXT_MUTED))));
        story.add(new Spacer(1, 12));

        sectionTitle(story, "Fatores Protetivos");
        story.add(new Spacer(1, 6));
        for (String item : new String[] {
                "Significado e proposito percebido no trabalho",
                "Suporte social de colegas e lideranca",
                "Autonomia e influencia nas proprias atividades",
                "Reconhecimento e feedback construtivo frequente"}) {
            story.add(new TickItem(item, "v"));
            story.add(new Spacer(1, 3));
        }

        story.add(new Spacer(1, 10));
        sectionTitle(story, "Fatores de Risco Comuns");
        story.add(new Spacer(1, 6));
        for (String item : new String[] {
                "Sobrecarga quantitativa e ritmo acelerado de trabalho",
                "Baixo suporte social e isolamento organizacional",
                "Conflito trabalho-familia sem politicas de mitigacao",
                "Ausencia de reconhecimento e recompensas percebidas como injustas"}) {
            story.add(new TickItem(item, "!"));
            story.add(new Spacer(1, 3));
        }

        story.add(new Spacer(1, 10));
        sectionTitle(story, "Referencias Tecnicas");
        story.add(new Spacer(1, 6));
        for (String ref : new String[] {
                "KRISTENSEN, T.S. et al. The Copenhagen Psychosocial Questionnaire — a tool for the assessment "
                + "and improvement of the psychosocial work environment. Scandinavian Journal of Work, Environment "
                + "& Health, 2005.",
                "BRASIL. Ministerio do Trabalho e Emprego. NR-01 — Disposicoes Gerais e Gerenciamento de Riscos "
                + "Ocupacionais. Atualizacao 2021.",
                "CONSELHO FEDERAL DE PSICOLOGIA. Resolucao CFP n. 006/2019 — Elaboracao de documentos escritos "
                + "produzidos pela(o) psicologa(o) no exercicio profissional. Brasilia: CFP, 2019.",
                "LEKA, S.; COX, T. (Eds.). The European Framework for Psychosocial Risk Management: PRIMA-EF. "
                + "Nottingham: I-WHO Publications, 2008."}) {
            story.add(para(ref, ITALIC_M));
            story.add(new Spacer(1, 6));
        }
        return story;
    }

    private List<Flowable> p11() {
        List<Flowable> story = new ArrayList<>();
        paragraphs(story, 8,
                "A analise global dos resultados confirma que a organizacao apresenta um perfil psicossocial "
                + "de risco moderado, com tendencia de concentracao dos fatores mais criticos nas dimensoes "
                + "relacionadas as exigencias do trabalho e a saude mental. A distribuicao dos scores evidencia "
                + "a necessidade de abordagem sistemica, integrando acoes de gestao de pessoas, saude ocupacional "
                + "e desenvolvimento organizacional.",
                "Destaca-se positivamente que as dimensoes de Comunidade Social e Significado do Trabalho "
                + "apresentaram scores abaixo do limiar de atencao, indicando preservacao do senso de pertencimento "
                + "e proposito entre os colaboradores. Esses fatores protetivos devem ser potencializados como "
                + "estrategia de resiliencia organizacional frente aos desafios identificados.");

        story.add(new Spacer(1, 10));
        sectionTitle(story, "Tabela Consolidada de Resultados");
        story.add(new Spacer(1, 8));

        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of(List.of(para("DIMENSAO", WHITE_B)), List.of(para("SCORE", WHITE_B)),
                List.of(para("CLASSIFICACAO", WHITE_B))));
        for (RelatorioConfig.Dimensao d : dimensoesOrdenadas()) {
            ParagraphStyle nivelStyle = new ParagraphStyle(StdFont.HELVETICA_BOLD, 8, 12, nivelColor(d.score()), Alignment.LEFT);
            rows.add(List.of(
                    List.of(para(d.nome(), BODY_L)),
                    List.of(para(fmt2(d.score()), BOLD9)),
                    List.of(para(nivel(d.score()), nivelStyle))));
        }
        story.add(new Table(rows, new double[] {AVAIL_W * 0.55, AVAIL_W * 0.15, AVAIL_W * 0.30}, List.of(
                Table.background(0, 0, -1, 0, DARK_BLUE),
                Table.cell("TOPPADDING", 0, 0, -1, -1, 6),
                Table.cell("BOTTOMPADDING", 0, 0, -1, -1, 6),
                Table.cell("LEFTPADDING", 0, 0, -1, -1, 8),
                Table.rowBackgrounds(0, 1, -1, -1, WHITE, LIGHT_GRAY),
                Table.line("LINEBELOW", 0, 0, -1, -1, 0.3, TEXT_MUTED),
                Table.line("BOX", 0, 0, -1, -1, 0.5, TEXT_MUTED))));
        return story;
    }

    private List<Flowable> p12() {
        List<Flowable> story = new ArrayList<>();
        String[][] domains = {
                {"Ritmo de Trabalho e Exigencias Quantitativas",
                        "Auditoria de processos para identificar ineficiencias geradoras de sobrecarga",
                        "Redistribuicao de carga com criterios baseados em dados e capacidade real das equipes",
                        "Implementacao de metodologias ageis com foco em ritmo sustentavel",
                        "Monitoramento continuo via indicadores de workload e absenteismo"},
                {"Exigencias Emocionais e Saude Mental",
                        "Implantacao de Programa de Assistencia ao Empregado (EAP) com suporte psicologico",
                        "Campanhas internas de saude mental e desmistificacao do adoecimento",
                        "Capacitacao de lideres como agentes de saude mental nas equipes",
                        "Protocolo de acolhimento para colaboradores em sofrimento psiquico"},
                {"Lideranca e Ambiente Organizacional",
                        "Programa estruturado de desenvolvimento de competencias de lideranca",
                        "Ciclos regulares de feedback 360 graus com planos de desenvolvimento individual",
                        "Revisao dos criterios de promocao e reconhecimento para maior transparencia",
                        "Fortalecimento dos canais de comunicacao e participacao dos colaboradores"},
        };
        for (String[] dom : domains) {
            story.add(new DomainCard(dom[0], List.of(dom).subList(1, dom.length), AVAIL_W));
            story.add(new Spacer(1, 10));
        }

        story.add(new Spacer(1, 6));
        sectionTitle(story, "Integracao ao Plano de Acao do PGR");
        story.add(new Spacer(1, 6));
        paragraphs(story, 7,
                "As recomendacoes tecnicas apresentadas neste relatorio devem ser integradas ao Programa de "
                + "Gerenciamento de Riscos (PGR) da organizacao, em conformidade com a NR-01 atualizada. "
                + "A incorporacao dos riscos psicossociais ao PGR e obrigatoria e deve seguir o ciclo PDCA "
                + "de planejamento, execucao, verificacao e melhoria continua.",
                "Recomenda-se a constituicao de um Comite Multidisciplinar de Saude Mental no Trabalho, "
                + "envolvendo representantes de RH, Saude Ocupacional, Lideranca e colaboradores, para "
                + "governanca das acoes e monitoramento dos indicadores definidos no plano de acao.");

        story.add(new Spacer(1, 8));
        sectionTitle(story, "Criterios de Reaplicacao Antecipada");
        story.add(new Spacer(1, 6));
        story.add(para(
                "A reaplicacao da pesquisa esta prevista para o 2° Trimestre/2026. No entanto, os seguintes "
                + "gatilhos podem indicar necessidade de reaplicacao antecipada:", BODY));
        story.add(new Spacer(1, 6));
        story.add(para("Gatilhos para reaplicacao", BOLD9));
        story.add(new Spacer(1, 6));
        for (String g : new String[] {
                "Aumento superior a 20% nos indicadores de absenteismo ou afastamentos",
                "Ocorrencia de eventos criticos (demissoes em massa, reestruturacao, conflitos graves)",
                "Solicitacao fundamentada do Comite de Saude Mental ou da CIPA",
                "Denuncias formais de assedio moral ou sexual com impacto sistemico",
                "Queda expressiva nos indicadores de engajamento em pesquisas de pulso mensais"}) {
            story.add(new OrangeBullet(g));
            story.add(new Spacer(1, 3));
        }
        return story;
    }

    private List<Flowable> p13() {
        List<Flowable> story = new ArrayList<>();
        story.add(para(
                "A Pesquisa de Clima Organizacional — 1° Trimestre/2026 cumpre seu papel diagnostico ao "
                + "evidenciar com rigor metodologico o estado psicossocial da " + cfg.empresa() + ". "
                + "Os resultados obtidos oferecem uma base solida para a tomada de decisao estrategica em "
                + "saude mental no trabalho, alinhando a organizacao as melhores praticas internacionais "
                + "e as exigencias regulatorias brasileiras. O compromisso com a saude e o bem-estar dos "
                + "colaboradores e, ao mesmo tempo, uma responsabilidade etica e um fator critico de "
                + "sustentabilidade e performance organizacional. A implementacao das acoes recomendadas, "
                + "acompanhada de monitoramento continuo, posicionara a organizacao em trajetoria de melhoria "
                + "consistente de seu ambiente psicossocial, com beneficios tangiveis para colaboradores, "
                + "liderancas e para os resultados do negocio.", BODY));

        story.add(new Spacer(1, 20));
        story.add(new HRFlowable(100, 1, TEXT_MUTED));
        story.add(new Spacer(1, 12));
        story.add(para("Relatorio elaborado por: " + cfg.responsavel(), BOLD10));
        story.add(new Spacer(1, 6));
        story.add(para("Data de emissao: 27 de maio de 2026", MUTED));
        return story;
    }

    // ── classificação de risco ────────────────────────────────────

    static String nivel(double score) {
        if (score >= 2.67) {
            return "ALTO";
        }
        if (score >= 1.67) {
            return "MODERADO";
        }
        return "BAIXO";
    }

    static Rgb nivelColor(double score) {
        return switch (nivel(score)) {
            case "ALTO" -> ORANGE;
            case "MODERADO" -> AMBER;
            default -> GREEN;
        };
    }

    /** {@code f"{valor:.2f}"} do Python (arredondamento do valor binário exato, empate para o par). */
    static String fmt2(double value) {
        if (!Double.isFinite(value)) {
            return Double.isNaN(value) ? "nan" : value > 0 ? "inf" : "-inf";
        }
        return new BigDecimal(value).setScale(2, RoundingMode.HALF_EVEN).toPlainString();
    }

    /** {@code str.split()} do Python (qualquer sequência de espaços em branco). */
    private static List<String> pySplit(String text) {
        List<String> words = new ArrayList<>();
        for (String w : text.strip().split("\\s+")) {
            if (!w.isEmpty()) {
                words.add(w);
            }
        }
        return words;
    }

    // ── elementos visuais do relatório ────────────────────────────

    /** Registra a página em que uma seção começa (para o título do cabeçalho). */
    private final class SectionMarker extends Flowable {
        private final String title;

        SectionMarker(String title) {
            this.title = title;
        }

        @Override
        public Size wrap(double availWidth, double availHeight) {
            return new Size(0, 0);
        }

        @Override
        public void draw(PdfCanvas canvas) {
            sectionPageMap.putIfAbsent(canvas.pageNumber(), title);
        }
    }

    private static final class OrangeLine extends Flowable {
        private static final double THICKNESS = 2;
        private static final double SPACE_BEFORE = 2;
        private static final double SPACE_AFTER = 6;
        private double width;

        @Override
        public Size wrap(double availWidth, double availHeight) {
            width = availWidth;
            return new Size(width, THICKNESS + SPACE_BEFORE + SPACE_AFTER);
        }

        @Override
        public void draw(PdfCanvas c) {
            c.setStrokeColor(ORANGE);
            c.setLineWidth(THICKNESS);
            c.line(0, SPACE_AFTER, width, SPACE_AFTER);
        }
    }

    private static final class OrangeUnderlineTitle extends Flowable {
        private final String text;
        private double width;

        OrangeUnderlineTitle(String text) {
            this.text = text;
        }

        @Override
        public Size wrap(double availWidth, double availHeight) {
            width = availWidth;
            return new Size(width, 22);
        }

        @Override
        public void draw(PdfCanvas c) {
            c.setFont(StdFont.HELVETICA_BOLD, 11);
            c.setFillColor(ORANGE);
            c.drawString(0, 10, text);
            c.setStrokeColor(ORANGE);
            c.setLineWidth(1);
            c.line(0, 8, width, 8);
        }
    }

    private static final class BarChart extends Flowable {
        private static final double MAX_SCORE = 4;
        private static final double BAR_H = 18;
        private static final double ROW_H = BAR_H + 28;
        private final String nome;
        private final double score;
        private final double width;

        BarChart(String nome, double score, double width) {
            this.nome = nome;
            this.score = score;
            this.width = width;
        }

        @Override
        public Size wrap(double availWidth, double availHeight) {
            return new Size(width, ROW_H);
        }

        @Override
        public void draw(PdfCanvas c) {
            Rgb nc = nivelColor(score);
            String n = nivel(score);
            double labelW = 180;
            double barX = labelW;
            double barW = width - labelW;
            double barY = ROW_H - BAR_H - 8;

            c.setFont(StdFont.HELVETICA_BOLD, 9);
            c.setFillColor(TEXT_BODY);
            c.drawString(0, barY + BAR_H + 4, nome);

            c.setFont(StdFont.HELVETICA_BOLD, 8);
            c.setFillColor(nc);
            c.drawRightString(width, barY + BAR_H + 4, n);

            c.setFillColor(LIGHT_GRAY);
            c.rect(barX, barY, barW, BAR_H, true, false);

            double fillW = (score / MAX_SCORE) * barW;
            c.setFillColor(nc);
            c.rect(barX, barY, fillW, BAR_H, true, false);

            String scoreStr = fmt2(score);
            c.setFont(StdFont.HELVETICA_BOLD, 8);
            if (fillW > 32) {
                c.setFillColor(WHITE);
                c.drawRightString(barX + fillW - 3, barY + 5, scoreStr);
            } else {
                c.setFillColor(TEXT_BODY);
                c.drawString(barX + fillW + 3, barY + 5, scoreStr);
            }
        }
    }

    private static final class KpiBox extends Flowable {
        private static final double HEIGHT = 72;
        private final String label;
        private final String value;
        private final String sub;
        private final Rgb valueColor;
        private final Rgb subColor;
        private final double width;

        KpiBox(String label, String value, String sub, Rgb valueColor, Rgb subColor, double width) {
            this.label = label;
            this.value = value;
            this.sub = sub;
            this.valueColor = valueColor;
            this.subColor = subColor;
            this.width = width;
        }

        @Override
        public Size wrap(double availWidth, double availHeight) {
            return new Size(width, HEIGHT);
        }

        @Override
        public void draw(PdfCanvas c) {
            c.setFillColor(LIGHT_GRAY);
            c.roundRect(0, 0, width, HEIGHT, 6, true, false);
            c.setFillColor(ORANGE);
            c.rect(0, HEIGHT - 3, width, 3, true, false);
            c.setFont(StdFont.HELVETICA, 7);
            c.setFillColor(TEXT_MUTED);
            c.drawCentredString(width / 2, HEIGHT - 18, label);
            c.setFont(StdFont.HELVETICA_BOLD, 20);
            c.setFillColor(valueColor);
            c.drawCentredString(width / 2, HEIGHT - 42, value);
            c.setFont(StdFont.HELVETICA_BOLD, 8);
            c.setFillColor(subColor);
            c.drawCentredString(width / 2, HEIGHT - 57, sub);
        }
    }

    private static final class DimensionCard extends Flowable {
        private final String nome;
        private final double score;
        private final String texto;
        private final double width;
        private double height;

        DimensionCard(String nome, double score, String texto, double width) {
            this.nome = nome;
            this.score = score;
            this.texto = texto;
            this.width = width;
        }

        @Override
        public Size wrap(double availWidth, double availHeight) {
            // altura estimada pelo número de caracteres, como no original
            int charsPerLine = Math.max(1, (int) ((width - 20) / 5.5));
            int lines = Math.max(1, texto.codePointCount(0, texto.length()) / charsPerLine + 1);
            height = 36 + lines * 13;
            return new Size(width, height);
        }

        @Override
        public void draw(PdfCanvas c) {
            double h = height;
            c.setFillColor(LIGHT_GRAY);
            c.rect(0, 0, width, h, true, false);
            c.setFillColor(ORANGE);
            c.rect(0, 0, 4, h, true, false);
            c.setFont(StdFont.HELVETICA_BOLD, 10);
            c.setFillColor(ORANGE);
            c.drawString(12, h - 18, nome + "  •  " + fmt2(score) + "/4");
            c.setFont(StdFont.HELVETICA, 8.5);
            c.setFillColor(TEXT_MUTED);
            String line = "";
            double y = h - 34;
            double maxW = width - 20;
            for (String word : pySplit(texto)) {
                String test = (line + " " + word).strip();
                if (c.stringWidth(test, StdFont.HELVETICA, 8.5) <= maxW) {
                    line = test;
                } else {
                    c.drawString(12, y, line);
                    y -= 13;
                    line = word;
                }
            }
            if (!line.isEmpty()) {
                c.drawString(12, y, line);
            }
        }
    }

    private static final class ActionCard extends Flowable {
        private final String nome;
        private final double score;
        private final List<String> acoes;
        private final String prazo;
        private final String impacto;
        private final double width;
        private double height;

        ActionCard(String nome, double score, List<String> acoes, String prazo, String impacto, double width) {
            this.nome = nome;
            this.score = score;
            this.acoes = acoes;
            this.prazo = prazo;
            this.impacto = impacto;
            this.width = width;
        }

        @Override
        public Size wrap(double availWidth, double availHeight) {
            height = 30 + acoes.size() * 16 + 40;
            return new Size(width, height);
        }

        @Override
        public void draw(PdfCanvas c) {
            double h = height;
            c.setFillColor(DARK_BLUE);
            c.rect(0, h - 26, width, 26, true, false);
            c.setFont(StdFont.HELVETICA_BOLD, 9);
            c.setFillColor(WHITE);
            c.drawString(8, h - 18, spaced(nome.toUpperCase(Locale.ROOT)) + "  —  Plano de Acao");
            c.drawRightString(width - 8, h - 18, fmt2(score) + "/4");
            c.setFillColor(LIGHT_GRAY);
            c.rect(0, 0, width, h - 26, true, false);
            double y = h - 44;
            for (String a : acoes) {
                c.setFillColor(ORANGE);
                c.circle(10, y + 4, 3, true, false);
                c.setFont(StdFont.HELVETICA, 8.5);
                c.setFillColor(TEXT_BODY);
                c.drawString(20, y, a);
                y -= 16;
            }
            c.setFont(StdFont.HELVETICA, 8);
            c.setFillColor(TEXT_MUTED);
            c.drawString(8, 18, "PRAZO SUGERIDO: " + prazo);
            c.drawString(8, 6, "IMPACTO ESPERADO: " + impacto);
        }

        /** {@code "  ".join(texto)}: dois espaços entre cada caractere. */
        private static String spaced(String text) {
            StringBuilder sb = new StringBuilder();
            text.codePoints().forEach(cp -> {
                if (sb.length() > 0) {
                    sb.append("  ");
                }
                sb.appendCodePoint(cp);
            });
            return sb.toString();
        }
    }

    private static final class OrangeBullet extends Flowable {
        private final String texto;

        OrangeBullet(String texto) {
            this.texto = texto;
        }

        @Override
        public Size wrap(double availWidth, double availHeight) {
            return new Size(availWidth, 16);
        }

        @Override
        public void draw(PdfCanvas c) {
            c.setFillColor(ORANGE);
            c.circle(6, 6, 4, true, false);
            c.setFont(StdFont.HELVETICA, 9);
            c.setFillColor(TEXT_BODY);
            c.drawString(16, 3, texto);
        }
    }

    private static final class TickItem extends Flowable {
        private final String texto;
        private final String symbol;

        TickItem(String texto, String symbol) {
            this.texto = texto;
            this.symbol = symbol;
        }

        @Override
        public Size wrap(double availWidth, double availHeight) {
            return new Size(availWidth, 14);
        }

        @Override
        public void draw(PdfCanvas c) {
            c.setFont(StdFont.HELVETICA_BOLD, 9);
            c.setFillColor(ORANGE);
            c.drawString(0, 2, symbol);
            c.setFont(StdFont.HELVETICA, 9);
            c.setFillColor(TEXT_BODY);
            c.drawString(14, 2, texto);
        }
    }

    private static final class ClassifBox extends Flowable {
        private static final double HEIGHT = 62;
        private final double width;

        ClassifBox(double width) {
            this.width = width;
        }

        @Override
        public Size wrap(double availWidth, double availHeight) {
            return new Size(width, HEIGHT);
        }

        @Override
        public void draw(PdfCanvas c) {
            c.setFillColor(ORANGE);
            c.roundRect(0, 0, width, HEIGHT, 8, true, false);
            c.setFont(StdFont.HELVETICA_BOLD, 18);
            c.setFillColor(WHITE);
            c.drawCentredString(width / 2, 34, "MODERADO — 2.25 / 4");
            c.setFont(StdFont.HELVETICA, 10);
            c.drawCentredString(width / 2, 16, "Zona de atencao. Implementar acoes preventivas.");
        }
    }

    private static final class DomainCard extends Flowable {
        private final String titulo;
        private final List<String> bullets;
        private final double width;
        private double height;

        DomainCard(String titulo, List<String> bullets, double width) {
            this.titulo = titulo;
            this.bullets = bullets;
            this.width = width;
        }

        @Override
        public Size wrap(double availWidth, double availHeight) {
            height = 30 + bullets.size() * 15 + 8;
            return new Size(width, height);
        }

        @Override
        public void draw(PdfCanvas c) {
            double h = height;
            c.setFillColor(DARK_BLUE);
            c.rect(0, h - 25, width, 25, true, false);
            c.setFont(StdFont.HELVETICA_BOLD, 9);
            c.setFillColor(WHITE);
            c.drawString(8, h - 17, titulo);
            c.setFillColor(LIGHT_GRAY);
            c.rect(0, 0, width, h - 25, true, false);
            double y = h - 40;
            for (String b : bullets) {
                c.setFillColor(ORANGE);
                c.circle(10, y + 4, 3, true, false);
                c.setFont(StdFont.HELVETICA, 8.5);
                c.setFillColor(TEXT_BODY);
                c.drawString(20, y, b);
                y -= 15;
            }
        }
    }
}
