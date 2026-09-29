package br.com.lector.menctor.scoring;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.fasterxml.jackson.databind.node.ObjectNode;

@Path("/api/campanhas/{id}/scoring")
@Produces(MediaType.APPLICATION_JSON)
public class ScoringResource {

    @Inject
    ScoringRepository scoringRepository;

    @GET
    public ObjectNode obterScoring(@PathParam("id") String campanhaId) {
        return scoringRepository.obterOuCalcularScoring(campanhaId, false);
    }

    @POST
    @Path("/recalcular")
    @Consumes(MediaType.WILDCARD)
    public ObjectNode recalcularScoring(@PathParam("id") String campanhaId) {
        return scoringRepository.calcularEGravarScoring(campanhaId);
    }

    @GET
    @Path("/dimensoes")
    public Response obterDimensoes(@PathParam("id") String campanhaId) {
        ObjectNode scoring = scoringRepository.obterOuCalcularScoring(campanhaId, false);
        return Response.ok(scoring.path("dimensoes")).build();
    }

    @GET
    @Path("/recortes")
    public Response obterRecortes(@PathParam("id") String campanhaId) {
        ObjectNode scoring = scoringRepository.obterOuCalcularScoring(campanhaId, false);
        return Response.ok(scoring.path("recortes")).build();
    }
}
