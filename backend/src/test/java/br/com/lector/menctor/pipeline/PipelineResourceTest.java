package br.com.lector.menctor.pipeline;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class PipelineResourceTest {

    @Test
    void fluxoPipelineVinculaClienteExistenteSemDuplicar() {
        // 1. Cria um cliente pré-existente sem pipeline (ex: cadastro manual)
        String clienteExistenteId = given()
                .contentType("application/json")
                .body("""
                        {
                          "name": "Delta Soluções Ambientais",
                          "cnpj": "98.765.432/0001-10",
                          "status": "ativo"
                        }
                        """)
                .when()
                .post("/api/clientes")
                .then()
                .statusCode(201)
                .extract().path("id");

        // 2. Um lead no pipeline caminha até assinar o contrato (com acentuação ou caixa diferente)
        String cardId = "card-delta-123";
        given()
                .contentType("application/json")
                .body("""
                        {
                          "id": "%s",
                          "empresa": "delta solucoes ambientais",
                          "stage": "lead",
                          "valor": 7500,
                          "funcionarios": 85
                        }
                        """.formatted(cardId))
                .when()
                .post("/api/pipeline")
                .then()
                .statusCode(200);

        // 3. Contrato assinado (stage muda para 'planejamento' ou extra.assinado = true)
        given()
                .contentType("application/json")
                .body("""
                        {
                          "id": "%s",
                          "empresa": "delta solucoes ambientais",
                          "stage": "planejamento",
                          "assinado": true,
                          "valor": 7500
                        }
                        """.formatted(cardId))
                .when()
                .post("/api/pipeline")
                .then()
                .statusCode(200);

        // 4. Verifica que o cliente existente recebeu o vínculo (pipeline_card_id) e NÃO foi criado outro cliente
        given()
                .when()
                .get("/api/clientes/" + clienteExistenteId)
                .then()
                .statusCode(200)
                .body("id", equalTo(clienteExistenteId))
                .body("pipeline_card_id", equalTo(cardId));

        // Total de clientes com o nome Delta Soluções Ambientais deve continuar sendo 1
        int deltaCount = given()
                .when()
                .get("/api/clientes")
                .then()
                .statusCode(200)
                .extract().jsonPath().getList("findAll { it.name == 'Delta Soluções Ambientais' }").size();

        assertEquals(1, deltaCount, "Cliente não deve ter sido duplicado");
    }

    @Test
    void contratoAssinadoCriaNovoClienteComOitoEtapasSeNaoExistir() {
        String cardId = "card-omega-999";
        given()
                .contentType("application/json")
                .body("""
                        {
                          "id": "%s",
                          "empresa": "Ômega Logística e Cargas",
                          "stage": "planejamento",
                          "assinado": true,
                          "valor": 9200,
                          "funcionarios": 230,
                          "contato": "Roberto Diretor",
                          "email": "roberto@omega.com"
                        }
                        """.formatted(cardId))
                .when()
                .post("/api/pipeline")
                .then()
                .statusCode(200);

        // Procura o cliente criado pelo vínculo
        String clienteCriadoId = given()
                .when()
                .get("/api/clientes")
                .then()
                .statusCode(200)
                .extract().jsonPath().getString("find { it.pipeline_card_id == '" + cardId + "' }.id");

        given()
                .when()
                .get("/api/clientes/" + clienteCriadoId)
                .then()
                .statusCode(200)
                .body("name", equalTo("Ômega Logística e Cargas"))
                .body("pipeline_card_id", equalTo(cardId))
                .body("progress", hasSize(8))
                .body("progress[0].status", equalTo("em_andamento"))
                .body("progress[1].status", equalTo("pendente"));
    }

    @Test
    void negocioEmExecucaoComAssinadoNoExtraViraClienteUmaVezSo() {
        // Mesmo formato que o frontend envia (toDbCard): "assinado" só dentro de extra
        String cardId = "card-sigma-321";
        String card = """
                {
                  "id": "%s",
                  "empresa": "Sigma Telecom",
                  "stage": "ativas",
                  "contato": "Paula Lima",
                  "valor": 3100,
                  "funcionarios": 60,
                  "extra": { "assinado": true, "etapaManual": false }
                }
                """.formatted(cardId);

        // gravado duas vezes (ao mover no pipeline e ao abrir pela tela de Clientes)
        for (int i = 0; i < 2; i++) {
            given().contentType("application/json").body(card)
                    .when().post("/api/pipeline")
                    .then().statusCode(200);
        }

        int vinculados = given()
                .when()
                .get("/api/clientes")
                .then()
                .statusCode(200)
                .extract().jsonPath().getList("findAll { it.pipeline_card_id == '" + cardId + "' }").size();
        assertEquals(1, vinculados, "O negócio deve gerar exatamente um cliente");

        String clienteId = given()
                .when()
                .get("/api/clientes")
                .then()
                .extract().jsonPath().getString("find { it.pipeline_card_id == '" + cardId + "' }.id");

        given()
                .when()
                .get("/api/clientes/" + clienteId)
                .then()
                .statusCode(200)
                .body("name", equalTo("Sigma Telecom"))
                .body("contact", equalTo("Paula Lima"))
                .body("progress", hasSize(8));
    }

    @Test
    void negocioAntesDoContratoNaoViraCliente() {
        String cardId = "card-kappa-654";
        given()
                .contentType("application/json")
                .body("""
                        {
                          "id": "%s",
                          "empresa": "Kappa Engenharia",
                          "stage": "proposta",
                          "valor": 5000,
                          "extra": { "assinado": false }
                        }
                        """.formatted(cardId))
                .when()
                .post("/api/pipeline")
                .then()
                .statusCode(200);

        int vinculados = given()
                .when()
                .get("/api/clientes")
                .then()
                .statusCode(200)
                .extract().jsonPath().getList("findAll { it.pipeline_card_id == '" + cardId + "' }").size();
        assertEquals(0, vinculados, "Negócio sem contrato assinado não deve virar cliente");
    }
}
