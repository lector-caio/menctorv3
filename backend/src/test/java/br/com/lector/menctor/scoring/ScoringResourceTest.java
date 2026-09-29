package br.com.lector.menctor.scoring;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;

@QuarkusTest
class ScoringResourceTest {

    @Test
    void cenariosCompletosDoMotorDeScoringPsicossocial() {
        // =========================================================================
        // Setup: Criar Cliente A e obter sua Matriz Publicada (v1.0 padrão)
        // =========================================================================
        String clienteA = given()
                .contentType("application/json")
                .body("""
                        {
                          "name": "Loghaus Transportes S/A",
                          "cnpj": "11.222.333/0001-44",
                          "status": "ativo"
                        }
                        """)
                .when()
                .post("/api/clientes")
                .then()
                .statusCode(201)
                .extract().path("id");

        // Inicializar matriz de risco para o Cliente A (v1.0 publicada)
        given()
                .when()
                .get("/api/clientes/{cid}/matriz", clienteA)
                .then()
                .statusCode(200)
                .body("versaoPublicada.status", equalTo("publicada"));

        // =========================================================================
        // Setup: Criar Cliente B
        // =========================================================================
        String clienteB = given()
                .contentType("application/json")
                .body("""
                        {
                          "name": "TechCorp Inovações",
                          "cnpj": "44.555.666/0001-77",
                          "status": "ativo"
                        }
                        """)
                .when()
                .post("/api/clientes")
                .then()
                .statusCode(201)
                .extract().path("id");

        // =========================================================================
        // CENÁRIO 8 — Ausência de Matriz Publicada
        // =========================================================================
        // Criar campanha para Cliente B sem ter gerado matriz publicada para ele
        String campanhaSemMatriz = given()
                .contentType("application/json")
                .body(String.format("""
                        {
                          "cliente_id": "%s",
                          "titulo": "Pesquisa Diagnóstica Preliminar",
                          "instrumento": "mte",
                          "ciclo": "2026-Q1",
                          "quantidade_funcionarios": 50,
                          "status": "ativa"
                        }
                        """, clienteB))
                .when()
                .post("/api/campanhas")
                .then()
                .statusCode(201)
                .extract().path("id");

        // Executar scoring sem matriz publicada deve falhar com HTTP 422
        given()
                .when()
                .get("/api/campanhas/{id}/scoring", campanhaSemMatriz)
                .then()
                .statusCode(422)
                .body("error", containsString("O cliente não possui uma versão publicada da Matriz de Risco"));

        // =========================================================================
        // CENÁRIO 1 & 2 — Scoring Básico e Integração com Matriz Publicada
        // =========================================================================
        // Criar campanha Ciclo 1 para Cliente A
        String campanhaQ1 = given()
                .contentType("application/json")
                .body(String.format("""
                        {
                          "cliente_id": "%s",
                          "titulo": "Avaliação de Riscos Psicossociais 2026-Q1",
                          "instrumento": "mte",
                          "ciclo": "2026-Q1",
                          "quantidade_funcionarios": 100,
                          "status": "ativa"
                        }
                        """, clienteA))
                .when()
                .post("/api/campanhas")
                .then()
                .statusCode(201)
                .extract().path("id");

        // Inserir 5 respostas no setor Operações (notas críticas para Violência e Assédio)
        for (int i = 1; i <= 5; i++) {
            given()
                    .contentType("application/json")
                    .body(String.format("""
                            {
                              "cpfHash": "hash-op-%d",
                              "setor": "Operações",
                              "cargo": "Operador",
                              "mediaRisco": 3.8,
                              "porDimensao": {
                                "VIOLENCIA_TRAUMA": 4.5,
                                "ASSEDIO": 4.2,
                                "SOBRECARGA": 3.6,
                                "RELACIONAMENTOS": 3.0,
                                "SUPORTE": 2.0,
                                "ISOLAMENTO": 1.5
                              }
                            }
                            """, i))
                    .when()
                    .post("/api/campanhas/{id}/respostas", campanhaQ1)
                    .then()
                    .statusCode(201);
        }

        // =========================================================================
        // CENÁRIO 4 — K-Anonimato (N = 4 vs N = 5)
        // =========================================================================
        // Inserir 4 respostas no setor Diretoria (N = 4 < 5 -> deve ser suprimido)
        for (int i = 1; i <= 4; i++) {
            given()
                    .contentType("application/json")
                    .body(String.format("""
                            {
                              "cpfHash": "hash-dir-%d",
                              "setor": "Diretoria",
                              "cargo": "Diretor",
                              "mediaRisco": 2.2,
                              "porDimensao": {
                                "VIOLENCIA_TRAUMA": 1.2,
                                "ASSEDIO": 1.0,
                                "SOBRECARGA": 2.5
                              }
                            }
                            """, i))
                    .when()
                    .post("/api/campanhas/{id}/respostas", campanhaQ1)
                    .then()
                    .statusCode(201);
        }

        // Consultar Scoring Consolidado
        Response respScoring = given()
                .when()
                .get("/api/campanhas/{id}/scoring", campanhaQ1)
                .then()
                .statusCode(200)
                .body("campanhaId", equalTo(campanhaQ1))
                .body("clienteId", equalTo(clienteA))
                .body("instrumento", equalTo("mte"))
                .body("ciclo", equalTo("2026-Q1"))
                .body("respondentes", equalTo(9))
                .body("confiabilidade", equalTo("Baixa")) // N = 9 (< 50)
                .body("recortesSuprimidos", greaterThanOrEqualTo(1))
                .body("matrizVersaoCodigo", equalTo("v1.0"))
                .extract().response();

        // Validar K-anonimato nos recortes
        // Setor Diretoria: N = 4 -> suprimido = true, ips = null, media = null
        respScoring.then()
                .body("recortes.setores.find { it.nome == 'Diretoria' }.suprimido", equalTo(true))
                .body("recortes.setores.find { it.nome == 'Diretoria' }.motivo", equalTo("K-anonimato (N < 5)"))
                .body("recortes.setores.find { it.nome == 'Diretoria' }.ips", nullValue())
                .body("recortes.setores.find { it.nome == 'Diretoria' }.media", nullValue());

        // Setor Operações: N = 5 -> suprimido = false, ips presente, media presente
        respScoring.then()
                .body("recortes.setores.find { it.nome == 'Operações' }.suprimido", equalTo(false))
                .body("recortes.setores.find { it.nome == 'Operações' }.ips", notNullValue())
                .body("recortes.setores.find { it.nome == 'Operações' }.media", notNullValue());

        // Validar Dimensão VIOLENCIA_TRAUMA
        // Média de 5 respostas com 4.5 e 4 com 1.2 = (5*4.5 + 4*1.2)/9 = (22.5 + 4.8)/9 = 27.3/9 = 3.03
        // Normalização: ((3.03 - 1) / 4) * 100 = 50.8% -> Faixa P4 na matriz padrão
        // Severidade calibrada no baseline MTE: S5 (Catastrófico)
        // Grade 5x5: P4 x S5 = Score 20 (Crítico)
        respScoring.then()
                .body("dimensoes.find { it.codigo == 'VIOLENCIA_TRAUMA' }.severidade", equalTo(5))
                .body("dimensoes.find { it.codigo == 'VIOLENCIA_TRAUMA' }.severidadeCodigo", equalTo("S5"))
                .body("dimensoes.find { it.codigo == 'VIOLENCIA_TRAUMA' }.severidadeConfigurada", equalTo(true))
                .body("dimensoes.find { it.codigo == 'VIOLENCIA_TRAUMA' }.nivelRisco", equalTo("critico"))
                .body("dimensoes.find { it.codigo == 'VIOLENCIA_TRAUMA' }.prioridade", equalTo("Imediata"));

        // Validar Prevalência: 5 de 9 respostas tinham nota >= 3.0 -> Prevalência = 55.6%
        Float prevViolencia = respScoring.path("dimensoes.find { it.codigo == 'VIOLENCIA_TRAUMA' }.prevalencia");
        assertNotNull(prevViolencia);
        assertTrue(prevViolencia >= 50.0f);

        // =========================================================================
        // CENÁRIO 7 — Ausência de Severidade para Fator Customizado
        // =========================================================================
        // Inserir resposta com fator sem calibração de severidade
        given()
                .contentType("application/json")
                .body("""
                        {
                          "cpfHash": "hash-custom-1",
                          "setor": "Operações",
                          "mediaRisco": 3.0,
                          "porDimensao": {
                            "FATOR_DESCONHECIDO_XYZ": 4.0
                          }
                        }
                        """)
                .when()
                .post("/api/campanhas/{id}/respostas", campanhaQ1)
                .then()
                .statusCode(201);

        // Recalcular Scoring via endpoint POST
        given()
                .when()
                .post("/api/campanhas/{id}/scoring/recalcular", campanhaQ1)
                .then()
                .statusCode(200);

        // Consultar dimensões isoladas
        given()
                .when()
                .get("/api/campanhas/{id}/scoring/dimensoes", campanhaQ1)
                .then()
                .statusCode(200)
                .body("size()", greaterThan(0));

        // =========================================================================
        // CENÁRIO 6 — Histórico por Ciclo (Preservação de Ciclos Separados)
        // =========================================================================
        // Criar segunda campanha para o Ciclo 2026-Q2 do mesmo Cliente A
        String campanhaQ2 = given()
                .contentType("application/json")
                .body(String.format("""
                        {
                          "cliente_id": "%s",
                          "titulo": "Avaliação de Riscos Psicossociais 2026-Q2",
                          "instrumento": "mte",
                          "ciclo": "2026-Q2",
                          "quantidade_funcionarios": 100,
                          "status": "ativa"
                        }
                        """, clienteA))
                .when()
                .post("/api/campanhas")
                .then()
                .statusCode(201)
                .extract().path("id");

        // Registrar respostas para campanha Q2 (melhoria nos fatores)
        for (int i = 1; i <= 6; i++) {
            given()
                    .contentType("application/json")
                    .body(String.format("""
                            {
                              "cpfHash": "hash-q2-%d",
                              "setor": "Operações",
                              "mediaRisco": 1.5,
                              "porDimensao": {
                                "VIOLENCIA_TRAUMA": 1.2,
                                "ASSEDIO": 1.1,
                                "SOBRECARGA": 2.0
                              }
                            }
                            """, i))
                    .when()
                    .post("/api/campanhas/{id}/respostas", campanhaQ2)
                    .then()
                    .statusCode(201);
        }

        // Executar scoring para a campanha Q2
        Response respQ2 = given()
                .when()
                .get("/api/campanhas/{id}/scoring", campanhaQ2)
                .then()
                .statusCode(200)
                .body("ciclo", equalTo("2026-Q2"))
                .body("respondentes", equalTo(6))
                .extract().response();

        // Consultar histórico por cliente
        given()
                .when()
                .get("/api/clientes/{cid}/scoring/historico", clienteA)
                .then()
                .statusCode(200)
                .body("size()", equalTo(2))
                .body("ciclo", hasItem("2026-Q1"))
                .body("ciclo", hasItem("2026-Q2"));

        // =========================================================================
        // CENÁRIO 3 — Isolamento entre Clientes
        // =========================================================================
        // Inicializar matriz do Cliente B e verificar que o histórico do Cliente B não contém as campanhas do Cliente A
        given()
                .when()
                .get("/api/clientes/{cid}/matriz", clienteB)
                .then()
                .statusCode(200);

        given()
                .when()
                .get("/api/clientes/{cid}/scoring/historico", clienteB)
                .then()
                .statusCode(200)
                .body("size()", equalTo(0)); // Nenhuma campanha do Cliente A vaza para o Cliente B
    }
}
