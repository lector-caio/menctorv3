package br.com.lector.menctor.dados;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import br.com.lector.menctor.api.InvalidRequestException;

/** Converte os campos de um corpo JSON para os valores das colunas SQL permitidas. */
public final class Valores {

    private Valores() {
    }

    /** Colunas em ordem, a partir de pares nome/tipo. */
    public static Map<String, Tipo> colunas(Object... pares) {
        Map<String, Tipo> map = new LinkedHashMap<>();
        for (int i = 0; i < pares.length; i += 2) {
            map.put((String) pares[i], (Tipo) pares[i + 1]);
        }
        return map;
    }

    /**
     * Só os campos presentes no corpo e permitidos na tabela, já convertidos (um campo ausente não é
     * alterado; um campo enviado como null grava NULL). Campos desconhecidos são ignorados.
     */
    public static Map<String, Object> extrair(JsonNode corpo, Map<String, Tipo> permitidas) {
        Map<String, Object> valores = new LinkedHashMap<>();
        if (corpo == null || !corpo.isObject()) {
            return valores;
        }
        for (Map.Entry<String, Tipo> coluna : permitidas.entrySet()) {
            if (corpo.has(coluna.getKey())) {
                valores.put(coluna.getKey(), converter(corpo.get(coluna.getKey()), coluna.getValue(), coluna.getKey()));
            }
        }
        return valores;
    }

    public static Object converter(JsonNode v, Tipo tipo, String campo) {
        if (v == null || v.isNull() || v.isMissingNode()) {
            return null;
        }
        return switch (tipo) {
            case JSON -> v;
            case TEXTO -> {
                if (v.isContainerNode()) {
                    throw new InvalidRequestException("Campo '" + campo + "' deve ser texto");
                }
                yield v.asText();
            }
            case INTEIRO -> inteiro(v, campo);
            case NUMERICO -> numerico(v, campo);
            case BOOLEANO -> {
                if (v.isBoolean()) {
                    yield v.booleanValue();
                }
                if (v.isTextual() && (v.asText().equalsIgnoreCase("true") || v.asText().equalsIgnoreCase("false"))) {
                    yield Boolean.parseBoolean(v.asText());
                }
                throw new InvalidRequestException("Campo '" + campo + "' deve ser verdadeiro ou falso");
            }
        };
    }

    private static Integer inteiro(JsonNode v, String campo) {
        BigDecimal n = numerico(v, campo);
        if (n == null) {
            return null;
        }
        try {
            // valores com casas decimais (ex.: 3500.5) são arredondados, como o PostgreSQL faria
            return n.setScale(0, java.math.RoundingMode.HALF_UP).intValueExact();
        } catch (ArithmeticException e) {
            throw new InvalidRequestException("Campo '" + campo + "' fora do intervalo permitido");
        }
    }

    private static BigDecimal numerico(JsonNode v, String campo) {
        if (v.isNumber()) {
            return v.decimalValue();
        }
        if (v.isTextual()) {
            String s = v.asText().strip();
            if (s.isEmpty()) {
                return null;
            }
            try {
                return new BigDecimal(s);
            } catch (NumberFormatException e) {
                // cai no erro abaixo
            }
        }
        throw new InvalidRequestException("Campo '" + campo + "' deve ser um número");
    }
}
