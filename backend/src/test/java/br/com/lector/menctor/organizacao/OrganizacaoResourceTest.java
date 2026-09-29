package br.com.lector.menctor.organizacao;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class OrganizacaoResourceTest {

    @Test
    void fluxoCompletoDeEstruturaOrganizacionalComIsolamentoPorCliente() {
        // 1. Criar Cliente A
        String clienteA = given()
                .contentType("application/json")
                .body("""
                        {
                          "name": "Alpha Indústria",
                          "cnpj": "12.345.678/0001-90",
                          "status": "ativo"
                        }
                        """)
                .when()
                .post("/api/clientes")
                .then()
                .statusCode(201)
                .extract().path("id");

        // 2. Criar Cliente B
        String clienteB = given()
                .contentType("application/json")
                .body("""
                        {
                          "name": "Beta Tecnologia",
                          "cnpj": "98.765.432/0001-10",
                          "status": "ativo"
                        }
                        """)
                .when()
                .post("/api/clientes")
                .then()
                .statusCode(201)
                .extract().path("id");

        // 4. Criar Unidade para Cliente A
        String unidadeAId = given()
                .contentType("application/json")
                .body("""
                        {
                          "nome": "Fábrica Matriz",
                          "codigo": "UN-MAT",
                          "cidade": "Joinville",
                          "estado": "SC"
                        }
                        """)
                .when()
                .post("/api/clientes/{cid}/organizacao/unidades", clienteA)
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("nome", equalTo("Fábrica Matriz"))
                .body("ativo", equalTo(true))
                .extract().path("id");

        // 5. Listar Unidades do Cliente A
        given()
                .when()
                .get("/api/clientes/{cid}/organizacao/unidades", clienteA)
                .then()
                .statusCode(200)
                .body("nome", hasItem("Fábrica Matriz"));

        // 3. Confirmar Isolamento: Unidade do Cliente A NÃO aparece no Cliente B
        given()
                .when()
                .get("/api/clientes/{cid}/organizacao/unidades", clienteB)
                .then()
                .statusCode(200)
                .body("", hasSize(0));

        // 6. Atualizar Unidade
        given()
                .contentType("application/json")
                .body("""
                        {
                          "nome": "Fábrica Matriz Joinville",
                          "codigo": "UN-MAT-01",
                          "cidade": "Joinville",
                          "estado": "SC"
                        }
                        """)
                .when()
                .put("/api/clientes/{cid}/organizacao/unidades/{id}", clienteA, unidadeAId)
                .then()
                .statusCode(200)
                .body("nome", equalTo("Fábrica Matriz Joinville"));

        // 7. Desativar Unidade (Toggle)
        given()
                .when()
                .patch("/api/clientes/{cid}/organizacao/unidades/{id}/toggle", clienteA, unidadeAId)
                .then()
                .statusCode(200)
                .body("ativo", equalTo(false));

        // Reativar para uso posterior
        given()
                .when()
                .patch("/api/clientes/{cid}/organizacao/unidades/{id}/toggle", clienteA, unidadeAId)
                .then()
                .statusCode(200)
                .body("ativo", equalTo(true));

        // Criar Diretoria para Cliente A
        String diretoriaAId = given()
                .contentType("application/json")
                .body("""
                        {
                          "nome": "Diretoria Industrial",
                          "responsavel": "Marcos Silveira"
                        }
                        """)
                .when()
                .post("/api/clientes/{cid}/organizacao/diretorias", clienteA)
                .then()
                .statusCode(201)
                .extract().path("id");

        // 8. Criar Setor vinculado à Unidade e Diretoria do Cliente A
        String setorAId = given()
                .contentType("application/json")
                .body("""
                        {
                          "nome": "Usinagem Pesada",
                          "unidadeId": "%s",
                          "diretoriaId": "%s",
                          "responsavel": "João Silva",
                          "colaboradores": 45,
                          "turno": "Turno Rotativo"
                        }
                        """.formatted(unidadeAId, diretoriaAId))
                .when()
                .post("/api/clientes/{cid}/organizacao/setores", clienteA)
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("nome", equalTo("Usinagem Pesada"))
                .body("colaboradores", equalTo(45))
                .extract().path("id");

        // 9. Consultar Setor individualmente
        given()
                .when()
                .get("/api/clientes/{cid}/organizacao/setores/{id}", clienteA, setorAId)
                .then()
                .statusCode(200)
                .body("nome", equalTo("Usinagem Pesada"))
                .body("responsavel", equalTo("João Silva"));

        // 10. Atualizar Setor
        given()
                .contentType("application/json")
                .body("""
                        {
                          "nome": "Usinagem e Corte",
                          "unidadeId": "%s",
                          "diretoriaId": "%s",
                          "responsavel": "João Silva",
                          "colaboradores": 50,
                          "turno": "12x36"
                        }
                        """.formatted(unidadeAId, diretoriaAId))
                .when()
                .put("/api/clientes/{cid}/organizacao/setores/{id}", clienteA, setorAId)
                .then()
                .statusCode(200)
                .body("nome", equalTo("Usinagem e Corte"))
                .body("colaboradores", equalTo(50));

        // 11. Criar Nível de Cargo
        String nivelCargoId = given()
                .contentType("application/json")
                .body("""
                        {
                          "nome": "Especialista Operacional",
                          "ordem": 3
                        }
                        """)
                .when()
                .post("/api/clientes/{cid}/organizacao/niveis-cargo", clienteA)
                .then()
                .statusCode(201)
                .extract().path("id");

        // 12 e 13. Criar Cargo associado ao Nível de Cargo
        String cargoId = given()
                .contentType("application/json")
                .body("""
                        {
                          "nome": "Operador CNC Sênior",
                          "nivelCargoId": "%s",
                          "cbo": "7212-15",
                          "descricao": "Operação de torno CNC"
                        }
                        """.formatted(nivelCargoId))
                .when()
                .post("/api/clientes/{cid}/organizacao/cargos", clienteA)
                .then()
                .statusCode(201)
                .body("nome", equalTo("Operador CNC Sênior"))
                .body("nivel_cargo_id", equalTo(nivelCargoId))
                .extract().path("id");

        // 14. Criar Ambiente
        String ambienteId = given()
                .contentType("application/json")
                .body("""
                        {
                          "nome": "Galpão Industrial 01",
                          "unidadeId": "%s",
                          "descricao": "Área de maquinário pesado com isolamento acústico"
                        }
                        """.formatted(unidadeAId))
                .when()
                .post("/api/clientes/{cid}/organizacao/ambientes", clienteA)
                .then()
                .statusCode(201)
                .extract().path("id");

        // 15. Criar GHE
        given()
                .contentType("application/json")
                .body("""
                        {
                          "nome": "GHE Operadores de Usinagem",
                          "codigo": "GHE-001",
                          "descricao": "Exposição a ruído contínuo e postura estática"
                        }
                        """)
                .when()
                .post("/api/clientes/{cid}/organizacao/ghes", clienteA)
                .then()
                .statusCode(201)
                .body("nome", equalTo("GHE Operadores de Usinagem"));

        // 16. Criar GES
        given()
                .contentType("application/json")
                .body("""
                        {
                          "nome": "GES Equipe Turno Manhã",
                          "codigo": "GES-10",
                          "descricao": "Mesma jornada e ritmo de produção matutino"
                        }
                        """)
                .when()
                .post("/api/clientes/{cid}/organizacao/gess", clienteA)
                .then()
                .statusCode(201)
                .body("nome", equalTo("GES Equipe Turno Manhã"));

        // 17. Consultar registros no Resumo Completo
        given()
                .when()
                .get("/api/clientes/{cid}/organizacao", clienteA)
                .then()
                .statusCode(200)
                .body("unidades", hasSize(greaterThanOrEqualTo(1)))
                .body("diretorias", hasSize(greaterThanOrEqualTo(1)))
                .body("setores", hasSize(greaterThanOrEqualTo(1)))
                .body("niveisCargo", hasSize(greaterThanOrEqualTo(1)))
                .body("cargos", hasSize(greaterThanOrEqualTo(1)))
                .body("ambientes", hasSize(greaterThanOrEqualTo(1)))
                .body("ghes", hasSize(greaterThanOrEqualTo(1)))
                .body("gess", hasSize(greaterThanOrEqualTo(1)));

        // 18 e 19. Tentar associar entidade do Cliente A no Cliente B (Integridade / Rejeição)
        // Setor no Cliente B tentando referenciar unidade do Cliente A
        given()
                .contentType("application/json")
                .body("""
                        {
                          "nome": "Setor Invasor",
                          "unidadeId": "%s"
                        }
                        """.formatted(unidadeAId))
                .when()
                .post("/api/clientes/{cid}/organizacao/setores", clienteB)
                .then()
                .statusCode(400); // rejeição inválida

        // Cargo no Cliente B tentando referenciar nível do Cliente A
        given()
                .contentType("application/json")
                .body("""
                        {
                          "nome": "Cargo Invasor",
                          "nivelCargoId": "%s"
                        }
                        """.formatted(nivelCargoId))
                .when()
                .post("/api/clientes/{cid}/organizacao/cargos", clienteB)
                .then()
                .statusCode(400);

        // 20. Confirmar persistência após nova consulta
        given()
                .when()
                .get("/api/clientes/{cid}/organizacao/cargos/{id}", clienteA, cargoId)
                .then()
                .statusCode(200)
                .body("nome", equalTo("Operador CNC Sênior"))
                .body("nivel_cargo_id", equalTo(nivelCargoId));
    }
}
