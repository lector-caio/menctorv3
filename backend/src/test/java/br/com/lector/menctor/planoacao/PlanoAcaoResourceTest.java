package br.com.lector.menctor.planoacao;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import br.com.lector.menctor.dados.Banco;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PlanoAcaoResourceTest {

    @Inject
    Banco banco;

    private String clienteA;
    private String clienteB;
    private String campanhaA;
    private String campanhaB;
    private String scoringId;
    private String acaoCriticaId;
    private String acaoAltaId;
    private List<Map<String, Object>> acoesIniciais;

    private void ensureSetup() {
        if (clienteA != null) return;

        clienteA = given()
                .contentType("application/json")
                .body("""
                        {
                          "name": "Metalúrgica Alvorada S/A",
                          "cnpj": "99.888.777/0001-66",
                          "status": "ativo"
                        }
                        """)
                .when()
                .post("/api/clientes")
                .then()
                .statusCode(201)
                .extract().path("id");

        given()
                .when()
                .get("/api/clientes/{cid}/matriz", clienteA)
                .then()
                .statusCode(200);

        banco.transacao(c -> {
            banco.executar(c, "update matriz_fatores_severidade set severidade = 1 where codigo = 'ISOLAMENTO'");
            return null;
        });

        clienteB = given()
                .contentType("application/json")
                .body("""
                        {
                          "name": "Serviços Digitais Beta Ltda",
                          "cnpj": "88.777.666/0001-55",
                          "status": "ativo"
                        }
                        """)
                .when()
                .post("/api/clientes")
                .then()
                .statusCode(201)
                .extract().path("id");

        given()
                .when()
                .get("/api/clientes/{cid}/matriz", clienteB)
                .then()
                .statusCode(200);

        campanhaA = given()
                .contentType("application/json")
                .body(String.format("""
                        {
                          "cliente_id": "%s",
                          "titulo": "Avaliação Psicossocial Q1 2026",
                          "instrumento": "mte",
                          "ciclo": "2026-Q1",
                          "quantidade_funcionarios": 60,
                          "status": "ativa"
                        }
                        """, clienteA))
                .when()
                .post("/api/campanhas")
                .then()
                .statusCode(201)
                .extract().path("id");

        campanhaB = given()
                .contentType("application/json")
                .body(String.format("""
                        {
                          "cliente_id": "%s",
                          "titulo": "Campanha Cliente B",
                          "instrumento": "mte",
                          "ciclo": "2026-Q1",
                          "status": "ativa"
                        }
                        """, clienteB))
                .when()
                .post("/api/campanhas")
                .then()
                .statusCode(201)
                .extract().path("id");
    }

    @Test
    @Order(1)
    void teste14_campanhaSemScoringRetorna422() {
        ensureSetup();
        given()
                .when()
                .post("/api/campanhas/{id}/plano-acao/gerar", campanhaA)
                .then()
                .statusCode(422)
                .body("error", containsString("Não existe um Scoring Psicossocial calculado para esta campanha"));

        given()
                .when()
                .get("/api/campanhas/{id}/plano-acao", campanhaA)
                .then()
                .statusCode(422)
                .body("error", containsString("Não existe um Scoring Psicossocial calculado para esta campanha"));
    }

    @Test
    @Order(2)
    void teste01_gerarPlanoComScoringValido() {
        for (int i = 1; i <= 6; i++) {
            given()
                    .contentType("application/json")
                    .body(String.format("""
                            {
                              "cpfHash": "hash-worker-%d",
                              "setor": "Produção",
                              "cargo": "Operador",
                              "mediaRisco": 3.5,
                              "porDimensao": {
                                "VIOLENCIA_TRAUMA": 4.6,
                                "SOBRECARGA": 3.9,
                                "RELACIONAMENTOS": 2.0,
                                "RECOMPENSAS": 1.5,
                                "ISOLAMENTO": 1.1
                              }
                            }
                            """, i))
                    .when()
                    .post("/api/campanhas/{id}/respostas", campanhaA)
                    .then()
                    .statusCode(201);
        }

        Response respScoring = given()
                .when()
                .get("/api/campanhas/{id}/scoring", campanhaA)
                .then()
                .statusCode(200)
                .extract().response();

        scoringId = respScoring.path("id");
        assertNotNull(scoringId);

        Response respGerar = given()
                .when()
                .post("/api/campanhas/{id}/plano-acao/gerar", campanhaA)
                .then()
                .statusCode(201)
                .body("campanhaId", equalTo(campanhaA))
                .body("scoringId", equalTo(scoringId))
                .body("clienteId", equalTo(clienteA))
                .body("status", equalTo("em_execucao"))
                .body("acoes", notNullValue())
                .extract().response();

        acoesIniciais = respGerar.path("acoes");
        assertTrue(acoesIniciais.size() >= 3, "Deveria ter gerado pelo menos 3 ações");
    }

    @Test
    @Order(3)
    void teste02_dimensaoCriticaGeraAcaoImediata() {
        Map<String, Object> acaoCritica = acoesIniciais.stream()
                .filter(a -> "VIOLENCIA_TRAUMA".equals(a.get("dimensaoCodigo")))
                .findFirst().orElseThrow(() -> new AssertionError("Ação VIOLENCIA_TRAUMA não encontrada"));

        assertEquals("critico", acaoCritica.get("nivelRisco").toString().toLowerCase());
        assertEquals("Imediata", acaoCritica.get("prioridade"));
        assertEquals("automatica", acaoCritica.get("origem"));
        assertEquals("pendente", acaoCritica.get("status"));
        assertNotNull(acaoCritica.get("score"));
        assertNotNull(acaoCritica.get("acao"));
        acaoCriticaId = acaoCritica.get("id").toString();
    }

    @Test
    @Order(4)
    void teste03_dimensaoAltaGeraAcaoCurtoPrazo() {
        Map<String, Object> acaoAlta = acoesIniciais.stream()
                .filter(a -> "SOBRECARGA".equals(a.get("dimensaoCodigo")))
                .findFirst().orElseThrow(() -> new AssertionError("Ação SOBRECARGA não encontrada"));

        assertEquals("alto", acaoAlta.get("nivelRisco").toString().toLowerCase());
        assertEquals("Curto prazo", acaoAlta.get("prioridade"));
        acaoAltaId = acaoAlta.get("id").toString();
    }

    @Test
    @Order(5)
    void teste04_dimensaoModeradaGeraAcaoAcompanhamentoMedioPrazo() {
        Map<String, Object> acaoModerada = acoesIniciais.stream()
                .filter(a -> "RELACIONAMENTOS".equals(a.get("dimensaoCodigo")))
                .findFirst().orElseThrow(() -> new AssertionError("Ação RELACIONAMENTOS não encontrada"));

        assertEquals("moderado", acaoModerada.get("nivelRisco").toString().toLowerCase());
        assertEquals("Médio prazo", acaoModerada.get("prioridade"));
    }

    @Test
    @Order(6)
    void teste05_dimensaoBaixoNaoGeraAcaoAutomatica() {
        boolean temRecompensas = acoesIniciais.stream().anyMatch(a -> "RECOMPENSAS".equals(a.get("dimensaoCodigo")));
        assertFalse(temRecompensas, "Dimensão Baixo (RECOMPENSAS) não deve gerar ação automática");
    }

    @Test
    @Order(7)
    void teste06_dimensaoInsignificanteNaoGeraAcaoAutomatica() {
        boolean temIsolamento = acoesIniciais.stream().anyMatch(a -> "ISOLAMENTO".equals(a.get("dimensaoCodigo")));
        assertFalse(temIsolamento, "Dimensão Insignificante (ISOLAMENTO) não deve gerar ação automática");
    }

    @Test
    @Order(8)
    void teste07_idempotenciaGerarNaoDuplicaAcoes() {
        Response respSegunda = given()
                .when()
                .post("/api/campanhas/{id}/plano-acao/gerar", campanhaA)
                .then()
                .statusCode(201)
                .extract().response();

        List<Map<String, Object>> acoesSegunda = respSegunda.path("acoes");
        assertEquals(acoesIniciais.size(), acoesSegunda.size(), "Idempotência violada: número de ações divergiu");
    }

    @Test
    @Order(9)
    void teste08_edicaoOperacionalPersisteCamposPermitidos() {
        given()
                .contentType("application/json")
                .body("""
                        {
                          "responsavel": "Dra. Carolina Mendes (Médica do Trabalho)",
                          "prazoDias": 45,
                          "dataPrevista": "2026-05-15",
                          "indicador": "Taxa de incidentes críticos acolhidos em até 24h",
                          "meta": "100% de acolhimento imediato",
                          "observacoes": "Treinamento das lideranças agendado para o início de abril."
                        }
                        """)
                .when()
                .put("/api/campanhas/{id}/plano-acao/acoes/{acaoId}", campanhaA, acaoCriticaId)
                .then()
                .statusCode(200)
                .body("responsavel", equalTo("Dra. Carolina Mendes (Médica do Trabalho)"))
                .body("prazoDias", equalTo(45))
                .body("dataPrevista", equalTo("2026-05-15"))
                .body("indicador", equalTo("Taxa de incidentes críticos acolhidos em até 24h"))
                .body("meta", equalTo("100% de acolhimento imediato"))
                .body("observacoes", equalTo("Treinamento das lideranças agendado para o início de abril."));
    }

    @Test
    @Order(10)
    void teste09_protecaoDoDiagnosticoRejeitaAlteracaoDeRiscoEScore() {
        given()
                .contentType("application/json")
                .body("""
                        {
                          "nivelRisco": "baixo",
                          "score": 2
                        }
                        """)
                .when()
                .put("/api/campanhas/{id}/plano-acao/acoes/{acaoId}", campanhaA, acaoCriticaId)
                .then()
                .statusCode(400)
                .body("error", containsString("Não é permitido alterar dados originais do diagnóstico"));

        given()
                .contentType("application/json")
                .body("""
                        {
                          "probabilidade": "P1",
                          "severidade": "S1"
                        }
                        """)
                .when()
                .put("/api/campanhas/{id}/plano-acao/acoes/{acaoId}", campanhaA, acaoCriticaId)
                .then()
                .statusCode(400)
                .body("error", containsString("Não é permitido alterar dados originais do diagnóstico"));
    }

    @Test
    @Order(11)
    void teste10_transicaoDeStatusPendenteParaEmAndamentoEConcluida() {
        given()
                .contentType("application/json")
                .body("""
                        {
                          "status": "em_andamento",
                          "motivo": "Início da implantação do protocolo",
                          "usuario": "auditor_sesmt"
                        }
                        """)
                .when()
                .patch("/api/campanhas/{id}/plano-acao/acoes/{acaoId}/status", campanhaA, acaoCriticaId)
                .then()
                .statusCode(200)
                .body("status", equalTo("em_andamento"));

        given()
                .contentType("application/json")
                .body("""
                        {
                          "status": "concluida",
                          "motivo": "Protocolo homologado pela CIPA e SESMT",
                          "usuario": "auditor_sesmt"
                        }
                        """)
                .when()
                .patch("/api/campanhas/{id}/plano-acao/acoes/{acaoId}/status", campanhaA, acaoCriticaId)
                .then()
                .statusCode(200)
                .body("status", equalTo("concluida"));
    }

    @Test
    @Order(12)
    void teste11_cancelamentoLogicoParaAcaoEmAndamento() {
        given()
                .contentType("application/json")
                .body("{\"status\": \"em_andamento\"}")
                .when()
                .patch("/api/campanhas/{id}/plano-acao/acoes/{acaoId}/status", campanhaA, acaoAltaId)
                .then()
                .statusCode(200);

        given()
                .queryParam("motivo", "Revisão estratégica da área")
                .when()
                .delete("/api/campanhas/{id}/plano-acao/acoes/{acaoId}", campanhaA, acaoAltaId)
                .then()
                .statusCode(204);

        given()
                .when()
                .get("/api/campanhas/{id}/plano-acao", campanhaA)
                .then()
                .statusCode(200)
                .body("acoes.find { it.id == '" + acaoAltaId + "' }.status", equalTo("cancelada"));
    }

    @Test
    @Order(13)
    void teste12_kAnonimatoPreservadoNoPlanoDeAcao() {
        Response respConsulta = given()
                .when()
                .get("/api/campanhas/{id}/plano-acao", campanhaA)
                .then()
                .statusCode(200)
                .extract().response();

        List<Map<String, Object>> todasAcoes = respConsulta.path("acoes");
        for (Map<String, Object> a : todasAcoes) {
            assertFalse(a.containsKey("cpfHash"), "Plano de ação não deve expor CPF hash");
            assertFalse(a.containsKey("respondente"), "Plano de ação não deve expor respondente individual");
            assertNotNull(a.get("dimensaoCodigo"), "Ação deve estar atrelada à dimensão");
        }
    }

    @Test
    @Order(14)
    void teste13_multiTenantIsolamentoEntreClientesRetorna404() {
        given()
                .header("X-Cliente-Id", clienteB)
                .when()
                .get("/api/campanhas/{id}/plano-acao", campanhaA)
                .then()
                .statusCode(404);

        given()
                .queryParam("clienteId", clienteB)
                .when()
                .get("/api/campanhas/{id}/plano-acao", campanhaA)
                .then()
                .statusCode(404);

        given()
                .contentType("application/json")
                .body("{\"responsavel\": \"Tentativa Externa\"}")
                .when()
                .put("/api/campanhas/{id}/plano-acao/acoes/{acaoId}", campanhaB, acaoCriticaId)
                .then()
                .statusCode(404);
    }

    @Test
    @Order(15)
    void teste15_historicoAlteracoesPersistidoEmScoringAcoesHistorico() {
        Response respHist = given()
                .when()
                .get("/api/campanhas/{id}/plano-acao/acoes/{acaoId}/historico", campanhaA, acaoCriticaId)
                .then()
                .statusCode(200)
                .extract().response();

        List<Map<String, Object>> historicoList = respHist.jsonPath().getList("$");
        assertTrue(historicoList.size() >= 3, "Histórico deve conter as edições e alterações de status");

        boolean temTransicaoConcluida = historicoList.stream().anyMatch(h ->
                "concluida".equals(h.get("status_novo")) || "concluida".equals(h.get("valor_novo")));
        assertTrue(temTransicaoConcluida, "Histórico deve registrar transição para 'concluida'");
    }

    @Test
    @Order(16)
    void teste16_resumoConsolidadoDoPlanoDeAcao() {
        given()
                .when()
                .get("/api/campanhas/{id}/plano-acao/resumo", campanhaA)
                .then()
                .statusCode(200)
                .body("totalAcoes", greaterThanOrEqualTo(3))
                .body("concluidas", equalTo(1))
                .body("canceladas", equalTo(1))
                .body("percentualConclusao", notNullValue());
    }

    @Test
    @Order(17)
    void teste17_criacaoManualDeAcaoPermiteQualquerDimensao() {
        given()
                .contentType("application/json")
                .body("""
                        {
                          "dimensaoCodigo": "SUPORTE",
                          "dimensaoNome": "Suporte da liderança e colegas",
                          "nivelRisco": "baixo",
                          "acao": "Oferecer mentoria semanal de boas-vindas para novos membros da equipe",
                          "responsavel": "RH Corporativo",
                          "prazoDias": 30
                        }
                        """)
                .when()
                .post("/api/campanhas/{id}/plano-acao/acoes", campanhaA)
                .then()
                .statusCode(201)
                .body("dimensaoCodigo", equalTo("SUPORTE"))
                .body("origem", equalTo("manual"))
                .body("status", equalTo("pendente"));
    }

    @Test
    @Order(18)
    void teste18_recalculoDeScoringPreservaPlanoHistorico() {
        given()
                .when()
                .post("/api/campanhas/{id}/scoring/recalcular", campanhaA)
                .then()
                .statusCode(200);

        given()
                .when()
                .get("/api/campanhas/{id}/plano-acao", campanhaA)
                .then()
                .statusCode(200)
                .body("acoes.find { it.id == '" + acaoCriticaId + "' }.status", equalTo("concluida"))
                .body("acoes.find { it.id == '" + acaoAltaId + "' }.status", equalTo("cancelada"));
    }
}
