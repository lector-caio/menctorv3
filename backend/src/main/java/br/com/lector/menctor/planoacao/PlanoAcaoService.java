package br.com.lector.menctor.planoacao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import br.com.lector.menctor.api.ApiException;
import br.com.lector.menctor.dados.Banco;

@ApplicationScoped
public class PlanoAcaoService {

    private static final Map<String, String> SUGESTOES_ACOES = Map.ofEntries(
        Map.entry("VIOLENCIA_TRAUMA", "Implantar protocolo de prevenção e acolhimento para incidentes críticos e suporte pós-trauma."),
        Map.entry("ASSEDIO", "Estruturar comitê de apuração de denúncias protegido e treinamento contínuo contra condutas abusivas."),
        Map.entry("SOBRECARGA", "Revisar distribuição de tarefas, metas e balanceamento da carga horária por equipe."),
        Map.entry("RELACIONAMENTOS", "Implementar ações de mediação de conflitos e fortalecimento do clima de cooperação interpessoal."),
        Map.entry("SUPORTE", "Capacitar gestores diretos para suporte técnico, escuta ativa e acolhimento emocional."),
        Map.entry("JUSTICA", "Revisar transparência nas decisões organizacionais, avaliações e critérios de reconhecimento."),
        Map.entry("MUDANCA_ORG", "Estruturar plano de comunicação prévia e capacitação para transições e mudanças operacionais."),
        Map.entry("CONTROLE", "Ampliar autonomia e participação dos trabalhadores na organização das rotinas laborais."),
        Map.entry("CLAREZA_PAPEL", "Alinhar descrições de função, responsabilidades e prioridades para evitar ambiguidade de papéis."),
        Map.entry("RECOMPENSAS", "Desenvolver programa estruturado de feedback construtivo e reconhecimento de esforços."),
        Map.entry("COMUNICACAO", "Garantir canais ágeis e acessíveis para circulação de informações críticas de trabalho."),
        Map.entry("SUBCARGA", "Redesenhar atribuições para enriquecimento de tarefas e aproveitamento adequado de competências."),
        Map.entry("ISOLAMENTO", "Promover rotinas de integração e suporte social para colaboradores em regime remoto ou isolado."),
        Map.entry("BURNOUT", "Implantar programa institucional de prevenção ao esgotamento profissional com apoio especializado."),
        Map.entry("ESTRESSE", "Realizar workshops de regulação do estresse e práticas de pausas regulares durante a jornada.")
    );

    @Inject
    Banco banco;

    @Inject
    PlanoAcaoRepository planoAcaoRepository;

    @Inject
    ObjectMapper mapper;

    public ObjectNode gerarPlano(String campanhaId, boolean regenerar) {
        return gerarPlano(campanhaId, regenerar, null);
    }

    public ObjectNode obterPlano(String campanhaId) {
        return obterPlano(campanhaId, null);
    }

    public ObjectNode obterResumo(String campanhaId) {
        return obterResumo(campanhaId, null);
    }

    public ObjectNode gerarPlano(String campanhaId, boolean regenerar, String clienteIdEsperado) {
        // 1. Validar existência da campanha e tenant
        ObjectNode campanha = validarCampanha(campanhaId, clienteIdEsperado);
        String clienteId = campanha.path("cliente_id").asText();
        String ciclo = campanha.hasNonNull("ciclo") ? campanha.path("ciclo").asText() : "";

        // 2. Validar existência de snapshot de Scoring calculado
        ObjectNode scoring = banco.transacao(c -> 
            banco.consultarUm(c, "select * from campanha_scoring where campanha_id = ?", campanhaId)
        ).orElseThrow(() -> ApiException.unprocessableEntity(
            "Não existe um Scoring Psicossocial calculado para esta campanha. Execute o Scoring antes de gerar o Plano de Ação."
        ));

        String scoringId = scoring.path("id").asText();

        // 3. Idempotência: Se já existe plano e não foi pedida regeneração explícita
        Optional<ObjectNode> planoExistente = planoAcaoRepository.obterPlanoPorScoring(scoringId);
        if (planoExistente.isPresent() && !regenerar) {
            return montarRespostaPlano(planoExistente.get());
        }

        // 4. Carregar dimensões do snapshot de scoring
        List<ObjectNode> dimensoes = banco.transacao(c -> 
            banco.consultar(c,
                "select * from campanha_scoring_dimensoes where scoring_id = ? order by ordem asc",
                scoringId
            )
        );

        String planoId = planoExistente.map(p -> p.path("id").asText()).orElseGet(() -> UUID.randomUUID().toString());

        ObjectNode plano = JsonNodeFactory.instance.objectNode();
        plano.put("id", planoId);
        plano.put("scoring_id", scoringId);
        plano.put("campanha_id", campanhaId);
        plano.put("cliente_id", clienteId);
        plano.put("ciclo", ciclo);
        plano.put("status", "em_execucao");

        // 5. Gerar ações automáticas para dimensões Crítico, Alto e Moderado
        List<ObjectNode> acoes = new ArrayList<>();
        int ordem = 1;

        for (ObjectNode dim : dimensoes) {
            String nivelRisco = dim.path("nivel_risco").asText().toLowerCase();
            boolean elegivel = "critico".equals(nivelRisco) || "alto".equals(nivelRisco) || "moderado".equals(nivelRisco);
            if (!elegivel) {
                // Baixo e Insignificante não geram ação automática
                continue;
            }

            String codigo = dim.path("codigo").asText();
            String nome = dim.path("nome").asText();
            String acaoSugerida = SUGESTOES_ACOES.getOrDefault(codigo, "Pendente de definição pelo responsável técnico");

            ObjectNode a = JsonNodeFactory.instance.objectNode();
            a.put("id", UUID.randomUUID().toString());
            a.put("plano_id", planoId);
            a.put("scoring_id", scoringId);
            a.put("dimensao_codigo", codigo);
            a.put("dimensao_nome", nome);
            if (dim.hasNonNull("categoria")) a.put("categoria", dim.path("categoria").asText());
            a.put("nivel_risco", dim.path("nivel_risco").asText());
            if (dim.hasNonNull("probabilidade_codigo")) a.put("probabilidade_codigo", dim.path("probabilidade_codigo").asText());
            if (dim.hasNonNull("severidade_codigo")) a.put("severidade_codigo", dim.path("severidade_codigo").asText());
            if (dim.hasNonNull("score_ps")) a.put("score_ps", dim.get("score_ps").decimalValue());
            a.put("prioridade", dim.path("prioridade").asText());
            a.put("acao", acaoSugerida);
            a.put("objetivo", "Mitigar a exposição aos fatores de risco psicossocial identificados na avaliação.");
            a.put("responsavel", "Pendente de definição");
            a.putNull("prazo_dias");
            a.putNull("data_prevista");
            a.put("indicador", "Pendente de definição pelo responsável técnico");
            a.put("meta", "Pendente de definição pelo responsável técnico");
            a.put("status", "pendente");
            a.putNull("observacoes");
            a.put("origem", "automatica");
            a.put("ordem", ordem++);

            acoes.add(a);
        }

        planoAcaoRepository.salvarPlanoComAcoes(plano, acoes);
        return montarRespostaPlano(plano);
    }

    public ObjectNode obterPlano(String campanhaId, String clienteIdEsperado) {
        validarCampanha(campanhaId, clienteIdEsperado);

        // Se a campanha não tem scoring calculado ainda, retornar 422
        boolean temScoring = banco.transacao(c -> 
            banco.consultarUm(c, "select id from campanha_scoring where campanha_id = ?", campanhaId).isPresent()
        );
        if (!temScoring) {
            throw ApiException.unprocessableEntity(
                "Não existe um Scoring Psicossocial calculado para esta campanha. Execute o Scoring antes de gerar o Plano de Ação."
            );
        }

        ObjectNode plano = planoAcaoRepository.obterPlanoPorCampanha(campanhaId)
                .orElseThrow(() -> ApiException.naoEncontrado("Plano de Ação não encontrado para esta campanha"));

        return montarRespostaPlano(plano);
    }

    public ObjectNode obterResumo(String campanhaId, String clienteIdEsperado) {
        validarCampanha(campanhaId, clienteIdEsperado);

        ObjectNode plano = planoAcaoRepository.obterPlanoPorCampanha(campanhaId)
                .orElseThrow(() -> ApiException.naoEncontrado("Plano de Ação não encontrado para esta campanha"));

        return planoAcaoRepository.calcularResumo(plano.path("id").asText());
    }

    public ObjectNode atualizarAcao(String campanhaId, String acaoId, JsonNode dados) {
        if (dados == null || !dados.isObject()) {
            throw ApiException.requisicaoInvalida("O corpo da requisição deve ser um objeto JSON");
        }

        validarCampanha(campanhaId);
        ObjectNode plano = planoAcaoRepository.obterPlanoPorCampanha(campanhaId)
                .orElseThrow(() -> ApiException.naoEncontrado("Plano de Ação não encontrado para esta campanha"));

        ObjectNode acaoAtual = planoAcaoRepository.obterAcao(acaoId)
                .orElseThrow(() -> ApiException.naoEncontrado("Ação não encontrada"));

        if (!acaoAtual.path("plano_id").asText().equals(plano.path("id").asText())) {
            throw ApiException.naoEncontrado("Ação não pertence a esta campanha");
        }

        // PROTEÇÃO DO DIAGNÓSTICO: não permitir alteração de campos do diagnóstico
        List<String> camposProibidos = List.of(
            "nivel_risco", "nivelRisco",
            "score_ps", "scorePs", "score",
            "probabilidade_codigo", "probabilidadeCodigo", "probabilidade",
            "severidade_codigo", "severidadeCodigo", "severidade",
            "dimensao_codigo", "dimensaoCodigo",
            "scoring_id", "scoringId",
            "plano_id", "planoId"
        );

        for (String proibido : camposProibidos) {
            if (dados.has(proibido)) {
                String valNovo = dados.get(proibido).asText();
                String valAtual = acaoAtual.has(proibido) ? acaoAtual.get(proibido).asText() : "";
                if (!valNovo.equalsIgnoreCase(valAtual)) {
                    throw ApiException.requisicaoInvalida(
                        "Não é permitido alterar dados originais do diagnóstico (nível de risco, score, probabilidade ou severidade) no Plano de Ação."
                    );
                }
            }
        }

        Map<String, Object> camposAtualizar = new HashMap<>();
        List<Map<String, String>> historico = new ArrayList<>();

        verificarEAtualizarCampo("acao", dados, acaoAtual, camposAtualizar, historico);
        verificarEAtualizarCampo("objetivo", dados, acaoAtual, camposAtualizar, historico);
        verificarEAtualizarCampo("responsavel", dados, acaoAtual, camposAtualizar, historico);
        verificarEAtualizarCampo("prazo_dias", dados, acaoAtual, camposAtualizar, historico, "prazoDias");
        verificarEAtualizarCampo("data_prevista", dados, acaoAtual, camposAtualizar, historico, "dataPrevista");
        verificarEAtualizarCampo("indicador", dados, acaoAtual, camposAtualizar, historico);
        verificarEAtualizarCampo("meta", dados, acaoAtual, camposAtualizar, historico);
        verificarEAtualizarCampo("observacoes", dados, acaoAtual, camposAtualizar, historico);

        if (dados.hasNonNull("status")) {
            String statusNovo = dados.path("status").asText().toLowerCase();
            String statusAtual = acaoAtual.path("status").asText().toLowerCase();
            if (!statusNovo.equals(statusAtual)) {
                validarStatus(statusNovo);
                camposAtualizar.put("status", statusNovo);
                Map<String, String> h = new HashMap<>();
                h.put("status_anterior", statusAtual);
                h.put("status_novo", statusNovo);
                h.put("campo_alterado", "status");
                h.put("valor_anterior", statusAtual);
                h.put("valor_novo", statusNovo);
                h.put("motivo", dados.hasNonNull("motivo") ? dados.path("motivo").asText() : "Atualização de status");
                h.put("usuario", dados.hasNonNull("usuario") ? dados.path("usuario").asText() : "sistema");
                historico.add(h);
            }
        }

        planoAcaoRepository.atualizarAcao(acaoId, camposAtualizar, historico);
        return formatarAcao(planoAcaoRepository.obterAcao(acaoId).orElseThrow());
    }

    public ObjectNode alterarStatus(String campanhaId, String acaoId, String statusNovo, String motivo, String usuario) {
        validarCampanha(campanhaId);
        ObjectNode plano = planoAcaoRepository.obterPlanoPorCampanha(campanhaId)
                .orElseThrow(() -> ApiException.naoEncontrado("Plano de Ação não encontrado"));

        ObjectNode acao = planoAcaoRepository.obterAcao(acaoId)
                .orElseThrow(() -> ApiException.naoEncontrado("Ação não encontrada"));

        if (!acao.path("plano_id").asText().equals(plano.path("id").asText())) {
            throw ApiException.naoEncontrado("Ação não pertence a esta campanha");
        }

        String statusNormalizado = statusNovo != null ? statusNovo.trim().toLowerCase() : "";
        validarStatus(statusNormalizado);

        String statusAnterior = acao.path("status").asText();
        if (statusNormalizado.equals(statusAnterior)) {
            return formatarAcao(acao);
        }

        Map<String, Object> campos = Map.of("status", statusNormalizado);
        List<Map<String, String>> hist = List.of(Map.of(
            "status_anterior", statusAnterior,
            "status_novo", statusNormalizado,
            "campo_alterado", "status",
            "valor_anterior", statusAnterior,
            "valor_novo", statusNormalizado,
            "motivo", motivo != null ? motivo : "Transição de status",
            "usuario", usuario != null ? usuario : "sistema"
        ));

        planoAcaoRepository.atualizarAcao(acaoId, campos, hist);
        return formatarAcao(planoAcaoRepository.obterAcao(acaoId).orElseThrow());
    }

    public void excluirAcao(String campanhaId, String acaoId, String motivo) {
        validarCampanha(campanhaId);
        ObjectNode plano = planoAcaoRepository.obterPlanoPorCampanha(campanhaId)
                .orElseThrow(() -> ApiException.naoEncontrado("Plano de Ação não encontrado"));

        ObjectNode acao = planoAcaoRepository.obterAcao(acaoId)
                .orElseThrow(() -> ApiException.naoEncontrado("Ação não encontrada"));

        if (!acao.path("plano_id").asText().equals(plano.path("id").asText())) {
            throw ApiException.naoEncontrado("Ação não pertence a esta campanha");
        }

        String status = acao.path("status").asText().toLowerCase();
        if ("em_andamento".equals(status) || "concluido".equals(status) || "concluida".equals(status)) {
            // Não permitir exclusão física de ações em andamento ou concluídas
            alterarStatus(campanhaId, acaoId, "cancelada", motivo != null ? motivo : "Ação cancelada pelo usuário", "sistema");
        } else {
            planoAcaoRepository.excluirAcao(acaoId);
        }
    }

    public ObjectNode criarAcaoManual(String campanhaId, JsonNode dados) {
        if (dados == null || !dados.isObject()) {
            throw ApiException.requisicaoInvalida("O corpo da requisição deve ser um objeto JSON");
        }

        validarCampanha(campanhaId);
        ObjectNode plano = planoAcaoRepository.obterPlanoPorCampanha(campanhaId)
                .orElseGet(() -> gerarPlano(campanhaId, false));

        String dimensaoCodigo = dados.hasNonNull("dimensaoCodigo") ? dados.path("dimensaoCodigo").asText() : dados.path("dimensao_codigo").asText("GERAL");
        String dimensaoNome = dados.hasNonNull("dimensaoNome") ? dados.path("dimensaoNome").asText() : dados.path("dimensao_nome").asText(dimensaoCodigo);
        String acaoTexto = dados.hasNonNull("acao") ? dados.path("acao").asText().trim() : "";

        if (acaoTexto.isEmpty()) {
            throw ApiException.requisicaoInvalida("Campo 'acao' é obrigatório para criação manual");
        }

        ObjectNode a = JsonNodeFactory.instance.objectNode();
        a.put("id", UUID.randomUUID().toString());
        a.put("plano_id", plano.path("id").asText());
        a.put("scoring_id", plano.path("scoring_id").asText());
        a.put("dimensao_codigo", dimensaoCodigo);
        a.put("dimensao_nome", dimensaoNome);
        if (dados.hasNonNull("categoria")) a.put("categoria", dados.path("categoria").asText());
        a.put("nivel_risco", dados.hasNonNull("nivelRisco") ? dados.path("nivelRisco").asText() : dados.path("nivel_risco").asText("moderado"));
        if (dados.hasNonNull("probabilidadeCodigo")) a.put("probabilidade_codigo", dados.path("probabilidadeCodigo").asText());
        if (dados.hasNonNull("severidadeCodigo")) a.put("severidade_codigo", dados.path("severidadeCodigo").asText());
        if (dados.hasNonNull("score")) a.put("score_ps", dados.get("score").decimalValue());
        a.put("prioridade", dados.hasNonNull("prioridade") ? dados.path("prioridade").asText() : "Médio prazo");
        a.put("acao", acaoTexto);
        if (dados.hasNonNull("objetivo")) a.put("objetivo", dados.path("objetivo").asText());
        if (dados.hasNonNull("responsavel")) a.put("responsavel", dados.path("responsavel").asText());
        if (dados.hasNonNull("prazoDias")) a.put("prazo_dias", dados.path("prazoDias").asInt());
        if (dados.hasNonNull("dataPrevista")) a.put("data_prevista", dados.path("dataPrevista").asText());
        if (dados.hasNonNull("indicador")) a.put("indicador", dados.path("indicador").asText());
        if (dados.hasNonNull("meta")) a.put("meta", dados.path("meta").asText());
        a.put("status", "pendente");
        if (dados.hasNonNull("observacoes")) a.put("observacoes", dados.path("observacoes").asText());
        a.put("origem", "manual");
        a.put("ordem", 99);

        planoAcaoRepository.inserirAcaoManual(a);
        return formatarAcao(a);
    }

    public ObjectNode validarCampanha(String campanhaId) {
        return validarCampanha(campanhaId, null);
    }

    public ObjectNode validarCampanha(String campanhaId, String clienteIdEsperado) {
        ObjectNode campanha = banco.transacao(c -> 
            banco.consultarUm(c, "select * from campanhas where id = ?", campanhaId)
        ).orElseThrow(() -> ApiException.naoEncontrado("Campanha não encontrada"));

        if (clienteIdEsperado != null && !clienteIdEsperado.isBlank()) {
            if (!clienteIdEsperado.equals(campanha.path("cliente_id").asText())) {
                throw ApiException.naoEncontrado("Campanha não encontrada");
            }
        }
        return campanha;
    }

    private void validarStatus(String status) {
        if (!List.of("pendente", "em_andamento", "concluida", "cancelada").contains(status)) {
            throw ApiException.requisicaoInvalida("Status inválido. Permitidos: pendente, em_andamento, concluida, cancelada.");
        }
    }

    private void verificarEAtualizarCampo(String campoSql, JsonNode dados, ObjectNode atual,
                                         Map<String, Object> campos, List<Map<String, String>> historico,
                                         String... aliases) {
        JsonNode node = null;
        if (dados.has(campoSql)) {
            node = dados.get(campoSql);
        } else {
            for (String alias : aliases) {
                if (dados.has(alias)) {
                    node = dados.get(alias);
                    break;
                }
            }
        }

        if (node != null) {
            Object novoVal = node.isNull() ? null : (node.isNumber() ? node.asInt() : node.asText());
            String valAntigoStr = atual.hasNonNull(campoSql) ? atual.get(campoSql).asText() : null;
            String valNovoStr = novoVal != null ? novoVal.toString() : null;

            if ((valAntigoStr == null && valNovoStr != null) || (valAntigoStr != null && !valAntigoStr.equals(valNovoStr))) {
                campos.put(campoSql, novoVal);
                Map<String, String> h = new HashMap<>();
                h.put("status_anterior", atual.path("status").asText());
                h.put("status_novo", atual.path("status").asText());
                h.put("campo_alterado", campoSql);
                h.put("valor_anterior", valAntigoStr);
                h.put("valor_novo", valNovoStr);
                h.put("motivo", "Edição operacional da ação");
                h.put("usuario", dados.hasNonNull("usuario") ? dados.path("usuario").asText() : "sistema");
                historico.add(h);
            }
        }
    }

    private ObjectNode montarRespostaPlano(ObjectNode plano) {
        String planoId = plano.path("id").asText();
        List<ObjectNode> acoes = planoAcaoRepository.listarAcoesDoPlano(planoId);

        ObjectNode resp = JsonNodeFactory.instance.objectNode();
        resp.put("id", planoId);
        resp.put("campanhaId", plano.path("campanha_id").asText());
        resp.put("scoringId", plano.path("scoring_id").asText());
        resp.put("clienteId", plano.path("cliente_id").asText());
        resp.put("ciclo", plano.hasNonNull("ciclo") ? plano.path("ciclo").asText() : "");
        resp.put("status", plano.path("status").asText("rascunho"));

        ArrayNode arr = resp.putArray("acoes");
        for (ObjectNode a : acoes) {
            arr.add(formatarAcao(a));
        }

        return resp;
    }

    private ObjectNode formatarAcao(ObjectNode a) {
        ObjectNode out = JsonNodeFactory.instance.objectNode();
        out.put("id", a.path("id").asText());
        out.put("dimensaoCodigo", a.path("dimensao_codigo").asText());
        out.put("dimensaoNome", a.path("dimensao_nome").asText());
        if (a.hasNonNull("categoria")) out.put("categoria", a.path("categoria").asText());
        out.put("nivelRisco", a.path("nivel_risco").asText());
        out.put("probabilidade", a.path("probabilidade_codigo").asText(null));
        out.put("severidade", a.path("severidade_codigo").asText(null));
        if (a.hasNonNull("score_ps")) {
            out.put("score", a.get("score_ps").isNumber() ? a.get("score_ps").decimalValue() : null);
        } else {
            out.putNull("score");
        }
        out.put("prioridade", a.path("prioridade").asText());
        out.put("acao", a.path("acao").asText());
        out.put("objetivo", a.path("objetivo").asText(null));
        out.put("responsavel", a.path("responsavel").asText(null));
        out.put("prazoDias", a.hasNonNull("prazo_dias") ? a.path("prazo_dias").asInt() : null);
        out.put("dataPrevista", a.path("data_prevista").asText(null));
        out.put("indicador", a.path("indicador").asText(null));
        out.put("meta", a.path("meta").asText(null));
        out.put("status", a.path("status").asText("pendente"));
        out.put("observacoes", a.path("observacoes").asText(null));
        out.put("origem", a.path("origem").asText("automatica"));
        return out;
    }
}
