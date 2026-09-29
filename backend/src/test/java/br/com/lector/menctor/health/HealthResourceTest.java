package br.com.lector.menctor.health;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class HealthResourceTest {

    @Test
    void respondeComoOServerPy() {
        given().when().get("/health")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("status", equalTo("ok"))
                .body("message", equalTo("Servidor de relatórios psicossociais está ativo"));
    }
}
