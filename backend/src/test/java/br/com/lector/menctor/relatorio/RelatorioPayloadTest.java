package br.com.lector.menctor.relatorio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.lector.menctor.api.InvalidRequestException;

class RelatorioPayloadTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String MINIMO = "{\"empresa\":\"ACME\",\"respondentes\":10,\"total_colaboradores\":20,\"dimensoes\":[]}";

    private static JsonNode json(String s) throws Exception {
        return MAPPER.readTree(s);
    }

    private static String erro(String payload) throws Exception {
        return assertThrows(InvalidRequestException.class, () -> RelatorioPayload.parse(json(payload))).getMessage();
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{}", "[]", "\"\"", "0", "false"})
    void corpoVazioOuFalso(String payload) throws Exception {
        assertEquals("Nenhum dado foi enviado", erro(payload));
    }

    @Test
    void camposObrigatoriosNaOrdemDoServerPy() throws Exception {
        assertEquals("Campos obrigatórios faltando: respondentes, dimensoes",
                erro("{\"empresa\":\"X\",\"total_colaboradores\":3}"));
        assertEquals("Campos obrigatórios faltando: empresa, respondentes, total_colaboradores, dimensoes",
                erro("[1]"));
    }

    @Test
    void aplicaOsPadroesDoServerPy() throws Exception {
        RelatorioConfig cfg = RelatorioPayload.parse(json(MINIMO));
        assertEquals("PCL-Q2", cfg.codigo());
        assertEquals("2° Trimestre/2026", cfg.tituloLinha2());
        assertEquals("Abril a Junho - 2026", cfg.periodo());
        assertEquals("0%", cfg.taxaAdesao());
        assertEquals("Toda a organização", cfg.foco());
        assertEquals("—", cfg.endereco());
        assertEquals("relatorio_psicossocial.pdf", cfg.outputFilename());
        assertEquals("ACME", cfg.empresa());
    }

    @Test
    void textoNuloUsaOPadrao() throws Exception {
        RelatorioConfig cfg = RelatorioPayload.parse(json(MINIMO.replace("{", "{\"cnpj\":null,")));
        assertEquals("XX.XXX.XXX/0001-XX", cfg.cnpj());
    }

    @Test
    void inteirosComoOIntDoPython() throws Exception {
        RelatorioConfig cfg = RelatorioPayload.parse(json(
                "{\"empresa\":\"X\",\"respondentes\":\" 15 \",\"total_colaboradores\":20.9,\"dimensoes\":[]}"));
        assertEquals(15, cfg.respondentes());
        assertEquals(20, cfg.totalColaboradores());
        assertEquals("Campo 'respondentes' deve ser um número inteiro",
                erro("{\"empresa\":\"X\",\"respondentes\":\"12.5\",\"total_colaboradores\":1,\"dimensoes\":[]}"));
        assertEquals("Campo 'total_colaboradores' deve ser um número inteiro",
                erro("{\"empresa\":\"X\",\"respondentes\":1,\"total_colaboradores\":null,\"dimensoes\":[]}"));
    }

    @Test
    void dimensoesValidadas() throws Exception {
        RelatorioConfig cfg = RelatorioPayload.parse(json(
                "{\"empresa\":\"X\",\"respondentes\":1,\"total_colaboradores\":1,"
                + "\"dimensoes\":[{\"nome\":\"Burnout\",\"score\":3},{\"nome\":\"Estresse\",\"score\":\"2.5\"}]}"));
        assertEquals(new RelatorioConfig.Dimensao("Burnout", 3.0), cfg.dimensoes().get(0));
        assertEquals(2.5, cfg.dimensoes().get(1).score());
        assertEquals("Campo 'dimensoes' deve ser uma lista de {\"nome\", \"score\"}",
                erro("{\"empresa\":\"X\",\"respondentes\":1,\"total_colaboradores\":1,\"dimensoes\":{}}"));
        assertEquals("Cada dimensão precisa de 'nome' (texto) e 'score' (número)",
                erro("{\"empresa\":\"X\",\"respondentes\":1,\"total_colaboradores\":1,\"dimensoes\":[{\"score\":1}]}"));
        assertEquals("O 'score' de cada dimensão deve ser um número",
                erro("{\"empresa\":\"X\",\"respondentes\":1,\"total_colaboradores\":1,\"dimensoes\":[{\"nome\":\"A\",\"score\":\"alto\"}]}"));
    }
}
