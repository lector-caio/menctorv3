-- Esquema inicial do Menctor no PostgreSQL (substitui o Supabase).
-- Segue o que o app usa hoje: as 8 etapas do cliente, o pipeline comercial e o canal de denúncias.
--
-- Os ids são texto: os clientes de demonstração do frontend usam ids como 'loghaus', e os
-- registros novos recebem um UUID.

create function set_updated_at() returns trigger language plpgsql as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

-- ── Etapas do projeto do cliente ─────────────────────────────
create table steps (
  number      integer primary key,
  label       text not null,
  description text
);

insert into steps (number, label, description) values
  (1, 'Cadastro',       'Dados completos da empresa e contexto de riscos psicossociais'),
  (2, 'Proposta',       'Visualizar proposta e enviar link para aceite do cliente'),
  (3, 'Contrato',       'Contrato com aceite digital do cliente'),
  (4, 'Sensibilização', 'Palestra, treinamento e trilha'),
  (5, 'Diagnóstico',    'Seleção de instrumentos: COPSOQ II, HSE, Entrevista, DRPS e Clima'),
  (6, 'Entrevistas',    'Avaliação qualitativa dos 12 fatores psicossociais e maturidade NR-1'),
  (7, 'Relatórios',     'Disponível após diagnóstico e entrevistas'),
  (8, 'Apresentação',   'Reunião de discussão do plano de ação');

-- ── Pipeline comercial ───────────────────────────────────────
-- stage sem restrição de valores: as colunas do pipeline ainda estão mudando no frontend.
create table pipeline_cards (
  id            text primary key,
  stage         text not null default 'lead',
  empresa       text not null,
  contato       text,
  email         text,
  funcionarios  integer not null default 0,
  valor         integer not null default 0,
  dias          integer not null default 0,
  decisor       text,
  proximo_passo text,
  probabilidade integer not null default 35,
  origem        text,
  extra         jsonb not null default '{}'::jsonb,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now()
);

create trigger pipeline_cards_updated_at before update on pipeline_cards
  for each row execute function set_updated_at();

-- ── Clientes ─────────────────────────────────────────────────
create table clients (
  id               text primary key default gen_random_uuid()::text,
  name             text not null,
  cnpj             text,
  contact          text,
  email            text,
  phone            text,
  sector           text,
  employees        integer,
  mrr              numeric(12, 2),
  color            text not null default '#2F7D6F',
  status           text not null default 'ativo'
                     constraint ck_clients_status check (status in ('ativo', 'negociacao', 'pausado', 'concluido')),
  current_step     integer not null default 1
                     constraint fk_clients_step references steps (number),
  next_action      text,
  -- negócio do pipeline que originou o cliente (preenchido quando o contrato é assinado)
  pipeline_card_id text unique
                     constraint fk_clients_pipeline_card references pipeline_cards (id) on delete set null,
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now()
);

create index idx_clients_status on clients (status);

create trigger clients_updated_at before update on clients
  for each row execute function set_updated_at();

-- ── Progresso de cada etapa por cliente ──────────────────────
create table client_step_progress (
  client_id    text not null
                 constraint fk_progress_client references clients (id) on delete cascade,
  step_number  integer not null
                 constraint fk_progress_step references steps (number),
  status       text not null default 'pendente'
                 constraint ck_progress_status check (status in ('pendente', 'em_andamento', 'concluida', 'bloqueada')),
  -- o que foi preenchido na etapa (aceite da proposta, instrumentos escolhidos, ...)
  data         jsonb not null default '{}'::jsonb,
  started_at   timestamptz,
  completed_at timestamptz,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now(),
  primary key (client_id, step_number)
);

create trigger client_step_progress_updated_at before update on client_step_progress
  for each row execute function set_updated_at();

-- ── Formulário de cadastro (etapa 1, 18 perguntas) ───────────
create table cadastro_responses (
  client_id               text primary key
                            constraint fk_cadastro_client references clients (id) on delete cascade,
  razao_social            text,
  responsavel             text,
  email                   text,
  telefone                text,
  cnpj                    text,
  qtd_por_area            jsonb,
  qtd_cargos              text,
  segmento                text,
  unidades                text,
  cidades                 text,
  terceirizados           text,
  possui                  jsonb,
  indicadores             jsonb,
  mapeamento_formal       text,
  pesquisa_clima          text,
  canais_escuta           text,
  fiscalizacao_evidencia  text,
  gestao_riscos_outra     text,
  pressao_metas           text,
  ritmo_intenso           text,
  capacitacao_lideranca   text,
  conflitos_recorrentes   text,
  assedio_moral           text,
  lideranca_outra         text,
  juridico_acompanha      text,
  acao_trabalhista_mental text,
  sente_protegida         text,
  juridica_outra          text,
  excesso_trabalho        boolean,
  prazos_inalcancaveis    boolean,
  falta_controle          boolean,
  estrutura_nao_aplica    boolean,
  estrutura_outra         text,
  trabalha_com            jsonb,
  submitted_at            text,
  form_token              text,
  created_at              timestamptz not null default now(),
  updated_at              timestamptz not null default now()
);

create trigger cadastro_responses_updated_at before update on cadastro_responses
  for each row execute function set_updated_at();

-- ── Canal de denúncias ───────────────────────────────────────
-- data e prazo_final ficam como texto ISO, exatamente como o frontend envia (sem fuso),
-- para os horários não mudarem na exibição.
create table denuncias (
  id              text primary key,
  protocolo       text not null
                    constraint uq_denuncias_protocolo unique,
  cliente_id      text,
  data            text,
  status          text not null default 'triagem',
  gravidade       text,
  tipo_id         text,
  natureza        text,
  anonimo         boolean not null default true,
  denunciante     text,
  area            text,
  relato          text not null default '',
  evidencias      jsonb not null default '[]'::jsonb,
  admissibilidade text,
  prazo_final     text,
  parecer         text,
  resultado       text,
  recomendacoes   text,
  andamentos      jsonb not null default '[]'::jsonb,
  mensagens       jsonb not null default '[]'::jsonb,
  audit_log       jsonb not null default '[]'::jsonb,
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now()
);

create index idx_denuncias_data on denuncias (data desc);
create index idx_denuncias_cliente on denuncias (cliente_id);

create trigger denuncias_updated_at before update on denuncias
  for each row execute function set_updated_at();
