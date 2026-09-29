-- Armazenamento das respostas de campanhas com anonimização de CPF
create table campanha_respostas (
  id              text primary key default gen_random_uuid()::text,
  campanha_id     text not null constraint fk_respostas_campanha references campanhas (id) on delete cascade,
  cpf_hash        text not null,
  setor           text,
  cargo           text,
  media_risco     numeric(4, 2),
  por_dimensao    jsonb not null default '{}'::jsonb,
  respostas_itens jsonb not null default '[]'::jsonb,
  created_at      timestamptz not null default now(),
  constraint uq_campanha_cpf unique (campanha_id, cpf_hash)
);

create index idx_respostas_campanha on campanha_respostas (campanha_id);
create index idx_respostas_created on campanha_respostas (created_at desc);
