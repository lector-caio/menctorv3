package br.com.lector.menctor.denuncias;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class DenunciasResourceTest {

    @Test
    void criaEConsultaPorProtocolo() {
        String protocolo = "DEN-2026-TEST99";
        String id = "den-uuid-99";

        given()
                .contentType("application/json")
                .body("""
                        {
                          "id": "%s",
                          "protocolo": "%s",
                          "relato": "Relato confidencial de assédio moral",
                          "status": "triagem",
                          "gravidade": "alta",
                          "anonimo": true
                        }
                        """.formatted(id, protocolo))
                .when()
                .post("/api/denuncias")
                .then()
                .statusCode(200)
                .body("id", equalTo(id))
                .body("protocolo", equalTo(protocolo));

        given()
                .when()
                .get("/api/denuncias/protocolo/" + protocolo)
                .then()
                .statusCode(200)
                .body("id", equalTo(id))
                .body("status", equalTo("triagem"))
                .body("relato", equalTo("Relato confidencial de assédio moral"))
                .body("anonimo", equalTo(true));

        given()
                .when()
                .get("/api/denuncias/" + id)
                .then()
                .statusCode(200)
                .body("protocolo", equalTo(protocolo));
    }
}
