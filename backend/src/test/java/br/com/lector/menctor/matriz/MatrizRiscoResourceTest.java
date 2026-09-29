package br.com.lector.menctor.matriz;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class MatrizRiscoResourceTest {

    @Test
    void cicloDeVidaCompletoDaMatrizDeRiscoEVersionamento() {
        // 1. Criar Cliente A
        String clienteA = given()
                .contentType("application/json")
                .body("""
                        {
                          "name": "Metalúrgica Vulcano",
                          "cnpj": "55.666.777/0001-88",
                          "status": "ativo"
                        }
                        """)
                .when()
                .post("/api/clientes")
                .then()
                .statusCode(201)
                .extract().path("id");

        // 2. Criar Cliente B
        String clienteB = given()
                .contentType("application/json")
                .body("""
                        {
                          "name": "Hospital Santa Cruz",
                          "cnpj": "99.888.777/0001-66",
                          "status": "ativo"
                        }
                        """)
                .when()
                .post("/api/clientes")
                .then()
                .statusCode(201)
                .extract().path("id");

        // 1. Obter ou criar matriz do Cliente A
        given()
                .when()
                .get("/api/clientes/{cid}/matriz", clienteA)
                .then()
                .statusCode(200)
                .body("id", notNullValue())
                .body("cliente_id", equalTo(clienteA))
                .body("versaoPublicada.versao", equalTo("v1.0"))
                .body("versaoPublicada.status", equalTo("publicada"));

        // 2. Criar nova versão (v2.0 em rascunho) para Cliente A
        String versao2Id = given()
                .contentType("application/json")
                .body("""
                        {
                          "versao": "v2.0",
                          "framework": "copsoq",
                          "criteriosPgr": "Calibração para Grau de Risco 4 com ênfase em sobrecarga física e mental."
                        }
                        """)
                .when()
                .post("/api/clientes/{cid}/matriz/versoes", clienteA)
                .then()
                .statusCode(201)
                .body("versao", equalTo("v2.0"))
                .body("status", equalTo("rascunho"))
                .extract().path("id");

        // 3. Consultar versão v2.0 com detalhes (critérios P1-P5, S1-S5, Grade 5x5 e fatores)
        given()
                .when()
                .get("/api/clientes/{cid}/matriz/versoes/{vid}", clienteA, versao2Id)
                .then()
                .statusCode(200)
                .body("versao", equalTo("v2.0"))
                // 4. Critérios P1-P5
                .body("criteriosProbabilidade", hasSize(5))
                .body("criteriosProbabilidade.codigo", hasItem("P1"))
                .body("criteriosProbabilidade.codigo", hasItem("P5"))
                // 5. Critérios S1-S5
                .body("criteriosSeveridade", hasSize(5))
                .body("criteriosSeveridade.codigo", hasItem("S1"))
                .body("criteriosSeveridade.codigo", hasItem("S5"))
                // 6. Matriz 5x5 (25 classificações)
                .body("classificacoesPs", hasSize(25))
                // Fatores de severidade
                .body("fatoresSeveridade", hasSize(greaterThanOrEqualTo(10)));

        // 7. Atualizar versão em rascunho (calibrar fator de severidade)
        given()
                .contentType("application/json")
                .body("""
                        {
                          "criteriosPgr": "Critérios revisados e atualizados pelo SESMT.",
                          "fatores": [
                            {
                              "framework": "copsoq",
                              "codigo": "SOBRECARGA",
                              "severidade": 5,
                              "justificativa": "Evidência de casos de sobrecarga aguda em plantões noturnos."
                            }
                          ]
                        }
                        """)
                .when()
                .put("/api/clientes/{cid}/matriz/versoes/{vid}", clienteA, versao2Id)
                .then()
                .statusCode(200)
                .body("criterios_pgr", equalTo("Critérios revisados e atualizados pelo SESMT."));

        // 8. Publicar versão v2.0
        given()
                .when()
                .post("/api/clientes/{cid}/matriz/versoes/{vid}/publicar", clienteA, versao2Id)
                .then()
                .statusCode(200)
                .body("status", equalTo("publicada"))
                .body("publicada_em", notNullValue());

        // 9. Tentativa de alterar versão publicada DEVE SER REJEITADA com 400
        given()
                .contentType("application/json")
                .body("""
                        {
                          "criteriosPgr": "Tentativa de alteração não permitida em versão já publicada."
                        }
                        """)
                .when()
                .put("/api/clientes/{cid}/matriz/versoes/{vid}", clienteA, versao2Id)
                .then()
                .statusCode(400);

        // 10. Criar nova versão v3.0 em rascunho com base na v2.0
        String versao3Id = given()
                .contentType("application/json")
                .body("""
                        {
                          "versao": "v3.0",
                          "versaoOrigemId": "%s"
                        }
                        """.formatted(versao2Id))
                .when()
                .post("/api/clientes/{cid}/matriz/versoes", clienteA)
                .then()
                .statusCode(201)
                .body("versao", equalTo("v3.0"))
                .body("status", equalTo("rascunho"))
                .extract().path("id");

        // 11. Isolamento entre Clientes:
        // Cliente B tentando acessar versão do Cliente A deve retornar 404
        given()
                .when()
                .get("/api/clientes/{cid}/matriz/versoes/{vid}", clienteB, versao2Id)
                .then()
                .statusCode(404);

        // Cliente B tentando publicar versão do Cliente A deve retornar 404
        given()
                .when()
                .post("/api/clientes/{cid}/matriz/versoes/{vid}/publicar", clienteB, versao3Id)
                .then()
                .statusCode(404);

        // 12. Testar cálculo de classificação oficial na versão publicada (P4 x S5 = Score 20, Crítico)
        given()
                .when()
                .get("/api/clientes/{cid}/matriz/calcular?probabilidade=4&severidade=5", clienteA)
                .then()
                .statusCode(200)
                .body("score", equalTo(20))
                .body("nivelRisco", equalTo("critico"))
                .body("prioridade", equalTo("Imediata"));

        // Testar cálculo de classificação (P1 x S1 = Score 1, Insignificante)
        given()
                .when()
                .get("/api/clientes/{cid}/matriz/calcular?probabilidade=1&severidade=1", clienteA)
                .then()
                .statusCode(200)
                .body("score", equalTo(1))
                .body("nivelRisco", equalTo("insignificante"))
                .body("prioridade", equalTo("Monitoramento"));

        // 13. Confirmar que listagem do Cliente A retorna todas as suas versões
        given()
                .when()
                .get("/api/clientes/{cid}/matriz/versoes", clienteA)
                .then()
                .statusCode(200)
                .body("", hasSize(greaterThanOrEqualTo(3)))
                .body("versao", hasItem("v1.0"))
                .body("versao", hasItem("v2.0"))
                .body("versao", hasItem("v3.0"));
    }
}
