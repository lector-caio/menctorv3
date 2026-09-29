-- V8: Motor de Priorização e Plano de Ação Psicossocial (NR-01 / GRO)
-- Persistência de planos de ação derivados do Scoring Psicossocial com rastreabilidade, controle de status e histórico de alterações.

-- 1. Tabela mestre do Plano de Ação vinculado ao Snapshot de Scoring
create table if not exists scoring_planos_acao (
  id           text primary key default gen_random_uuid()::text,
  scoring_id   text not null references campanha_scoring(id) on delete cascade,
  campanha_id  text not null references campanhas(id) on delete cascade,
  cliente_id   text not null references clients(id) on delete cascade,
  ciclo        text,
  status       text not null default 'rascunho' check (status in ('rascunho', 'em_execucao', 'concluido', 'arquivado')),
  gerado_em    timestamptz not null default now(),
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now(),
  constraint uq_scoring_plano unique (scoring_id)
);

create index if not exists idx_planos_cliente on scoring_planos_acao(cliente_id);
create index if not exists idx_planos_campanha on scoring_planos_acao(campanha_id);
create index if not exists idx_planos_scoring on scoring_planos_acao(scoring_id);

create trigger scoring_planos_acao_updated_at before update on scoring_planos_acao
  for each row execute function set_updated_at();

-- 2. Tabela de ações operacionais derivadas dos fatores de risco
create table if not exists scoring_acoes (
  id                   text primary key default gen_random_uuid()::text,
  plano_id             text not null references scoring_planos_acao(id) on delete cascade,
  scoring_id           text not null references campanha_scoring(id) on delete cascade,
  dimensao_codigo      text not null,
  dimensao_nome        text not null,
  categoria            text,
  nivel_risco          text not null,
  probabilidade_codigo text,
  severidade_codigo    text,
  score_ps             numeric(5, 2),
  prioridade           text not null,
  acao                 text not null,
  objetivo             text,
  responsavel          text,
  prazo_dias           integer,
  data_prevista        text,
  indicador            text,
  meta                 text,
  status               text not null default 'pendente' check (status in ('pendente', 'em_andamento', 'concluida', 'cancelada')),
  observacoes          text,
  origem               text not null default 'automatica' check (origem in ('automatica', 'manual')),
  ordem                integer not null default 0,
  created_at           timestamptz not null default now(),
  updated_at           timestamptz not null default now()
);

create index if not exists idx_acoes_plano on scoring_acoes(plano_id);
create index if not exists idx_acoes_scoring on scoring_acoes(scoring_id);
create index if not exists idx_acoes_status on scoring_acoes(status);
create index if not exists idx_acoes_nivel on scoring_acoes(nivel_risco);

create trigger scoring_acoes_updated_at before update on scoring_acoes
  for each row execute function set_updated_at();

-- 3. Histórico de auditoria e alterações das ações
create table if not exists scoring_acoes_historico (
  id              text primary key default gen_random_uuid()::text,
  acao_id         text not null references scoring_acoes(id) on delete cascade,
  status_anterior text,
  status_novo     text,
  campo_alterado  text,
  valor_anterior  text,
  valor_novo      text,
  usuario         text default 'sistema',
  motivo          text,
  created_at      timestamptz not null default now()
);

create index if not exists idx_acoes_hist_acao on scoring_acoes_historico(acao_id);

-- 4. Catálogo futuro de ações recomendadas (preparação de infraestrutura)
create table if not exists plano_acao_catalogo (
  id                 text primary key default gen_random_uuid()::text,
  codigo             text not null unique,
  nome               text not null,
  descricao          text,
  categoria          text,
  nivel_risco_minimo text,
  ativo              boolean not null default true,
  created_at         timestamptz not null default now()
);
