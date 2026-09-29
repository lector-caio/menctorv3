package br.com.lector.menctor.relatorio;

import java.util.List;
import java.util.Map;

/**
 * Textos técnicos por dimensão (interpretação e plano de ação). A busca é pelo nome exato da
 * dimensão; nomes sem texto cadastrado usam os textos genéricos, como no gerador original.
 */
final class ConteudoTecnico {

    private ConteudoTecnico() {
    }

    record Plano(List<String> acoes, String prazo, String impacto) {
    }

    static final String INTERPRETACAO_PADRAO = "Analise em elaboracao.";

    static final Plano PLANO_PADRAO = new Plano(
            List.of("Monitorar indicador", "Coletar feedbacks", "Planejar intervencao", "Reavaliar em 90 dias"),
            "60 dias",
            "Melhora geral nos indicadores psicossociais");

    static final Map<String, String> INTERPRETACOES = Map.ofEntries(
            Map.entry("Carga de Trabalho",
                    "Score de 3.12 indica alta sobrecarga de trabalho percebida pelos colaboradores. "
                    + "Esse nivel de exigencia quantitativa esta associado a maior risco de burnout, "
                    + "absenteismo e rotatividade. Recomenda-se revisao imediata da distribuicao de "
                    + "tarefas, dotacao de equipes e processos de priorizacao."),
            Map.entry("Burnout",
                    "Score de 2.95 coloca esta dimensao na zona de alto risco. Indicadores de esgotamento "
                    + "emocional e despersonalizacao estao elevados. Intervencoes de suporte psicologico, "
                    + "revisao da carga e programas de bem-estar sao urgentemente necessarios."),
            Map.entry("Estresse",
                    "Score de 2.88 indica niveis elevados de estresse ocupacional. A percepcao de pressao "
                    + "constante e falta de recursos para lidar com as demandas do trabalho pode levar a "
                    + "consequencias negativas para a saude fisica e mental dos colaboradores."),
            Map.entry("Conflito trabalho-familia",
                    "Score de 2.74 evidencia dificuldade significativa no equilibrio entre as demandas "
                    + "profissionais e as responsabilidades familiares. Politicas de flexibilidade e "
                    + "gestao do tempo sao recomendadas para mitigar este fator de risco."),
            Map.entry("Ritmo de trabalho",
                    "Score de 2.68, limiar do alto risco, indica percepcao de ritmo acelerado e pressao "
                    + "temporal constante. O monitoramento continuo e a adocao de pausas regulamentadas "
                    + "sao medidas essenciais para controle deste indicador."),
            Map.entry("Reconhecimento",
                    "Score de 2.51 aponta para deficit moderado de reconhecimento percebido pelos "
                    + "colaboradores. A ausencia de reconhecimento adequado impacta diretamente a "
                    + "motivacao, o engajamento e a retencao de talentos na organizacao."),
            Map.entry("Suporte social",
                    "Score de 2.42 indica suporte social moderadamente baixo entre colegas e lideranca. "
                    + "O fortalecimento das redes de apoio interpessoal e a capacitacao de lideres para "
                    + "suporte emocional sao estrategias prioritarias."),
            Map.entry("Qualidade da lideranca",
                    "Score de 2.38 reflete avaliacao moderada da qualidade da lideranca. Aspectos como "
                    + "comunicacao, feedback, autonomia concedida e suporte ao desenvolvimento precisam "
                    + "ser fortalecidos por meio de programas estruturados de desenvolvimento de lideres."),
            Map.entry("Justica e respeito",
                    "Score de 2.20 indica percepcao moderada de justica organizacional. A transparencia "
                    + "nos processos decisorios, criterios claros de avaliacao e promocao, e respeito nas "
                    + "relacoes de trabalho sao pilares a serem reforcados."),
            Map.entry("Influencia no trabalho",
                    "Score de 2.15 revela percepcao moderada de autonomia e influencia nas proprias "
                    + "atividades. Estrategias de empoderamento, participacao em decisoes e delegacao "
                    + "responsavel podem elevar este indicador."),
            Map.entry("Comunidade social",
                    "Score de 1.88 representa risco baixo-moderado. O senso de comunidade e pertencimento "
                    + "esta razoavelmente preservado, mas acoes de integracao e cultura organizacional "
                    + "podem potencializar este fator protetor."),
            Map.entry("Significado do trabalho",
                    "Score de 1.72 indica que os colaboradores percebem significado moderado em suas "
                    + "atividades. Acoes de comunicacao estrategica, alinhamento de proposito e valorizacao "
                    + "das contribuicoes individuais podem fortalecer este aspecto."));

    static final Map<String, Plano> PLANOS = Map.of(
            "Carga de Trabalho", new Plano(List.of(
                    "Realizar mapeamento de processos e redistribuicao de tarefas criticas",
                    "Implementar metodologia de gestao por prioridades (MoSCoW ou similar)",
                    "Revisar dotacao de pessoal nos setores com maior sobrecarga identificada",
                    "Estabelecer reunioes semanais de alinhamento e gestao de demandas"),
                    "30 dias",
                    "Reducao de 20-30% na percepcao de sobrecarga nos proximos 90 dias"),
            "Burnout", new Plano(List.of(
                    "Implantar programa de apoio psicologico (EAP) com acesso facilitado",
                    "Treinar lideres para identificacao precoce de sinais de esgotamento",
                    "Criar politica de descanso obrigatorio e desconexao digital fora do horario",
                    "Estabelecer grupos de suporte e rodas de conversa sobre saude mental"),
                    "45 dias",
                    "Reducao de absenteismo e melhora nos indicadores de saude mental em 6 meses"),
            "Estresse", new Plano(List.of(
                    "Oferecer treinamentos de gestao do estresse e mindfulness para equipes",
                    "Revisar metas e prazos, alinhando expectativas de forma realista",
                    "Implantar pausas estruturadas durante a jornada de trabalho",
                    "Monitorar indicadores de saude com pesquisas mensais de pulso"),
                    "30 dias",
                    "Melhora de 15% no bem-estar geral percebido no trimestre seguinte"),
            "Conflito trabalho-familia", new Plano(List.of(
                    "Implementar politica de flexibilidade de horario e trabalho hibrido",
                    "Criar programa de apoio a colaboradores com dependentes",
                    "Revisar politica de comunicacao fora do horario de trabalho",
                    "Oferecer treinamentos de gestao do tempo e equilibrio vida-trabalho"),
                    "60 dias",
                    "Aumento na satisfacao geral e reducao de conflitos reportados em 90 dias"),
            "Ritmo de trabalho", new Plano(List.of(
                    "Mapear gargalos de processo que geram aceleracao desnecessaria do ritmo",
                    "Implantar metodologia agil com sprints equilibrados e retrospectivas",
                    "Estabelecer metas de ritmo sustentavel com indicadores de monitoramento",
                    "Capacitar lideres em gestao de fluxo de trabalho e prevencao de urgencias"),
                    "45 dias",
                    "Estabilizacao do ritmo percebido e reducao de horas extras em 60 dias"),
            "Reconhecimento", new Plano(List.of(
                    "Estruturar programa formal de reconhecimento com criterios transparentes",
                    "Capacitar lideres para oferta de feedback construtivo e reconhecimento frequente",
                    "Criar mecanismos de celebracao de conquistas individuais e coletivas",
                    "Revisar politica de remuneracao e beneficios com base em equidade interna"),
                    "60 dias",
                    "Melhora de 20% nos indicadores de engajamento no proximo semestre"),
            "Suporte social", new Plano(List.of(
                    "Implementar programa de mentoria e buddy system entre colaboradores",
                    "Promover atividades de integracao e fortalecimento de vinculos de equipe",
                    "Treinar lideres em escuta ativa e suporte emocional as equipes",
                    "Criar canais seguros para reporte de situacoes de conflito e assedio"),
                    "30 dias",
                    "Fortalecimento do clima de equipe e reducao de conflitos interpessoais"),
            "Qualidade da lideranca", new Plano(List.of(
                    "Realizar diagnostico 360 graus de competencias de lideranca",
                    "Implantar programa estruturado de desenvolvimento de lideres (PDL)",
                    "Estabelecer rotina de feedback bidirecional entre lideres e equipes",
                    "Monitorar qualidade da lideranca com indicadores trimestrais"),
                    "90 dias",
                    "Melhora na avaliacao de lideranca e aumento do engajamento das equipes"));
}
