package br.com.lector.menctor.api;

/** Requisição inválida: vira HTTP 400 com {@code {"error": mensagem}}. */
public class InvalidRequestException extends ApiException {

    public InvalidRequestException(String message) {
        super(400, message);
    }
}
