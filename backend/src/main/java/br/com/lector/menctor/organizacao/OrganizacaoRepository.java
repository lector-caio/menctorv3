package br.com.lector.menctor.organizacao;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import br.com.lector.menctor.api.ApiException;
import br.com.lector.menctor.api.InvalidRequestException;
import br.com.lector.menctor.dados.Banco;

@ApplicationScoped
public class OrganizacaoRepository {

    private static final Map<String, String> TABELAS = Map.of(
        "unidades", "organizacao_unidades",
        "diretorias", "organizacao_diretorias",
        "setores", "organizacao_setores",
        "niveis-cargo", "organizacao_niveis_cargo",
        "cargos", "organizacao_cargos",
        "ambientes", "organizacao_ambientes",
        "ghes", "organizacao_ghes",
        "gess", "organizacao_gess"
    );

    @Inject
    Banco banco;

    @Inject
    ObjectMapper mapper;

    public void validarCliente(String clienteId) {
        boolean existe = banco.transacao(c -> 
            !banco.consultar(c, "select 1 from clients where id = ?", clienteId).isEmpty()
        );

        if (!existe) {
            throw ApiException.naoEncontrado("Cliente não encontrado");
        }
    }

    public String tabelaParaCategoria(String categoria) {
        String tabela = TABELAS.get(categoria.toLowerCase());
        if (tabela == null) {
            throw new InvalidRequestException("Categoria organizacional inválida: " + categoria + 
                ". Categorias válidas: " + String.join(", ", TABELAS.keySet()));
        }
        return tabela;
    }

    public List<ObjectNode> listar(String clienteId, String categoria, Boolean apenasAtivos) {
        validarCliente(clienteId);
        String tabela = tabelaParaCategoria(categoria);

        String sql = "select * from " + tabela + " where cliente_id = ?"
            + (Boolean.TRUE.equals(apenasAtivos) ? " and ativo = true" : "")
            + " order by ordem asc, nome asc";

        return banco.transacao(c -> banco.consultar(c, sql, clienteId));
    }

    public ObjectNode obter(String clienteId, String categoria, String id) {
        validarCliente(clienteId);
        String tabela = tabelaParaCategoria(categoria);

        return banco.transacao(c -> 
            banco.consultarUm(c, "select * from " + tabela + " where cliente_id = ? and id = ?", clienteId, id)
        ).orElseThrow(() -> ApiException.naoEncontrado("Registro não encontrado na categoria " + categoria));
    }

    public ObjectNode criar(String clienteId, String categoria, JsonNode dados) {
        validarCliente(clienteId);
        String tabela = tabelaParaCategoria(categoria);

        String nome = dados.path("nome").asText("").trim();
        if (nome.isEmpty()) {
            throw new InvalidRequestException("O campo 'nome' é obrigatório");
        }

        String id = dados.path("id").asText("").trim();
        if (id.isEmpty()) {
            id = UUID.randomUUID().toString();
        }

        int ordem = dados.path("ordem").asInt(0);
        boolean ativo = !dados.has("ativo") || dados.path("ativo").asBoolean(true);

        validarRelacionamentos(clienteId, categoria, dados);

        final String finalId = id;
        banco.transacao(c -> {
            switch (categoria.toLowerCase()) {
                case "unidades" -> {
                    String codigo = textoOuNull(dados, "codigo");
                    String cidade = textoOuNull(dados, "cidade");
                    String estado = textoOuNull(dados, "estado");
                    banco.executar(c,
                        "insert into organizacao_unidades (id, cliente_id, nome, codigo, cidade, estado, ativo, ordem) values (?, ?, ?, ?, ?, ?, ?, ?)",
                        finalId, clienteId, nome, codigo, cidade, estado, ativo, ordem
                    );
                }
                case "diretorias" -> {
                    String responsavel = textoOuNull(dados, "responsavel");
                    banco.executar(c,
                        "insert into organizacao_diretorias (id, cliente_id, nome, responsavel, ativo, ordem) values (?, ?, ?, ?, ?, ?)",
                        finalId, clienteId, nome, responsavel, ativo, ordem
                    );
                }
                case "setores" -> {
                    String unidadeId = textoOuNull(dados, "unidade_id", "unidadeId");
                    String diretoriaId = textoOuNull(dados, "diretoria_id", "diretoriaId");
                    String responsavel = textoOuNull(dados, "responsavel");
                    Integer colab = dados.hasNonNull("colaboradores") ? dados.path("colaboradores").asInt() : 
                                    dados.hasNonNull("colab") ? dados.path("colab").asInt() : null;
                    String turno = textoOuNull(dados, "turno");
                    banco.executar(c,
                        "insert into organizacao_setores (id, cliente_id, unidade_id, diretoria_id, nome, responsavel, colaboradores, turno, ativo, ordem) " +
                        "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        finalId, clienteId, unidadeId, diretoriaId, nome, responsavel, colab, turno, ativo, ordem
                    );
                }
                case "niveis-cargo" -> {
                    banco.executar(c,
                        "insert into organizacao_niveis_cargo (id, cliente_id, nome, ativo, ordem) values (?, ?, ?, ?, ?)",
                        finalId, clienteId, nome, ativo, ordem
                    );
                }
                case "cargos" -> {
                    String nivelCargoId = textoOuNull(dados, "nivel_cargo_id", "nivelCargoId");
                    String cbo = textoOuNull(dados, "cbo");
                    String descricao = textoOuNull(dados, "descricao");
                    banco.executar(c,
                        "insert into organizacao_cargos (id, cliente_id, nivel_cargo_id, nome, cbo, descricao, ativo, ordem) values (?, ?, ?, ?, ?, ?, ?, ?)",
                        finalId, clienteId, nivelCargoId, nome, cbo, descricao, ativo, ordem
                    );
                }
                case "ambientes" -> {
                    String unidadeId = textoOuNull(dados, "unidade_id", "unidadeId");
                    String descricao = textoOuNull(dados, "descricao");
                    banco.executar(c,
                        "insert into organizacao_ambientes (id, cliente_id, unidade_id, nome, descricao, ativo, ordem) values (?, ?, ?, ?, ?, ?, ?)",
                        finalId, clienteId, unidadeId, nome, descricao, ativo, ordem
                    );
                }
                case "ghes" -> {
                    String codigo = textoOuNull(dados, "codigo");
                    String descricao = textoOuNull(dados, "descricao");
                    banco.executar(c,
                        "insert into organizacao_ghes (id, cliente_id, nome, codigo, descricao, ativo, ordem) values (?, ?, ?, ?, ?, ?, ?)",
                        finalId, clienteId, nome, codigo, descricao, ativo, ordem
                    );
                }
                case "gess" -> {
                    String codigo = textoOuNull(dados, "codigo");
                    String descricao = textoOuNull(dados, "descricao");
                    banco.executar(c,
                        "insert into organizacao_gess (id, cliente_id, nome, codigo, descricao, ativo, ordem) values (?, ?, ?, ?, ?, ?, ?)",
                        finalId, clienteId, nome, codigo, descricao, ativo, ordem
                    );
                }
            }
            return null;
        });

        return obter(clienteId, categoria, finalId);
    }

    public ObjectNode atualizar(String clienteId, String categoria, String id, JsonNode dados) {
        obter(clienteId, categoria, id); // garante existência prévia
        String tabela = tabelaParaCategoria(categoria);

        String nome = dados.path("nome").asText("").trim();
        if (nome.isEmpty()) {
            throw new InvalidRequestException("O campo 'nome' é obrigatório");
        }

        int ordem = dados.path("ordem").asInt(0);
        boolean ativo = !dados.has("ativo") || dados.path("ativo").asBoolean(true);

        validarRelacionamentos(clienteId, categoria, dados);

        banco.transacao(c -> {
            switch (categoria.toLowerCase()) {
                case "unidades" -> {
                    String codigo = textoOuNull(dados, "codigo");
                    String cidade = textoOuNull(dados, "cidade");
                    String estado = textoOuNull(dados, "estado");
                    banco.executar(c,
                        "update organizacao_unidades set nome = ?, codigo = ?, cidade = ?, estado = ?, ativo = ?, ordem = ? where cliente_id = ? and id = ?",
                        nome, codigo, cidade, estado, ativo, ordem, clienteId, id
                    );
                }
                case "diretorias" -> {
                    String responsavel = textoOuNull(dados, "responsavel");
                    banco.executar(c,
                        "update organizacao_diretorias set nome = ?, responsavel = ?, ativo = ?, ordem = ? where cliente_id = ? and id = ?",
                        nome, responsavel, ativo, ordem, clienteId, id
                    );
                }
                case "setores" -> {
                    String unidadeId = textoOuNull(dados, "unidade_id", "unidadeId");
                    String diretoriaId = textoOuNull(dados, "diretoria_id", "diretoriaId");
                    String responsavel = textoOuNull(dados, "responsavel");
                    Integer colab = dados.hasNonNull("colaboradores") ? dados.path("colaboradores").asInt() : 
                                    dados.hasNonNull("colab") ? dados.path("colab").asInt() : null;
                    String turno = textoOuNull(dados, "turno");
                    banco.executar(c,
                        "update organizacao_setores set unidade_id = ?, diretoria_id = ?, nome = ?, responsavel = ?, colaboradores = ?, turno = ?, ativo = ?, ordem = ? " +
                        "where cliente_id = ? and id = ?",
                        unidadeId, diretoriaId, nome, responsavel, colab, turno, ativo, ordem, clienteId, id
                    );
                }
                case "niveis-cargo" -> {
                    banco.executar(c,
                        "update organizacao_niveis_cargo set nome = ?, ativo = ?, ordem = ? where cliente_id = ? and id = ?",
                        nome, ativo, ordem, clienteId, id
                    );
                }
                case "cargos" -> {
                    String nivelCargoId = textoOuNull(dados, "nivel_cargo_id", "nivelCargoId");
                    String cbo = textoOuNull(dados, "cbo");
                    String descricao = textoOuNull(dados, "descricao");
                    banco.executar(c,
                        "update organizacao_cargos set nivel_cargo_id = ?, nome = ?, cbo = ?, descricao = ?, ativo = ?, ordem = ? where cliente_id = ? and id = ?",
                        nivelCargoId, nome, cbo, descricao, ativo, ordem, clienteId, id
                    );
                }
                case "ambientes" -> {
                    String unidadeId = textoOuNull(dados, "unidade_id", "unidadeId");
                    String descricao = textoOuNull(dados, "descricao");
                    banco.executar(c,
                        "update organizacao_ambientes set unidade_id = ?, nome = ?, descricao = ?, ativo = ?, ordem = ? where cliente_id = ? and id = ?",
                        unidadeId, nome, descricao, ativo, ordem, clienteId, id
                    );
                }
                case "ghes" -> {
                    String codigo = textoOuNull(dados, "codigo");
                    String descricao = textoOuNull(dados, "descricao");
                    banco.executar(c,
                        "update organizacao_ghes set nome = ?, codigo = ?, descricao = ?, ativo = ?, ordem = ? where cliente_id = ? and id = ?",
                        nome, codigo, descricao, ativo, ordem, clienteId, id
                    );
                }
                case "gess" -> {
                    String codigo = textoOuNull(dados, "codigo");
                    String descricao = textoOuNull(dados, "descricao");
                    banco.executar(c,
                        "update organizacao_gess set nome = ?, codigo = ?, descricao = ?, ativo = ?, ordem = ? where cliente_id = ? and id = ?",
                        nome, codigo, descricao, ativo, ordem, clienteId, id
                    );
                }
            }
            return null;
        });

        return obter(clienteId, categoria, id);
    }

    public ObjectNode alternarAtivo(String clienteId, String categoria, String id) {
        ObjectNode atual = obter(clienteId, categoria, id);
        String tabela = tabelaParaCategoria(categoria);
        boolean novoAtivo = !atual.path("ativo").asBoolean(true);

        banco.transacao(c -> banco.executar(c,
            "update " + tabela + " set ativo = ? where cliente_id = ? and id = ?",
            novoAtivo, clienteId, id
        ));

        return obter(clienteId, categoria, id);
    }

    public void excluir(String clienteId, String categoria, String id) {
        obter(clienteId, categoria, id);
        String tabela = tabelaParaCategoria(categoria);

        banco.transacao(c -> banco.executar(c,
            "delete from " + tabela + " where cliente_id = ? and id = ?",
            clienteId, id
        ));
    }

    public ObjectNode obterResumoCompleto(String clienteId) {
        validarCliente(clienteId);

        ObjectNode resumo = mapper.createObjectNode();
        resumo.put("clienteId", clienteId);

        for (Map.Entry<String, String> entry : TABELAS.entrySet()) {
            String categoria = entry.getKey();
            String propName = camelCase(categoria);
            List<ObjectNode> itens = listar(clienteId, categoria, false);
            resumo.set(propName, mapper.valueToTree(itens));
        }

        return resumo;
    }

    private void validarRelacionamentos(String clienteId, String categoria, JsonNode dados) {
        if ("setores".equalsIgnoreCase(categoria)) {
            String unidId = textoOuNull(dados, "unidade_id", "unidadeId");
            if (unidId != null) {
                boolean unidOk = banco.transacao(c -> 
                    !banco.consultar(c, "select 1 from organizacao_unidades where id = ? and cliente_id = ?", unidId, clienteId).isEmpty()
                );
                if (!unidOk) {
                    throw new InvalidRequestException("A unidade referenciada não existe ou não pertence a este cliente");
                }
            }

            String dirId = textoOuNull(dados, "diretoria_id", "diretoriaId");
            if (dirId != null) {
                boolean dirOk = banco.transacao(c -> 
                    !banco.consultar(c, "select 1 from organizacao_diretorias where id = ? and cliente_id = ?", dirId, clienteId).isEmpty()
                );
                if (!dirOk) {
                    throw new InvalidRequestException("A diretoria referenciada não existe ou não pertence a este cliente");
                }
            }
        }

        if ("cargos".equalsIgnoreCase(categoria)) {
            String nivelId = textoOuNull(dados, "nivel_cargo_id", "nivelCargoId");
            if (nivelId != null) {
                boolean nivelOk = banco.transacao(c -> 
                    !banco.consultar(c, "select 1 from organizacao_niveis_cargo where id = ? and cliente_id = ?", nivelId, clienteId).isEmpty()
                );
                if (!nivelOk) {
                    throw new InvalidRequestException("O nível de cargo referenciado não existe ou não pertence a este cliente");
                }
            }
        }

        if ("ambientes".equalsIgnoreCase(categoria)) {
            String unidId = textoOuNull(dados, "unidade_id", "unidadeId");
            if (unidId != null) {
                boolean unidOk = banco.transacao(c -> 
                    !banco.consultar(c, "select 1 from organizacao_unidades where id = ? and cliente_id = ?", unidId, clienteId).isEmpty()
                );
                if (!unidOk) {
                    throw new InvalidRequestException("A unidade referenciada não existe ou não pertence a este cliente");
                }
            }
        }
    }

    private String textoOuNull(JsonNode dados, String... chaves) {
        for (String chave : chaves) {
            if (dados.has(chave) && !dados.path(chave).isNull()) {
                String val = dados.path(chave).asText("").trim();
                return val.isEmpty() ? null : val;
            }
        }
        return null;
    }

    private String camelCase(String kebab) {
        StringBuilder sb = new StringBuilder();
        boolean proximoMaiusculo = false;
        for (char c : kebab.toCharArray()) {
            if (c == '-') {
                proximoMaiusculo = true;
            } else if (proximoMaiusculo) {
                sb.append(Character.toUpperCase(c));
                proximoMaiusculo = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
