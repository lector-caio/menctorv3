package br.com.lector.menctor.email;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class EmailResourceTest {

    @Inject
    MockMailbox mailbox;

    @BeforeEach
    void limpar() {
        mailbox.clear();
    }

    @Test
    void enviaHtmlTextoEReplyTo() {
        given().contentType("application/json")
                .body("{\"to\":\"  cliente@empresa.com \",\"subject\":\" Proposta Menctor para Loghaus Logística \","
                        + "\"html\":\"<h2>Proposta</h2>\",\"text\":\"Proposta em texto\",\"replyTo\":\"caio@lector.com.br\"}")
                .when().post("/api/send-email")
                .then().statusCode(200)
                .body("ok", equalTo(true))
                .body("to", equalTo("cliente@empresa.com"))
                .body("subject", equalTo("Proposta Menctor para Loghaus Logística"));

        List<Mail> enviados = mailbox.getMailsSentTo("cliente@empresa.com");
        assertEquals(1, enviados.size());
        Mail mail = enviados.get(0);
        assertEquals("Menctor Testes <menctor@teste.local>", mail.getFrom());
        assertEquals("Proposta Menctor para Loghaus Logística", mail.getSubject());
        assertEquals("<h2>Proposta</h2>", mail.getHtml());
        assertEquals("Proposta em texto", mail.getText());
        assertEquals("caio@lector.com.br", mail.getReplyTo());
    }

    @Test
    void somenteHtmlGanhaTextoPadrao() {
        given().contentType("application/json")
                .body("{\"to\":\"a@b.com\",\"subject\":\"Oi\",\"html\":\"<b>x</b>\"}")
                .when().post("/api/send-email")
                .then().statusCode(200);
        Mail mail = mailbox.getMailsSentTo("a@b.com").get(0);
        assertEquals("Este e-mail possui uma versao HTML.", mail.getText());
        assertNull(mail.getReplyTo());
    }

    @Test
    void variosDestinatarios() {
        given().contentType("application/json")
                .body("{\"to\":\"a@b.com, c@d.com\",\"subject\":\"Oi\",\"text\":\"texto\"}")
                .when().post("/api/send-email")
                .then().statusCode(200);
        assertEquals(1, mailbox.getMailsSentTo("a@b.com").size());
        assertEquals(1, mailbox.getMailsSentTo("c@d.com").size());
    }

    @Test
    void camposObrigatorios() {
        for (String body : new String[] {"{}", "{\"to\":\"a@b.com\",\"subject\":\"Oi\"}", "{\"subject\":\"Oi\",\"text\":\"x\"}", "[]"}) {
            given().contentType("application/json").body(body)
                    .when().post("/api/send-email")
                    .then().statusCode(400)
                    .body("error", equalTo("Campos obrigatorios: to, subject e html/text"));
        }
        assertEquals(0, mailbox.getTotalMessagesSent());
    }

    @Test
    void remetenteComCaracteresEspeciais() {
        assertEquals("Menctor <x@y.com>", EmailService.sender("Menctor", "x@y.com"));
        assertEquals("\"Lector, Menctor\" <x@y.com>", EmailService.sender("Lector, Menctor", "x@y.com"));
        assertEquals("x@y.com", EmailService.sender(" ", "x@y.com"));
    }
}
