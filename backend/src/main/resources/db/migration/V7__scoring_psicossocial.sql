-- V7: Motor de Scoring Psicossocial Persistente (NR-01 / GRO)
-- Persistência de resultados consolidados de Scoring por campanha, ciclo, dimensões e recortes organizacionais.

-- 1. Ampliar demografia nas respostas das campanhas se necessário
alter table campanha_respostas add column if not exists unidade text;
alter table campanha_respostas add column if not exists ghe text;
alter table campanha_respostas add column if not exists ges text;

-- 2. Tabela principal de snapshots de Scoring por campanha / ciclo
create table if not exists campanha_scoring (
  id                      text primary key default gen_random_uuid()::text,
  campanha_id             text not null references campanhas(id) on delete cascade,
  cliente_id              text not null references clients(id) on delete cascade,
  instrumento_id          text not null,
  ciclo                   text,
  matriz_versao_id        text not null references matriz_versoes(id),
  matriz_versao_codigo    text not null,
  total_respondentes      integer not null default 0,
  ips_global              numeric(5, 2),
  confiabilidade          text not null check (confiabilidade in ('Alta', 'Média', 'Baixa', 'Insuficiente')),
  confiabilidade_detalhe  text,
  fatores_alto_critico_pct numeric(5, 2) default 0.0,
  recortes_suprimidos_qtd integer not null default 0,
  dados_consolidados      jsonb default '{}'::jsonb,
  created_at              timestamptz not null default now(),
  updated_at              timestamptz not null default now(),
  constraint uq_campanha_scoring unique (campanha_id)
);

create index if not exists idx_scoring_cliente_ciclo on campanha_scoring(cliente_id, ciclo);
create index if not exists idx_scoring_campanha on campanha_scoring(campanha_id);
create index if not exists idx_scoring_matriz_versao on campanha_scoring(matriz_versao_id);

create trigger campanha_scoring_updated_at before update on campanha_scoring
  for each row execute function set_updated_at();

-- 3. Detalhamento por dimensão / fator do instrumento
create table if not exists campanha_scoring_dimensoes (
  id                     text primary key default gen_random_uuid()::text,
  scoring_id             text not null references campanha_scoring(id) on delete cascade,
  codigo                 text not null,
  nome                   text not null,
  categoria              text,
  total_questoes         integer not null default 0,
  total_respondentes     integer not null default 0,
  media_likert           numeric(4, 2),
  indice_normalizado     numeric(5, 2),
  prevalencia            numeric(5, 2),
  probabilidade_nivel    integer check (probabilidade_nivel between 1 and 5),
  probabilidade_codigo   text,
  probabilidade_nome     text,
  severidade_nivel       integer check (severidade_nivel between 1 and 5),
  severidade_codigo      text,
  severidade_nome        text,
  severidade_configurada boolean not null default true,
  score_ps               numeric(5, 2),
  nivel_risco            text,
  prioridade             text,
  cor                    text,
  motivo_auditoria       text,
  ordem                  integer not null default 0,
  constraint uq_scoring_dimensao unique (scoring_id, codigo)
);

create index if not exists idx_scoring_dim_scoring on campanha_scoring_dimensoes(scoring_id);
create index if not exists idx_scoring_dim_nivel on campanha_scoring_dimensoes(nivel_risco);

-- 4. Recortes organizacionais e demográficos com controle de K-anonimato
create table if not exists campanha_scoring_recortes (
  id                     text primary key default gen_random_uuid()::text,
  scoring_id             text not null references campanha_scoring(id) on delete cascade,
  tipo_recorte           text not null check (tipo_recorte in ('setor', 'cargo', 'unidade', 'ghe', 'ges')),
  nome_recorte           text not null,
  total_respondentes     integer not null default 0,
  suprimido_k_anonimato  boolean not null default false,
  ips                    numeric(5, 2),
  media_likert           numeric(4, 2),
  motivo_supressao       text,
  constraint uq_scoring_recorte unique (scoring_id, tipo_recorte, nome_recorte)
);

create index if not exists idx_scoring_recorte_scoring on campanha_scoring_recortes(scoring_id);
create index if not exists idx_scoring_recorte_tipo on campanha_scoring_recortes(tipo_recorte);
