package br.com.lector.menctor.relatorio;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.lector.menctor.api.ApiErrors;
import br.com.lector.menctor.api.JsonBody;
import br.com.lector.menctor.pdf.LayoutException;

@Path("/api")
public class RelatorioResource {

    private static final Logger LOG = Logger.getLogger(RelatorioResource.class);
    private static final String PDF = "application/pdf";

    @Inject
    ObjectMapper mapper;

    /** Gera o relatório com os dados enviados (mesmo contrato do antigo server.py). */
    @POST
    @Path("/gerar-relatorio")
    @Consumes(MediaType.WILDCARD)
    public Response gerarRelatorio(byte[] body) {
        RelatorioConfig cfg = RelatorioPayload.parse(JsonBody.parse(mapper, body));
        return pdf(cfg, cfg.outputFilename());
    }

    /** Relatório de demonstração, com os dados de exemplo. */
    @GET
    @Path("/teste")
    public Response teste() {
        return pdf(RelatorioConfig.EXEMPLO, "relatorio_teste.pdf");
    }

    private Response pdf(RelatorioConfig cfg, String filename) {
        byte[] pdf;
        try {
            pdf = RelatorioPsicossocialPdf.gerar(cfg);
        } catch (LayoutException e) {
            LOG.warnf("Relatório não pôde ser diagramado: %s", e.getMessage());
            return ApiErrors.response(422, "Erro ao gerar relatório: " + e.getMessage());
        } catch (RuntimeException e) {
            LOG.error("Erro ao gerar relatório", e);
            return ApiErrors.response(500, "Erro ao gerar relatório: " + e.getMessage());
        }
        return Response.ok(pdf, PDF)
                .header("Content-Disposition", attachment(filename))
                .build();
    }

    /** Content-Disposition com nome ASCII e, se preciso, a versão UTF-8 (RFC 6266). */
    static String attachment(String filename) {
        String clean = filename.replaceAll("[\\p{Cntrl}\"\\\\/]", "_").strip();
        if (clean.isEmpty()) {
            clean = "relatorio.pdf";
        }
        String ascii = Normalizer.normalize(clean, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^\\x20-\\x7E]", "_");
        String header = "attachment; filename=\"" + ascii + "\"";
        if (!ascii.equals(clean)) {
            header += "; filename*=UTF-8''" + URLEncoder.encode(clean, StandardCharsets.UTF_8).replace("+", "%20");
        }
        return header;
    }
}
