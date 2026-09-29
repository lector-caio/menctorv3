package br.com.lector.menctor.health;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/health")
public class HealthResource {

    public record Health(String status, String message) {
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Health health() {
        return new Health("ok", "Servidor de relatórios psicossociais está ativo");
    }
}
