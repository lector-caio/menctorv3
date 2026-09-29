package br.com.lector.menctor.relatorio;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import br.com.lector.menctor.api.InvalidRequestException;

/**
 * Converte o JSON de {@code POST /api/gerar-relatorio} em {@link RelatorioConfig}, com os mesmos
 * campos obrigatórios e valores padrão do antigo {@code server.py}.
 */
public final class RelatorioPayload {

    private static final List<String> REQUIRED = List.of("empresa", "respondentes", "total_colaboradores", "dimensoes");

    /** Padrões aplicados quando a chave não vem no JSON (idênticos aos do server.py). */
    private static final Map<String, String> DEFAULTS = Map.ofEntries(
            Map.entry("codigo", "PCL-Q2"),
            Map.entry("titulo_linha1", "Pesquisa de Clima Organizacional —"),
            Map.entry("titulo_linha2", "2° Trimestre/2026"),
            Map.entry("descricao", "Pesquisa trimestral combinando dimensões COPSOQ II com indicadores de clima e engajamento."),
            Map.entry("periodo", "Abril a Junho - 2026"),
            Map.entry("aplicacao", "15/04/2026 a 30/04/2026"),
            Map.entry("responsavel", "Comite de Pessoas & Cultura"),
            Map.entry("taxa_adesao", "0%"),
            Map.entry("foco", "Toda a organização"),
            Map.entry("emissao", "27 de maio de 2026"),
            Map.entry("empresa", "Empresa"),
            Map.entry("cnpj", "XX.XXX.XXX/0001-XX"),
            Map.entry("endereco", "—"),
            Map.entry("data_avaliacao", "01/04/2026"),
            Map.entry("rt_nome", "Caio Guedes"),
            Map.entry("rt_registro", "CRP-06/12345"),
            Map.entry("rt_especialidade", "Psicologia Organizacional"),
            Map.entry("rt_contato", "(11) 99999-9999"),
            Map.entry("output_filename", "relatorio_psicossocial.pdf"));

    private RelatorioPayload() {
    }

    public static RelatorioConfig parse(JsonNode data) {
        if (isEmpty(data)) {
            throw new InvalidRequestException("Nenhum dado foi enviado");
        }
        List<String> missing = new ArrayList<>();
        for (String field : REQUIRED) {
            if (!data.isObject() || !data.has(field)) {
                missing.add(field);
            }
        }
        if (!missing.isEmpty()) {
            throw new InvalidRequestException("Campos obrigatórios faltando: " + String.join(", ", missing));
        }
        return new RelatorioConfig(
                text(data, "codigo"),
                text(data, "titulo_linha1"),
                text(data, "titulo_linha2"),
                text(data, "descricao"),
                text(data, "periodo"),
                text(data, "aplicacao"),
                text(data, "responsavel"),
                integer(data, "respondentes"),
                integer(data, "total_colaboradores"),
                text(data, "taxa_adesao"),
                text(data, "foco"),
                text(data, "emissao"),
                text(data, "empresa"),
                text(data, "cnpj"),
                text(data, "endereco"),
                text(data, "data_avaliacao"),
                text(data, "rt_nome"),
                text(data, "rt_registro"),
                text(data, "rt_especialidade"),
                text(data, "rt_contato"),
                text(data, "output_filename"),
                dimensoes(data.get("dimensoes")));
    }

    /** "Falso" no sentido do Python ({@code if not data}): nulo, {}, [], "", 0 ou false. */
    private static boolean isEmpty(JsonNode data) {
        if (data == null || data.isNull() || data.isMissingNode()) {
            return true;
        }
        if (data.isContainerNode()) {
            return data.isEmpty();
        }
        if (data.isTextual()) {
            return data.asText().isEmpty();
        }
        if (data.isNumber()) {
            return data.asDouble() == 0;
        }
        return data.isBoolean() && !data.asBoolean();
    }

    /** Texto do campo; ausente ou nulo usa o padrão. */
    private static String text(JsonNode data, String field) {
        JsonNode v = data.get(field);
        if (v == null || v.isNull()) {
            return DEFAULTS.get(field);
        }
        if (v.isContainerNode()) {
            throw new InvalidRequestException("Campo '" + field + "' deve ser texto");
        }
        return v.asText();
    }

    /** Inteiro como o {@code int()} do Python: aceita número (truncado) ou texto numérico. */
    private static long integer(JsonNode data, String field) {
        JsonNode v = data.get(field);
        String error = "Campo '" + field + "' deve ser um número inteiro";
        if (v.isBoolean()) {
            return v.asBoolean() ? 1 : 0;
        }
        if (v.isNumber()) {
            double d = v.doubleValue();
            if (v.isIntegralNumber() && v.canConvertToLong()) {
                return v.longValue();
            }
            if (!Double.isFinite(d) || Math.abs(d) >= 0x1p63) {
                throw new InvalidRequestException(error);
            }
            return (long) d;
        }
        if (v.isTextual()) {
            try {
                return Long.parseLong(v.asText().strip());
            } catch (NumberFormatException e) {
                throw new InvalidRequestException(error);
            }
        }
        throw new InvalidRequestException(error);
    }

    private static List<RelatorioConfig.Dimensao> dimensoes(JsonNode node) {
        if (node == null || !node.isArray()) {
            throw new InvalidRequestException("Campo 'dimensoes' deve ser uma lista de {\"nome\", \"score\"}");
        }
        List<RelatorioConfig.Dimensao> result = new ArrayList<>(node.size());
        for (JsonNode d : node) {
            JsonNode nome = d.get("nome");
            JsonNode score = d.get("score");
            if (!d.isObject() || nome == null || nome.isNull() || nome.isContainerNode() || score == null) {
                throw new InvalidRequestException("Cada dimensão precisa de 'nome' (texto) e 'score' (número)");
            }
            result.add(new RelatorioConfig.Dimensao(nome.asText(), score(score)));
        }
        return result;
    }

    private static double score(JsonNode v) {
        double value;
        if (v.isNumber()) {
            value = v.doubleValue();
        } else if (v.isBoolean()) {
            value = v.asBoolean() ? 1 : 0;
        } else if (v.isTextual()) {
            try {
                value = Double.parseDouble(v.asText().strip());
            } catch (NumberFormatException e) {
                value = Double.NaN;
            }
        } else {
            value = Double.NaN;
        }
        if (!Double.isFinite(value)) {
            throw new InvalidRequestException("O 'score' de cada dimensão deve ser um número");
        }
        return value;
    }
}
