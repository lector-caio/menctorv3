package br.com.lector.menctor.planoacao;

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
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import br.com.lector.menctor.api.ApiException;
import br.com.lector.menctor.api.JsonBody;

@Path("/api/campanhas/{id}/plano-acao")
@Produces(MediaType.APPLICATION_JSON)
public class PlanoAcaoResource {

    @Inject
    PlanoAcaoService planoAcaoService;

    @Inject
    PlanoAcaoRepository planoAcaoRepository;

    @Inject
    ObjectMapper mapper;

    private String resolverCliente(String h, String q1, String q2) {
        if (h != null && !h.isBlank()) return h.trim();
        if (q1 != null && !q1.isBlank()) return q1.trim();
        if (q2 != null && !q2.isBlank()) return q2.trim();
        return null;
    }

    @GET
    public ObjectNode obterPlano(
            @PathParam("id") String campanhaId,
            @HeaderParam("X-Cliente-Id") String headerClienteId,
            @QueryParam("clienteId") String queryClienteId,
            @QueryParam("cliente_id") String queryClienteIdSnake) {
        String cid = resolverCliente(headerClienteId, queryClienteId, queryClienteIdSnake);
        return planoAcaoService.obterPlano(campanhaId, cid);
    }

    @POST
    @Path("/gerar")
    @Consumes(MediaType.WILDCARD)
    public Response gerarPlano(
            @PathParam("id") String campanhaId,
            @QueryParam("regenerar") Boolean regenerar,
            @HeaderParam("X-Cliente-Id") String headerClienteId,
            @QueryParam("clienteId") String queryClienteId,
            @QueryParam("cliente_id") String queryClienteIdSnake) {
        boolean reg = regenerar != null && regenerar;
        String cid = resolverCliente(headerClienteId, queryClienteId, queryClienteIdSnake);
        ObjectNode plano = planoAcaoService.gerarPlano(campanhaId, reg, cid);
        return Response.status(Response.Status.CREATED).entity(plano).build();
    }

    @POST
    @Path("/regenerar")
    @Consumes(MediaType.WILDCARD)
    public Response regenerarPlano(
            @PathParam("id") String campanhaId,
            @HeaderParam("X-Cliente-Id") String headerClienteId,
            @QueryParam("clienteId") String queryClienteId,
            @QueryParam("cliente_id") String queryClienteIdSnake) {
        String cid = resolverCliente(headerClienteId, queryClienteId, queryClienteIdSnake);
        ObjectNode plano = planoAcaoService.gerarPlano(campanhaId, true, cid);
        return Response.ok(plano).build();
    }

    @GET
    @Path("/resumo")
    public ObjectNode obterResumo(
            @PathParam("id") String campanhaId,
            @HeaderParam("X-Cliente-Id") String headerClienteId,
            @QueryParam("clienteId") String queryClienteId,
            @QueryParam("cliente_id") String queryClienteIdSnake) {
        String cid = resolverCliente(headerClienteId, queryClienteId, queryClienteIdSnake);
        return planoAcaoService.obterResumo(campanhaId, cid);
    }

    @POST
    @Path("/acoes")
    @Consumes(MediaType.WILDCARD)
    public Response criarAcaoManual(@PathParam("id") String campanhaId, byte[] body) {
        JsonNode dados = JsonBody.parse(mapper, body);
        ObjectNode criada = planoAcaoService.criarAcaoManual(campanhaId, dados);
        return Response.status(Response.Status.CREATED).entity(criada).build();
    }

    @PUT
    @Path("/acoes/{acaoId}")
    @Consumes(MediaType.WILDCARD)
    public ObjectNode atualizarAcao(
            @PathParam("id") String campanhaId,
            @PathParam("acaoId") String acaoId,
            byte[] body) {
        JsonNode dados = JsonBody.parse(mapper, body);
        return planoAcaoService.atualizarAcao(campanhaId, acaoId, dados);
    }

    @PATCH
    @Path("/acoes/{acaoId}/status")
    @Consumes(MediaType.WILDCARD)
    public ObjectNode alterarStatus(
            @PathParam("id") String campanhaId,
            @PathParam("acaoId") String acaoId,
            byte[] body) {
        JsonNode dados = JsonBody.parse(mapper, body);
        if (dados == null || !dados.hasNonNull("status")) {
            throw ApiException.requisicaoInvalida("Campo 'status' é obrigatório");
        }
        String novoStatus = dados.path("status").asText();
        String motivo = dados.hasNonNull("motivo") ? dados.path("motivo").asText() : null;
        String usuario = dados.hasNonNull("usuario") ? dados.path("usuario").asText() : "sistema";
        return planoAcaoService.alterarStatus(campanhaId, acaoId, novoStatus, motivo, usuario);
    }

    @DELETE
    @Path("/acoes/{acaoId}")
    public Response excluirAcao(
            @PathParam("id") String campanhaId,
            @PathParam("acaoId") String acaoId,
            @QueryParam("motivo") String motivo) {
        planoAcaoService.excluirAcao(campanhaId, acaoId, motivo);
        return Response.noContent().build();
    }

    @GET
    @Path("/acoes/{acaoId}/historico")
    public List<ObjectNode> listarHistorico(
            @PathParam("id") String campanhaId,
            @PathParam("acaoId") String acaoId) {
        return planoAcaoRepository.listarHistoricoAcao(acaoId);
    }
}
