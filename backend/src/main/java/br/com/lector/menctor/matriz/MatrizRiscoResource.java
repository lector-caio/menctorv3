package br.com.lector.menctor.matriz;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Path("/api/clientes/{clienteId}/matriz")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MatrizRiscoResource {

    @Inject
    MatrizRiscoRepository repository;

    @GET
    public ObjectNode obterMatrizComVersaoPublicada(@PathParam("clienteId") String clienteId) {
        ObjectNode matriz = repository.obterOuCriarMatriz(clienteId);
        ObjectNode versaoPublicada = repository.obterVersaoPublicada(clienteId);
        matriz.set("versaoPublicada", repository.obterVersaoDetalhada(clienteId, versaoPublicada.path("id").asText()));
        return matriz;
    }

    @GET
    @Path("/versoes")
    public List<ObjectNode> listarVersoes(@PathParam("clienteId") String clienteId) {
        return repository.listarVersoes(clienteId);
    }

    @GET
    @Path("/versoes/{versaoId}")
    public ObjectNode obterVersao(
            @PathParam("clienteId") String clienteId,
            @PathParam("versaoId") String versaoId) {
        return repository.obterVersaoDetalhada(clienteId, versaoId);
    }

    @POST
    @Path("/versoes")
    public Response criarVersao(
            @PathParam("clienteId") String clienteId,
            JsonNode payload) {
        ObjectNode versao = repository.criarVersao(clienteId, payload);
        return Response.status(Response.Status.CREATED).entity(versao).build();
    }

    @PUT
    @Path("/versoes/{versaoId}")
    public ObjectNode atualizarVersao(
            @PathParam("clienteId") String clienteId,
            @PathParam("versaoId") String versaoId,
            JsonNode payload) {
        return repository.atualizarVersao(clienteId, versaoId, payload);
    }

    @POST
    @Path("/versoes/{versaoId}/publicar")
    @Consumes(MediaType.WILDCARD)
    public ObjectNode publicarVersao(
            @PathParam("clienteId") String clienteId,
            @PathParam("versaoId") String versaoId) {
        return repository.publicarVersao(clienteId, versaoId);
    }

    @GET
    @Path("/calcular")
    public ObjectNode calcularScore(
            @PathParam("clienteId") String clienteId,
            @QueryParam("probabilidade") Double probabilidade,
            @QueryParam("severidade") Double severidade) {
        double p = probabilidade != null ? probabilidade : 3.0;
        double s = severidade != null ? severidade : 3.0;
        return repository.calcularClassificacao(clienteId, p, s);
    }
}
