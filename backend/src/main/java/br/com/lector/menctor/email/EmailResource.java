package br.com.lector.menctor.email;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.lector.menctor.api.ApiErrors;
import br.com.lector.menctor.api.JsonBody;

/** E-mails transacionais do pipeline (proposta, contrato). */
@Path("/api/send-email")
public class EmailResource {

    private static final Logger LOG = Logger.getLogger(EmailResource.class);

    public record EmailSent(boolean ok, String to, String subject) {
    }

    @Inject
    ObjectMapper mapper;

    @Inject
    EmailService emailService;

    @POST
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.APPLICATION_JSON)
    public Response send(byte[] body) {
        EmailRequest request = EmailRequest.from(JsonBody.parse(mapper, body));
        if (!emailService.isConfigured()) {
            return ApiErrors.response(500, "SMTP nao configurado. Verifique MAIL_HOST, MAIL_USERNAME e MAIL_PASSWORD.");
        }
        try {
            emailService.send(request);
        } catch (RuntimeException e) {
            LOG.errorf(e, "Erro ao enviar e-mail para %s", request.to());
            return ApiErrors.response(500, "Erro ao enviar e-mail: " + describe(e));
        }
        return Response.ok(new EmailSent(true, request.to(), request.subject())).build();
    }

    /** Mensagem mais útil da cadeia de causas (o Mailer embrulha o erro do SMTP). */
    private static String describe(Throwable e) {
        Throwable t = e;
        String message = e.getMessage();
        while (true) {
            if (t instanceof io.smallrye.mutiny.TimeoutException || t instanceof java.util.concurrent.TimeoutException) {
                return "tempo esgotado aguardando o servidor SMTP (quarkus.mailer.timeout)";
            }
            if (t.getCause() == null || t.getCause() == t) {
                break;
            }
            t = t.getCause();
            if (t.getMessage() != null) {
                message = t.getMessage();
            }
        }
        if (message == null || message.isBlank()) {
            message = t.getClass().getSimpleName();
        }
        return message;
    }
}
