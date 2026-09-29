package br.com.lector.menctor.frontend;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/** Usa a pasta src/test/resources/frontend-teste (que tem até um .env falso). */
@QuarkusTest
class FrontendRoutesTest {

    @Test
    void serveOIndex() {
        given().when().get("/")
                .then().statusCode(200)
                .contentType(containsString("text/html"))
                .header("Cache-Control", "no-cache")
                .body(containsString("<div id=\"root\"></div>"));
    }

    @Test
    void jsxComoJavascript() {
        given().when().get("/app.jsx?v=20260101a")
                .then().statusCode(200)
                .contentType(containsString("text/javascript"))
                .body(containsString("const App"));
        given().when().get("/screens/home.jsx").then().statusCode(200);
    }

    @Test
    void rotasDoFrontendDevolvemOIndex() {
        for (String rota : new String[] {"/diagnosticos", "/admin/relatorios", "/doc/relatorio"}) {
            given().when().get(rota)
                    .then().statusCode(200)
                    .contentType(containsString("text/html"))
                    .body(containsString("<div id=\"root\"></div>"));
        }
    }

    @Test
    void arquivoInexistenteE404ComoNaVercel() {
        given().when().get("/nao-existe.js").then().statusCode(404);
        given().when().get("/api/nao-existe").then().statusCode(404);
    }

    @Test
    void naoServeArquivosOcultosNemOBackend() {
        given().when().get("/.env")
                .then().statusCode(404)
                .body(not(containsString("segredo-de-teste")));
        given().urlEncodingEnabled(false).when().get("/%2Eenv")
                .then().statusCode(404)
                .body(not(containsString("segredo-de-teste")));
        given().when().get("/.oculta/segredo.txt").then().statusCode(404);
        given().when().get("/backend/segredo.txt").then().statusCode(404);
    }

    @Test
    void revalidacaoComLastModified() {
        String lastModified = given().when().get("/app.jsx")
                .then().statusCode(200)
                .extract().header("Last-Modified");
        given().header("If-Modified-Since", lastModified)
                .when().get("/app.jsx")
                .then().statusCode(304);
    }
}
