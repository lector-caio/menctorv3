package br.com.lector.menctor.api;

/** Erro com status HTTP definido: vira {@code {"error": mensagem}} com esse status. */
public class ApiException extends RuntimeException {

    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }

    public static ApiException naoEncontrado(String message) {
        return new ApiException(404, message);
    }

    public static ApiException conflito(String message) {
        return new ApiException(409, message);
    }

    public static ApiException unprocessableEntity(String message) {
        return new ApiException(422, message);
    }

    public static ApiException requisicaoInvalida(String message) {
        return new ApiException(400, message);
    }
}
