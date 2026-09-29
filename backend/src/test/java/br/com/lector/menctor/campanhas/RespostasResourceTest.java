package br.com.lector.menctor.campanhas;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class RespostasResourceTest {

    @Test
    void fluxoCompletoDeRespostasComAnonimizacaoEPrevecaoDeDuplicidade() {
        // Criar campanha A
        String campA = given()
                .contentType("application/json")
                .body("""
                        {
                          "titulo": "Campanha Respostas Teste A",
                          "instrumento": "COPSOQ",
                          "quantidadeFuncionarios": 100
                        }
                        """)
                .when()
                .post("/api/campanhas")
                .then()
                .statusCode(201)
                .extract().path("id");

        // Criar campanha B para teste de isolamento
        String campB = given()
                .contentType("application/json")
                .body("""
                        {
                          "titulo": "Campanha Isolada B",
                          "instrumento": "HSE",
                          "quantidadeFuncionarios": 50
                        }
                        """)
                .when()
                .post("/api/campanhas")
                .then()
                .statusCode(201)
                .extract().path("id");

        // 1. Submissão de resposta válida por Trabalhador 1
        String cpfHash1 = "hash-anonimo-colaborador-001";
        given()
                .contentType("application/json")
                .body("""
                        {
                          "cpfHash": "%s",
                          "setor": "Operações",
                          "cargo": "Operador de Máquinas",
                          "mediaRisco": 3.45,
                          "porDimensao": {
                            "Carga de trabalho": 4.00,
                            "Burnout": 3.50,
                            "Suporte social": 2.85
                          },
                          "respostasItens": [
                            { "pergunta": "Você precisa trabalhar muito rapidamente?", "valor": 4 },
                            { "pergunta": "Sua carga de trabalho é distribuída de forma desigual?", "valor": 4 }
                          ]
                        }
                        """.formatted(cpfHash1))
                .when()
                .post("/api/campanhas/" + campA + "/respostas")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("campanha_id", equalTo(campA))
                .body("cpf_hash", equalTo(cpfHash1))
                .body("setor", equalTo("Operações"));

        // 2. Prevenção de duplicidade: mesmo CPF tenta responder novamente na mesma campanha -> 409
        given()
                .contentType("application/json")
                .body("""
                        {
                          "cpfHash": "%s",
                          "setor": "Operações",
                          "mediaRisco": 2.00
                        }
                        """.formatted(cpfHash1))
                .when()
                .post("/api/campanhas/" + campA + "/respostas")
                .then()
                .statusCode(409)
                .body("error", equalTo("Este CPF já respondeu a esta campanha"));

        // 3. Submissão por Trabalhador 2 na Campanha A
        String cpfHash2 = "hash-anonimo-colaborador-002";
        given()
                .contentType("application/json")
                .body("""
                        {
                          "cpfHash": "%s",
                          "setor": "Comercial",
                          "cargo": "Vendedor",
                          "mediaRisco": 2.15,
                          "porDimensao": {
                            "Carga de trabalho": 2.00,
                            "Burnout": 2.30,
                            "Suporte social": 4.00
                          }
                        }
                        """.formatted(cpfHash2))
                .when()
                .post("/api/campanhas/" + campA + "/respostas")
                .then()
                .statusCode(201);

        // 4. Recuperação das respostas da Campanha A
        given()
                .when()
                .get("/api/campanhas/" + campA + "/respostas")
                .then()
                .statusCode(200)
                .body("$", hasSize(2));

        // 5. Isolamento: Campanha B deve continuar com 0 respostas
        given()
                .when()
                .get("/api/campanhas/" + campB + "/respostas")
                .then()
                .statusCode(200)
                .body("$", hasSize(0));

        // 6. Checagem individual de já respondeu
        given()
                .queryParam("cpfHash", cpfHash1)
                .when()
                .get("/api/campanhas/" + campA + "/ja-respondeu")
                .then()
                .statusCode(200)
                .body("jaRespondeu", equalTo(true));

        given()
                .queryParam("cpfHash", "outro-cpf-qualquer")
                .when()
                .get("/api/campanhas/" + campA + "/ja-respondeu")
                .then()
                .statusCode(200)
                .body("jaRespondeu", equalTo(false));

        // 7. Obtenção do resultado agregado consolidado do Postgres
        given()
                .when()
                .get("/api/campanhas/" + campA + "/resultado")
                .then()
                .statusCode(200)
                .body("total", equalTo(2))
                .body("media", notNullValue())
                .body("porDimensao", hasSize(greaterThanOrEqualTo(3)))
                .body("porSetor", hasSize(2));
    }
}
