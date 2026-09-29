package br.com.lector.menctor.denuncias;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import br.com.lector.menctor.api.ApiException;
import br.com.lector.menctor.api.JsonBody;

@Path("/api/denuncias")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DenunciasResource {

    @Inject
    DenunciasRepository denunciasRepository;

    @Inject
    ObjectMapper mapper;

    @GET
    public List<ObjectNode> listar() {
        return denunciasRepository.listar();
    }

    @GET
    @Path("/{id}")
    public ObjectNode detalhar(@PathParam("id") String id) {
        return denunciasRepository.detalhar(id)
                .orElseThrow(() -> ApiException.naoEncontrado("Denúncia não encontrada"));
    }

    @GET
    @Path("/protocolo/{protocolo}")
    public ObjectNode buscarPorProtocolo(@PathParam("protocolo") String protocolo) {
        return denunciasRepository.buscarPorProtocolo(protocolo)
                .orElseThrow(() -> ApiException.naoEncontrado("Denúncia não encontrada com o protocolo informado"));
    }

    @POST
    @Consumes(MediaType.WILDCARD)
    public Response salvar(byte[] body) {
        JsonNode corpo = JsonBody.parse(mapper, body);
        ObjectNode salvo = denunciasRepository.salvar(corpo);
        return Response.ok(salvo).build();
    }
}
