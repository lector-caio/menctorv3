package br.com.lector.menctor.clientes;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class ClientesResourceTest {

    @Test
    void listaEtapasPadrao() {
        given().when().get("/api/clientes/etapas")
                .then()
                .statusCode(200)
                .body("$", hasSize(8))
                .body("[0].number", equalTo(1))
                .body("[0].label", equalTo("Cadastro"))
                .body("[1].label", equalTo("Estrutura Organizacional"))
                .body("[2].label", equalTo("Matriz de risco"))
                .body("[6].label", equalTo("Resultados"))
                .body("[7].number", equalTo(8))
                .body("[7].label", equalTo("Plano de Ação"));
    }

    @Test
    void criaClienteEInicializaAsOitoEtapas() {
        String payload = """
                {
                  "name": "Alpha Indústria",
                  "cnpj": "12.345.678/0001-90",
                  "contact": "Mariana Souza",
                  "sector": "Manufatura",
                  "employees": 120,
                  "mrr": 4500.00
                }
                """;

        String clienteId = given()
                .contentType("application/json")
                .body(payload)
                .when()
                .post("/api/clientes")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("name", equalTo("Alpha Indústria"))
                .body("current_step", equalTo(1))
                .body("status", equalTo("ativo"))
                .extract().path("id");

        // Verifica que o detalhamento inclui as 8 etapas (1 em andamento, 2..8 pendentes)
        given()
                .when()
                .get("/api/clientes/" + clienteId)
                .then()
                .statusCode(200)
                .body("id", equalTo(clienteId))
                .body("progress", hasSize(8))
                .body("progress[0].step_number", equalTo(1))
                .body("progress[0].status", equalTo("em_andamento"))
                .body("progress[1].step_number", equalTo(2))
                .body("progress[1].status", equalTo("pendente"))
                .body("progress[7].step_number", equalTo(8))
                .body("progress[7].status", equalTo("pendente"));
    }

    @Test
    void salvaCadastroEAtualizaEtapa() {
        String clienteId = given()
                .contentType("application/json")
                .body("{\"name\": \"Beta Serviços\"}")
                .when()
                .post("/api/clientes")
                .then()
                .statusCode(201)
                .extract().path("id");

        // Salvar cadastro etapa 1
        given()
                .contentType("application/json")
                .body("""
                        {
                          "razao_social": "Beta Serviços LTDA",
                          "responsavel": "Carlos Diretor",
                          "email": "carlos@beta.com"
                        }
                        """)
                .when()
                .put("/api/clientes/" + clienteId + "/cadastro")
                .then()
                .statusCode(200)
                .body("razao_social", equalTo("Beta Serviços LTDA"))
                .body("client_id", equalTo(clienteId));

        // Concluir etapa 1 e verificar avanço da etapa atual do cliente
        given()
                .contentType("application/json")
                .body("""
                        {
                          "status": "concluida",
                          "data": { "formularioPreenchido": true }
                        }
                        """)
                .when()
                .put("/api/clientes/" + clienteId + "/etapas/1")
                .then()
                .statusCode(200)
                .body("status", equalTo("concluida"))
                .body("step_number", equalTo(1));

        given()
                .when()
                .get("/api/clientes/" + clienteId)
                .then()
                .statusCode(200)
                .body("current_step", equalTo(2))
                .body("cadastro.razao_social", equalTo("Beta Serviços LTDA"));
    }
}
