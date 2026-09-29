package br.com.lector.menctor.relatorio;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class RelatorioResourceTest {

    private static String payload(String caso) throws IOException {
        try (InputStream in = RelatorioResourceTest.class.getResourceAsStream("/referencia-python/" + caso + ".json")) {
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static String texto(byte[] pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    private static int paginas(byte[] pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return doc.getNumberOfPages();
        }
    }

    @Test
    void relatorioDeTeste() throws IOException {
        byte[] pdf = given().when().get("/api/teste")
                .then()
                .statusCode(200)
                .contentType("application/pdf")
                .header("Content-Disposition", "attachment; filename=\"relatorio_teste.pdf\"")
                .extract().asByteArray();
        assertEquals(14, paginas(pdf));
        assertTrue(texto(pdf).contains("Loghaus Logistica"));
    }

    @Test
    void relatorioComDadosEnviados() throws IOException {
        byte[] pdf = given().contentType("application/json").body(payload("test_api"))
                .when().post("/api/gerar-relatorio")
                .then()
                .statusCode(200)
                .contentType("application/pdf")
                .header("Content-Disposition", "attachment; filename=\"relatorio_teste.pdf\"")
                .extract().asByteArray();
        assertEquals(14, paginas(pdf));
        String texto = texto(pdf);
        assertTrue(texto.contains("Loghaus Logística"));
        assertTrue(texto.contains("Avenida Paulista, 1000"));
    }

    @Test
    void maisDeDozeDimensoesNaoQuebram() throws IOException {
        // o gerador Python falhava com LayoutError a partir de 13 dimensões
        byte[] pdf = given().contentType("application/json").body(payload("dims_030"))
                .when().post("/api/gerar-relatorio")
                .then().statusCode(200).extract().asByteArray();
        assertEquals(20, paginas(pdf));
    }

    @Test
    void nomeDoArquivoComAcento() {
        given().contentType("application/json")
                .body("{\"empresa\":\"X\",\"respondentes\":1,\"total_colaboradores\":2,\"dimensoes\":[],"
                        + "\"output_filename\":\"relatório São João.pdf\"}")
                .when().post("/api/gerar-relatorio")
                .then().statusCode(200)
                .header("Content-Disposition", "attachment; filename=\"relatorio Sao Joao.pdf\"; "
                        + "filename*=UTF-8''relat%C3%B3rio%20S%C3%A3o%20Jo%C3%A3o.pdf");
    }

    @Test
    void erros400ComMensagemDoServerPy() {
        given().contentType("application/json").body("{}")
                .when().post("/api/gerar-relatorio")
                .then().statusCode(400).body("error", equalTo("Nenhum dado foi enviado"));
        given().when().post("/api/gerar-relatorio")
                .then().statusCode(400).body("error", equalTo("Nenhum dado foi enviado"));
        given().contentType("application/json").body("{\"empresa\":\"X\",\"total_colaboradores\":3}")
                .when().post("/api/gerar-relatorio")
                .then().statusCode(400).body("error", equalTo("Campos obrigatórios faltando: respondentes, dimensoes"));
        given().contentType("application/json").body("{\"empresa\": ")
                .when().post("/api/gerar-relatorio")
                .then().statusCode(400).body("error", startsWith("JSON inválido"));
    }

    @Test
    void conteudoQueNaoCabeNaPagina() {
        String endereco = "Endereço muito longo ".repeat(400);
        given().contentType("application/json")
                .body("{\"empresa\":\"X\",\"respondentes\":1,\"total_colaboradores\":2,\"dimensoes\":[],"
                        + "\"endereco\":\"" + endereco + "\"}")
                .when().post("/api/gerar-relatorio")
                .then().statusCode(422).body("error", startsWith("Erro ao gerar relatório: "));
    }

    @Test
    void corsLiberadoComoNoFlask() {
        given().header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type")
                .when().options("/api/gerar-relatorio")
                .then().statusCode(200)
                .header("Access-Control-Allow-Origin", "http://localhost:3000")
                .header("Access-Control-Allow-Headers", "content-type");
    }
}
