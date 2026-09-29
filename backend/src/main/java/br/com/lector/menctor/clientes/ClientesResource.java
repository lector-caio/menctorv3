package br.com.lector.menctor.clientes;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
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

@Path("/api/clientes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ClientesResource {

    @Inject
    ClientesRepository clientesRepository;

    @Inject
    ObjectMapper mapper;

    @GET
    public List<ObjectNode> listar() {
        return clientesRepository.listar();
    }

    @GET
    @Path("/etapas")
    public List<ObjectNode> etapas() {
        return clientesRepository.etapas();
    }

    @GET
    @Path("/{id}")
    public ObjectNode detalhar(@PathParam("id") String id) {
        return clientesRepository.detalhar(id)
                .orElseThrow(() -> ApiException.naoEncontrado("Cliente não encontrado"));
    }

    @POST
    @Consumes(MediaType.WILDCARD)
    public Response criar(byte[] body) {
        JsonNode corpo = JsonBody.parse(mapper, body);
        ObjectNode criado = clientesRepository.criar(corpo);
        return Response.status(Response.Status.CREATED).entity(criado).build();
    }

    @PATCH
    @Path("/{id}")
    @Consumes(MediaType.WILDCARD)
    public ObjectNode atualizarPatch(@PathParam("id") String id, byte[] body) {
        JsonNode corpo = JsonBody.parse(mapper, body);
        return clientesRepository.atualizar(id, corpo)
                .orElseThrow(() -> ApiException.naoEncontrado("Cliente não encontrado"));
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.WILDCARD)
    public ObjectNode atualizarPut(@PathParam("id") String id, byte[] body) {
        JsonNode corpo = JsonBody.parse(mapper, body);
        return clientesRepository.atualizar(id, corpo)
                .orElseThrow(() -> ApiException.naoEncontrado("Cliente não encontrado"));
    }

    @PUT
    @Path("/{id}/etapas/{numero}")
    @Consumes(MediaType.WILDCARD)
    public ObjectNode salvarEtapaPut(@PathParam("id") String id, @PathParam("numero") int numero, byte[] body) {
        JsonNode corpo = JsonBody.parse(mapper, body);
        return clientesRepository.salvarEtapa(id, numero, corpo);
    }

    @POST
    @Path("/{id}/etapas/{numero}")
    @Consumes(MediaType.WILDCARD)
    public ObjectNode salvarEtapaPost(@PathParam("id") String id, @PathParam("numero") int numero, byte[] body) {
        JsonNode corpo = JsonBody.parse(mapper, body);
        return clientesRepository.salvarEtapa(id, numero, corpo);
    }

    @PUT
    @Path("/{id}/cadastro")
    @Consumes(MediaType.WILDCARD)
    public ObjectNode salvarCadastroPut(@PathParam("id") String id, byte[] body) {
        JsonNode corpo = JsonBody.parse(mapper, body);
        return clientesRepository.salvarCadastro(id, corpo);
    }

    @POST
    @Path("/{id}/cadastro")
    @Consumes(MediaType.WILDCARD)
    public ObjectNode salvarCadastroPost(@PathParam("id") String id, byte[] body) {
        JsonNode corpo = JsonBody.parse(mapper, body);
        return clientesRepository.salvarCadastro(id, corpo);
    }
}
