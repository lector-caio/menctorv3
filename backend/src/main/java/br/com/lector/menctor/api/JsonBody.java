package br.com.lector.menctor.api;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;

/** Leitura do corpo JSON das requisições; corpo vazio vira {@code null} JSON. */
public final class JsonBody {

    private JsonBody() {
    }

    public static JsonNode parse(ObjectMapper mapper, byte[] body) {
        if (body == null || body.length == 0) {
            return NullNode.getInstance();
        }
        try {
            JsonNode node = mapper.readTree(body);
            return node == null || node.isMissingNode() ? NullNode.getInstance() : node;
        } catch (JsonProcessingException e) {
            throw new InvalidRequestException("JSON inválido: " + e.getOriginalMessage());
        } catch (IOException e) {
            throw new InvalidRequestException("Não foi possível ler o corpo da requisição");
        }
    }
}
