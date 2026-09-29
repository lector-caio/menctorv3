package br.com.lector.menctor.relatorio;

import java.util.List;

/** Dados de um relatório psicossocial (as mesmas chaves do {@code CONFIG} do gerador Python). */
public record RelatorioConfig(
        String codigo,
        String tituloLinha1,
        String tituloLinha2,
        String descricao,
        String periodo,
        String aplicacao,
        String responsavel,
        long respondentes,
        long totalColaboradores,
        String taxaAdesao,
        String foco,
        String emissao,
        String empresa,
        String cnpj,
        String endereco,
        String dataAvaliacao,
        String rtNome,
        String rtRegistro,
        String rtEspecialidade,
        String rtContato,
        String outputFilename,
        List<Dimensao> dimensoes) {

    /** Dimensão COPSOQ avaliada, com score de 0 a 4. */
    public record Dimensao(String nome, double score) {
    }

    /** Relatório de demonstração (usado por {@code GET /api/teste}). */
    public static final RelatorioConfig EXEMPLO = new RelatorioConfig(
            "PCL-Q1",
            "Pesquisa de Clima Organizacional —",
            "1° Trimestre/2026",
            "Pesquisa trimestral combinando dimensões COPSOQ II com indicadores de clima e engajamento.",
            "Janeiro a Marco - 2026",
            "10/03/2026 a 28/03/2026",
            "Comite de Pessoas & Cultura",
            312,
            340,
            "91.8%",
            "Toda a organizacao",
            "25 de maio de 2026",
            "Loghaus Logistica",
            "12.345.678/0001-90",
            "—",
            "01/04/2026",
            "Caio Guedes",
            "CRP-06/12345",
            "Psicologia Organizacional",
            "(11) 99999-9999",
            "relatorio_psicossocial.pdf",
            List.of(
                    new Dimensao("Carga de Trabalho", 3.12),
                    new Dimensao("Burnout", 2.95),
                    new Dimensao("Estresse", 2.88),
                    new Dimensao("Conflito trabalho-familia", 2.74),
                    new Dimensao("Ritmo de trabalho", 2.68),
                    new Dimensao("Reconhecimento", 2.51),
                    new Dimensao("Suporte social", 2.42),
                    new Dimensao("Qualidade da lideranca", 2.38),
                    new Dimensao("Justica e respeito", 2.20),
                    new Dimensao("Influencia no trabalho", 2.15),
                    new Dimensao("Comunidade social", 1.88),
                    new Dimensao("Significado do trabalho", 1.72)));
}
