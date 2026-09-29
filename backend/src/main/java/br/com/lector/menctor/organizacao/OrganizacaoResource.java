package br.com.lector.menctor.organizacao;

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

@Path("/api/clientes/{clienteId}/organizacao")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OrganizacaoResource {

    @Inject
    OrganizacaoRepository repository;

    @GET
    public JsonNode resumo(@PathParam("clienteId") String clienteId) {
        return repository.obterResumoCompleto(clienteId);
    }

    @GET
    @Path("/{categoria}")
    public List<com.fasterxml.jackson.databind.node.ObjectNode> listar(
            @PathParam("clienteId") String clienteId,
            @PathParam("categoria") String categoria,
            @QueryParam("apenasAtivos") Boolean apenasAtivos) {
        return repository.listar(clienteId, categoria, apenasAtivos);
    }

    @GET
    @Path("/{categoria}/{id}")
    public JsonNode obter(
            @PathParam("clienteId") String clienteId,
            @PathParam("categoria") String categoria,
            @PathParam("id") String id) {
        return repository.obter(clienteId, categoria, id);
    }

    @POST
    @Path("/{categoria}")
    public Response criar(
            @PathParam("clienteId") String clienteId,
            @PathParam("categoria") String categoria,
            JsonNode payload) {
        JsonNode criado = repository.criar(clienteId, categoria, payload);
        return Response.status(Response.Status.CREATED).entity(criado).build();
    }

    @PUT
    @Path("/{categoria}/{id}")
    public JsonNode atualizar(
            @PathParam("clienteId") String clienteId,
            @PathParam("categoria") String categoria,
            @PathParam("id") String id,
            JsonNode payload) {
        return repository.atualizar(clienteId, categoria, id, payload);
    }

    @PATCH
    @Path("/{categoria}/{id}/toggle")
    public JsonNode alternarAtivo(
            @PathParam("clienteId") String clienteId,
            @PathParam("categoria") String categoria,
            @PathParam("id") String id) {
        return repository.alternarAtivo(clienteId, categoria, id);
    }

    @DELETE
    @Path("/{categoria}/{id}")
    public Response excluir(
            @PathParam("clienteId") String clienteId,
            @PathParam("categoria") String categoria,
            @PathParam("id") String id) {
        repository.excluir(clienteId, categoria, id);
        return Response.noContent().build();
    }
}
