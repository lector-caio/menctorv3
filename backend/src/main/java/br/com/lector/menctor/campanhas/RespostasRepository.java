package br.com.lector.menctor.campanhas;

import static br.com.lector.menctor.dados.Tipo.JSON;
import static br.com.lector.menctor.dados.Tipo.NUMERICO;
import static br.com.lector.menctor.dados.Tipo.TEXTO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import br.com.lector.menctor.api.ApiException;
import br.com.lector.menctor.api.InvalidRequestException;
import br.com.lector.menctor.dados.Banco;
import br.com.lector.menctor.dados.Tipo;
import br.com.lector.menctor.dados.Valores;

@ApplicationScoped
public class RespostasRepository {

    static final Map<String, Tipo> COLUNAS = Valores.colunas(
            "id", TEXTO,
            "campanha_id", TEXTO,
            "cpf_hash", TEXTO,
            "setor", TEXTO,
            "cargo", TEXTO,
            "media_risco", NUMERICO,
            "por_dimensao", JSON,
            "respostas_itens", JSON);

    @Inject
    Banco banco;

    public ObjectNode registrar(String campanhaId, JsonNode corpo) {
        if (corpo == null || !corpo.isObject()) {
            throw new InvalidRequestException("O corpo da requisição deve ser um objeto JSON");
        }

        // Verifica existência da campanha
        banco.transacao(c -> {
            if (banco.consultarUm(c, "select id from campanhas where id = ?", campanhaId).isEmpty()) {
                throw ApiException.naoEncontrado("Campanha não encontrada");
            }
            return null;
        });

        Map<String, Object> valores = Valores.extrair(corpo, COLUNAS);
        valores.put("campanha_id", campanhaId);

        // Suporte camelCase
        if (!valores.containsKey("cpf_hash") && corpo.has("cpfHash")) {
            valores.put("cpf_hash", corpo.get("cpfHash").asText());
        }
        if (!valores.containsKey("media_risco") && corpo.has("mediaRisco")) {
            valores.put("media_risco", Valores.converter(corpo.get("mediaRisco"), NUMERICO, "mediaRisco"));
        }
        if (!valores.containsKey("por_dimensao") && corpo.has("porDimensao")) {
            valores.put("por_dimensao", corpo.get("porDimensao"));
        }
        if (!valores.containsKey("respostas_itens") && corpo.has("respostasItens")) {
            valores.put("respostas_itens", corpo.get("respostasItens"));
        }

        if (!valores.containsKey("id") || valores.get("id") == null || ((String) valores.get("id")).isBlank()) {
            valores.put("id", UUID.randomUUID().toString());
        }

        if (!valores.containsKey("cpf_hash") || valores.get("cpf_hash") == null || ((String) valores.get("cpf_hash")).isBlank()) {
            throw new InvalidRequestException("Campo obrigatório: cpfHash ou cpf_hash");
        }

        if (!valores.containsKey("por_dimensao") || valores.get("por_dimensao") == null) {
            valores.put("por_dimensao", JsonNodeFactory.instance.objectNode());
        }
        if (!valores.containsKey("respostas_itens") || valores.get("respostas_itens") == null) {
            valores.put("respostas_itens", JsonNodeFactory.instance.arrayNode());
        }

        return banco.transacao(c -> banco.inserir(c, "campanha_respostas", COLUNAS, valores));
    }

    public List<ObjectNode> listar(String campanhaId) {
        return banco.transacao(c -> banco.consultar(c,
                "select * from campanha_respostas where campanha_id = ? order by created_at desc", campanhaId));
    }

    public boolean jaRespondeu(String campanhaId, String cpfHash) {
        if (cpfHash == null || cpfHash.isBlank()) {
            return false;
        }
        return banco.transacao(c -> banco.consultarUm(c,
                "select id from campanha_respostas where campanha_id = ? and cpf_hash = ?", campanhaId, cpfHash).isPresent());
    }

    /**
     * Consolida médias gerais, por dimensão e por setor da campanha a partir do banco PostgreSQL.
     */
    public ObjectNode obterResultado(String campanhaId) {
        List<ObjectNode> respostas = listar(campanhaId);
        int total = respostas.size();

        ObjectNode resultado = JsonNodeFactory.instance.objectNode();
        resultado.put("total", total);

        if (total == 0) {
            resultado.putNull("media");
            resultado.putArray("porDimensao");
            resultado.putArray("porSetor");
            return resultado;
        }

        BigDecimal somaMedia = BigDecimal.ZERO;
        int countComMedia = 0;
        Map<String, double[]> dims = new HashMap<>(); // [soma, n]
        Map<String, double[]> setores = new HashMap<>();

        for (ObjectNode r : respostas) {
            if (r.hasNonNull("media_risco")) {
                somaMedia = somaMedia.add(r.get("media_risco").decimalValue());
                countComMedia++;
            }
            JsonNode pDim = r.get("por_dimensao");
            if (pDim != null && pDim.isObject()) {
                pDim.fields().forEachRemaining(entry -> {
                    if (entry.getValue().isNumber()) {
                        double v = entry.getValue().doubleValue();
                        dims.computeIfAbsent(entry.getKey(), k -> new double[2]);
                        dims.get(entry.getKey())[0] += v;
                        dims.get(entry.getKey())[1] += 1;
                    }
                });
            }

            String setor = r.hasNonNull("setor") && !r.get("setor").asText().isBlank()
                    ? r.get("setor").asText()
                    : "Não informado";
            if (r.hasNonNull("media_risco")) {
                setores.computeIfAbsent(setor, k -> new double[2]);
                setores.get(setor)[0] += r.get("media_risco").doubleValue();
                setores.get(setor)[1] += 1;
            }
        }

        if (countComMedia > 0) {
            resultado.put("media", somaMedia.divide(BigDecimal.valueOf(countComMedia), 2, RoundingMode.HALF_UP));
        } else {
            resultado.putNull("media");
        }

        ArrayNode arrDims = resultado.putArray("porDimensao");
        dims.entrySet().stream()
                .map(e -> Map.entry(e.getKey(), e.getValue()[1] > 0 ? e.getValue()[0] / e.getValue()[1] : 0.0))
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .forEach(e -> {
                    ObjectNode item = arrDims.addObject();
                    item.put("name", e.getKey());
                    item.put("v", BigDecimal.valueOf(e.getValue()).setScale(2, RoundingMode.HALF_UP));
                });

        ArrayNode arrSetores = resultado.putArray("porSetor");
        setores.forEach((nome, dados) -> {
            ObjectNode item = arrSetores.addObject();
            item.put("setor", nome);
            item.put("media", dados[1] > 0 ? BigDecimal.valueOf(dados[0] / dados[1]).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
            item.put("n", (int) dados[1]);
        });

        return resultado;
    }
}
