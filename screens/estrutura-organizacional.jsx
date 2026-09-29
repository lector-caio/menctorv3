/* global React, Icon, Page, MenctorDB */

// ════════════════════════════════════════════════════════════
// ESTRUTURA ORGANIZACIONAL (Etapa 2 do Cliente)
// ════════════════════════════════════════════════════════════

const CATEGORIAS_ORG = [
  { id: "unidades",     label: "Unidades",       singular: "Unidade",       icone: "map-pin" },
  { id: "setores",      label: "Setores",        singular: "Setor",         icone: "layers" },
  { id: "diretorias",   label: "Diretorias",     singular: "Diretoria",     icone: "briefcase" },
  { id: "niveis-cargo", label: "Níveis de Cargo",singular: "Nível de Cargo",icone: "trending-up" },
  { id: "cargos",       label: "Cargos",         singular: "Cargo",         icone: "user-check" },
  { id: "ambientes",    label: "Ambientes",      singular: "Ambiente",      icone: "home" },
  { id: "ghes",         label: "GHE",            singular: "GHE",           icone: "users" },
  { id: "gess",         label: "GES",            singular: "GES",           icone: "shield" },
];

const EstruturaOrganizacionalEtapa = ({ cliente, onNext, onNavigateMatriz }) => {
  const [categoriaAtiva, setCategoriaAtiva] = React.useState("unidades");
  const [dados, setDados] = React.useState({
    unidades: [],
    setores: [],
    diretorias: [],
    niveisCargo: [],
    cargos: [],
    ambientes: [],
    ghes: [],
    gess: [],
  });
  const [carregando, setCarregando] = React.useState(true);
  const [busca, setBusca] = React.useState("");

  // Modais
  const [modalEdicao, setModalEdicao] = React.useState(false);
  const [registroAtual, setRegistroAtual] = React.useState(null);
  const [modalImportar, setModalImportar] = React.useState(false);
  const [linhasImportacao, setLinhasImportacao] = React.useState("");
  const [salvando, setSalvando] = React.useState(false);
  const [erroMsg, setErroMsg] = React.useState(null);

  const clienteId = cliente?.id;

  const carregarDados = React.useCallback(async () => {
    if (!clienteId || !window.MenctorDB?.organizacao?.resumo) {
      setCarregando(false);
      return;
    }
    setCarregando(true);
    try {
      const res = await window.MenctorDB.organizacao.resumo(clienteId);
      if (res) {
        setDados({
          unidades: res.unidades || [],
          setores: res.setores || [],
          diretorias: res.diretorias || [],
          niveisCargo: res.niveisCargo || [],
          cargos: res.cargos || [],
          ambientes: res.ambientes || [],
          ghes: res.ghes || [],
          gess: res.gess || [],
        });
      }
    } catch (err) {
      console.warn("Erro ao carregar estrutura organizacional:", err);
    } finally {
      setCarregando(false);
    }
  }, [clienteId]);

  React.useEffect(() => {
    carregarDados();
  }, [carregarDados]);

  const catMeta = CATEGORIAS_ORG.find(c => c.id === categoriaAtiva) || CATEGORIAS_ORG[0];
  const propNome = categoriaAtiva === "niveis-cargo" ? "niveisCargo" : categoriaAtiva;
  const listaRegistros = (dados[propNome] || []).filter(item => {
    if (!busca) return true;
    const term = busca.toLowerCase();
    return (item.nome || "").toLowerCase().includes(term) ||
           (item.codigo || "").toLowerCase().includes(term) ||
           (item.responsavel || "").toLowerCase().includes(term);
  });

  const abrirNovo = () => {
    setRegistroAtual({
      nome: "",
      codigo: "",
      cidade: "",
      estado: "",
      responsavel: "",
      colaboradores: "",
      turno: "Comercial",
      unidadeId: "",
      diretoriaId: "",
      nivelCargoId: "",
      cbo: "",
      descricao: "",
      ordem: 0,
      ativo: true,
    });
    setErroMsg(null);
    setModalEdicao(true);
  };

  const abrirEdicao = (item) => {
    setRegistroAtual({
      id: item.id,
      nome: item.nome || "",
      codigo: item.codigo || "",
      cidade: item.cidade || "",
      estado: item.estado || "",
      responsavel: item.responsavel || "",
      colaboradores: item.colaboradores || item.colab || "",
      turno: item.turno || "Comercial",
      unidadeId: item.unidade_id || item.unidadeId || "",
      diretoriaId: item.diretoria_id || item.diretoriaId || "",
      nivelCargoId: item.nivel_cargo_id || item.nivelCargoId || "",
      cbo: item.cbo || "",
      descricao: item.descricao || "",
      ordem: item.ordem || 0,
      ativo: item.ativo !== false,
    });
    setErroMsg(null);
    setModalEdicao(true);
  };

  const salvarRegistro = async (e) => {
    e.preventDefault();
    if (!registroAtual.nome.trim()) {
      setErroMsg("O nome é obrigatório.");
      return;
    }
    setSalvando(true);
    setErroMsg(null);
    try {
      if (registroAtual.id) {
        await window.MenctorDB.organizacao.atualizar(clienteId, categoriaAtiva, registroAtual.id, registroAtual);
      } else {
        await window.MenctorDB.organizacao.criar(clienteId, categoriaAtiva, registroAtual);
      }
      setModalEdicao(false);
      setRegistroAtual(null);
      await carregarDados();
    } catch (err) {
      setErroMsg(err.message || "Erro ao salvar registro");
    } finally {
      setSalvando(false);
    }
  };

  const alternarAtivo = async (item) => {
    try {
      await window.MenctorDB.organizacao.alternarAtivo(clienteId, categoriaAtiva, item.id);
      await carregarDados();
    } catch (err) {
      alert("Erro ao alterar status: " + (err.message || err));
    }
  };

  const excluirRegistro = async (item) => {
    if (!confirm(`Deseja realmente excluir ${catMeta.singular.toLowerCase()} "${item.nome}"?`)) return;
    try {
      await window.MenctorDB.organizacao.excluir(clienteId, categoriaAtiva, item.id);
      await carregarDados();
    } catch (err) {
      alert("Não foi possível excluir o registro. Ele pode estar sendo referenciado por outras entidades.");
    }
  };

  const executarImportacao = async () => {
    const linhas = linhasImportacao.split("\n").map(l => l.trim()).filter(Boolean);
    if (!linhas.length) {
      alert("Insira ao menos um nome para importar.");
      return;
    }
    setSalvando(true);
    let sucessos = 0;
    let falhas = 0;
    for (const linha of linhas) {
      try {
        await window.MenctorDB.organizacao.criar(clienteId, categoriaAtiva, { nome: linha, ativo: true });
        sucessos++;
      } catch (e) {
        falhas++;
      }
    }
    setSalvando(false);
    setModalImportar(false);
    setLinhasImportacao("");
    alert(`Importação concluída: ${sucessos} adicionados${falhas > 0 ? `, ${falhas} com erro/duplicados` : ""}.`);
    await carregarDados();
  };

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 20 }}>
      {/* ── 1. Top Banner Azul ──────────────────────────────────── */}
      <div style={{
        background: "linear-gradient(135deg, #1877F2 0%, #0D65D9 100%)",
        borderRadius: 14,
        padding: "24px 28px",
        color: "#fff",
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        boxShadow: "0 4px 14px rgba(24, 119, 242, 0.2)"
      }}>
        <div>
          <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
            <h1 style={{ fontSize: 24, fontWeight: 700, margin: 0, color: "#fff" }}>Estrutura Organizacional</h1>
            <span title="Gerencie unidades, setores, cargos e grupos para estratificação de risco." style={{ cursor: "pointer", opacity: 0.85 }}>
              <Icon name="info" size={18} />
            </span>
          </div>
          <div style={{ fontSize: 14, opacity: 0.9, marginTop: 4 }}>
            {cliente?.name || "Empresa Demonstração Ltda"}
          </div>
        </div>

        <button
          onClick={() => {
            if (onNavigateMatriz) onNavigateMatriz();
            else if (onNext) onNext();
          }}
          className="btn"
          style={{
            background: "#fff",
            color: "#1877F2",
            border: "none",
            fontWeight: 600,
            display: "flex",
            alignItems: "center",
            gap: 8,
            padding: "10px 18px",
            borderRadius: 8,
            boxShadow: "0 2px 6px rgba(0,0,0,0.08)",
            cursor: "pointer"
          }}
        >
          <Icon name="shield" size={17} color="#1877F2" />
          Matriz de Risco (PGR)
        </button>
      </div>

      {/* ── 2. Card Explicativo ─────────────────────────────────── */}
      <div style={{
        background: "#F3F7FD",
        border: "1px solid #DCE6F5",
        borderRadius: 12,
        padding: "20px 24px",
        display: "flex",
        flexDirection: "column",
        gap: 16
      }}>
        <div style={{ display: "flex", gap: 14, alignItems: "flex-start" }}>
          <div style={{
            width: 36,
            height: 36,
            borderRadius: "50%",
            background: "#E1EEFE",
            color: "#1877F2",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            flexShrink: 0
          }}>
            <Icon name="zap" size={18} />
          </div>
          <div>
            <div style={{ fontSize: 15, fontWeight: 700, color: "var(--ink)", marginBottom: 4 }}>
              Por que cadastrar a estrutura organizacional?
            </div>
            <div style={{ fontSize: 13, color: "var(--ink-muted)", lineHeight: 1.5 }}>
              A estrutura organizacional permite <strong>estratificar os resultados do diagnóstico psicossocial</strong> por diferentes recortes da empresa. Ao cadastrar unidades, setores, cargos e demais categorias, você habilita filtros avançados que revelam <strong>onde exatamente os riscos estão concentrados</strong>, tornando o plano de ação muito mais direcionado e eficaz.
            </div>
          </div>
        </div>

        {/* Exemplos em balões lado a lado */}
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
          <div style={{
            background: "#fff",
            border: "1px solid #E2E8F0",
            borderRadius: 8,
            padding: "12px 14px",
            display: "flex",
            alignItems: "flex-start",
            gap: 10
          }}>
            <Icon name="filter" size={16} color="#1877F2" />
            <div style={{ fontSize: 12, color: "var(--ink-muted)", lineHeight: 1.45 }}>
              <strong>Exemplo:</strong> Ao cadastrar "Setor Comercial" e "Setor Operacional", o relatório mostrará se o risco de sobrecarga é maior em vendas ou na fábrica.
            </div>
          </div>

          <div style={{
            background: "#fff",
            border: "1px solid #E2E8F0",
            borderRadius: 8,
            padding: "12px 14px",
            display: "flex",
            alignItems: "flex-start",
            gap: 10
          }}>
            <Icon name="filter" size={16} color="#1877F2" />
            <div style={{ fontSize: 12, color: "var(--ink-muted)", lineHeight: 1.45 }}>
              <strong>Exemplo:</strong> Com GHE cadastrados, é possível comparar a exposição a riscos entre grupos com atividades similares, conforme NR-01.
            </div>
          </div>
        </div>

        {/* Aviso Não é Obrigatório */}
        <div style={{
          background: "#FFFFFF",
          border: "1px dashed #CBD5E1",
          borderRadius: 8,
          padding: "10px 14px",
          display: "flex",
          alignItems: "center",
          gap: 10,
          fontSize: 12,
          color: "var(--ink-muted)"
        }}>
          <Icon name="info" size={15} color="var(--ink-soft)" />
          <span>
            <strong>Não é obrigatório.</strong> Se a empresa não utilizar alguma categoria (ex: GHE ou GES), isso <strong>não impede</strong> a geração do relatório. O diagnóstico será gerado normalmente — apenas os filtros daquela categoria não estarão disponíveis nos resultados.
          </span>
        </div>
      </div>

      {/* ── 3. Navegação em Abas Segmentadas ─────────────────────── */}
      <div style={{
        background: "#EAEFF5",
        borderRadius: 10,
        padding: 4,
        display: "flex",
        alignItems: "center",
        gap: 4,
        overflowX: "auto"
      }}>
        {CATEGORIAS_ORG.map(cat => {
          const ativa = cat.id === categoriaAtiva;
          const prop = cat.id === "niveis-cargo" ? "niveisCargo" : cat.id;
          const qtd = (dados[prop] || []).length;
          return (
            <button
              key={cat.id}
              onClick={() => { setCategoriaAtiva(cat.id); setBusca(""); }}
              style={{
                flex: 1,
                minWidth: 110,
                border: "none",
                background: ativa ? "#fff" : "transparent",
                color: ativa ? "var(--ink)" : "var(--ink-muted)",
                fontWeight: ativa ? 700 : 500,
                padding: "10px 12px",
                borderRadius: 8,
                fontSize: 13,
                cursor: "pointer",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                gap: 6,
                boxShadow: ativa ? "0 2px 6px rgba(0,0,0,0.06)" : "none",
                transition: "all 0.15s ease"
              }}
            >
              <span>{cat.label}</span>
              {qtd > 0 && (
                <span style={{
                  fontSize: 11,
                  padding: "1px 6px",
                  borderRadius: 10,
                  background: ativa ? "var(--surface-2)" : "#DFE5ED",
                  color: "var(--ink-muted)"
                }}>
                  {qtd}
                </span>
              )}
            </button>
          );
        })}
      </div>

      {/* ── 4. Card da Categoria Ativa ───────────────────────────── */}
      <div className="card" style={{ padding: "24px 28px", border: "1px solid var(--line)", borderRadius: 12 }}>
        <div style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          marginBottom: 20,
          flexWrap: "wrap",
          gap: 12
        }}>
          <div>
            <h2 style={{ fontSize: 20, fontWeight: 700, margin: 0 }}>{catMeta.label}</h2>
            <div style={{ fontSize: 13, color: "var(--ink-muted)", marginTop: 2 }}>
              {listaRegistros.length} {listaRegistros.length === 1 ? "registro cadastrado" : "registros cadastrados"} no PostgreSQL
            </div>
          </div>

          <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
            {/* Input de Busca */}
            <div style={{ position: "relative" }}>
              <input
                type="text"
                placeholder={`Buscar em ${catMeta.label.toLowerCase()}...`}
                value={busca}
                onChange={e => setBusca(e.target.value)}
                style={{
                  padding: "8px 12px 8px 32px",
                  borderRadius: 8,
                  border: "1px solid var(--line)",
                  fontSize: 13,
                  width: 200,
                  outline: "none"
                }}
              />
              <span style={{ position: "absolute", left: 10, top: 10, color: "var(--ink-faint)" }}>
                <Icon name="search" size={14} />
              </span>
            </div>

            {/* Botão Importar */}
            <button
              onClick={() => { setLinhasImportacao(""); setModalImportar(true); }}
              className="btn btn-outline"
              style={{
                display: "flex",
                alignItems: "center",
                gap: 6,
                padding: "8px 14px",
                fontSize: 13,
                fontWeight: 600,
                borderRadius: 8
              }}
            >
              <Icon name="upload" size={15} />
              Importar
            </button>

            {/* Botão + Adicionar */}
            <button
              onClick={abrirNovo}
              className="btn btn-primary"
              style={{
                display: "flex",
                alignItems: "center",
                gap: 6,
                padding: "8px 16px",
                fontSize: 13,
                fontWeight: 600,
                borderRadius: 8,
                background: "#1877F2"
              }}
            >
              <Icon name="plus" size={16} />
              Adicionar
            </button>
          </div>
        </div>

        {/* Tabela de Registros */}
        {carregando ? (
          <div style={{ textAlign: "center", padding: "40px 0", color: "var(--ink-muted)" }}>
            Carregando {catMeta.label.toLowerCase()} do PostgreSQL...
          </div>
        ) : listaRegistros.length === 0 ? (
          <div style={{
            textAlign: "center",
            padding: "48px 20px",
            border: "1px dashed var(--line)",
            borderRadius: 10,
            background: "var(--canvas-warm)"
          }}>
            <Icon name={catMeta.icone} size={36} color="var(--ink-faint)" />
            <div style={{ fontSize: 15, fontWeight: 600, color: "var(--ink)", marginTop: 12 }}>
              Nenhum registro em {catMeta.label}
            </div>
            <div style={{ fontSize: 13, color: "var(--ink-muted)", marginTop: 4, maxWidth: 420, margin: "4px auto 16px" }}>
              Clique em <strong>+ Adicionar</strong> para cadastrar {catMeta.singular.toLowerCase()} ou use <strong>Importar</strong> para importar em lote.
            </div>
            <button onClick={abrirNovo} className="btn btn-primary" style={{ background: "#1877F2" }}>
              <Icon name="plus" size={15} /> Cadastrar primeiro(a) {catMeta.singular.toLowerCase()}
            </button>
          </div>
        ) : (
          <div style={{ overflowX: "auto" }}>
            <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
              <thead>
                <tr style={{ borderBottom: "1px solid var(--line)", textAlign: "left", color: "var(--ink-muted)" }}>
                  <th style={{ padding: "10px 14px", fontWeight: 600 }}>Nome</th>
                  {categoriaAtiva === "unidades" && <th style={{ padding: "10px 14px", fontWeight: 600 }}>Código / Cidade</th>}
                  {categoriaAtiva === "setores" && <th style={{ padding: "10px 14px", fontWeight: 600 }}>Turno / Colaboradores</th>}
                  {categoriaAtiva === "cargos" && <th style={{ padding: "10px 14px", fontWeight: 600 }}>Nível / CBO</th>}
                  {categoriaAtiva === "diretorias" && <th style={{ padding: "10px 14px", fontWeight: 600 }}>Responsável</th>}
                  {(categoriaAtiva === "ghes" || categoriaAtiva === "gess") && <th style={{ padding: "10px 14px", fontWeight: 600 }}>Código</th>}
                  <th style={{ padding: "10px 14px", fontWeight: 600, width: 100 }}>Status</th>
                  <th style={{ padding: "10px 14px", fontWeight: 600, textAlign: "right", width: 140 }}>Ações</th>
                </tr>
              </thead>
              <tbody>
                {listaRegistros.map(item => (
                  <tr key={item.id} style={{ borderBottom: "1px solid var(--line-light)" }}>
                    <td style={{ padding: "12px 14px", fontWeight: 600, color: "var(--ink)" }}>
                      {item.nome}
                      {item.descricao && (
                        <div style={{ fontSize: 11, color: "var(--ink-muted)", fontWeight: 400, marginTop: 2 }}>
                          {item.descricao}
                        </div>
                      )}
                    </td>

                    {/* Detalhes específicos de cada categoria */}
                    {categoriaAtiva === "unidades" && (
                      <td style={{ padding: "12px 14px", color: "var(--ink-muted)" }}>
                        {item.codigo ? `${item.codigo} · ` : ""}{item.cidade ? `${item.cidade}/${item.estado || ""}` : "—"}
                      </td>
                    )}
                    {categoriaAtiva === "setores" && (
                      <td style={{ padding: "12px 14px", color: "var(--ink-muted)" }}>
                        {item.turno || "Comercial"} {item.colaboradores ? `· ${item.colaboradores} colab.` : ""}
                      </td>
                    )}
                    {categoriaAtiva === "cargos" && (
                      <td style={{ padding: "12px 14px", color: "var(--ink-muted)" }}>
                        {dados.niveisCargo.find(n => n.id === item.nivel_cargo_id)?.nome || "—"} {item.cbo ? `(${item.cbo})` : ""}
                      </td>
                    )}
                    {categoriaAtiva === "diretorias" && (
                      <td style={{ padding: "12px 14px", color: "var(--ink-muted)" }}>
                        {item.responsavel || "—"}
                      </td>
                    )}
                    {(categoriaAtiva === "ghes" || categoriaAtiva === "gess") && (
                      <td style={{ padding: "12px 14px", color: "var(--ink-muted)" }}>
                        {item.codigo || "—"}
                      </td>
                    )}

                    {/* Badge de Status */}
                    <td style={{ padding: "12px 14px" }}>
                      <span style={{
                        display: "inline-block",
                        padding: "3px 8px",
                        borderRadius: 12,
                        fontSize: 11,
                        fontWeight: 600,
                        background: item.ativo ? "var(--surface-sage)" : "var(--surface-2)",
                        color: item.ativo ? "var(--health-deep)" : "var(--ink-muted)"
                      }}>
                        {item.ativo ? "Ativo" : "Inativo"}
                      </span>
                    </td>

                    {/* Botões de Ações */}
                    <td style={{ padding: "12px 14px", textAlign: "right" }}>
                      <div style={{ display: "inline-flex", gap: 6 }}>
                        <button
                          onClick={() => alternarAtivo(item)}
                          title={item.ativo ? "Desativar" : "Ativar"}
                          style={{
                            border: "1px solid var(--line)",
                            background: "transparent",
                            borderRadius: 6,
                            padding: "4px 8px",
                            cursor: "pointer",
                            fontSize: 11,
                            color: "var(--ink-muted)"
                          }}
                        >
                          {item.ativo ? "Desativar" : "Ativar"}
                        </button>
                        <button
                          onClick={() => abrirEdicao(item)}
                          title="Editar"
                          style={{
                            border: "1px solid var(--line)",
                            background: "transparent",
                            borderRadius: 6,
                            padding: "4px 8px",
                            cursor: "pointer",
                            color: "var(--ink)"
                          }}
                        >
                          <Icon name="edit" size={13} />
                        </button>
                        <button
                          onClick={() => excluirRegistro(item)}
                          title="Excluir"
                          style={{
                            border: "1px solid var(--line)",
                            background: "transparent",
                            borderRadius: 6,
                            padding: "4px 8px",
                            cursor: "pointer",
                            color: "var(--coral)"
                          }}
                        >
                          <Icon name="trash" size={13} />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* ── 5. Modal Adicionar / Editar ──────────────────────────── */}
      {modalEdicao && registroAtual && (
        <div style={{
          position: "fixed",
          inset: 0,
          background: "rgba(0,0,0,0.45)",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          zIndex: 1000,
          padding: 20
        }}>
          <div style={{
            background: "#fff",
            borderRadius: 14,
            width: "100%",
            maxWidth: 520,
            boxShadow: "0 10px 30px rgba(0,0,0,0.18)",
            overflow: "hidden"
          }}>
            <div style={{
              padding: "18px 24px",
              borderBottom: "1px solid var(--line)",
              display: "flex",
              alignItems: "center",
              justifyContent: "space-between"
            }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700 }}>
                {registroAtual.id ? `Editar ${catMeta.singular}` : `Adicionar ${catMeta.singular}`}
              </h3>
              <button
                onClick={() => setModalEdicao(false)}
                style={{ border: "none", background: "transparent", cursor: "pointer", color: "var(--ink-muted)" }}
              >
                <Icon name="x" size={18} />
              </button>
            </div>

            <form onSubmit={salvarRegistro} style={{ padding: "20px 24px", display: "flex", flexDirection: "column", gap: 14 }}>
              {erroMsg && (
                <div style={{ background: "var(--coral-soft)", color: "var(--coral)", padding: "10px 14px", borderRadius: 8, fontSize: 12 }}>
                  {erroMsg}
                </div>
              )}

              <div>
                <label style={{ fontSize: 12, fontWeight: 600, color: "var(--ink-muted)", display: "block", marginBottom: 4 }}>
                  Nome de {catMeta.singular} *
                </label>
                <input
                  type="text"
                  required
                  value={registroAtual.nome}
                  onChange={e => setRegistroAtual(p => ({ ...p, nome: e.target.value }))}
                  placeholder={`Ex: ${catMeta.singular === "Setor" ? "Operações" : catMeta.singular === "Unidade" ? "Matriz Joinville" : "Nome"}`}
                  style={{ width: "100%", padding: "9px 12px", borderRadius: 8, border: "1px solid var(--line)", fontSize: 13 }}
                />
              </div>

              {/* Campos específicos */}
              {categoriaAtiva === "unidades" && (
                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
                  <div>
                    <label style={{ fontSize: 12, fontWeight: 600, color: "var(--ink-muted)", display: "block", marginBottom: 4 }}>Código</label>
                    <input
                      type="text"
                      value={registroAtual.codigo}
                      onChange={e => setRegistroAtual(p => ({ ...p, codigo: e.target.value }))}
                      placeholder="UN-01"
                      style={{ width: "100%", padding: "9px 12px", borderRadius: 8, border: "1px solid var(--line)", fontSize: 13 }}
                    />
                  </div>
                  <div>
                    <label style={{ fontSize: 12, fontWeight: 600, color: "var(--ink-muted)", display: "block", marginBottom: 4 }}>Cidade / Estado</label>
                    <input
                      type="text"
                      value={registroAtual.cidade}
                      onChange={e => setRegistroAtual(p => ({ ...p, cidade: e.target.value }))}
                      placeholder="Joinville - SC"
                      style={{ width: "100%", padding: "9px 12px", borderRadius: 8, border: "1px solid var(--line)", fontSize: 13 }}
                    />
                  </div>
                </div>
              )}

              {categoriaAtiva === "setores" && (
                <>
                  <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
                    <div>
                      <label style={{ fontSize: 12, fontWeight: 600, color: "var(--ink-muted)", display: "block", marginBottom: 4 }}>Unidade</label>
                      <select
                        value={registroAtual.unidadeId}
                        onChange={e => setRegistroAtual(p => ({ ...p, unidadeId: e.target.value }))}
                        style={{ width: "100%", padding: "9px 12px", borderRadius: 8, border: "1px solid var(--line)", fontSize: 13 }}
                      >
                        <option value="">Selecione (opcional)...</option>
                        {dados.unidades.map(u => <option key={u.id} value={u.id}>{u.nome}</option>)}
                      </select>
                    </div>
                    <div>
                      <label style={{ fontSize: 12, fontWeight: 600, color: "var(--ink-muted)", display: "block", marginBottom: 4 }}>Diretoria</label>
                      <select
                        value={registroAtual.diretoriaId}
                        onChange={e => setRegistroAtual(p => ({ ...p, diretoriaId: e.target.value }))}
                        style={{ width: "100%", padding: "9px 12px", borderRadius: 8, border: "1px solid var(--line)", fontSize: 13 }}
                      >
                        <option value="">Selecione (opcional)...</option>
                        {dados.diretorias.map(d => <option key={d.id} value={d.id}>{d.nome}</option>)}
                      </select>
                    </div>
                  </div>

                  <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
                    <div>
                      <label style={{ fontSize: 12, fontWeight: 600, color: "var(--ink-muted)", display: "block", marginBottom: 4 }}>Colaboradores</label>
                      <input
                        type="number"
                        value={registroAtual.colaboradores}
                        onChange={e => setRegistroAtual(p => ({ ...p, colaboradores: e.target.value }))}
                        placeholder="Ex: 40"
                        style={{ width: "100%", padding: "9px 12px", borderRadius: 8, border: "1px solid var(--line)", fontSize: 13 }}
                      />
                    </div>
                    <div>
                      <label style={{ fontSize: 12, fontWeight: 600, color: "var(--ink-muted)", display: "block", marginBottom: 4 }}>Turno</label>
                      <select
                        value={registroAtual.turno}
                        onChange={e => setRegistroAtual(p => ({ ...p, turno: e.target.value }))}
                        style={{ width: "100%", padding: "9px 12px", borderRadius: 8, border: "1px solid var(--line)", fontSize: 13 }}
                      >
                        <option value="Comercial">Comercial</option>
                        <option value="Manhã">Manhã</option>
                        <option value="Tarde">Tarde</option>
                        <option value="Noturno">Noturno</option>
                        <option value="12x36">12x36</option>
                        <option value="Rotativo">Rotativo</option>
                      </select>
                    </div>
                  </div>
                </>
              )}

              {categoriaAtiva === "cargos" && (
                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
                  <div>
                    <label style={{ fontSize: 12, fontWeight: 600, color: "var(--ink-muted)", display: "block", marginBottom: 4 }}>Nível de Cargo</label>
                    <select
                      value={registroAtual.nivelCargoId}
                      onChange={e => setRegistroAtual(p => ({ ...p, nivelCargoId: e.target.value }))}
                      style={{ width: "100%", padding: "9px 12px", borderRadius: 8, border: "1px solid var(--line)", fontSize: 13 }}
                    >
                      <option value="">Selecione (opcional)...</option>
                      {dados.niveisCargo.map(n => <option key={n.id} value={n.id}>{n.nome}</option>)}
                    </select>
                  </div>
                  <div>
                    <label style={{ fontSize: 12, fontWeight: 600, color: "var(--ink-muted)", display: "block", marginBottom: 4 }}>CBO</label>
                    <input
                      type="text"
                      value={registroAtual.cbo}
                      onChange={e => setRegistroAtual(p => ({ ...p, cbo: e.target.value }))}
                      placeholder="Ex: 7212-15"
                      style={{ width: "100%", padding: "9px 12px", borderRadius: 8, border: "1px solid var(--line)", fontSize: 13 }}
                    />
                  </div>
                </div>
              )}

              {(categoriaAtiva === "diretorias" || categoriaAtiva === "setores") && (
                <div>
                  <label style={{ fontSize: 12, fontWeight: 600, color: "var(--ink-muted)", display: "block", marginBottom: 4 }}>Responsável</label>
                  <input
                    type="text"
                    value={registroAtual.responsavel}
                    onChange={e => setRegistroAtual(p => ({ ...p, responsavel: e.target.value }))}
                    placeholder="Nome do gestor ou responsável"
                    style={{ width: "100%", padding: "9px 12px", borderRadius: 8, border: "1px solid var(--line)", fontSize: 13 }}
                  />
                </div>
              )}

              {(categoriaAtiva === "ghes" || categoriaAtiva === "gess") && (
                <div>
                  <label style={{ fontSize: 12, fontWeight: 600, color: "var(--ink-muted)", display: "block", marginBottom: 4 }}>Código</label>
                  <input
                    type="text"
                    value={registroAtual.codigo}
                    onChange={e => setRegistroAtual(p => ({ ...p, codigo: e.target.value }))}
                    placeholder="Ex: GHE-01"
                    style={{ width: "100%", padding: "9px 12px", borderRadius: 8, border: "1px solid var(--line)", fontSize: 13 }}
                  />
                </div>
              )}

              <div>
                <label style={{ fontSize: 12, fontWeight: 600, color: "var(--ink-muted)", display: "block", marginBottom: 4 }}>Descrição / Observações</label>
                <textarea
                  rows={2}
                  value={registroAtual.descricao}
                  onChange={e => setRegistroAtual(p => ({ ...p, descricao: e.target.value }))}
                  placeholder="Informações adicionais..."
                  style={{ width: "100%", padding: "9px 12px", borderRadius: 8, border: "1px solid var(--line)", fontSize: 13, resize: "vertical" }}
                />
              </div>

              <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 10 }}>
                <button
                  type="button"
                  onClick={() => setModalEdicao(false)}
                  className="btn btn-outline"
                  style={{ padding: "8px 16px", borderRadius: 8 }}
                >
                  Cancelar
                </button>
                <button
                  type="submit"
                  disabled={salvando}
                  className="btn btn-primary"
                  style={{ padding: "8px 20px", borderRadius: 8, background: "#1877F2" }}
                >
                  {salvando ? "Salvando..." : "Salvar"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ── 6. Modal Importar ────────────────────────────────────── */}
      {modalImportar && (
        <div style={{
          position: "fixed",
          inset: 0,
          background: "rgba(0,0,0,0.45)",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          zIndex: 1000,
          padding: 20
        }}>
          <div style={{
            background: "#fff",
            borderRadius: 14,
            width: "100%",
            maxWidth: 520,
            boxShadow: "0 10px 30px rgba(0,0,0,0.18)",
            overflow: "hidden"
          }}>
            <div style={{
              padding: "18px 24px",
              borderBottom: "1px solid var(--line)",
              display: "flex",
              alignItems: "center",
              justifyContent: "space-between"
            }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700 }}>
                Importar {catMeta.label} em Lote
              </h3>
              <button
                onClick={() => setModalImportar(false)}
                style={{ border: "none", background: "transparent", cursor: "pointer", color: "var(--ink-muted)" }}
              >
                <Icon name="x" size={18} />
              </button>
            </div>

            <div style={{ padding: "20px 24px", display: "flex", flexDirection: "column", gap: 14 }}>
              <div style={{ background: "#F3F7FD", padding: "12px 14px", borderRadius: 8, fontSize: 12, color: "var(--ink-muted)", lineHeight: 1.5 }}>
                ℹ️ <strong>Importação rápida:</strong> Cole os nomes abaixo (um por linha) para adicionar diretamente à base de dados PostgreSQL da empresa. O layout de importação via planilha Excel/CSV está em fase de homologação técnica.
              </div>

              <div>
                <label style={{ fontSize: 12, fontWeight: 600, color: "var(--ink-muted)", display: "block", marginBottom: 4 }}>
                  Nomes para importação (um por linha)
                </label>
                <textarea
                  rows={6}
                  value={linhasImportacao}
                  onChange={e => setLinhasImportacao(e.target.value)}
                  placeholder={`Setor Comercial\nSetor de Operações\nAlmoxarifado Central`}
                  style={{ width: "100%", padding: "10px 12px", borderRadius: 8, border: "1px solid var(--line)", fontSize: 13, resize: "vertical", fontFamily: "monospace" }}
                />
              </div>

              <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 10 }}>
                <button
                  onClick={() => setModalImportar(false)}
                  className="btn btn-outline"
                  style={{ padding: "8px 16px", borderRadius: 8 }}
                >
                  Cancelar
                </button>
                <button
                  onClick={executarImportacao}
                  disabled={salvando}
                  className="btn btn-primary"
                  style={{ padding: "8px 20px", borderRadius: 8, background: "#1877F2" }}
                >
                  {salvando ? "Importando..." : "Importar Linhas"}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

Object.assign(window, { EstruturaOrganizacionalEtapa });
