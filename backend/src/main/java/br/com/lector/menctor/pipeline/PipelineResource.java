package br.com.lector.menctor.pipeline;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
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

@Path("/api/pipeline")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PipelineResource {

    @Inject
    PipelineRepository pipelineRepository;

    @Inject
    ObjectMapper mapper;

    @GET
    public List<ObjectNode> listar() {
        return pipelineRepository.listar();
    }

    @GET
    @Path("/{id}")
    public ObjectNode detalhar(@PathParam("id") String id) {
        return pipelineRepository.detalhar(id)
                .orElseThrow(() -> ApiException.naoEncontrado("Card do pipeline não encontrado"));
    }

    @POST
    @Consumes(MediaType.WILDCARD)
    public Response salvar(byte[] body) {
        JsonNode corpo = JsonBody.parse(mapper, body);
        ObjectNode salvo = pipelineRepository.salvar(corpo);
        return Response.ok(salvo).build();
    }

    @DELETE
    @Path("/{id}")
    public Response excluir(@PathParam("id") String id) {
        if (!pipelineRepository.excluir(id)) {
            throw ApiException.naoEncontrado("Card do pipeline não encontrado");
        }
        return Response.noContent().build();
    }
}
