package br.com.lector.menctor.api;

import java.util.Map;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/** Erros da API no formato do antigo server.py: {@code {"error": "mensagem"}}. */
public class ApiErrors {

    public static Response response(int status, String message) {
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(Map.of("error", message == null ? "" : message))
                .build();
    }

    @ServerExceptionMapper
    public Response apiException(ApiException e) {
        return response(e.status(), e.getMessage());
    }
}
