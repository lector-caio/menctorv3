package br.com.lector.menctor.scoring;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
public class ScoringRepository {

    @Inject
    Banco banco;

    @Inject
    ObjectMapper mapper;

    /**
     * Obtém o resultado consolidado do Scoring para a campanha.
     * Se ainda não foi calculado ou se forçado, executa o motor e persiste o snapshot.
     */
    public ObjectNode obterOuCalcularScoring(String campanhaId, boolean forcarRecalculo) {
        if (!forcarRecalculo) {
            Optional<ObjectNode> existente = banco.transacao(c -> 
                banco.consultarUm(c, "select * from campanha_scoring where campanha_id = ?", campanhaId)
            );
            if (existente.isPresent()) {
                ObjectNode s = existente.get();
                if (s.hasNonNull("dados_consolidados") && s.get("dados_consolidados").isObject()) {
                    return (ObjectNode) s.get("dados_consolidados");
                }
            }
        }
        return calcularEGravarScoring(campanhaId);
    }

    /**
     * Executa o motor central de Scoring Psicossocial.
     */
    public ObjectNode calcularEGravarScoring(String campanhaId) {
        // 1. Identificar Campanha e Cliente
        ObjectNode campanha = banco.transacao(c -> 
            banco.consultarUm(c, "select * from campanhas where id = ?", campanhaId)
        ).orElseThrow(() -> ApiException.naoEncontrado("Campanha não encontrada"));

        String clienteId = campanha.path("cliente_id").asText();
        String instrumentoId = campanha.hasNonNull("instrumento") && !campanha.path("instrumento").asText().isBlank()
                ? campanha.path("instrumento").asText().toLowerCase()
                : (campanha.hasNonNull("diagnostico_id") ? campanha.path("diagnostico_id").asText().toLowerCase() : "mte");
        String ciclo = campanha.hasNonNull("ciclo") ? campanha.path("ciclo").asText() : "";

        // 2. Identificar Matriz de Risco Publicada do Cliente
        ObjectNode versaoPublicada = banco.transacao(c -> 
            banco.consultarUm(c,
                "select * from matriz_versoes where cliente_id = ? and status = 'publicada' " +
                "order by publicada_em desc nulls last, created_at desc limit 1",
                clienteId
            )
        ).orElseThrow(() -> ApiException.unprocessableEntity(
            "O cliente não possui uma versão publicada da Matriz de Risco. Publique uma versão da Matriz de Risco antes de executar o Scoring."
        ));

        String versaoId = versaoPublicada.path("id").asText();
        String versaoCodigo = versaoPublicada.path("versao").asText();
        String matrizFramework = versaoPublicada.hasNonNull("framework") ? versaoPublicada.path("framework").asText().toLowerCase() : "copsoq";

        // 3. Carregar Configuração da Matriz Publicada
        List<ObjectNode> criteriosProb = banco.transacao(c -> banco.consultar(c,
            "select * from matriz_criterios_probabilidade where versao_id = ? order by nivel asc", versaoId
        ));
        List<ObjectNode> criteriosSev = banco.transacao(c -> banco.consultar(c,
            "select * from matriz_criterios_severidade where versao_id = ? order by nivel asc", versaoId
        ));
        List<ObjectNode> gradePs = banco.transacao(c -> banco.consultar(c,
            "select * from matriz_classificacoes_ps where versao_id = ? order by probabilidade asc, severidade asc", versaoId
        ));
        List<ObjectNode> fatoresSev = banco.transacao(c -> banco.consultar(c,
            "select * from matriz_fatores_severidade where versao_id = ? order by framework asc, codigo asc", versaoId
        ));

        // 4. Carregar Dimensões e Questões do Instrumento
        List<ObjectNode> dimensoesCadastradas = banco.transacao(c -> banco.consultar(c,
            "select d.*, " +
            "(select count(*) from instrumento_questoes q where q.dimensao_id = d.id and q.ativo = true) as total_questoes " +
            "from instrumento_dimensoes d where d.instrumento_id = ? and d.ativo = true order by d.ordem asc",
            instrumentoId
        ));

        // Se o instrumento não tiver dimensões cadastradas no banco (ex: fallback MTE ou COPSOQ)
        if (dimensoesCadastradas.isEmpty()) {
            dimensoesCadastradas = banco.transacao(c -> banco.consultar(c,
                "select d.*, " +
                "(select count(*) from instrumento_questoes q where q.dimensao_id = d.id and q.ativo = true) as total_questoes " +
                "from instrumento_dimensoes d where d.instrumento_id = 'mte' and d.ativo = true order by d.ordem asc"
            ));
        }

        // 5. Carregar Respostas Válidas da Campanha
        List<ObjectNode> respostas = banco.transacao(c -> banco.consultar(c,
            "select * from campanha_respostas where campanha_id = ? order by created_at asc", campanhaId
        ));

        int totalRespondentes = respostas.size();

        // 6. Confiabilidade da Amostra (Documento Técnico)
        String confiabilidade;
        String confiabilidadeDetalhe;
        if (totalRespondentes >= 100) {
            confiabilidade = "Alta";
            confiabilidadeDetalhe = "Amostra robusta (N ≥ 100). Resultados estatisticamente sólidos e representativos do grupo avaliado.";
        } else if (totalRespondentes >= 50) {
            confiabilidade = "Média";
            confiabilidadeDetalhe = "Amostra moderada (50 ≤ N < 100). Representatividade estatística satisfatória.";
        } else if (totalRespondentes >= 5) {
            confiabilidade = "Baixa";
            confiabilidadeDetalhe = "Amostra reduzida (N < 50). Resultados preliminares, interpretar com cautela.";
        } else {
            confiabilidade = "Insuficiente";
            confiabilidadeDetalhe = "Amostra insuficiente (N < 5). Abaixo do limiar de K-anonimato.";
        }

        // 7. Processar cada Dimensão
        List<ObjectNode> dimensoesCalculadas = new ArrayList<>();
        double somaIndicesIps = 0.0;
        int dimsComRespostas = 0;
        int totalFatoresAltoCritico = 0;

        for (ObjectNode dim : dimensoesCadastradas) {
            String dimCodigo = dim.path("codigo").asText().trim();
            String dimNome = dim.path("nome").asText().trim();
            String categoria = dim.hasNonNull("categoria") ? dim.path("categoria").asText() : "geral";
            int totalQuestoes = dim.path("total_questoes").asInt(0);

            // Coletar respostas dos colaboradores para esta dimensão
            List<Double> scoresRespondentes = new ArrayList<>();

            for (ObjectNode r : respostas) {
                Double valorResp = extrairValorDimensao(r, dimCodigo, dimNome);
                if (valorResp != null) {
                    scoresRespondentes.add(valorResp);
                }
            }

            int nDim = scoresRespondentes.size();
            double mediaLikert = 0.0;
            double indiceNormalizado = 0.0;
            double prevalencia = 0.0;

            if (nDim > 0) {
                double soma = scoresRespondentes.stream().mapToDouble(Double::doubleValue).sum();
                mediaLikert = BigDecimal.valueOf(soma / nDim).setScale(2, RoundingMode.HALF_UP).doubleValue();

                // Normalização Likert 1-5 -> 0-100: ((media - 1) / 4) * 100
                double norm = ((mediaLikert - 1.0) / 4.0) * 100.0;
                if (norm < 0.0) norm = 0.0;
                if (norm > 100.0) norm = 100.0;
                indiceNormalizado = BigDecimal.valueOf(norm).setScale(1, RoundingMode.HALF_UP).doubleValue();

                // Prevalência dos trabalhadores expostos ao risco (score na faixa desfavorável >= 3.0)
                long expostos = scoresRespondentes.stream().filter(v -> v >= 3.0).count();
                prevalencia = BigDecimal.valueOf(((double) expostos / nDim) * 100.0).setScale(1, RoundingMode.HALF_UP).doubleValue();

                somaIndicesIps += indiceNormalizado;
                dimsComRespostas++;
            }

            // 7.1 Mapear Probabilidade P1-P5 da Matriz Publicada
            ObjectNode critProb = determinarProbabilidade(criteriosProb, indiceNormalizado);
            int probNivel = critProb.path("nivel").asInt(1);
            String probCodigo = critProb.path("codigo").asText("P" + probNivel);
            String probNome = critProb.path("nome").asText("");

            // 7.2 Mapear Severidade S1-S5 Calibrada na Matriz Publicada
            Optional<ObjectNode> fatorSevOpt = localizarFatorSeveridade(fatoresSev, matrizFramework, dimCodigo, dimNome);
            Integer sevNivel = null;
            String sevCodigo = null;
            String sevNome = null;
            boolean sevConfigurada = false;
            String motivoAuditoria = null;

            if (fatorSevOpt.isPresent()) {
                sevNivel = fatorSevOpt.get().path("severidade").asInt();
                sevConfigurada = true;
                sevCodigo = "S" + sevNivel;
                // Buscar nome do critério de severidade
                final int finalSev = sevNivel;
                sevNome = criteriosSev.stream()
                        .filter(s -> s.path("nivel").asInt() == finalSev)
                        .findFirst()
                        .map(s -> s.path("nome").asText())
                        .orElse("Nível S" + finalSev);
            } else {
                sevConfigurada = false;
                motivoAuditoria = "Severidade não calibrada na versão publicada da Matriz de Risco para o fator " + dimCodigo;
            }

            // 7.3 Cruzar na Grade 5x5 PxS
            Integer scoreDiscreto = null;
            Double scoreContinuo = null;
            String nivelRisco = "indefinido";
            String prioridade = "Requer calibração de severidade";
            String cor = "#94a3b8";

            if (sevConfigurada && sevNivel != null) {
                final int finalP = probNivel;
                final int finalS = sevNivel;

                Optional<ObjectNode> celulaPs = gradePs.stream()
                        .filter(g -> g.path("probabilidade").asInt() == finalP && g.path("severidade").asInt() == finalS)
                        .findFirst();

                if (celulaPs.isPresent()) {
                    ObjectNode c = celulaPs.get();
                    scoreDiscreto = c.path("score").asInt(finalP * finalS);
                    nivelRisco = c.path("nivel_risco").asText("moderado");
                    prioridade = c.path("prioridade").asText("Médio prazo");
                    cor = c.path("cor").asText("#facc15");
                } else {
                    scoreDiscreto = finalP * finalS;
                    nivelRisco = scoreDiscreto >= 20 ? "critico" : scoreDiscreto >= 10 ? "alto" : scoreDiscreto >= 6 ? "moderado" : "baixo";
                }

                // Score contínuo P contínuo (média Likert) x S
                scoreContinuo = BigDecimal.valueOf(mediaLikert * finalS).setScale(1, RoundingMode.HALF_UP).doubleValue();

                if ("alto".equalsIgnoreCase(nivelRisco) || "critico".equalsIgnoreCase(nivelRisco)) {
                    totalFatoresAltoCritico++;
                }
            }

            ObjectNode dimResult = JsonNodeFactory.instance.objectNode();
            dimResult.put("codigo", dimCodigo);
            dimResult.put("nome", dimNome);
            dimResult.put("categoria", categoria);
            dimResult.put("questoes", totalQuestoes);
            dimResult.put("respondentes", nDim);
            dimResult.put("mediaLikert", mediaLikert);
            dimResult.put("indice", indiceNormalizado);
            dimResult.put("prevalencia", prevalencia);
            dimResult.put("probabilidade", probNivel);
            dimResult.put("probabilidadeCodigo", probCodigo);
            dimResult.put("probabilidadeNome", probNome);

            if (sevConfigurada && sevNivel != null) {
                dimResult.put("severidade", sevNivel);
                dimResult.put("severidadeCodigo", sevCodigo);
                dimResult.put("severidadeNome", sevNome);
                dimResult.put("severidadeConfigurada", true);
                dimResult.put("score", scoreDiscreto);
                dimResult.put("scoreContinuo", scoreContinuo);
                dimResult.put("nivelRisco", nivelRisco);
                dimResult.put("prioridade", prioridade);
                dimResult.put("cor", cor);
            } else {
                dimResult.putNull("severidade");
                dimResult.putNull("severidadeCodigo");
                dimResult.putNull("severidadeNome");
                dimResult.put("severidadeConfigurada", false);
                dimResult.putNull("score");
                dimResult.putNull("scoreContinuo");
                dimResult.put("nivelRisco", "indefinido");
                dimResult.put("prioridade", "Requer calibração de severidade");
                dimResult.put("cor", "#94a3b8");
                dimResult.put("motivoAuditoria", motivoAuditoria);
            }

            dimResult.put("ordem", dim.path("ordem").asInt(0));
            dimensoesCalculadas.add(dimResult);
        }

        // 8. Calcular IPS Global
        Double ipsGlobal = null;
        if (dimsComRespostas > 0) {
            ipsGlobal = BigDecimal.valueOf(somaIndicesIps / dimsComRespostas).setScale(1, RoundingMode.HALF_UP).doubleValue();
        }

        double fatoresAltoCriticoPct = dimensoesCalculadas.isEmpty()
                ? 0.0
                : BigDecimal.valueOf(((double) totalFatoresAltoCritico / dimensoesCalculadas.size()) * 100.0)
                        .setScale(1, RoundingMode.HALF_UP).doubleValue();

        // 9. Recortes Organizacionais com Regra Estrita de K-Anonimato (N < 5)
        ObjectNode recortesNode = JsonNodeFactory.instance.objectNode();
        int totalSuprimidos = 0;

        totalSuprimidos += processarRecorte(respostas, "setor", recortesNode.putArray("setores"));
        totalSuprimidos += processarRecorte(respostas, "cargo", recortesNode.putArray("cargos"));
        totalSuprimidos += processarRecorte(respostas, "unidade", recortesNode.putArray("unidades"));
        totalSuprimidos += processarRecorte(respostas, "ghe", recortesNode.putArray("ghes"));
        totalSuprimidos += processarRecorte(respostas, "ges", recortesNode.putArray("gess"));

        // 10. Montar Resultado Consolidado
        ObjectNode resultado = JsonNodeFactory.instance.objectNode();
        resultado.put("campanhaId", campanhaId);
        resultado.put("clienteId", clienteId);
        resultado.put("instrumento", instrumentoId);
        resultado.put("ciclo", ciclo);
        resultado.put("respondentes", totalRespondentes);
        if (ipsGlobal != null) {
            resultado.put("ips", ipsGlobal);
        } else {
            resultado.putNull("ips");
        }
        resultado.put("confiabilidade", confiabilidade);
        resultado.put("confiabilidadeDetalhe", confiabilidadeDetalhe);
        resultado.put("fatoresAltoCriticoPct", fatoresAltoCriticoPct);
        resultado.put("matrizVersaoId", versaoId);
        resultado.put("matrizVersaoCodigo", versaoCodigo);
        resultado.put("recortesSuprimidos", totalSuprimidos);

        ArrayNode dimsArray = resultado.putArray("dimensoes");
        dimensoesCalculadas.forEach(dimsArray::add);

        resultado.set("recortes", recortesNode);
        final Double finalIpsGlobal = ipsGlobal;
        final String finalConfiabilidade = confiabilidade;
        final String finalConfiabilidadeDetalhe = confiabilidadeDetalhe;
        final double finalFatoresAltoCriticoPct = fatoresAltoCriticoPct;
        final int finalSuprimidos = totalSuprimidos;

        // 11. Persistência dos Resultados Relacionais no PostgreSQL
        banco.transacao(c -> {
            // Upsert campanha_scoring
            Optional<ObjectNode> scoringDb = banco.consultarUm(c,
                "select id from campanha_scoring where campanha_id = ?", campanhaId
            );
            final String scoringId = scoringDb.map(s -> s.path("id").asText()).orElseGet(() -> UUID.randomUUID().toString());
            resultado.put("id", scoringId);
            resultado.put("scoringId", scoringId);

            banco.executar(c,
                "insert into campanha_scoring (" +
                "  id, campanha_id, cliente_id, instrumento_id, ciclo, matriz_versao_id, matriz_versao_codigo, " +
                "  total_respondentes, ips_global, confiabilidade, confiabilidade_detalhe, fatores_alto_critico_pct, " +
                "  recortes_suprimidos_qtd, dados_consolidados" +
                ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb) " +
                "on conflict (campanha_id) do update set " +
                "  cliente_id = excluded.cliente_id, " +
                "  instrumento_id = excluded.instrumento_id, " +
                "  ciclo = excluded.ciclo, " +
                "  matriz_versao_id = excluded.matriz_versao_id, " +
                "  matriz_versao_codigo = excluded.matriz_versao_codigo, " +
                "  total_respondentes = excluded.total_respondentes, " +
                "  ips_global = excluded.ips_global, " +
                "  confiabilidade = excluded.confiabilidade, " +
                "  confiabilidade_detalhe = excluded.confiabilidade_detalhe, " +
                "  fatores_alto_critico_pct = excluded.fatores_alto_critico_pct, " +
                "  recortes_suprimidos_qtd = excluded.recortes_suprimidos_qtd, " +
                "  dados_consolidados = excluded.dados_consolidados, " +
                "  updated_at = now()",
                scoringId, campanhaId, clienteId, instrumentoId, ciclo, versaoId, versaoCodigo,
                totalRespondentes, finalIpsGlobal, finalConfiabilidade, finalConfiabilidadeDetalhe, finalFatoresAltoCriticoPct,
                finalSuprimidos, resultado.toString()
            );

            // Deletar e reinserir dimensões relacionais
            banco.executar(c, "delete from campanha_scoring_dimensoes where scoring_id = ?", scoringId);
            for (ObjectNode d : dimensoesCalculadas) {
                banco.executar(c,
                    "insert into campanha_scoring_dimensoes (" +
                    "  id, scoring_id, codigo, nome, categoria, total_questoes, total_respondentes, " +
                    "  media_likert, indice_normalizado, prevalencia, probabilidade_nivel, probabilidade_codigo, " +
                    "  probabilidade_nome, severidade_nivel, severidade_codigo, severidade_nome, severidade_configurada, " +
                    "  score_ps, nivel_risco, prioridade, cor, motivo_auditoria, ordem" +
                    ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID().toString(), scoringId, d.path("codigo").asText(), d.path("nome").asText(),
                    d.path("categoria").asText(), d.path("questoes").asInt(0), d.path("respondentes").asInt(0),
                    d.path("mediaLikert").isNumber() ? d.path("mediaLikert").decimalValue() : null,
                    d.path("indice").isNumber() ? d.path("indice").decimalValue() : null,
                    d.path("prevalencia").isNumber() ? d.path("prevalencia").decimalValue() : null,
                    d.path("probabilidade").isNumber() ? d.path("probabilidade").asInt() : null,
                    d.path("probabilidadeCodigo").asText(null), d.path("probabilidadeNome").asText(null),
                    d.path("severidade").isNumber() ? d.path("severidade").asInt() : null,
                    d.path("severidadeCodigo").asText(null), d.path("severidadeNome").asText(null),
                    d.path("severidadeConfigurada").asBoolean(true),
                    d.path("scoreContinuo").isNumber() ? d.path("scoreContinuo").decimalValue() : (d.path("score").isNumber() ? d.path("score").decimalValue() : null),
                    d.path("nivelRisco").asText(null), d.path("prioridade").asText(null),
                    d.path("cor").asText(null), d.path("motivoAuditoria").asText(null),
                    d.path("ordem").asInt(0)
                );
            }

            // Deletar e reinserir recortes relacionais
            banco.executar(c, "delete from campanha_scoring_recortes where scoring_id = ?", scoringId);
            gravarRecortesBanco(c, scoringId, "setor", recortesNode.path("setores"));
            gravarRecortesBanco(c, scoringId, "cargo", recortesNode.path("cargos"));
            gravarRecortesBanco(c, scoringId, "unidade", recortesNode.path("unidades"));
            gravarRecortesBanco(c, scoringId, "ghe", recortesNode.path("ghes"));
            gravarRecortesBanco(c, scoringId, "ges", recortesNode.path("gess"));

            return null;
        });

        return resultado;
    }

    private void gravarRecortesBanco(java.sql.Connection c, String scoringId, String tipo, JsonNode arr) throws java.sql.SQLException {
        if (arr != null && arr.isArray()) {
            for (JsonNode item : arr) {
                banco.executar(c,
                    "insert into campanha_scoring_recortes (" +
                    "  id, scoring_id, tipo_recorte, nome_recorte, total_respondentes, suprimido_k_anonimato, " +
                    "  ips, media_likert, motivo_supressao" +
                    ") values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID().toString(), scoringId, tipo, item.path("nome").asText(),
                    item.path("respondentes").asInt(0), item.path("suprimido").asBoolean(false),
                    item.hasNonNull("ips") ? item.path("ips").decimalValue() : null,
                    item.hasNonNull("media") ? item.path("media").decimalValue() : null,
                    item.hasNonNull("motivo") ? item.path("motivo").asText() : null
                );
            }
        }
    }

    /**
     * Processa um tipo de recorte organizacional aplicando K-anonimato (N < 5).
     */
    private int processarRecorte(List<ObjectNode> respostas, String campo, ArrayNode arrayDestino) {
        Map<String, List<Double>> grupos = new LinkedHashMap<>();

        for (ObjectNode r : respostas) {
            String valor = r.hasNonNull(campo) && !r.path(campo).asText().isBlank()
                    ? r.path(campo).asText().trim()
                    : null;
            if (valor == null) continue;

            grupos.computeIfAbsent(valor, k -> new ArrayList<>());
            if (r.hasNonNull("media_risco")) {
                grupos.get(valor).add(r.path("media_risco").asDouble());
            }
        }

        int suprimidos = 0;
        for (Map.Entry<String, List<Double>> entry : grupos.entrySet()) {
            String nome = entry.getKey();
            List<Double> scores = entry.getValue();
            int n = scores.size();

            ObjectNode obj = arrayDestino.addObject();
            obj.put("nome", nome);
            obj.put("respondentes", n);

            if (n < 5) {
                obj.put("suprimido", true);
                obj.put("motivo", "K-anonimato (N < 5)");
                obj.putNull("ips");
                obj.putNull("media");
                suprimidos++;
            } else {
                obj.put("suprimido", false);
                double soma = scores.stream().mapToDouble(Double::doubleValue).sum();
                double media = BigDecimal.valueOf(soma / n).setScale(2, RoundingMode.HALF_UP).doubleValue();
                double ips = BigDecimal.valueOf(((media - 1.0) / 4.0) * 100.0).setScale(1, RoundingMode.HALF_UP).doubleValue();
                obj.put("ips", ips);
                obj.put("media", media);
            }
        }

        return suprimidos;
    }

    /**
     * Determina a probabilidade P1-P5 conforme os critérios da versão publicada da Matriz de Risco.
     */
    private ObjectNode determinarProbabilidade(List<ObjectNode> criterios, double indice0a100) {
        if (criterios.isEmpty()) {
            ObjectNode fallback = JsonNodeFactory.instance.objectNode();
            int n = indice0a100 >= 75 ? 5 : indice0a100 >= 50 ? 4 : indice0a100 >= 25 ? 3 : indice0a100 >= 10 ? 2 : 1;
            fallback.put("nivel", n);
            fallback.put("codigo", "P" + n);
            fallback.put("nome", "Probabilidade P" + n);
            return fallback;
        }

        for (ObjectNode c : criterios) {
            double min = c.hasNonNull("faixa_min") ? c.path("faixa_min").asDouble() : 0.0;
            double max = c.hasNonNull("faixa_max") ? c.path("faixa_max").asDouble() : 100.0;
            if (indice0a100 >= min && indice0a100 <= max) {
                return c;
            }
        }

        // Se estiver no limite máximo ou mínimo fora da faixa por arredondamento
        if (indice0a100 > 75.0) {
            return criterios.get(criterios.size() - 1);
        }
        return criterios.get(0);
    }

    /**
     * Localiza a severidade calibrada na matriz de risco para o fator/dimensão.
     */
    private Optional<ObjectNode> localizarFatorSeveridade(List<ObjectNode> fatores, String framework, String codigo, String nome) {
        // 1. Tentar por código exato no mesmo framework
        Optional<ObjectNode> opt = fatores.stream()
                .filter(f -> f.path("framework").asText().equalsIgnoreCase(framework) && f.path("codigo").asText().equalsIgnoreCase(codigo))
                .findFirst();
        if (opt.isPresent()) return opt;

        // 2. Tentar por código exato independente do framework
        opt = fatores.stream()
                .filter(f -> f.path("codigo").asText().equalsIgnoreCase(codigo))
                .findFirst();
        if (opt.isPresent()) return opt;

        // 3. Tentar por nome ou similaridade
        opt = fatores.stream()
                .filter(f -> f.path("nome").asText().equalsIgnoreCase(nome))
                .findFirst();
        return opt;
    }

    /**
     * Extrai a nota Likert 1-5 dada pelo respondente na dimensão.
     */
    private Double extrairValorDimensao(ObjectNode resposta, String codigo, String nome) {
        JsonNode porDim = resposta.get("por_dimensao");
        if (porDim != null && porDim.isObject()) {
            if (porDim.hasNonNull(codigo) && porDim.get(codigo).isNumber()) {
                return porDim.get(codigo).asDouble();
            }
            if (porDim.hasNonNull(nome) && porDim.get(nome).isNumber()) {
                return porDim.get(nome).asDouble();
            }
            // Case insensitive search
            var it = porDim.fields();
            while (it.hasNext()) {
                var entry = it.next();
                if (entry.getKey().equalsIgnoreCase(codigo) || entry.getKey().equalsIgnoreCase(nome)) {
                    if (entry.getValue().isNumber()) {
                        return entry.getValue().asDouble();
                    }
                }
            }
        }

        JsonNode itens = resposta.get("respostas_itens");
        if (itens != null && itens.isArray()) {
            double soma = 0;
            int count = 0;
            for (JsonNode itm : itens) {
                String dimRef = itm.hasNonNull("dimensaoId") ? itm.path("dimensaoId").asText() : "";
                String dimCod = itm.hasNonNull("dimensaoCodigo") ? itm.path("dimensaoCodigo").asText() : "";
                if (dimRef.equalsIgnoreCase(codigo) || dimCod.equalsIgnoreCase(codigo) || dimRef.equalsIgnoreCase(nome)) {
                    if (itm.hasNonNull("valor") && itm.get("valor").isNumber()) {
                        soma += itm.get("valor").asDouble();
                        count++;
                    }
                }
            }
            if (count > 0) {
                return soma / count;
            }
        }

        return null;
    }

    public List<ObjectNode> listarHistoricoCliente(String clienteId) {
        return banco.transacao(c -> banco.consultar(c,
            "select s.*, c.titulo as campanha_titulo " +
            "from campanha_scoring s " +
            "join campanhas c on c.id = s.campanha_id " +
            "where s.cliente_id = ? " +
            "order by s.created_at desc",
            clienteId
        ));
    }
}
