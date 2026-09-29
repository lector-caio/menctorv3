package br.com.lector.menctor.frontend;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.FileSystemAccess;
import io.vertx.ext.web.handler.StaticHandler;

/**
 * Serve o frontend (index.html, .jsx, .css, assets) como o antigo server.py, com o fallback de SPA
 * igual ao do vercel.json: caminhos sem extensão devolvem o index.html; arquivos inexistentes,
 * 404. Arquivos e pastas ocultos (.env, .git) e o próprio backend nunca são servidos.
 */
@ApplicationScoped
public class FrontendRoutes {

    private static final Logger LOG = Logger.getLogger(FrontendRoutes.class);
    /** Tudo que não é API, health check ou endpoints do Quarkus (/q). */
    private static final String FRONTEND_PATHS = "^(?!/api(?:/|$)|/q(?:/|$)|/health$).*$";

    @ConfigProperty(name = "menctor.frontend.dir")
    Optional<String> configuredDir;

    void register(@Observes Router router) {
        Optional<Path> root = resolveRoot();
        if (root.isEmpty()) {
            LOG.warn("Frontend não encontrado (nenhum index.html em menctor.frontend.dir, no diretório atual ou no pai): "
                    + "somente a API será servida");
            return;
        }
        Path dir = root.get();
        String indexHtml = dir.resolve("index.html").toString();
        StaticHandler files = StaticHandler.create(FileSystemAccess.ROOT, dir.toString())
                .setIncludeHidden(false)
                .setDirectoryListing(false)
                .setCachingEnabled(true)
                // os arquivos mudam durante o desenvolvimento: revalida as datas a cada segundo
                .setFilesReadOnly(false)
                .setCacheEntryTimeout(1000)
                .setDefaultContentEncoding("UTF-8");
        router.routeWithRegex(FRONTEND_PATHS)
                .method(HttpMethod.GET)
                .method(HttpMethod.HEAD)
                .handler(this::guard)
                .handler(files)
                .handler(ctx -> fallback(ctx, indexHtml));
        LOG.infof("Servindo o frontend de %s", dir);
    }

    private void guard(RoutingContext ctx) {
        String path = ctx.normalizedPath();
        if (isBlocked(path)) {
            notFound(ctx);
            return;
        }
        // como o Flask (e a Vercel): o navegador sempre revalida, então deploys novos aparecem na hora
        ctx.response().putHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
        if (path.endsWith(".jsx")) {
            ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "text/javascript;charset=UTF-8");
        }
        ctx.next();
    }

    private static void fallback(RoutingContext ctx, String indexHtml) {
        String path = ctx.normalizedPath();
        String lastSegment = path.substring(path.lastIndexOf('/') + 1);
        if (lastSegment.contains(".")) {
            notFound(ctx);
            return;
        }
        ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "text/html;charset=UTF-8");
        ctx.response().sendFile(indexHtml);
    }

    static boolean isBlocked(String path) {
        String[] segments = path.split("/");
        for (String s : segments) {
            if (s.startsWith(".")) {
                return true;
            }
        }
        return segments.length > 1 && segments[1].equals("backend");
    }

    private static void notFound(RoutingContext ctx) {
        ctx.response().setStatusCode(404)
                .putHeader(HttpHeaders.CONTENT_TYPE, "text/plain;charset=UTF-8")
                .end("Não encontrado");
    }

    private Optional<Path> resolveRoot() {
        List<String> candidates = configuredDir.filter(d -> !d.isBlank())
                .map(List::of)
                .orElse(List.of(".", ".."));
        for (String candidate : candidates) {
            Path p = Path.of(candidate).toAbsolutePath().normalize();
            if (Files.isRegularFile(p.resolve("index.html"))) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }
}
