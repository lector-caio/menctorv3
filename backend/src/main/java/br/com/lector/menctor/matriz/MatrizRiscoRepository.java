package br.com.lector.menctor.matriz;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import br.com.lector.menctor.api.ApiException;
import br.com.lector.menctor.api.InvalidRequestException;
import br.com.lector.menctor.dados.Banco;

@ApplicationScoped
public class MatrizRiscoRepository {

    @Inject
    Banco banco;

    @Inject
    ObjectMapper mapper;

    public void validarCliente(String clienteId) {
        boolean existe = banco.transacao(c -> 
            !banco.consultar(c, "select 1 from clients where id = ?", clienteId).isEmpty()
        );
        if (!existe) {
            throw ApiException.naoEncontrado("Cliente não encontrado");
        }
    }

    public ObjectNode obterOuCriarMatriz(String clienteId) {
        validarCliente(clienteId);

        Optional<ObjectNode> existente = banco.transacao(c -> 
            banco.consultarUm(c, "select * from matrizes_risco where cliente_id = ?", clienteId)
        );

        if (existente.isPresent()) {
            return existente.get();
        }

        String matrizId = UUID.randomUUID().toString();
        String versaoId = UUID.randomUUID().toString();

        banco.transacao(c -> {
            banco.executar(c,
                "insert into matrizes_risco (id, cliente_id, nome, descricao) values (?, ?, ?, ?)",
                matrizId, clienteId, "Matriz de Risco (PGR)", "Matriz 5x5 de riscos psicossociais e NR-01."
            );

            banco.executar(c,
                "insert into matriz_versoes (id, matriz_id, cliente_id, versao, status, framework, criterios_pgr, publicada_em) " +
                "values (?, ?, ?, 'v1.0', 'publicada', 'copsoq', ?, now())",
                versaoId, matrizId, clienteId,
                "Matriz 5×5 padrão NR-01 / GRO. Cruzamento da Probabilidade de ocorrência (P1 a P5) apurada no diagnóstico com a Severidade do dano potencial (S1 a S5)."
            );

            banco.consultar(c, "select popular_configuracao_padrao_matriz(?)", versaoId);
            return null;
        });

        return banco.transacao(c -> 
            banco.consultarUm(c, "select * from matrizes_risco where id = ?", matrizId)
        ).orElseThrow();
    }

    public List<ObjectNode> listarVersoes(String clienteId) {
        validarCliente(clienteId);
        obterOuCriarMatriz(clienteId);

        return banco.transacao(c -> banco.consultar(c,
            "select v.*, " +
            "(select count(*) from campanhas where cliente_id = v.cliente_id) as campanhas " +
            "from matriz_versoes v " +
            "where v.cliente_id = ? order by v.created_at desc",
            clienteId
        ));
    }

    public ObjectNode obterVersao(String clienteId, String versaoIdOuCodigo) {
        validarCliente(clienteId);

        return banco.transacao(c -> banco.consultarUm(c,
            "select * from matriz_versoes where cliente_id = ? and (id = ? or versao = ?)",
            clienteId, versaoIdOuCodigo, versaoIdOuCodigo
        )).orElseThrow(() -> ApiException.naoEncontrado("Versão da matriz não encontrada para este cliente"));
    }

    public ObjectNode obterVersaoPublicada(String clienteId) {
        validarCliente(clienteId);
        obterOuCriarMatriz(clienteId);

        return banco.transacao(c -> banco.consultarUm(c,
            "select * from matriz_versoes where cliente_id = ? and status = 'publicada' " +
            "order by publicada_em desc nulls last, created_at desc limit 1",
            clienteId
        )).orElseThrow(() -> ApiException.naoEncontrado("Nenhuma versão publicada encontrada para este cliente"));
    }

    public ObjectNode obterVersaoDetalhada(String clienteId, String versaoIdOuCodigo) {
        ObjectNode versao = obterVersao(clienteId, versaoIdOuCodigo);
        String versaoId = versao.path("id").asText();

        return banco.transacao(c -> {
            List<ObjectNode> prob = banco.consultar(c,
                "select * from matriz_criterios_probabilidade where versao_id = ? order by nivel asc", versaoId);
            List<ObjectNode> sev = banco.consultar(c,
                "select * from matriz_criterios_severidade where versao_id = ? order by nivel asc", versaoId);
            List<ObjectNode> grid = banco.consultar(c,
                "select * from matriz_classificacoes_ps where versao_id = ? order by probabilidade asc, severidade asc", versaoId);
            List<ObjectNode> fatores = banco.consultar(c,
                "select * from matriz_fatores_severidade where versao_id = ? order by framework asc, nome asc", versaoId);

            versao.set("criteriosProbabilidade", mapper.valueToTree(prob));
            versao.set("criteriosSeveridade", mapper.valueToTree(sev));
            versao.set("classificacoesPs", mapper.valueToTree(grid));
            versao.set("fatoresSeveridade", mapper.valueToTree(fatores));

            return versao;
        });
    }

    public ObjectNode criarVersao(String clienteId, JsonNode dados) {
        validarCliente(clienteId);
        ObjectNode matriz = obterOuCriarMatriz(clienteId);
        String matrizId = matriz.path("id").asText();

        String versaoId = UUID.randomUUID().toString();
        String codigoVersao = dados != null && dados.hasNonNull("versao") ? dados.path("versao").asText().trim() : null;

        if (codigoVersao == null || codigoVersao.isEmpty()) {
            Integer total = banco.transacao(c -> 
                banco.consultar(c, "select count(*) as total from matriz_versoes where matriz_id = ?", matrizId).get(0).path("total").asInt()
            );
            codigoVersao = "v" + (total + 1) + ".0";
        }

        String framework = dados != null && dados.hasNonNull("framework") ? dados.path("framework").asText() : "copsoq";
        String criteriosPgr = dados != null && dados.hasNonNull("criteriosPgr") ? dados.path("criteriosPgr").asText() :
            "Matriz 5×5 padrão NR-01 / GRO. Cruzamento de Probabilidade (P1 a P5) com Severidade (S1 a S5).";

        String versaoOrigemId = dados != null && dados.hasNonNull("versaoOrigemId") ? dados.path("versaoOrigemId").asText() : null;

        final String codVer = codigoVersao;
        final String critPgr = criteriosPgr;
        final String framew = framework;

        banco.transacao(c -> {
            banco.executar(c,
                "insert into matriz_versoes (id, matriz_id, cliente_id, versao, status, framework, criterios_pgr) " +
                "values (?, ?, ?, ?, 'rascunho', ?, ?)",
                versaoId, matrizId, clienteId, codVer, framew, critPgr
            );

            if (versaoOrigemId != null && !versaoOrigemId.isBlank()) {
                // Copia critérios e fatores da versão de origem
                banco.executar(c,
                    "insert into matriz_criterios_probabilidade (versao_id, nivel, codigo, nome, descricao, faixa_min, faixa_max) " +
                    "select ?, nivel, codigo, nome, descricao, faixa_min, faixa_max from matriz_criterios_probabilidade where versao_id = ?",
                    versaoId, versaoOrigemId
                );
                banco.executar(c,
                    "insert into matriz_criterios_severidade (versao_id, nivel, codigo, nome, descricao, cor) " +
                    "select ?, nivel, codigo, nome, descricao, cor from matriz_criterios_severidade where versao_id = ?",
                    versaoId, versaoOrigemId
                );
                banco.executar(c,
                    "insert into matriz_classificacoes_ps (versao_id, probabilidade, severidade, score, nivel_risco, prioridade, cor) " +
                    "select ?, probabilidade, severidade, score, nivel_risco, prioridade, cor from matriz_classificacoes_ps where versao_id = ?",
                    versaoId, versaoOrigemId
                );
                banco.executar(c,
                    "insert into matriz_fatores_severidade (versao_id, framework, codigo, nome, severidade, justificativa, sugestao) " +
                    "select ?, framework, codigo, nome, severidade, justificativa, sugestao from matriz_fatores_severidade where versao_id = ?",
                    versaoId, versaoOrigemId
                );
            } else {
                banco.consultar(c, "select popular_configuracao_padrao_matriz(?)", versaoId);
            }
            return null;
        });

        return obterVersao(clienteId, versaoId);
    }

    public ObjectNode atualizarVersao(String clienteId, String versaoIdOuCodigo, JsonNode dados) {
        ObjectNode versao = obterVersao(clienteId, versaoIdOuCodigo);
        String versaoId = versao.path("id").asText();
        String status = versao.path("status").asText();

        // Regra fundamental: Versão publicada NÃO pode ser alterada!
        if ("publicada".equalsIgnoreCase(status)) {
            throw new InvalidRequestException("Uma versão publicada não pode ser alterada. Crie uma nova versão para realizar modificações.");
        }

        banco.transacao(c -> {
            if (dados.hasNonNull("framework")) {
                banco.executar(c, "update matriz_versoes set framework = ? where id = ?", dados.path("framework").asText(), versaoId);
            }
            if (dados.hasNonNull("criteriosPgr")) {
                banco.executar(c, "update matriz_versoes set criterios_pgr = ? where id = ?", dados.path("criteriosPgr").asText(), versaoId);
            }

            // Atualização de fatores calibrados
            if (dados.has("fatores") && dados.path("fatores").isArray()) {
                for (JsonNode fat : dados.path("fatores")) {
                    String framework = fat.path("framework").asText("copsoq");
                    String codigo = fat.path("codigo").asText();
                    if (codigo != null && !codigo.isBlank()) {
                        Integer severidade = fat.hasNonNull("severidade") ? fat.path("severidade").asInt() : null;
                        String justificativa = fat.hasNonNull("justificativa") ? fat.path("justificativa").asText() : null;

                        if (severidade != null && justificativa != null) {
                            banco.executar(c,
                                "update matriz_fatores_severidade set severidade = ?, justificativa = ? where versao_id = ? and framework = ? and codigo = ?",
                                severidade, justificativa, versaoId, framework, codigo
                            );
                        } else if (severidade != null) {
                            banco.executar(c,
                                "update matriz_fatores_severidade set severidade = ? where versao_id = ? and framework = ? and codigo = ?",
                                severidade, versaoId, framework, codigo
                            );
                        } else if (justificativa != null) {
                            banco.executar(c,
                                "update matriz_fatores_severidade set justificativa = ? where versao_id = ? and framework = ? and codigo = ?",
                                justificativa, versaoId, framework, codigo
                            );
                        }
                    }
                }
            }

            return null;
        });

        return obterVersaoDetalhada(clienteId, versaoId);
    }

    public ObjectNode publicarVersao(String clienteId, String versaoIdOuCodigo) {
        ObjectNode versao = obterVersao(clienteId, versaoIdOuCodigo);
        String versaoId = versao.path("id").asText();

        banco.transacao(c -> {
            banco.executar(c,
                "update matriz_versoes set status = 'publicada', publicada_em = now() where id = ?",
                versaoId
            );
            return null;
        });

        return obterVersao(clienteId, versaoId);
    }

    public ObjectNode calcularClassificacao(String clienteId, double probabilidade, double severidade) {
        ObjectNode versao = obterVersaoPublicada(clienteId);
        String versaoId = versao.path("id").asText();

        int p = (int) Math.max(1, Math.min(5, Math.round(probabilidade)));
        int s = (int) Math.max(1, Math.min(5, Math.round(severidade)));

        return banco.transacao(c -> banco.consultarUm(c,
            "select probabilidade, severidade, score, nivel_risco as \"nivelRisco\", prioridade, cor " +
            "from matriz_classificacoes_ps where versao_id = ? and probabilidade = ? and severidade = ?",
            versaoId, p, s
        )).orElseGet(() -> {
            ObjectNode padrao = mapper.createObjectNode();
            padrao.put("probabilidade", p);
            padrao.put("severidade", s);
            padrao.put("score", p * s);
            padrao.put("nivelRisco", "moderado");
            padrao.put("prioridade", "Médio prazo");
            padrao.put("cor", "#facc15");
            return padrao;
        });
    }
}
