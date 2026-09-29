-- Criação da tabela de campanhas de avaliação
create table campanhas (
  id                      text primary key default gen_random_uuid()::text,
  cliente_id              text,
  titulo                  text not null,
  descricao               text,
  diagnostico_id          text,
  instrumento             text,
  ciclo                   text,
  reavaliacao             text,
  data_inicial            text,
  data_final              text,
  quantidade_funcionarios integer not null default 0,
  status                  text not null default 'ativa',
  link_token              text,
  extra                   jsonb not null default '{}'::jsonb,
  created_at              timestamptz not null default now(),
  updated_at              timestamptz not null default now()
);

create index idx_campanhas_cliente on campanhas (cliente_id);
create index idx_campanhas_status on campanhas (status);
create index idx_campanhas_data on campanhas (data_final);

create trigger campanhas_updated_at before update on campanhas
  for each row execute function set_updated_at();

-- Seeds iniciais para preservar demonstrações existentes
insert into campanhas (id, titulo, cliente_id, diagnostico_id, instrumento, ciclo, reavaliacao, descricao, data_inicial, data_final, quantidade_funcionarios, status) values
  ('camp-1', 'Avalia COPSOQ II — Julho 2026', 'loghaus', 'copsoq', 'COPSOQ', '2026-Q2', '90 dias', 'Campanha periódica de riscos psicossociais para atendimento à NR-1 e COPSOQ II.', '2026-06-15', '2026-07-15', 340, 'ativa'),
  ('camp-2', 'Pulso Bem-Estar Julho', 'vitamed', 'clima', 'CLIMA', '2026-Q3', '30 dias', 'Pesquisa pulso rápida sobre bem-estar e suporte da liderança.', '2026-06-20', '2026-07-20', 612, 'ativa'),
  ('camp-3', 'NR-1 Operações Trimestral', 'agrocorp', 'copsoq', 'COPSOQ', '2026-Q1', '180 dias', 'Mapeamento psicossocial das operações agrícolas do 1º trimestre.', '2026-04-01', '2026-06-30', 92, 'pausada'),
  ('camp-4', 'HSE-IT — Bem-estar Operacional', 'loghaus', 'hse', 'HSE', '2026-Q3', '60 dias', 'Avaliação baseada no padrão de gestão de estresse HSE Management Standards.', '2026-07-01', '2026-08-01', 50, 'ativa'),
  ('camp-5', 'EDRPS — Diagnóstico Trimestral', 'agrocorp', 'drps', 'COPSOQ', '2026-Q3', '90 dias', 'Escala diagnóstica de riscos psicossociais para unidades de produção.', '2026-07-01', '2026-08-15', 40, 'ativa')
on conflict (id) do nothing;
