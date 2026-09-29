package br.com.lector.menctor.email;

import com.fasterxml.jackson.databind.JsonNode;

import br.com.lector.menctor.api.InvalidRequestException;

/** Corpo de {@code POST /api/send-email}: {@code {to, subject, html, text, replyTo}}. */
public record EmailRequest(String to, String subject, String html, String text, String replyTo) {

    static EmailRequest from(JsonNode data) {
        EmailRequest r = new EmailRequest(
                field(data, "to").strip(),
                field(data, "subject").strip(),
                field(data, "html"),
                field(data, "text"),
                field(data, "replyTo").strip());
        if (r.to().isEmpty() || r.subject().isEmpty() || (r.html().isEmpty() && r.text().isEmpty())) {
            throw new InvalidRequestException("Campos obrigatorios: to, subject e html/text");
        }
        return r;
    }

    private static String field(JsonNode data, String name) {
        JsonNode v = data != null && data.isObject() ? data.get(name) : null;
        return v == null || v.isNull() || v.isContainerNode() ? "" : v.asText();
    }
}
