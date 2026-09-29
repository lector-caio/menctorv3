-- V5: Estrutura Organizacional do Cliente (Etapa 2)
-- Contempla: Unidades, Diretorias, Setores, Níveis de Cargo, Cargos, Ambientes, GHE e GES.

-- 1. Unidades
create table if not exists organizacao_unidades (
  id            text primary key default gen_random_uuid()::text,
  cliente_id    text not null references clients(id) on delete cascade,
  nome          text not null,
  codigo        text,
  cidade        text,
  estado        text,
  ativo         boolean not null default true,
  ordem         integer not null default 0,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now(),
  constraint uq_unidade_cliente_nome unique (cliente_id, nome)
);
create index if not exists idx_org_unidades_cliente on organizacao_unidades(cliente_id);
create trigger org_unidades_updated_at before update on organizacao_unidades
  for each row execute function set_updated_at();

-- 2. Diretorias
create table if not exists organizacao_diretorias (
  id            text primary key default gen_random_uuid()::text,
  cliente_id    text not null references clients(id) on delete cascade,
  nome          text not null,
  responsavel   text,
  ativo         boolean not null default true,
  ordem         integer not null default 0,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now(),
  constraint uq_diretoria_cliente_nome unique (cliente_id, nome)
);
create index if not exists idx_org_diretorias_cliente on organizacao_diretorias(cliente_id);
create trigger org_diretorias_updated_at before update on organizacao_diretorias
  for each row execute function set_updated_at();

-- 3. Setores
create table if not exists organizacao_setores (
  id            text primary key default gen_random_uuid()::text,
  cliente_id    text not null references clients(id) on delete cascade,
  unidade_id    text references organizacao_unidades(id) on delete set null,
  diretoria_id  text references organizacao_diretorias(id) on delete set null,
  nome          text not null,
  responsavel   text,
  colaboradores integer,
  turno         text,
  ativo         boolean not null default true,
  ordem         integer not null default 0,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now(),
  constraint uq_setor_cliente_nome unique (cliente_id, nome)
);
create index if not exists idx_org_setores_cliente on organizacao_setores(cliente_id);
create trigger org_setores_updated_at before update on organizacao_setores
  for each row execute function set_updated_at();

-- 4. Níveis de Cargo
create table if not exists organizacao_niveis_cargo (
  id            text primary key default gen_random_uuid()::text,
  cliente_id    text not null references clients(id) on delete cascade,
  nome          text not null,
  ordem         integer not null default 0,
  ativo         boolean not null default true,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now(),
  constraint uq_nivel_cargo_cliente_nome unique (cliente_id, nome)
);
create index if not exists idx_org_niveis_cargo_cliente on organizacao_niveis_cargo(cliente_id);
create trigger org_niveis_cargo_updated_at before update on organizacao_niveis_cargo
  for each row execute function set_updated_at();

-- 5. Cargos
create table if not exists organizacao_cargos (
  id             text primary key default gen_random_uuid()::text,
  cliente_id     text not null references clients(id) on delete cascade,
  nivel_cargo_id text references organizacao_niveis_cargo(id) on delete set null,
  nome           text not null,
  cbo            text,
  descricao      text,
  ativo          boolean not null default true,
  ordem          integer not null default 0,
  created_at     timestamptz not null default now(),
  updated_at     timestamptz not null default now(),
  constraint uq_cargo_cliente_nome unique (cliente_id, nome)
);
create index if not exists idx_org_cargos_cliente on organizacao_cargos(cliente_id);
create trigger org_cargos_updated_at before update on organizacao_cargos
  for each row execute function set_updated_at();

-- 6. Ambientes
create table if not exists organizacao_ambientes (
  id            text primary key default gen_random_uuid()::text,
  cliente_id    text not null references clients(id) on delete cascade,
  unidade_id    text references organizacao_unidades(id) on delete set null,
  nome          text not null,
  descricao     text,
  ativo         boolean not null default true,
  ordem         integer not null default 0,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now(),
  constraint uq_ambiente_cliente_nome unique (cliente_id, nome)
);
create index if not exists idx_org_ambientes_cliente on organizacao_ambientes(cliente_id);
create trigger org_ambientes_updated_at before update on organizacao_ambientes
  for each row execute function set_updated_at();

-- 7. GHE — Grupos Homogêneos de Exposição
create table if not exists organizacao_ghes (
  id            text primary key default gen_random_uuid()::text,
  cliente_id    text not null references clients(id) on delete cascade,
  nome          text not null,
  codigo        text,
  descricao     text,
  ativo         boolean not null default true,
  ordem         integer not null default 0,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now(),
  constraint uq_ghe_cliente_nome unique (cliente_id, nome)
);
create index if not exists idx_org_ghes_cliente on organizacao_ghes(cliente_id);
create trigger org_ghes_updated_at before update on organizacao_ghes
  for each row execute function set_updated_at();

-- 8. GES — Grupos de Exposição Similar
create table if not exists organizacao_gess (
  id            text primary key default gen_random_uuid()::text,
  cliente_id    text not null references clients(id) on delete cascade,
  nome          text not null,
  codigo        text,
  descricao     text,
  ativo         boolean not null default true,
  ordem         integer not null default 0,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now(),
  constraint uq_ges_cliente_nome unique (cliente_id, nome)
);
create index if not exists idx_org_gess_cliente on organizacao_gess(cliente_id);
create trigger org_gess_updated_at before update on organizacao_gess
  for each row execute function set_updated_at();

-- Seeds demonstrativos para o cliente padrão 'loghaus' (se existir)
insert into organizacao_unidades (cliente_id, nome, codigo, cidade, estado)
select 'loghaus', 'Matriz Blumenau', 'UN-01', 'Blumenau', 'SC'
where exists (select 1 from clients where id = 'loghaus')
on conflict (cliente_id, nome) do nothing;

insert into organizacao_diretorias (cliente_id, nome, responsavel)
select 'loghaus', 'Diretoria de Operações', 'Carlos Silva'
where exists (select 1 from clients where id = 'loghaus')
on conflict (cliente_id, nome) do nothing;

insert into organizacao_setores (cliente_id, nome, turno, colaboradores)
select 'loghaus', 'Operações', 'Comercial', 85
where exists (select 1 from clients where id = 'loghaus')
on conflict (cliente_id, nome) do nothing;

insert into organizacao_setores (cliente_id, nome, turno, colaboradores)
select 'loghaus', 'Administrativo', 'Comercial', 30
where exists (select 1 from clients where id = 'loghaus')
on conflict (cliente_id, nome) do nothing;

insert into organizacao_niveis_cargo (cliente_id, nome, ordem)
select 'loghaus', 'Direção', 1
where exists (select 1 from clients where id = 'loghaus')
on conflict (cliente_id, nome) do nothing;

insert into organizacao_niveis_cargo (cliente_id, nome, ordem)
select 'loghaus', 'Operacional', 2
where exists (select 1 from clients where id = 'loghaus')
on conflict (cliente_id, nome) do nothing;

insert into organizacao_cargos (cliente_id, nome)
select 'loghaus', 'Analista de Operações'
where exists (select 1 from clients where id = 'loghaus')
on conflict (cliente_id, nome) do nothing;

insert into organizacao_ambientes (cliente_id, nome, descricao)
select 'loghaus', 'Centro de Distribuição', 'Galpão logístico climatizado'
where exists (select 1 from clients where id = 'loghaus')
on conflict (cliente_id, nome) do nothing;

insert into organizacao_ghes (cliente_id, nome, codigo, descricao)
select 'loghaus', 'GHE Logística Pesada', 'GHE-01', 'Operadores de empilhadeira e separadores'
where exists (select 1 from clients where id = 'loghaus')
on conflict (cliente_id, nome) do nothing;

insert into organizacao_gess (cliente_id, nome, codigo, descricao)
select 'loghaus', 'GES Administrativo Geral', 'GES-01', 'Equipes de suporte e atendimento'
where exists (select 1 from clients where id = 'loghaus')
on conflict (cliente_id, nome) do nothing;
