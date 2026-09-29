package br.com.lector.menctor.instrumentos;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import br.com.lector.menctor.api.ApiException;
import br.com.lector.menctor.api.JsonBody;

@Path("/api/instrumentos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class InstrumentosResource {

    @Inject
    InstrumentosRepository instrumentosRepository;

    @Inject
    ObjectMapper mapper;

    @GET
    public List<ObjectNode> listar() {
        return instrumentosRepository.listarInstrumentos();
    }

    @GET
    @Path("/{id}")
    public ObjectNode detalhar(@PathParam("id") String id) {
        return instrumentosRepository.detalharInstrumento(id)
                .orElseThrow(() -> ApiException.naoEncontrado("Instrumento não encontrado"));
    }

    @GET
    @Path("/{id}/dimensoes")
    public List<ObjectNode> listarDimensoes(@PathParam("id") String id) {
        return instrumentosRepository.listarDimensoes(id);
    }

    @PATCH
    @Path("/questoes/{id}/toggle")
    @Consumes(MediaType.WILDCARD)
    public ObjectNode toggleQuestao(@PathParam("id") String id, byte[] body) {
        JsonNode corpo = JsonBody.parse(mapper, body);
        boolean ativo = corpo.path("ativo").asBoolean(true);
        return instrumentosRepository.toggleQuestao(id, ativo)
                .orElseThrow(() -> ApiException.naoEncontrado("Questão não encontrada"));
    }

    @PATCH
    @Path("/dimensoes/{id}/toggle")
    @Consumes(MediaType.WILDCARD)
    public ObjectNode toggleDimensao(@PathParam("id") String id, byte[] body) {
        JsonNode corpo = JsonBody.parse(mapper, body);
        boolean ativo = corpo.path("ativo").asBoolean(true);
        return instrumentosRepository.toggleDimensao(id, ativo)
                .orElseThrow(() -> ApiException.naoEncontrado("Dimensão não encontrada"));
    }
}
