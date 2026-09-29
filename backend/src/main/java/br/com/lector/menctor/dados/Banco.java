package br.com.lector.menctor.dados;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import br.com.lector.menctor.api.ApiException;
import br.com.lector.menctor.api.InvalidRequestException;

/**
 * Acesso ao PostgreSQL com JDBC. As linhas voltam como JSON com os nomes das colunas (snake_case),
 * no mesmo formato que o Supabase devolvia, para o frontend continuar usando os mesmos conversores.
 */
@ApplicationScoped
public class Banco {

    private static final Logger LOG = Logger.getLogger(Banco.class);

    @Inject
    DataSource dataSource;

    @Inject
    ObjectMapper mapper;

    @FunctionalInterface
    public interface Trabalho<T> {
        T executar(Connection c) throws SQLException;
    }

    /** Executa numa transação: confirma no fim ou desfaz tudo se algo falhar. */
    public <T> T transacao(Trabalho<T> trabalho) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                T resultado = trabalho.executar(c);
                c.commit();
                return resultado;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw traduzir(e);
        }
    }

    public List<ObjectNode> consultar(Connection c, String sql, Object... parametros) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            vincular(ps, parametros);
            try (ResultSet rs = ps.executeQuery()) {
                List<ObjectNode> linhas = new ArrayList<>();
                while (rs.next()) {
                    linhas.add(linha(rs));
                }
                return linhas;
            }
        }
    }

    public Optional<ObjectNode> consultarUm(Connection c, String sql, Object... parametros) throws SQLException {
        List<ObjectNode> linhas = consultar(c, sql, parametros);
        return linhas.isEmpty() ? Optional.empty() : Optional.of(linhas.get(0));
    }

    public int executar(Connection c, String sql, Object... parametros) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            vincular(ps, parametros);
            return ps.executeUpdate();
        }
    }

    /** INSERT ... ON CONFLICT (chave) DO UPDATE com as colunas informadas; devolve a linha gravada. */
    public ObjectNode upsert(Connection c, String tabela, Map<String, Tipo> tipos, Map<String, Object> valores,
                             String... chave) throws SQLException {
        List<String> colunas = new ArrayList<>(valores.keySet());
        Set<String> chaves = Set.of(chave);
        String atualizacoes = colunas.stream()
                .filter(col -> !chaves.contains(col))
                .map(col -> col + " = excluded." + col)
                .collect(Collectors.joining(", "));
        if (atualizacoes.isEmpty()) {
            // nada a alterar: atualização neutra só para o RETURNING devolver a linha existente
            atualizacoes = chave[0] + " = excluded." + chave[0];
        }
        String sql = "insert into " + tabela + " (" + String.join(", ", colunas) + ") values ("
                + marcadores(colunas, tipos) + ") on conflict (" + String.join(", ", chave)
                + ") do update set " + atualizacoes + " returning *";
        return consultar(c, sql, colunas.stream().map(valores::get).toArray()).get(0);
    }

    public ObjectNode inserir(Connection c, String tabela, Map<String, Tipo> tipos, Map<String, Object> valores)
            throws SQLException {
        List<String> colunas = new ArrayList<>(valores.keySet());
        String sql = colunas.isEmpty()
                ? "insert into " + tabela + " default values returning *"
                : "insert into " + tabela + " (" + String.join(", ", colunas) + ") values ("
                        + marcadores(colunas, tipos) + ") returning *";
        return consultar(c, sql, colunas.stream().map(valores::get).toArray()).get(0);
    }

    /** UPDATE das colunas informadas na linha com {@code colunaChave = chave}; vazio se não existir. */
    public Optional<ObjectNode> atualizar(Connection c, String tabela, Map<String, Tipo> tipos,
                                          Map<String, Object> valores, String colunaChave, Object chave)
            throws SQLException {
        if (valores.isEmpty()) {
            return consultarUm(c, "select * from " + tabela + " where " + colunaChave + " = ?", chave);
        }
        List<String> colunas = new ArrayList<>(valores.keySet());
        String atribuicoes = colunas.stream()
                .map(col -> col + " = " + marcador(tipos.get(col)))
                .collect(Collectors.joining(", "));
        List<Object> parametros = new ArrayList<>();
        colunas.forEach(col -> parametros.add(valores.get(col)));
        parametros.add(chave);
        return consultarUm(c, "update " + tabela + " set " + atribuicoes + " where " + colunaChave + " = ? returning *",
                parametros.toArray());
    }

    private static String marcadores(List<String> colunas, Map<String, Tipo> tipos) {
        return colunas.stream().map(col -> marcador(tipos.get(col))).collect(Collectors.joining(", "));
    }

    private static String marcador(Tipo tipo) {
        return tipo == Tipo.JSON ? "?::jsonb" : "?";
    }

    private void vincular(PreparedStatement ps, Object[] parametros) throws SQLException {
        for (int i = 0; i < parametros.length; i++) {
            Object v = parametros[i];
            if (v == null) {
                ps.setNull(i + 1, Types.NULL);
            } else if (v instanceof JsonNode json) {
                ps.setString(i + 1, json.toString());
            } else {
                ps.setObject(i + 1, v);
            }
        }
    }

    private ObjectNode linha(ResultSet rs) throws SQLException {
        ObjectNode node = mapper.createObjectNode();
        ResultSetMetaData md = rs.getMetaData();
        for (int i = 1; i <= md.getColumnCount(); i++) {
            String coluna = md.getColumnLabel(i);
            switch (md.getColumnTypeName(i)) {
                case "jsonb", "json" -> {
                    String s = rs.getString(i);
                    node.set(coluna, s == null ? node.nullNode() : lerJson(s));
                }
                case "timestamptz", "timestamp" -> {
                    OffsetDateTime t = rs.getObject(i, OffsetDateTime.class);
                    node.put(coluna, t == null ? null : t.toString());
                }
                case "numeric" -> node.put(coluna, rs.getBigDecimal(i));
                case "int2", "int4", "int8" -> {
                    long n = rs.getLong(i);
                    if (rs.wasNull()) {
                        node.putNull(coluna);
                    } else {
                        node.put(coluna, n);
                    }
                }
                case "bool" -> {
                    boolean b = rs.getBoolean(i);
                    if (rs.wasNull()) {
                        node.putNull(coluna);
                    } else {
                        node.put(coluna, b);
                    }
                }
                default -> node.put(coluna, rs.getString(i));
            }
        }
        return node;
    }

    private JsonNode lerJson(String s) {
        try {
            return mapper.readTree(s);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Erros do PostgreSQL viram respostas HTTP com mensagem clara. */
    static RuntimeException traduzir(SQLException e) {
        String estado = e.getSQLState() == null ? "" : e.getSQLState();
        String restricao = null;
        String coluna = null;
        if (e instanceof PSQLException pe && pe.getServerErrorMessage() != null) {
            ServerErrorMessage m = pe.getServerErrorMessage();
            restricao = m.getConstraint();
            coluna = m.getColumn();
        }
        switch (estado) {
            case "23503" -> {
                if ("fk_progress_client".equals(restricao) || "fk_cadastro_client".equals(restricao)) {
                    return ApiException.naoEncontrado("Cliente não encontrado");
                }
                if ("fk_progress_step".equals(restricao) || "fk_clients_step".equals(restricao)) {
                    return new InvalidRequestException("Etapa inexistente (use de 1 a 8)");
                }
                return new InvalidRequestException("Referência inválida (" + restricao + ")");
            }
            case "23505" -> {
                if ("uq_denuncias_protocolo".equals(restricao)) {
                    return ApiException.conflito("Já existe outra denúncia com este protocolo");
                }
                if ("uq_campanha_cpf".equals(restricao)) {
                    return ApiException.conflito("Este CPF já respondeu a esta campanha");
                }
                if ("uq_unidade_cliente_nome".equals(restricao)) {
                    return ApiException.conflito("Já existe uma unidade com este nome para este cliente");
                }
                if ("uq_diretoria_cliente_nome".equals(restricao)) {
                    return ApiException.conflito("Já existe uma diretoria com este nome para este cliente");
                }
                if ("uq_setor_cliente_nome".equals(restricao)) {
                    return ApiException.conflito("Já existe um setor com este nome para este cliente");
                }
                if ("uq_nivel_cargo_cliente_nome".equals(restricao)) {
                    return ApiException.conflito("Já existe um nível de cargo com este nome para este cliente");
                }
                if ("uq_cargo_cliente_nome".equals(restricao)) {
                    return ApiException.conflito("Já existe um cargo com este nome para este cliente");
                }
                if ("uq_ambiente_cliente_nome".equals(restricao)) {
                    return ApiException.conflito("Já existe um ambiente com este nome para este cliente");
                }
                if ("uq_ghe_cliente_nome".equals(restricao)) {
                    return ApiException.conflito("Já existe um GHE com este nome para este cliente");
                }
                if ("uq_ges_cliente_nome".equals(restricao)) {
                    return ApiException.conflito("Já existe um GES com este nome para este cliente");
                }
                if ("uq_matriz_versao".equals(restricao)) {
                    return ApiException.conflito("Já existe uma versão com este identificador nesta matriz");
                }
                if ("uq_matriz_cliente".equals(restricao)) {
                    return ApiException.conflito("Este cliente já possui uma matriz de risco cadastrada");
                }
                return ApiException.conflito("Registro duplicado (" + restricao + ")");
            }
            case "23514" -> {
                if ("ck_clients_status".equals(restricao)) {
                    return new InvalidRequestException("Status do cliente inválido (use ativo, negociacao, pausado ou concluido)");
                }
                if ("ck_progress_status".equals(restricao)) {
                    return new InvalidRequestException("Status da etapa inválido (use pendente, em_andamento, concluida ou bloqueada)");
                }
                return new InvalidRequestException("Valor inválido (" + restricao + ")");
            }
            case "23502" -> {
                return new InvalidRequestException("Campo obrigatório: " + coluna);
            }
            default -> {
                // segue abaixo
            }
        }
        if (estado.startsWith("22")) {
            return new InvalidRequestException("Valor inválido: " + e.getMessage());
        }
        if (estado.startsWith("08")) {
            LOG.error("Banco de dados indisponível", e);
            return new ApiException(503, "Banco de dados indisponível");
        }
        LOG.error("Erro no banco de dados", e);
        return new ApiException(500, "Erro no banco de dados");
    }
}
