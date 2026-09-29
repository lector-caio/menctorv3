package br.com.lector.menctor.scoring;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import com.fasterxml.jackson.databind.node.ObjectNode;

@Path("/api/clientes/{clienteId}/scoring")
@Produces(MediaType.APPLICATION_JSON)
public class ClienteScoringResource {

    @Inject
    ScoringRepository scoringRepository;

    @GET
    @Path("/historico")
    public List<ObjectNode> listarHistorico(@PathParam("clienteId") String clienteId) {
        return scoringRepository.listarHistoricoCliente(clienteId);
    }
}
