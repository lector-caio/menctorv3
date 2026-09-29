package br.com.lector.menctor.pipeline;

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
import br.com.lector.menctor.clientes.ClientesRepository;
import br.com.lector.menctor.dados.Banco;
import br.com.lector.menctor.dados.Tipo;
import br.com.lector.menctor.dados.Valores;

@ApplicationScoped
public class PipelineRepository {

    static final Map<String, Tipo> COLUNAS = Valores.colunas(
            "id", TEXTO,
            "stage", TEXTO,
            "empresa", TEXTO,
            "contato", TEXTO,
            "email", TEXTO,
            "funcionarios", INTEIRO,
            "valor", INTEIRO,
            "dias", INTEIRO,
            "decisor", TEXTO,
            "proximo_passo", TEXTO,
            "probabilidade", INTEIRO,
            "origem", TEXTO,
            "extra", JSON);

    @Inject
    Banco banco;

    @Inject
    ClientesRepository clientesRepository;

    public List<ObjectNode> listar() {
        return banco.transacao(c -> banco.consultar(c, "select * from pipeline_cards order by updated_at desc"));
    }

    public Optional<ObjectNode> detalhar(String id) {
        return banco.transacao(c -> banco.consultarUm(c, "select * from pipeline_cards where id = ?", id));
    }

    public ObjectNode salvar(JsonNode corpo) {
        if (corpo == null || !corpo.isObject()) {
            throw new InvalidRequestException("O corpo da requisição deve ser um objeto JSON");
        }
        Map<String, Object> valores = Valores.extrair(corpo, COLUNAS);

        // Se não tiver ID informado, gera novo UUID
        if (!valores.containsKey("id") || valores.get("id") == null || ((String) valores.get("id")).isBlank()) {
            valores.put("id", UUID.randomUUID().toString());
        }

        // Se o frontend enviou camelCase proximoPasso
        if (!valores.containsKey("proximo_passo") && corpo.has("proximoPasso")) {
            valores.put("proximo_passo", corpo.get("proximoPasso").asText());
        }

        // Garantir que extra é json válido e preserva assinado
        if (!valores.containsKey("extra") || valores.get("extra") == null) {
            ObjectNode extra = JsonNodeFactory.instance.objectNode();
            if (corpo.has("assinado")) {
                extra.put("assinado", corpo.get("assinado").asBoolean());
            }
            if (corpo.has("etapaManual")) {
                extra.put("etapaManual", corpo.get("etapaManual").asBoolean());
            }
            valores.put("extra", extra);
        } else if (valores.get("extra") instanceof ObjectNode extraObj && corpo.has("assinado") && !extraObj.has("assinado")) {
            extraObj.put("assinado", corpo.get("assinado").asBoolean());
        }

        return banco.transacao(c -> {
            ObjectNode salvo = banco.upsert(c, "pipeline_cards", COLUNAS, valores, "id");
            if (isContratoAssinado(corpo, salvo)) {
                clientesRepository.garantirClienteDoPipeline(c, salvo);
            }
            return salvo;
        });
    }

    public boolean excluir(String id) {
        return banco.transacao(c -> banco.executar(c, "delete from pipeline_cards where id = ?", id) > 0);
    }

    private boolean isContratoAssinado(JsonNode original, ObjectNode salvo) {
        String stage = salvo.path("stage").asText("");
        if ("planejamento".equalsIgnoreCase(stage) || "fechado".equalsIgnoreCase(stage)) {
            return true;
        }
        if (original.path("assinado").asBoolean(false)) {
            return true;
        }
        return salvo.path("extra").path("assinado").asBoolean(false);
    }
}
