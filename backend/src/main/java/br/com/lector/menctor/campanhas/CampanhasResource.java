package br.com.lector.menctor.campanhas;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import br.com.lector.menctor.api.ApiException;
import br.com.lector.menctor.api.JsonBody;

@Path("/api/campanhas")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CampanhasResource {

    @Inject
    CampanhasRepository campanhasRepository;

    @Inject
    RespostasRepository respostasRepository;

    @Inject
    ObjectMapper mapper;

    @GET
    public List<ObjectNode> listar(@QueryParam("clienteId") String clienteId) {
        return campanhasRepository.listar(clienteId);
    }

    @GET
    @Path("/{id}")
    public ObjectNode detalhar(@PathParam("id") String id) {
        return campanhasRepository.detalhar(id)
                .orElseThrow(() -> ApiException.naoEncontrado("Campanha não encontrada"));
    }

    @POST
    @Consumes(MediaType.WILDCARD)
    public Response criar(byte[] body) {
        JsonNode corpo = JsonBody.parse(mapper, body);
        ObjectNode salva = campanhasRepository.salvar(corpo);
        return Response.status(Response.Status.CREATED).entity(salva).build();
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.WILDCARD)
    public ObjectNode atualizarPut(@PathParam("id") String id, byte[] body) {
        JsonNode corpo = JsonBody.parse(mapper, body);
        if (corpo instanceof ObjectNode obj && !obj.has("id")) {
            obj.put("id", id);
        }
        return campanhasRepository.salvar(corpo);
    }

    @PATCH
    @Path("/{id}")
    @Consumes(MediaType.WILDCARD)
    public ObjectNode atualizarPatch(@PathParam("id") String id, byte[] body) {
        JsonNode corpo = JsonBody.parse(mapper, body);
        if (corpo instanceof ObjectNode obj && !obj.has("id")) {
            obj.put("id", id);
        }
        return campanhasRepository.salvar(corpo);
    }

    @PATCH
    @Path("/{id}/status")
    @Consumes(MediaType.WILDCARD)
    public ObjectNode atualizarStatus(@PathParam("id") String id, byte[] body) {
        JsonNode corpo = JsonBody.parse(mapper, body);
        String novoStatus = corpo.path("status").asText(null);
        return campanhasRepository.atualizarStatus(id, novoStatus)
                .orElseThrow(() -> ApiException.naoEncontrado("Campanha não encontrada"));
    }

    @DELETE
    @Path("/{id}")
    public Response excluir(@PathParam("id") String id) {
        if (!campanhasRepository.excluir(id)) {
            throw ApiException.naoEncontrado("Campanha não encontrada");
        }
        return Response.noContent().build();
    }

    @POST
    @Path("/{id}/respostas")
    @Consumes(MediaType.WILDCARD)
    public Response submeterResposta(@PathParam("id") String id, byte[] body) {
        JsonNode corpo = JsonBody.parse(mapper, body);
        ObjectNode criada = respostasRepository.registrar(id, corpo);
        return Response.status(Response.Status.CREATED).entity(criada).build();
    }

    @GET
    @Path("/{id}/respostas")
    public List<ObjectNode> listarRespostas(@PathParam("id") String id) {
        return respostasRepository.listar(id);
    }

    @GET
    @Path("/{id}/resultado")
    public ObjectNode obterResultado(@PathParam("id") String id) {
        return respostasRepository.obterResultado(id);
    }

    @GET
    @Path("/{id}/ja-respondeu")
    public ObjectNode jaRespondeu(@PathParam("id") String id, @QueryParam("cpfHash") String cpfHash) {
        boolean respondeu = respostasRepository.jaRespondeu(id, cpfHash);
        return mapper.createObjectNode().put("jaRespondeu", respondeu);
    }
}
