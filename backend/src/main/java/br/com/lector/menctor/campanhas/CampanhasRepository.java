package br.com.lector.menctor.campanhas;

import static br.com.lector.menctor.dados.Tipo.INTEIRO;
import static br.com.lector.menctor.dados.Tipo.JSON;
import static br.com.lector.menctor.dados.Tipo.TEXTO;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import br.com.lector.menctor.api.InvalidRequestException;
import br.com.lector.menctor.dados.Banco;
import br.com.lector.menctor.dados.Tipo;
import br.com.lector.menctor.dados.Valores;

@ApplicationScoped
public class CampanhasRepository {

    static final Map<String, Tipo> COLUNAS = Valores.colunas(
            "id", TEXTO,
            "cliente_id", TEXTO,
            "titulo", TEXTO,
            "descricao", TEXTO,
            "diagnostico_id", TEXTO,
            "instrumento", TEXTO,
            "ciclo", TEXTO,
            "reavaliacao", TEXTO,
            "data_inicial", TEXTO,
            "data_final", TEXTO,
            "quantidade_funcionarios", INTEIRO,
            "status", TEXTO,
            "link_token", TEXTO,
            "extra", JSON);

    @Inject
    Banco banco;

    public List<ObjectNode> listar(String clienteId) {
        if (clienteId != null && !clienteId.isBlank()) {
            return banco.transacao(c -> banco.consultar(c,
                    "select * from campanhas where cliente_id = ? order by created_at desc", clienteId));
        }
        return banco.transacao(c -> banco.consultar(c, "select * from campanhas order by created_at desc"));
    }

    public Optional<ObjectNode> detalhar(String id) {
        return banco.transacao(c -> banco.consultarUm(c, "select * from campanhas where id = ?", id));
    }

    public ObjectNode salvar(JsonNode corpo) {
        if (corpo == null || !corpo.isObject()) {
            throw new InvalidRequestException("O corpo da requisição deve ser um objeto JSON");
        }
        Map<String, Object> valores = Valores.extrair(corpo, COLUNAS);

        // Mapeamento camelCase do frontend
        mapearSePresente(corpo, valores, "clienteId", "cliente_id", TEXTO);
        mapearSePresente(corpo, valores, "diagnosticoId", "diagnostico_id", TEXTO);
        mapearSePresente(corpo, valores, "dataInicial", "data_inicial", TEXTO);
        mapearSePresente(corpo, valores, "dataFinal", "data_final", TEXTO);
        mapearSePresente(corpo, valores, "quantidadeFuncionarios", "quantidade_funcionarios", INTEIRO);
        mapearSePresente(corpo, valores, "linkToken", "link_token", TEXTO);

        if (!valores.containsKey("id") || valores.get("id") == null || ((String) valores.get("id")).isBlank()) {
            valores.put("id", UUID.randomUUID().toString());
        }

        if (!valores.containsKey("titulo") || valores.get("titulo") == null || ((String) valores.get("titulo")).isBlank()) {
            throw new InvalidRequestException("Campo obrigatório: titulo");
        }

        if (!valores.containsKey("status") || valores.get("status") == null) {
            valores.put("status", "ativa");
        }

        if (!valores.containsKey("extra") || valores.get("extra") == null) {
            valores.put("extra", JsonNodeFactory.instance.objectNode());
        }

        return banco.transacao(c -> banco.upsert(c, "campanhas", COLUNAS, valores, "id"));
    }

    public Optional<ObjectNode> atualizarStatus(String id, String novoStatus) {
        if (novoStatus == null || novoStatus.isBlank()) {
            throw new InvalidRequestException("Campo obrigatório: status");
        }
        return banco.transacao(c -> banco.atualizar(c, "campanhas", COLUNAS, Map.of("status", novoStatus), "id", id));
    }

    public boolean excluir(String id) {
        return banco.transacao(c -> banco.executar(c, "delete from campanhas where id = ?", id) > 0);
    }

    private void mapearSePresente(JsonNode corpo, Map<String, Object> valores, String camel, String snake, Tipo tipo) {
        if (!valores.containsKey(snake) && corpo.has(camel)) {
            valores.put(snake, Valores.converter(corpo.get(camel), tipo, camel));
        }
    }
}
