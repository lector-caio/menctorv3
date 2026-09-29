package br.com.lector.menctor.denuncias;

import static br.com.lector.menctor.dados.Tipo.BOOLEANO;
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
public class DenunciasRepository {

    static final Map<String, Tipo> COLUNAS = Valores.colunas(
            "id", TEXTO,
            "protocolo", TEXTO,
            "cliente_id", TEXTO,
            "data", TEXTO,
            "status", TEXTO,
            "gravidade", TEXTO,
            "tipo_id", TEXTO,
            "natureza", TEXTO,
            "anonimo", BOOLEANO,
            "denunciante", TEXTO,
            "area", TEXTO,
            "relato", TEXTO,
            "evidencias", JSON,
            "admissibilidade", TEXTO,
            "prazo_final", TEXTO,
            "parecer", TEXTO,
            "resultado", TEXTO,
            "recomendacoes", TEXTO,
            "andamentos", JSON,
            "mensagens", JSON,
            "audit_log", JSON);

    @Inject
    Banco banco;

    public List<ObjectNode> listar() {
        return banco.transacao(c -> banco.consultar(c, "select * from denuncias order by data desc, created_at desc"));
    }

    public Optional<ObjectNode> detalhar(String id) {
        return banco.transacao(c -> banco.consultarUm(c, "select * from denuncias where id = ?", id));
    }

    public Optional<ObjectNode> buscarPorProtocolo(String protocolo) {
        if (protocolo == null || protocolo.isBlank()) {
            return Optional.empty();
        }
        return banco.transacao(c -> banco.consultarUm(c,
                "select * from denuncias where upper(trim(protocolo)) = upper(trim(?))", protocolo));
    }

    public ObjectNode salvar(JsonNode corpo) {
        if (corpo == null || !corpo.isObject()) {
            throw new InvalidRequestException("O corpo da requisição deve ser um objeto JSON");
        }
        Map<String, Object> valores = Valores.extrair(corpo, COLUNAS);

        // Suporte a campos camelCase comuns vindos do frontend
        mapearSePresente(corpo, valores, "clienteId", "cliente_id", TEXTO);
        mapearSePresente(corpo, valores, "tipoId", "tipo_id", TEXTO);
        mapearSePresente(corpo, valores, "prazoFinal", "prazo_final", TEXTO);
        mapearSePresente(corpo, valores, "auditLog", "audit_log", JSON);

        if (!valores.containsKey("id") || valores.get("id") == null || ((String) valores.get("id")).isBlank()) {
            valores.put("id", UUID.randomUUID().toString());
        }

        if (!valores.containsKey("protocolo") || valores.get("protocolo") == null || ((String) valores.get("protocolo")).isBlank()) {
            throw new InvalidRequestException("Campo obrigatório: protocolo");
        }

        // Garante arrays padrão vazios em campos JSON
        garantirArray(valores, "evidencias");
        garantirArray(valores, "andamentos");
        garantirArray(valores, "mensagens");
        garantirArray(valores, "audit_log");

        return banco.transacao(c -> banco.upsert(c, "denuncias", COLUNAS, valores, "id"));
    }

    private void mapearSePresente(JsonNode corpo, Map<String, Object> valores, String camel, String snake, Tipo tipo) {
        if (!valores.containsKey(snake) && corpo.has(camel)) {
            valores.put(snake, Valores.converter(corpo.get(camel), tipo, camel));
        }
    }

    private void garantirArray(Map<String, Object> valores, String campo) {
        if (!valores.containsKey(campo) || valores.get(campo) == null) {
            valores.put(campo, JsonNodeFactory.instance.arrayNode());
        }
    }
}
