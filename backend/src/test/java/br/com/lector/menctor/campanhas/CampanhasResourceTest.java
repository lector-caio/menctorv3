package br.com.lector.menctor.campanhas;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class CampanhasResourceTest {

    @Test
    void cicloDeVidaCompletoDaCampanhaNoPostgres() {
        // 1. Criar cliente para associar à campanha
        String clienteId = given()
                .contentType("application/json")
                .body("""
                        {
                          "name": "Metalúrgica Progresso",
                          "cnpj": "11.222.333/0001-44",
                          "status": "ativo"
                        }
                        """)
                .when()
                .post("/api/clientes")
                .then()
                .statusCode(201)
                .extract().path("id");

        // 2. Criar campanha
        String campanhaPayload = """
                {
                  "titulo": "Pesquisa Clima e Riscos 2026.2",
                  "clienteId": "%s",
                  "diagnosticoId": "copsoq",
                  "instrumento": "COPSOQ",
                  "ciclo": "2026-Q2",
                  "reavaliacao": "90 dias",
                  "descricao": "Avaliação semestral obrigatória NR-01",
                  "dataInicial": "2026-06-01",
                  "dataFinal": "2026-07-01",
                  "quantidadeFuncionarios": 180,
                  "status": "ativa"
                }
                """.formatted(clienteId);

        String campanhaId = given()
                .contentType("application/json")
                .body(campanhaPayload)
                .when()
                .post("/api/campanhas")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("titulo", equalTo("Pesquisa Clima e Riscos 2026.2"))
                .body("cliente_id", equalTo(clienteId))
                .body("status", equalTo("ativa"))
                .extract().path("id");

        // 3. Consultar campanha criada (garante persistência real no PostgreSQL)
        given()
                .when()
                .get("/api/campanhas/" + campanhaId)
                .then()
                .statusCode(200)
                .body("id", equalTo(campanhaId))
                .body("cliente_id", equalTo(clienteId))
                .body("ciclo", equalTo("2026-Q2"))
                .body("quantidade_funcionarios", equalTo(180));

        // 4. Listar e filtrar por cliente
        given()
                .queryParam("clienteId", clienteId)
                .when()
                .get("/api/campanhas")
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(1))
                .body("id", hasItem(campanhaId));

        // 5. Atualizar dados da campanha
        given()
                .contentType("application/json")
                .body("""
                        {
                          "titulo": "Pesquisa Clima e Riscos 2026.2 - Revisada",
                          "reavaliacao": "120 dias",
                          "quantidadeFuncionarios": 200
                        }
                        """)
                .when()
                .patch("/api/campanhas/" + campanhaId)
                .then()
                .statusCode(200)
                .body("titulo", equalTo("Pesquisa Clima e Riscos 2026.2 - Revisada"))
                .body("reavaliacao", equalTo("120 dias"))
                .body("quantidade_funcionarios", equalTo(200));

        // 6. Alterar status da campanha
        given()
                .contentType("application/json")
                .body("{\"status\": \"pausada\"}")
                .when()
                .patch("/api/campanhas/" + campanhaId + "/status")
                .then()
                .statusCode(200)
                .body("status", equalTo("pausada"));

        // 7. Excluir campanha
        given()
                .when()
                .delete("/api/campanhas/" + campanhaId)
                .then()
                .statusCode(204);

        // 8. Confirma que foi excluída
        given()
                .when()
                .get("/api/campanhas/" + campanhaId)
                .then()
                .statusCode(404);
    }
}
