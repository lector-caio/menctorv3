package br.com.lector.menctor.planoacao;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import br.com.lector.menctor.dados.Banco;

@ApplicationScoped
public class PlanoAcaoRepository {

    @Inject
    Banco banco;

    @Inject
    ObjectMapper mapper;

    public Optional<ObjectNode> obterPlanoPorCampanha(String campanhaId) {
        return banco.transacao(c -> 
            banco.consultarUm(c, "select * from scoring_planos_acao where campanha_id = ?", campanhaId)
        );
    }

    public Optional<ObjectNode> obterPlanoPorScoring(String scoringId) {
        return banco.transacao(c -> 
            banco.consultarUm(c, "select * from scoring_planos_acao where scoring_id = ?", scoringId)
        );
    }

    public List<ObjectNode> listarAcoesDoPlano(String planoId) {
        return banco.transacao(c -> 
            banco.consultar(c,
                "select * from scoring_acoes where plano_id = ? order by ordem asc, created_at asc",
                planoId
            )
        );
    }

    public Optional<ObjectNode> obterAcao(String acaoId) {
        return banco.transacao(c -> 
            banco.consultarUm(c, "select * from scoring_acoes where id = ?", acaoId)
        );
    }

    public ObjectNode salvarPlanoComAcoes(ObjectNode plano, List<ObjectNode> acoes) {
        return banco.transacao(c -> {
            String planoId = plano.path("id").asText();
            banco.executar(c,
                "insert into scoring_planos_acao (id, scoring_id, campanha_id, cliente_id, ciclo, status, gerado_em) " +
                "values (?, ?, ?, ?, ?, ?, now()) " +
                "on conflict (scoring_id) do update set " +
                "  status = excluded.status, " +
                "  updated_at = now()",
                planoId,
                plano.path("scoring_id").asText(),
                plano.path("campanha_id").asText(),
                plano.path("cliente_id").asText(),
                plano.hasNonNull("ciclo") ? plano.path("ciclo").asText() : null,
                plano.path("status").asText("rascunho")
            );

            // Deletar ações anteriores deste plano (se regeneração)
            banco.executar(c, "delete from scoring_acoes where plano_id = ?", planoId);

            for (ObjectNode acao : acoes) {
                banco.executar(c,
                    "insert into scoring_acoes (" +
                    "  id, plano_id, scoring_id, dimensao_codigo, dimensao_nome, categoria, nivel_risco, " +
                    "  probabilidade_codigo, severidade_codigo, score_ps, prioridade, acao, objetivo, " +
                    "  responsavel, prazo_dias, data_prevista, indicador, meta, status, observacoes, origem, ordem" +
                    ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    acao.path("id").asText(),
                    planoId,
                    acao.path("scoring_id").asText(),
                    acao.path("dimensao_codigo").asText(),
                    acao.path("dimensao_nome").asText(),
                    acao.hasNonNull("categoria") ? acao.path("categoria").asText() : null,
                    acao.path("nivel_risco").asText(),
                    acao.hasNonNull("probabilidade_codigo") ? acao.path("probabilidade_codigo").asText() : null,
                    acao.hasNonNull("severidade_codigo") ? acao.path("severidade_codigo").asText() : null,
                    acao.hasNonNull("score_ps") && acao.get("score_ps").isNumber() ? acao.get("score_ps").decimalValue() : null,
                    acao.path("prioridade").asText(),
                    acao.path("acao").asText(),
                    acao.hasNonNull("objetivo") ? acao.path("objetivo").asText() : null,
                    acao.hasNonNull("responsavel") ? acao.path("responsavel").asText() : null,
                    acao.hasNonNull("prazo_dias") && acao.get("prazo_dias").isNumber() ? acao.get("prazo_dias").asInt() : null,
                    acao.hasNonNull("data_prevista") ? acao.path("data_prevista").asText() : null,
                    acao.hasNonNull("indicador") ? acao.path("indicador").asText() : null,
                    acao.hasNonNull("meta") ? acao.path("meta").asText() : null,
                    acao.path("status").asText("pendente"),
                    acao.hasNonNull("observacoes") ? acao.path("observacoes").asText() : null,
                    acao.path("origem").asText("automatica"),
                    acao.path("ordem").asInt(0)
                );
            }

            return plano;
        });
    }

    public ObjectNode inserirAcaoManual(ObjectNode acao) {
        return banco.transacao(c -> {
            banco.executar(c,
                "insert into scoring_acoes (" +
                "  id, plano_id, scoring_id, dimensao_codigo, dimensao_nome, categoria, nivel_risco, " +
                "  probabilidade_codigo, severidade_codigo, score_ps, prioridade, acao, objetivo, " +
                "  responsavel, prazo_dias, data_prevista, indicador, meta, status, observacoes, origem, ordem" +
                ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                acao.path("id").asText(),
                acao.path("plano_id").asText(),
                acao.path("scoring_id").asText(),
                acao.path("dimensao_codigo").asText(),
                acao.path("dimensao_nome").asText(),
                acao.hasNonNull("categoria") ? acao.path("categoria").asText() : null,
                acao.path("nivel_risco").asText("moderado"),
                acao.hasNonNull("probabilidade_codigo") ? acao.path("probabilidade_codigo").asText() : null,
                acao.hasNonNull("severidade_codigo") ? acao.path("severidade_codigo").asText() : null,
                acao.hasNonNull("score_ps") && acao.get("score_ps").isNumber() ? acao.get("score_ps").decimalValue() : null,
                acao.path("prioridade").asText("Médio prazo"),
                acao.path("acao").asText(),
                acao.hasNonNull("objetivo") ? acao.path("objetivo").asText() : null,
                acao.hasNonNull("responsavel") ? acao.path("responsavel").asText() : null,
                acao.hasNonNull("prazo_dias") && acao.get("prazo_dias").isNumber() ? acao.get("prazo_dias").asInt() : null,
                acao.hasNonNull("data_prevista") ? acao.path("data_prevista").asText() : null,
                acao.hasNonNull("indicador") ? acao.path("indicador").asText() : null,
                acao.hasNonNull("meta") ? acao.path("meta").asText() : null,
                acao.path("status").asText("pendente"),
                acao.hasNonNull("observacoes") ? acao.path("observacoes").asText() : null,
                acao.path("origem").asText("manual"),
                acao.path("ordem").asInt(99)
            );
            return acao;
        });
    }

    public void atualizarAcao(String acaoId, Map<String, Object> campos, List<Map<String, String>> historico) {
        banco.transacao(c -> {
            if (!campos.isEmpty()) {
                StringBuilder sql = new StringBuilder("update scoring_acoes set ");
                List<Object> params = new ArrayList<>();
                int idx = 0;
                for (Map.Entry<String, Object> entry : campos.entrySet()) {
                    if (idx > 0) sql.append(", ");
                    sql.append(entry.getKey()).append(" = ?");
                    params.add(entry.getValue());
                    idx++;
                }
                sql.append(", updated_at = now() where id = ?");
                params.add(acaoId);
                banco.executar(c, sql.toString(), params.toArray());
            }

            for (Map<String, String> h : historico) {
                banco.executar(c,
                    "insert into scoring_acoes_historico (id, acao_id, status_anterior, status_novo, campo_alterado, valor_anterior, valor_novo, usuario, motivo) " +
                    "values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID().toString(),
                    acaoId,
                    h.get("status_anterior"),
                    h.get("status_novo"),
                    h.get("campo_alterado"),
                    h.get("valor_anterior"),
                    h.get("valor_novo"),
                    h.getOrDefault("usuario", "sistema"),
                    h.get("motivo")
                );
            }
            return null;
        });
    }

    public void excluirAcao(String acaoId) {
        banco.transacao(c -> {
            banco.executar(c, "delete from scoring_acoes where id = ?", acaoId);
            return null;
        });
    }

    public List<ObjectNode> listarHistoricoAcao(String acaoId) {
        return banco.transacao(c -> 
            banco.consultar(c,
                "select * from scoring_acoes_historico where acao_id = ? order by created_at desc",
                acaoId
            )
        );
    }

    public ObjectNode calcularResumo(String planoId) {
        return banco.transacao(c -> {
            List<ObjectNode> acoes = banco.consultar(c,
                "select status, nivel_risco from scoring_acoes where plano_id = ?",
                planoId
            );

            int totalAcoes = acoes.size();
            int pendentes = 0;
            int emAndamento = 0;
            int concluidas = 0;
            int canceladas = 0;
            int criticas = 0;
            int altas = 0;
            int moderadas = 0;

            for (ObjectNode a : acoes) {
                String st = a.path("status").asText().toLowerCase();
                String nv = a.path("nivel_risco").asText().toLowerCase();

                switch (st) {
                    case "pendente" -> pendentes++;
                    case "em_andamento" -> emAndamento++;
                    case "concluida" -> concluidas++;
                    case "cancelada" -> canceladas++;
                }

                switch (nv) {
                    case "critico" -> criticas++;
                    case "alto" -> altas++;
                    case "moderado" -> moderadas++;
                }
            }

            int validasParaConclusao = totalAcoes - canceladas;
            double percentualConclusao = validasParaConclusao > 0
                    ? BigDecimal.valueOf(((double) concluidas / validasParaConclusao) * 100.0)
                            .setScale(1, RoundingMode.HALF_UP).doubleValue()
                    : 0.0;

            ObjectNode resumo = JsonNodeFactory.instance.objectNode();
            resumo.put("totalAcoes", totalAcoes);
            resumo.put("pendentes", pendentes);
            resumo.put("emAndamento", emAndamento);
            resumo.put("concluidas", concluidas);
            resumo.put("canceladas", canceladas);
            resumo.put("criticas", criticas);
            resumo.put("altas", altas);
            resumo.put("moderadas", moderadas);
            resumo.put("percentualConclusao", percentualConclusao);

            return resumo;
        });
    }
}
