package br.com.lector.menctor.email;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;

/** Envio pelo SMTP configurado nas variáveis MAIL_* (STARTTLS + login, como o server.py). */
@ApplicationScoped
public class EmailService {

    /** Texto enviado quando a mensagem só tem versão HTML. */
    static final String FALLBACK_TEXT = "Este e-mail possui uma versao HTML.";

    @Inject
    Mailer mailer;

    @ConfigProperty(name = "menctor.mail.host")
    Optional<String> host;

    @ConfigProperty(name = "menctor.mail.username")
    Optional<String> username;

    @ConfigProperty(name = "menctor.mail.password")
    Optional<String> password;

    @ConfigProperty(name = "menctor.mail.from-name", defaultValue = "Menctor")
    String fromName;

    public boolean isConfigured() {
        return present(host) && present(username) && present(password);
    }

    public void send(EmailRequest request) {
        Mail mail = new Mail()
                .setFrom(sender(fromName, username.orElseThrow()))
                .setTo(recipients(request.to()))
                .setSubject(request.subject())
                .setText(request.text().isEmpty() ? FALLBACK_TEXT : request.text());
        if (!request.html().isEmpty()) {
            mail.setHtml(request.html());
        }
        if (!request.replyTo().isEmpty()) {
            mail.setReplyTo(request.replyTo());
        }
        mailer.send(mail);
    }

    /** "Nome <endereço>", com o nome entre aspas se tiver caracteres especiais. */
    static String sender(String name, String address) {
        String n = name.strip();
        if (n.isEmpty()) {
            return address;
        }
        if (n.matches(".*[()<>\\[\\]:;@\\\\,.\"].*")) {
            n = "\"" + n.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
        return n + " <" + address + ">";
    }

    /** Aceita um ou vários destinatários separados por vírgula, como o cabeçalho To do Python. */
    static List<String> recipients(String to) {
        List<String> list = new ArrayList<>();
        for (String part : to.split(",")) {
            if (!part.isBlank()) {
                list.add(part.strip());
            }
        }
        return list;
    }

    private static boolean present(Optional<String> value) {
        return value.filter(v -> !v.isBlank()).isPresent();
    }
}
