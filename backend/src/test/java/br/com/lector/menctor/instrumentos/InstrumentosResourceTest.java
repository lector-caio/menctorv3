package br.com.lector.menctor.instrumentos;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class InstrumentosResourceTest {

    @Test
    void consultaInstrumentosEDimensoesHierarquizadas() {
        // 1. Listar instrumentos com contagem calculada
        given()
                .when()
                .get("/api/instrumentos")
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(5))
                .body("find { it.id == 'copsoq' }.total_dimensoes", equalTo(10))
                .body("find { it.id == 'copsoq' }.total_questoes", greaterThan(30))
                .body("find { it.id == 'mte' }.total_dimensoes", equalTo(13))
                .body("find { it.id == 'hse' }.total_dimensoes", equalTo(6))
                .body("find { it.id == 'drps' }.total_questoes", equalTo(0)); // Preservação rigorosa: DRPS sem questões artificiais

        // 2. Detalhar instrumento com árvore de dimensões e questões
        String dimensaoId = given()
                .when()
                .get("/api/instrumentos/mte")
                .then()
                .statusCode(200)
                .body("id", equalTo("mte"))
                .body("nome", equalTo("MTE Psicossociais — Padrão NR-01"))
                .body("dimensoes", notNullValue())
                .body("dimensoes.size()", equalTo(13))
                .body("dimensoes[0].questoes", notNullValue())
                .body("dimensoes[0].questoes.size()", greaterThan(0))
                .extract().path("dimensoes[0].id");

        // 3. Consultar dimensões isoladamente
        given()
                .when()
                .get("/api/instrumentos/mte/dimensoes")
                .then()
                .statusCode(200)
                .body("size()", equalTo(13));

        // 4. Toggle de ativação de questão
        String questaoId = given()
                .when()
                .get("/api/instrumentos/mte")
                .then()
                .statusCode(200)
                .extract().path("dimensoes[0].questoes[0].id");

        given()
                .contentType("application/json")
                .body("{\"ativo\": false}")
                .when()
                .patch("/api/instrumentos/questoes/" + questaoId + "/toggle")
                .then()
                .statusCode(200)
                .body("id", equalTo(questaoId))
                .body("ativo", equalTo(false));

        // Reativar para manter integridade
        given()
                .contentType("application/json")
                .body("{\"ativo\": true}")
                .when()
                .patch("/api/instrumentos/questoes/" + questaoId + "/toggle")
                .then()
                .statusCode(200)
                .body("ativo", equalTo(true));
    }
}
