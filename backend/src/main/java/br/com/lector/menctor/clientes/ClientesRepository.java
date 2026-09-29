package br.com.lector.menctor.clientes;

import static br.com.lector.menctor.dados.Tipo.BOOLEANO;
import static br.com.lector.menctor.dados.Tipo.INTEIRO;
import static br.com.lector.menctor.dados.Tipo.JSON;
import static br.com.lector.menctor.dados.Tipo.NUMERICO;
import static br.com.lector.menctor.dados.Tipo.TEXTO;

import java.sql.Connection;
import java.sql.SQLException;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import br.com.lector.menctor.api.InvalidRequestException;
import br.com.lector.menctor.dados.Banco;
import br.com.lector.menctor.dados.Tipo;
import br.com.lector.menctor.dados.Valores;

/** Clientes, o progresso das 8 etapas e o formulário de cadastro. */
@ApplicationScoped
public class ClientesRepository {

    static final Map<String, Tipo> COLUNAS = Valores.colunas(
            "id", TEXTO, "name", TEXTO, "cnpj", TEXTO, "contact", TEXTO, "email", TEXTO, "phone", TEXTO,
            "sector", TEXTO, "employees", INTEIRO, "mrr", NUMERICO, "color", TEXTO, "status", TEXTO,
            "current_step", INTEIRO, "next_action", TEXTO);

    static final Map<String, Tipo> COLUNAS_CADASTRO = Valores.colunas(
            "razao_social", TEXTO, "responsavel", TEXTO, "email", TEXTO, "telefone", TEXTO, "cnpj", TEXTO,
            "qtd_por_area", JSON, "qtd_cargos", TEXTO, "segmento", TEXTO, "unidades", TEXTO, "cidades", TEXTO,
            "terceirizados", TEXTO, "possui", JSON, "indicadores", JSON, "mapeamento_formal", TEXTO,
            "pesquisa_clima", TEXTO, "canais_escuta", TEXTO, "fiscalizacao_evidencia", TEXTO,
            "gestao_riscos_outra", TEXTO, "pressao_metas", TEXTO, "ritmo_intenso", TEXTO,
            "capacitacao_lideranca", TEXTO, "conflitos_recorrentes", TEXTO, "assedio_moral", TEXTO,
            "lideranca_outra", TEXTO, "juridico_acompanha", TEXTO, "acao_trabalhista_mental", TEXTO,
            "sente_protegida", TEXTO, "juridica_outra", TEXTO, "excesso_trabalho", BOOLEANO,
            "prazos_inalcancaveis", BOOLEANO, "falta_controle", BOOLEANO, "estrutura_nao_aplica", BOOLEANO,
            "estrutura_outra", TEXTO, "trabalha_com", JSON, "submitted_at", TEXTO, "form_token", TEXTO);

    private static final Map<String, Tipo> CHAVE_CADASTRO = Valores.colunas("client_id", TEXTO);

    @Inject
    Banco banco;

    public List<ObjectNode> listar() {
        return banco.transacao(c -> banco.consultar(c, "select * from clients order by updated_at desc"));
    }

    /** Cliente com o progresso das etapas e o cadastro (ou vazio se não existir). */
    public Optional<ObjectNode> detalhar(String id) {
        return banco.transacao(c -> {
            Optional<ObjectNode> cliente = banco.consultarUm(c, "select * from clients where id = ?", id);
            if (cliente.isEmpty()) {
                return cliente;
            }
            ObjectNode resultado = cliente.get();
            ArrayNode progresso = resultado.putArray("progress");
            banco.consultar(c, "select * from client_step_progress where client_id = ? order by step_number", id)
                    .forEach(progresso::add);
            resultado.set("cadastro", banco.consultarUm(c, "select * from cadastro_responses where client_id = ?", id)
                    .map(JsonNode.class::cast)
                    .orElse(resultado.nullNode()));
            return Optional.of(resultado);
        });
    }

    public ObjectNode criar(JsonNode corpo) {
        exigirObjeto(corpo);
        Map<String, Object> valores = Valores.extrair(corpo, COLUNAS);
        return banco.transacao(c -> {
            ObjectNode cliente = banco.inserir(c, "clients", COLUNAS, valores);
            criarEtapas(c, cliente.get("id").asText());
            return cliente;
        });
    }

    public Optional<ObjectNode> atualizar(String id, JsonNode corpo) {
        exigirObjeto(corpo);
        Map<String, Object> valores = Valores.extrair(corpo, COLUNAS);
        valores.remove("id");
        return banco.transacao(c -> banco.atualizar(c, "clients", COLUNAS, valores, "id", id));
    }

    /**
     * Grava o estado de uma etapa e move a etapa atual do cliente: concluir leva para a etapa
     * seguinte (como a tela já faz), iniciar torna aquela a etapa atual.
     */
    public ObjectNode salvarEtapa(String id, int numero, JsonNode corpo) {
        exigirObjeto(corpo);
        Object status = Valores.converter(corpo.get("status"), TEXTO, "status");
        String situacao = status == null ? "em_andamento" : (String) status;
        JsonNode dados = corpo.has("data") && corpo.get("data").isObject()
                ? corpo.get("data")
                : JsonNodeFactory.instance.objectNode();
        return banco.transacao(c -> {
            ObjectNode etapa = banco.consultar(c, """
                    insert into client_step_progress (client_id, step_number, status, data, started_at, completed_at)
                    values (?, ?, ?, ?::jsonb,
                            case when ? = 'em_andamento' then now() end,
                            case when ? = 'concluida' then now() end)
                    on conflict (client_id, step_number) do update set
                      status = excluded.status,
                      data = excluded.data,
                      started_at = coalesce(client_step_progress.started_at, excluded.started_at),
                      completed_at = case when excluded.status = 'concluida'
                                          then coalesce(client_step_progress.completed_at, now()) end
                    returning *""", id, numero, situacao, dados, situacao, situacao).get(0);
            if (situacao.equals("concluida")) {
                banco.executar(c, """
                        update clients
                           set current_step = greatest(current_step, least(? + 1, (select max(number) from steps)))
                         where id = ?""", numero, id);
            } else if (situacao.equals("em_andamento")) {
                banco.executar(c, "update clients set current_step = ? where id = ?", numero, id);
            }
            return etapa;
        });
    }

    /** Grava só os campos enviados do cadastro (os demais continuam como estavam). */
    public ObjectNode salvarCadastro(String id, JsonNode corpo) {
        exigirObjeto(corpo);
        Map<String, Object> valores = new LinkedHashMap<>();
        valores.put("client_id", id);
        valores.putAll(Valores.extrair(corpo, COLUNAS_CADASTRO));
        Map<String, Tipo> tipos = new LinkedHashMap<>(CHAVE_CADASTRO);
        tipos.putAll(COLUNAS_CADASTRO);
        return banco.transacao(c -> banco.upsert(c, "cadastro_responses", tipos, valores, "client_id"));
    }

    public List<ObjectNode> etapas() {
        return banco.transacao(c -> banco.consultar(c, "select number, label, description from steps order by number"));
    }

    /**
     * Negócio com contrato assinado vira cliente: vincula ao cliente de mesmo nome ou CNPJ
     * que ainda não veio do pipeline, ou cria um novo. Chamado dentro da transação que
     * grava o card.
     */
    public void garantirClienteDoPipeline(Connection c, ObjectNode card) throws SQLException {
        String cardId = card.get("id").asText();
        if (banco.consultarUm(c, "select id from clients where pipeline_card_id = ?", cardId).isPresent()) {
            return;
        }
        String empresa = texto(card, "empresa");
        String cnpj = texto(card, "cnpj");
        if (cnpj == null && card.has("extra") && card.get("extra").has("cnpj")) {
            cnpj = texto(card.get("extra"), "cnpj");
        }
        for (ObjectNode existente : banco.consultar(c,
                "select id, name, cnpj from clients where pipeline_card_id is null")) {
            if (mesmoCnpj(existente, cnpj) || mesmoCliente(existente, empresa)) {
                banco.executar(c, "update clients set pipeline_card_id = ? where id = ?", cardId,
                        existente.get("id").asText());
                return;
            }
        }
        Map<String, Object> valores = new LinkedHashMap<>();
        valores.put("name", empresa);
        if (cnpj != null && !cnpj.isBlank()) {
            valores.put("cnpj", cnpj);
        }
        valores.put("contact", texto(card, "contato"));
        valores.put("email", texto(card, "email"));
        valores.put("employees", card.path("funcionarios").isNumber() ? card.get("funcionarios").intValue() : null);
        valores.put("mrr", card.path("valor").isNumber() ? card.get("valor").decimalValue() : null);
        valores.put("status", "ativo");
        valores.put("next_action", texto(card, "proximo_passo"));
        Map<String, Tipo> tipos = new LinkedHashMap<>(COLUNAS);
        tipos.put("pipeline_card_id", TEXTO);
        valores.put("pipeline_card_id", cardId);
        ObjectNode cliente = banco.inserir(c, "clients", tipos, valores);
        criarEtapas(c, cliente.get("id").asText());
    }

    private void criarEtapas(Connection c, String clienteId) throws SQLException {
        banco.executar(c, """
                insert into client_step_progress (client_id, step_number, status, started_at)
                select ?, number,
                       case when number = 1 then 'em_andamento' else 'pendente' end,
                       case when number = 1 then now() end
                  from steps
                on conflict do nothing""", clienteId);
    }

    private static boolean mesmoCliente(ObjectNode existente, String empresa) {
        return !normalizar(empresa).isEmpty() && normalizar(texto(existente, "name")).equals(normalizar(empresa));
    }

    private static boolean mesmoCnpj(ObjectNode existente, String cnpj) {
        String c1 = limparCnpj(cnpj);
        String c2 = limparCnpj(texto(existente, "cnpj"));
        return !c1.isEmpty() && c1.equals(c2);
    }

    private static String limparCnpj(String s) {
        return s == null ? "" : s.replaceAll("\\D", "");
    }

    static String normalizar(String s) {
        if (s == null) {
            return "";
        }
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("\\s+", " ")
                .strip();
    }

    private static String texto(JsonNode node, String campo) {
        JsonNode v = node.get(campo);
        return v == null || v.isNull() ? null : v.asText();
    }

    static void exigirObjeto(JsonNode corpo) {
        if (corpo == null || !corpo.isObject()) {
            throw new InvalidRequestException("O corpo da requisição deve ser um objeto JSON");
        }
    }
}
