package br.com.lector.menctor.email;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(EmailSemSmtpTest.SemSmtp.class)
class EmailSemSmtpTest {

    public static class SemSmtp implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("menctor.mail.host", "", "menctor.mail.username", "", "menctor.mail.password", "");
        }
    }

    @Test
    void semSmtpConfiguradoResponde500() {
        given().contentType("application/json")
                .body("{\"to\":\"a@b.com\",\"subject\":\"Oi\",\"text\":\"x\"}")
                .when().post("/api/send-email")
                .then().statusCode(500)
                .body("error", equalTo("SMTP nao configurado. Verifique MAIL_HOST, MAIL_USERNAME e MAIL_PASSWORD."));
    }
}
