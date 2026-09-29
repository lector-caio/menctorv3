/* global window */

// =====================================================
// MenctorDB — cliente HTTP exclusivo para o backend
// Quarkus + PostgreSQL (porta 5000 em dev)
// =====================================================

const getApiBase = () => {
  if (typeof window !== "undefined") {
    if (window.MENCTOR_API_URL) return window.MENCTOR_API_URL.replace(/\/$/, "");
    try {
      const stored = window.localStorage?.getItem("MENCTOR_API_URL");
      if (stored) return stored.replace(/\/$/, "");
    } catch (e) {}
    if (window.location && window.location.port === "3000") {
      return "http://localhost:5000";
    }
    return "";
  }
  return "http://localhost:5000";
};

const apiFetch = async (path, options = {}) => {
  const base = getApiBase();
  const url = `${base}/api/${path.replace(/^\//, "")}`;
  const response = await fetch(url, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...(options.headers || {}),
    },
  });
  if (!response.ok) {
    let errorMsg = `Erro na requisição ${response.status}`;
    try {
      const json = await response.json();
      errorMsg = json.error || json.message || errorMsg;
    } catch (e) {
      try {
        const text = await response.text();
        if (text) errorMsg = text;
      } catch (_) {}
    }
    const error = new Error(errorMsg);
    error.status = response.status;
    throw error;
  }
  if (response.status === 204) return null;
  return response.json();
};

// Busca de um registro: "não encontrado" (404) devolve null em vez de erro.
const apiFetchOrNull = async (path) => {
  try {
    return await apiFetch(path);
  } catch (err) {
    if (err.status === 404) return null;
    throw err;
  }
};

// =====================================================
// PIPELINE
// =====================================================

// Colunas de pipeline_cards; o pipeline.jsx usa valor, proximoPasso, decisor, probabilidade e assinado.
const toDbCard = (card, stage) => ({
  id: card.id,
  stage,
  empresa: card.empresa || "Novo lead",
  contato: card.contato || "",
  email: card.email || "",
  funcionarios: Number(card.funcionarios || card.colaboradores || 0),
  valor: Number(card.valor ?? card.mrr ?? card.ticket ?? 0),
  dias: Number(card.dias || 0),
  decisor: card.decisor || card.contato || "",
  proximo_passo: card.proximoPasso || "",
  probabilidade: Number(card.probabilidade ?? 35),
  origem: card.origem || "",
  extra: {
    ...(card.extra || {}),
    assinado: !!(card.assinado ?? card.extra?.assinado),
    etapaManual: !!(card.etapaManual ?? card.extra?.etapaManual),
  },
});

const toAppCard = (row) => ({
  id: row.id,
  stage: row.stage,
  empresa: row.empresa,
  contato: row.contato,
  email: row.email,
  funcionarios: row.funcionarios,
  colaboradores: row.funcionarios,
  valor: row.valor,
  mrr: row.valor,
  dias: row.dias,
  decisor: row.decisor,
  proximoPasso: row.proximo_passo,
  probabilidade: row.probabilidade,
  origem: row.origem,
  assinado: !!row.extra?.assinado,
  etapaManual: !!row.extra?.etapaManual,
  extra: row.extra || {},
});

const MenctorDB = {
  async listPipelineCards() {
    const rows = await apiFetch("pipeline");
    return (rows || []).map(toAppCard);
  },

  async upsertPipelineCard(card, stage = card.stage || "lead") {
    const saved = await apiFetch("pipeline", {
      method: "POST",
      body: JSON.stringify(toDbCard(card, stage)),
    });
    return saved ? toAppCard(saved) : null;
  },

  async updatePipelineStage(card, stage, patch = {}) {
    const updated = { ...card, ...patch, stage, dias: 0 };
    return this.upsertPipelineCard(updated, stage);
  },
};

// =====================================================
// CLIENT JOURNEY (Etapas + Cadastro)
// =====================================================

MenctorDB.listClients = async () => {
  return await apiFetch("clientes");
};

MenctorDB.getClient = async (id) => {
  const client = await apiFetchOrNull(`clientes/${encodeURIComponent(id)}`);
  if (!client) return null;

  const status = {};
  (client.progress || []).forEach(p => {
    status[p.step_number] = { status: p.status, ...(p.data || {}) };
  });

  const etapaAtual = client.current_step || 1;
  const loadedCadastro = toCamelCadastro(client.cadastro || null);

  return {
    ...client,
    progress: client.progress || [],
    cadastro: loadedCadastro,
    etapas: {
      etapaAtual,
      status,
    },
  };
};

MenctorDB.createClient = async (data = {}) => {
  const payload = {
    name: data.name || "Nova Empresa",
    cnpj: data.cnpj || "",
    contact: data.contact || "",
    sector: data.sector || "Serviços",
    employees: data.employees || 50,
    mrr: data.mrr || 3500,
    color: data.color || "#2F7D6F",
    status: data.status || "ativo",
    current_step: 1,
    ...data,
  };

  return await apiFetch("clientes", {
    method: "POST",
    body: JSON.stringify(payload),
  });
};

MenctorDB.updateClient = async (id, patch = {}) => {
  return await apiFetch(`clientes/${encodeURIComponent(id)}`, {
    method: "PATCH",
    body: JSON.stringify(patch),
  });
};

MenctorDB.saveStepProgress = async (clientId, stepNumber, status, extraData = {}) => {
  return await apiFetch(`clientes/${encodeURIComponent(clientId)}/etapas/${stepNumber}`, {
    method: "PUT",
    body: JSON.stringify({ status, data: extraData }),
  });
};

// Helper to convert camelCase form to snake_case for DB
const toSnakeCadastro = (data) => ({
  client_id: data.client_id,
  razao_social: data.razaoSocial || data.razao_social,
  responsavel: data.responsavel,
  email: data.email,
  telefone: data.telefone,
  cnpj: data.cnpj,
  qtd_por_area: data.qtdPorArea || data.qtd_por_area,
  qtd_cargos: data.qtdCargos || data.qtd_cargos,
  segmento: data.segmento,
  unidades: data.unidades,
  cidades: data.cidades,
  terceirizados: data.terceirizados,
  possui: data.possui,
  indicadores: data.indicadores,
  mapeamento_formal: data.mapeamentoFormal || data.mapeamento_formal,
  pesquisa_clima: data.pesquisaClima || data.pesquisa_clima,
  canais_escuta: data.canaisEscuta || data.canais_escuta,
  fiscalizacao_evidencia: data.fiscalizacaoEvidencia || data.fiscalizacao_evidencia,
  gestao_riscos_outra: data.gestaoRiscosOutra || data.gestao_riscos_outra,
  pressao_metas: data.pressaoMetas || data.pressao_metas,
  ritmo_intenso: data.ritmoIntenso || data.ritmo_intenso,
  capacitacao_lideranca: data.capacitacaoLideranca || data.capacitacao_lideranca,
  conflitos_recorrentes: data.conflitosRecorrentes || data.conflitos_recorrentes,
  assedio_moral: data.assedioMoral || data.assedio_moral,
  lideranca_outra: data.liderancaOutra || data.lideranca_outra,
  juridico_acompanha: data.juridicoAcompanha || data.juridico_acompanha,
  acao_trabalhista_mental: data.acaoTrabalhistaMental || data.acao_trabalhista_mental,
  sente_protegida: data.senteProtegida || data.sente_protegida,
  juridica_outra: data.juridicaOutra || data.juridica_outra,
  excesso_trabalho: data.excessoTrabalho || data.excesso_trabalho,
  prazos_inalcancaveis: data.prazosInatingiveis || data.prazos_inalcancaveis,
  falta_controle: data.faltaControle || data.falta_controle,
  estrutura_nao_aplica: data.estruturaNaoAplica || data.estrutura_nao_aplica,
  estrutura_outra: data.estruturaOutra || data.estrutura_outra,
  trabalha_com: data.trabalhaCom || data.trabalha_com,
  submitted_at: data.submittedAt || data.submitted_at || new Date().toISOString(),
  form_token: data.formToken || data.form_token,
});

// Reverse for loading
const toCamelCadastro = (row) => {
  if (!row) return null;
  return {
    ...row,
    razaoSocial: row.razao_social,
    qtdPorArea: row.qtd_por_area,
    qtdCargos: row.qtd_cargos,
    mapeamentoFormal: row.mapeamento_formal,
    pesquisaClima: row.pesquisa_clima,
    canaisEscuta: row.canais_escuta,
    fiscalizacaoEvidencia: row.fiscalizacao_evidencia,
    gestaoRiscosOutra: row.gestao_riscos_outra,
    pressaoMetas: row.pressao_metas,
    ritmoIntenso: row.ritmo_intenso,
    capacitacaoLideranca: row.capacitacao_lideranca,
    conflitosRecorrentes: row.conflitos_recorrentes,
    assedioMoral: row.assedio_moral,
    liderancaOutra: row.lideranca_outra,
    juridicoAcompanha: row.juridico_acompanha,
    acaoTrabalhistaMental: row.acao_trabalhista_mental,
    senteProtegida: row.sente_protegida,
    juridicaOutra: row.juridica_outra,
    excessoTrabalho: row.excesso_trabalho,
    prazosInatingiveis: row.prazos_inalcancaveis,
    faltaControle: row.falta_controle,
    estruturaNaoAplica: row.estrutura_nao_aplica,
    estruturaOutra: row.estrutura_outra,
    trabalhaCom: row.trabalha_com,
    submittedAt: row.submitted_at,
    formToken: row.form_token,
  };
};

MenctorDB.saveCadastro = async (clientId, formData) => {
  const payload = toSnakeCadastro({ client_id: clientId, ...formData });
  const cad = await apiFetch(`clientes/${encodeURIComponent(clientId)}/cadastro`, {
    method: "PUT",
    body: JSON.stringify(payload),
  });
  await MenctorDB.saveStepProgress(clientId, 1, "concluida", formData);
  return cad;
};

MenctorDB.listSteps = async () => {
  return await apiFetch("clientes/etapas");
};

// =====================================================
// CANAL DE DENÚNCIAS
// =====================================================

const toDbDenuncia = (d) => ({
  id: d.id,
  protocolo: d.protocolo,
  cliente_id: d.clienteId || d.cliente_id,
  data: d.data,
  status: d.status || "triagem",
  gravidade: d.gravidade || null,
  tipo_id: d.tipoId || d.tipo_id || null,
  natureza: d.natureza || null,
  anonimo: d.anonimo !== false,
  denunciante: d.denunciante || null,
  area: d.area || null,
  relato: d.relato || "",
  evidencias: d.evidencias || [],
  admissibilidade: d.admissibilidade || null,
  prazo_final: d.prazoFinal || d.prazo_final || null,
  parecer: d.parecer || null,
  resultado: d.resultado || null,
  recomendacoes: d.recomendacoes || null,
  andamentos: d.andamentos || [],
  mensagens: d.mensagens || [],
  audit_log: d.auditLog || d.audit_log || [],
});

const toAppDenuncia = (row) => ({
  id: row.id,
  protocolo: row.protocolo,
  clienteId: row.cliente_id,
  data: row.data,
  status: row.status,
  gravidade: row.gravidade,
  tipoId: row.tipo_id,
  natureza: row.natureza,
  anonimo: row.anonimo,
  denunciante: row.denunciante,
  area: row.area,
  relato: row.relato,
  evidencias: row.evidencias || [],
  admissibilidade: row.admissibilidade,
  prazoFinal: row.prazo_final,
  parecer: row.parecer,
  resultado: row.resultado,
  recomendacoes: row.recomendacoes,
  andamentos: row.andamentos || [],
  mensagens: row.mensagens || [],
  auditLog: row.audit_log || [],
});

MenctorDB.listDenuncias = async () => {
  const rows = await apiFetch("denuncias");
  return (rows || []).map(toAppDenuncia);
};

MenctorDB.getDenuncia = async (id) => {
  const row = await apiFetchOrNull(`denuncias/${encodeURIComponent(id)}`);
  return row ? toAppDenuncia(row) : null;
};

MenctorDB.getDenunciaByProtocolo = async (protocolo) => {
  const row = await apiFetchOrNull(`denuncias/protocolo/${encodeURIComponent(protocolo)}`);
  return row ? toAppDenuncia(row) : null;
};

MenctorDB.upsertDenuncia = async (denuncia) => {
  const row = await apiFetch("denuncias", {
    method: "POST",
    body: JSON.stringify(toDbDenuncia(denuncia)),
  });
  return row ? toAppDenuncia(row) : null;
};

// =====================================================
// CAMPANHAS & RESPOSTAS (PostgreSQL / Quarkus)
// =====================================================

const toDbCampanha = (c) => ({
  id: c.id,
  cliente_id: c.clienteId || c.cliente_id || null,
  titulo: c.titulo,
  descricao: c.descricao || "",
  diagnostico_id: c.diagnosticoId || c.diagnostico_id || "copsoq",
  instrumento: c.instrumento || "COPSOQ",
  ciclo: c.ciclo || "2026-Q1",
  reavaliacao: c.reavaliacao || "90 dias",
  data_inicial: c.dataInicial || c.data_inicial || null,
  data_final: c.dataFinal || c.data_final || null,
  quantidade_funcionarios: Number(c.quantidadeFuncionarios || c.quantidade_funcionarios || 0),
  status: c.status || "ativa",
  link_token: c.linkToken || c.link_token || null,
  extra: {
    ...(c.extra || {}),
    ...(c.numEventosPresenciais !== undefined ? { numEventosPresenciais: Number(c.numEventosPresenciais) } : {}),
    ...(c.agenda !== undefined ? { agenda: c.agenda } : {}),
  },
});

const toAppCampanha = (row) => ({
  id: row.id,
  clienteId: row.cliente_id,
  titulo: row.titulo,
  descricao: row.descricao,
  diagnosticoId: row.diagnostico_id,
  instrumento: row.instrumento,
  ciclo: row.ciclo,
  reavaliacao: row.reavaliacao,
  dataInicial: row.data_inicial,
  dataFinal: row.data_final,
  quantidadeFuncionarios: row.quantidade_funcionarios,
  numEventosPresenciais: row.extra?.numEventosPresenciais ?? row.quantidade_funcionarios,
  agenda: row.extra?.agenda || "",
  status: row.status,
  linkToken: row.link_token,
  createdAt: row.created_at,
  updatedAt: row.updated_at,
  extra: row.extra || {},
});

MenctorDB.listCampanhas = async (clienteId = "") => {
  try {
    const query = clienteId ? `campanhas?clienteId=${encodeURIComponent(clienteId)}` : "campanhas";
    const rows = await apiFetch(query);
    return (rows || []).map(toAppCampanha);
  } catch (err) {
    console.warn("Erro ao listar campanhas:", err);
    return (window.CAMPANHAS || []).filter(c => !clienteId || c.clienteId === clienteId);
  }
};

MenctorDB.getCampanha = async (id) => {
  try {
    const row = await apiFetch(`campanhas/${encodeURIComponent(id)}`);
    return row ? toAppCampanha(row) : null;
  } catch (err) {
    return (window.CAMPANHAS || []).find(c => c.id === id) || null;
  }
};

MenctorDB.upsertCampanha = async (campanha) => {
  try {
    const row = await apiFetch("campanhas", {
      method: "POST",
      body: JSON.stringify(toDbCampanha(campanha)),
    });
    return row ? toAppCampanha(row) : null;
  } catch (err) {
    console.warn("Erro ao salvar campanha:", err);
    return campanha;
  }
};

MenctorDB.updateCampanhaStatus = async (id, status) => {
  try {
    const row = await apiFetch(`campanhas/${encodeURIComponent(id)}/status`, {
      method: "PATCH",
      body: JSON.stringify({ status }),
    });
    return row ? toAppCampanha(row) : null;
  } catch (err) {
    return null;
  }
};

MenctorDB.deleteCampanha = async (id) => {
  try {
    await apiFetch(`campanhas/${encodeURIComponent(id)}`, { method: "DELETE" });
    return true;
  } catch (err) {
    return false;
  }
};

MenctorDB.submeterRespostaCampanha = async (campanhaId, payload) => {
  try {
    return await apiFetch(`campanhas/${encodeURIComponent(campanhaId)}/respostas`, {
      method: "POST",
      body: JSON.stringify(payload),
    });
  } catch (err) {
    console.warn("Erro ao registrar resposta:", err);
    if (window.registrarRespostaCampanha) {
      window.registrarRespostaCampanha({ id: campanhaId }, payload);
    }
    return payload;
  }
};

MenctorDB.listRespostasCampanha = async (campanhaId) => {
  try {
    return await apiFetch(`campanhas/${encodeURIComponent(campanhaId)}/respostas`);
  } catch (err) {
    if (window.getCampanhaRespostas) return window.getCampanhaRespostas(campanhaId);
    return [];
  }
};

MenctorDB.getResultadoCampanha = async (campanhaId) => {
  try {
    return await apiFetch(`campanhas/${encodeURIComponent(campanhaId)}/resultado`);
  } catch (err) {
    if (window.getCampanhaResultado) return window.getCampanhaResultado({ id: campanhaId });
    return { total: 0, media: null, porDimensao: [], porSetor: [] };
  }
};

MenctorDB.jaRespondeuCampanha = async (campanhaId, cpfHash) => {
  try {
    const res = await apiFetch(`campanhas/${encodeURIComponent(campanhaId)}/ja-respondeu?cpfHash=${encodeURIComponent(cpfHash)}`);
    return !!res?.jaRespondeu;
  } catch (err) {
    if (window.jaRespondeuCampanha) return window.jaRespondeuCampanha(campanhaId, cpfHash);
    return false;
  }
};

// =====================================================
// INSTRUMENTOS & QUESTIONÁRIOS (PostgreSQL / Quarkus)
// =====================================================

MenctorDB.listInstrumentos = async () => {
  try {
    return await apiFetch("instrumentos");
  } catch (err) {
    console.warn("Erro ao listar instrumentos:", err);
    return window.DIAGNOSTICOS || [];
  }
};

MenctorDB.getInstrumento = async (id) => {
  try {
    return await apiFetch(`instrumentos/${encodeURIComponent(id)}`);
  } catch (err) {
    console.warn("Erro ao obter instrumento:", err);
    return null;
  }
};

// =====================================================
// ESTRUTURA ORGANIZACIONAL (PostgreSQL / Quarkus)
// =====================================================

MenctorDB.organizacao = {
  resumo: async (clienteId) => {
    return await apiFetch(`clientes/${encodeURIComponent(clienteId)}/organizacao`);
  },
  listar: async (clienteId, categoria, apenasAtivos = false) => {
    const q = apenasAtivos ? "?apenasAtivos=true" : "";
    return await apiFetch(`clientes/${encodeURIComponent(clienteId)}/organizacao/${encodeURIComponent(categoria)}${q}`);
  },
  obter: async (clienteId, categoria, id) => {
    return await apiFetch(`clientes/${encodeURIComponent(clienteId)}/organizacao/${encodeURIComponent(categoria)}/${encodeURIComponent(id)}`);
  },
  criar: async (clienteId, categoria, dados) => {
    return await apiFetch(`clientes/${encodeURIComponent(clienteId)}/organizacao/${encodeURIComponent(categoria)}`, {
      method: "POST",
      body: JSON.stringify(dados),
    });
  },
  atualizar: async (clienteId, categoria, id, dados) => {
    return await apiFetch(`clientes/${encodeURIComponent(clienteId)}/organizacao/${encodeURIComponent(categoria)}/${encodeURIComponent(id)}`, {
      method: "PUT",
      body: JSON.stringify(dados),
    });
  },
  alternarAtivo: async (clienteId, categoria, id) => {
    return await apiFetch(`clientes/${encodeURIComponent(clienteId)}/organizacao/${encodeURIComponent(categoria)}/${encodeURIComponent(id)}/toggle`, {
      method: "PATCH",
    });
  },
  excluir: async (clienteId, categoria, id) => {
    return await apiFetch(`clientes/${encodeURIComponent(clienteId)}/organizacao/${encodeURIComponent(categoria)}/${encodeURIComponent(id)}`, {
      method: "DELETE",
    });
  },
};

// =====================================================
// MATRIZ DE RISCO PGR (PostgreSQL / Quarkus)
// =====================================================

MenctorDB.matriz = {
  obter: async (clienteId) => {
    return await apiFetch(`clientes/${encodeURIComponent(clienteId)}/matriz`);
  },
  listarVersoes: async (clienteId) => {
    try {
      return await apiFetch(`clientes/${encodeURIComponent(clienteId)}/matriz/versoes`);
    } catch (err) {
      return window.MATRIZES_VERSOES?.[clienteId] || [];
    }
  },
  obterVersao: async (clienteId, versaoId) => {
    return await apiFetch(`clientes/${encodeURIComponent(clienteId)}/matriz/versoes/${encodeURIComponent(versaoId)}`);
  },
  criarVersao: async (clienteId, dados) => {
    return await apiFetch(`clientes/${encodeURIComponent(clienteId)}/matriz/versoes`, {
      method: "POST",
      body: JSON.stringify(dados),
    });
  },
  atualizarVersao: async (clienteId, versaoId, dados) => {
    return await apiFetch(`clientes/${encodeURIComponent(clienteId)}/matriz/versoes/${encodeURIComponent(versaoId)}`, {
      method: "PUT",
      body: JSON.stringify(dados),
    });
  },
  publicarVersao: async (clienteId, versaoId) => {
    return await apiFetch(`clientes/${encodeURIComponent(clienteId)}/matriz/versoes/${encodeURIComponent(versaoId)}/publicar`, {
      method: "POST",
    });
  },
  calcular: async (clienteId, probabilidade, severidade) => {
    try {
      return await apiFetch(`clientes/${encodeURIComponent(clienteId)}/matriz/calcular?probabilidade=${probabilidade}&severidade=${severidade}`);
    } catch (err) {
      return null;
    }
  },
};

// =====================================================
// MOTOR DE SCORING PSICOSSOCIAL (Quarkus / PostgreSQL)
// =====================================================
MenctorDB.scoring = {
  getCampanhaScoring: async (campanhaId) => {
    return await apiFetch(`campanhas/${encodeURIComponent(campanhaId)}/scoring`);
  },
  recalcularCampanhaScoring: async (campanhaId) => {
    return await apiFetch(`campanhas/${encodeURIComponent(campanhaId)}/scoring/recalcular`, {
      method: "POST",
    });
  },
  getCampanhaScoringDimensoes: async (campanhaId) => {
    return await apiFetch(`campanhas/${encodeURIComponent(campanhaId)}/scoring/dimensoes`);
  },
  getCampanhaScoringRecortes: async (campanhaId) => {
    return await apiFetch(`campanhas/${encodeURIComponent(campanhaId)}/scoring/recortes`);
  },
  getHistoricoCliente: async (clienteId) => {
    return await apiFetch(`clientes/${encodeURIComponent(clienteId)}/scoring/historico`);
  },
};

// =====================================================
// MOTOR DE PLANO DE AÇÃO PSICOSSOCIAL (Quarkus / PostgreSQL)
// =====================================================
MenctorDB.planoAcao = {
  get: async (campanhaId) => {
    return await apiFetch(`campanhas/${encodeURIComponent(campanhaId)}/plano-acao`);
  },
  gerar: async (campanhaId, regenerar = false) => {
    const url = regenerar
      ? `campanhas/${encodeURIComponent(campanhaId)}/plano-acao/gerar?regenerar=true`
      : `campanhas/${encodeURIComponent(campanhaId)}/plano-acao/gerar`;
    return await apiFetch(url, { method: "POST" });
  },
  regenerar: async (campanhaId) => {
    return await apiFetch(`campanhas/${encodeURIComponent(campanhaId)}/plano-acao/regenerar`, {
      method: "POST",
    });
  },
  getResumo: async (campanhaId) => {
    return await apiFetch(`campanhas/${encodeURIComponent(campanhaId)}/plano-acao/resumo`);
  },
  atualizarAcao: async (campanhaId, acaoId, dados) => {
    return await apiFetch(`campanhas/${encodeURIComponent(campanhaId)}/plano-acao/acoes/${encodeURIComponent(acaoId)}`, {
      method: "PUT",
      body: JSON.stringify(dados),
    });
  },
  alterarStatus: async (campanhaId, acaoId, status, motivo = null, usuario = "sistema") => {
    return await apiFetch(`campanhas/${encodeURIComponent(campanhaId)}/plano-acao/acoes/${encodeURIComponent(acaoId)}/status`, {
      method: "PATCH",
      body: JSON.stringify({ status, motivo, usuario }),
    });
  },
  criarAcao: async (campanhaId, dados) => {
    return await apiFetch(`campanhas/${encodeURIComponent(campanhaId)}/plano-acao/acoes`, {
      method: "POST",
      body: JSON.stringify(dados),
    });
  },
  excluirAcao: async (campanhaId, acaoId, motivo = null) => {
    const query = motivo ? `?motivo=${encodeURIComponent(motivo)}` : "";
    return await apiFetch(`campanhas/${encodeURIComponent(campanhaId)}/plano-acao/acoes/${encodeURIComponent(acaoId)}${query}`, {
      method: "DELETE",
    });
  },
  getHistorico: async (campanhaId, acaoId) => {
    return await apiFetch(`campanhas/${encodeURIComponent(campanhaId)}/plano-acao/acoes/${encodeURIComponent(acaoId)}/historico`);
  },
};

Object.assign(window, { MenctorDB });
