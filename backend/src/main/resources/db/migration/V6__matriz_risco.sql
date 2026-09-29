-- V6: Matriz de Risco PGR Persistente (NR-01)
-- Modelagem: Matriz -> Versões -> Critérios P1-P5, S1-S5, Grade 5x5 PxS e Fatores de Severidade.

-- 1. Matrizes de Risco (1 matriz por cliente)
create table if not exists matrizes_risco (
  id          text primary key default gen_random_uuid()::text,
  cliente_id  text not null references clients(id) on delete cascade,
  nome        text not null default 'Matriz de Risco (PGR)',
  descricao   text,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now(),
  constraint uq_matriz_cliente unique (cliente_id)
);
create index if not exists idx_matrizes_cliente on matrizes_risco(cliente_id);
create trigger matrizes_risco_updated_at before update on matrizes_risco
  for each row execute function set_updated_at();

-- 2. Versões da Matriz por Cliente
create table if not exists matriz_versoes (
  id           text primary key default gen_random_uuid()::text,
  matriz_id    text not null references matrizes_risco(id) on delete cascade,
  cliente_id   text not null references clients(id) on delete cascade,
  versao       text not null,
  status       text not null default 'rascunho' check (status in ('rascunho', 'publicada', 'arquivada')),
  framework    text not null default 'copsoq' check (framework in ('copsoq', 'hse', 'mte')),
  criterios_pgr text,
  publicada_em timestamptz,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now(),
  constraint uq_matriz_versao unique (matriz_id, versao)
);
create index if not exists idx_matriz_versoes_cliente on matriz_versoes(cliente_id);
create trigger matriz_versoes_updated_at before update on matriz_versoes
  for each row execute function set_updated_at();

-- 3. Critérios de Probabilidade (P1 a P5)
create table if not exists matriz_criterios_probabilidade (
  id          text primary key default gen_random_uuid()::text,
  versao_id   text not null references matriz_versoes(id) on delete cascade,
  nivel       integer not null check (nivel between 1 and 5),
  codigo      text not null,
  nome        text not null,
  descricao   text not null,
  faixa_min   numeric(5,2),
  faixa_max   numeric(5,2),
  constraint uq_versao_prob_nivel unique (versao_id, nivel)
);
create index if not exists idx_criterios_prob_versao on matriz_criterios_probabilidade(versao_id);

-- 4. Critérios de Severidade (S1 a S5)
create table if not exists matriz_criterios_severidade (
  id          text primary key default gen_random_uuid()::text,
  versao_id   text not null references matriz_versoes(id) on delete cascade,
  nivel       integer not null check (nivel between 1 and 5),
  codigo      text not null,
  nome        text not null,
  descricao   text not null,
  cor         text not null,
  constraint uq_versao_sev_nivel unique (versao_id, nivel)
);
create index if not exists idx_criterios_sev_versao on matriz_criterios_severidade(versao_id);

-- 5. Classificações da Matriz 5x5 (PxS)
create table if not exists matriz_classificacoes_ps (
  id            text primary key default gen_random_uuid()::text,
  versao_id     text not null references matriz_versoes(id) on delete cascade,
  probabilidade integer not null check (probabilidade between 1 and 5),
  severidade    integer not null check (severidade between 1 and 5),
  score         integer not null,
  nivel_risco   text not null check (nivel_risco in ('insignificante', 'baixo', 'moderado', 'alto', 'critico')),
  prioridade    text not null,
  cor           text not null,
  constraint uq_versao_ps unique (versao_id, probabilidade, severidade)
);
create index if not exists idx_classificacoes_ps_versao on matriz_classificacoes_ps(versao_id);

-- 6. Calibração de Severidade por Fator / Dimensão
create table if not exists matriz_fatores_severidade (
  id            text primary key default gen_random_uuid()::text,
  versao_id     text not null references matriz_versoes(id) on delete cascade,
  framework     text not null,
  codigo        text not null,
  nome          text not null,
  severidade    integer not null default 3 check (severidade between 1 and 5),
  justificativa text,
  sugestao      text,
  constraint uq_versao_framework_fator unique (versao_id, framework, codigo)
);
create index if not exists idx_fatores_sev_versao on matriz_fatores_severidade(versao_id);

-- Helper function para popular configuração padrão de uma nova versão
create or replace function popular_configuracao_padrao_matriz(p_versao_id text) returns void language plpgsql as $$
begin
  -- 1. Inserir critérios P1-P5 padrão
  insert into matriz_criterios_probabilidade (versao_id, nivel, codigo, nome, descricao, faixa_min, faixa_max) values
    (p_versao_id, 1, 'P1', 'Raro / Insignificante', 'Frequência quase nula; evento excepcional ou não registrado historicamente.', 0.00, 10.00),
    (p_versao_id, 2, 'P2', 'Improvável / Baixo', 'Pouca probabilidade de ocorrência; condições controladas; baixa reincidência.', 10.01, 25.00),
    (p_versao_id, 3, 'P3', 'Possível / Moderado', 'Pode ocorrer em situações normais de trabalho; relatos ocasionais.', 25.01, 50.00),
    (p_versao_id, 4, 'P4', 'Provável / Alto', 'Ocorrência frequente; fatores de pressão evidentes no cotidiano laboral.', 50.01, 75.00),
    (p_versao_id, 5, 'P5', 'Quase Certo / Crítico', 'Ocorrência contínua ou reiterada na ausência de controles imediatos.', 75.01, 100.00)
  on conflict do nothing;

  -- 2. Inserir critérios S1-S5 padrão
  insert into matriz_criterios_severidade (versao_id, nivel, codigo, nome, descricao, cor) values
    (p_versao_id, 1, 'S1', 'Insignificante', 'Impacto residual ou nulo na saúde psíquica.', '#38bdf8'),
    (p_versao_id, 2, 'S2', 'Leve', 'Insatisfação pontual, desconforto transitório, turnover pontual.', '#4ade80'),
    (p_versao_id, 3, 'S3', 'Moderado', 'Estresse crônico, queda de engajamento, conflito trabalho-família.', '#facc15'),
    (p_versao_id, 4, 'S4', 'Grave', 'Burnout, adoecimento, afastamentos prolongados (B91/B31).', '#fb923c'),
    (p_versao_id, 5, 'S5', 'Catastrófico', 'Risco jurídico grave, assédio, ideação, trauma severo.', '#f87171')
  on conflict do nothing;

  -- 3. Inserir as 25 células da grade 5x5 PxS
  insert into matriz_classificacoes_ps (versao_id, probabilidade, severidade, score, nivel_risco, prioridade, cor) values
    (p_versao_id, 1, 1, 1, 'insignificante', 'Monitoramento', '#38bdf8'),
    (p_versao_id, 1, 2, 2, 'baixo', 'Longo prazo', '#4ade80'),
    (p_versao_id, 1, 3, 3, 'baixo', 'Longo prazo', '#4ade80'),
    (p_versao_id, 1, 4, 4, 'moderado', 'Médio prazo', '#facc15'),
    (p_versao_id, 1, 5, 5, 'moderado', 'Médio prazo', '#facc15'),

    (p_versao_id, 2, 1, 2, 'baixo', 'Longo prazo', '#4ade80'),
    (p_versao_id, 2, 2, 4, 'baixo', 'Longo prazo', '#4ade80'),
    (p_versao_id, 2, 3, 6, 'moderado', 'Médio prazo', '#facc15'),
    (p_versao_id, 2, 4, 8, 'moderado', 'Médio prazo', '#facc15'),
    (p_versao_id, 2, 5, 10, 'alto', 'Curto prazo', '#fb923c'),

    (p_versao_id, 3, 1, 3, 'baixo', 'Longo prazo', '#4ade80'),
    (p_versao_id, 3, 2, 6, 'moderado', 'Médio prazo', '#facc15'),
    (p_versao_id, 3, 3, 9, 'moderado', 'Médio prazo', '#facc15'),
    (p_versao_id, 3, 4, 12, 'alto', 'Curto prazo', '#fb923c'),
    (p_versao_id, 3, 5, 15, 'alto', 'Curto prazo', '#fb923c'),

    (p_versao_id, 4, 1, 4, 'moderado', 'Médio prazo', '#facc15'),
    (p_versao_id, 4, 2, 8, 'moderado', 'Médio prazo', '#facc15'),
    (p_versao_id, 4, 3, 12, 'alto', 'Curto prazo', '#fb923c'),
    (p_versao_id, 4, 4, 16, 'alto', 'Curto prazo', '#fb923c'),
    (p_versao_id, 4, 5, 20, 'critico', 'Imediata', '#f87171'),

    (p_versao_id, 5, 1, 5, 'moderado', 'Médio prazo', '#facc15'),
    (p_versao_id, 5, 2, 10, 'alto', 'Curto prazo', '#fb923c'),
    (p_versao_id, 5, 3, 15, 'alto', 'Curto prazo', '#fb923c'),
    (p_versao_id, 5, 4, 20, 'critico', 'Imediata', '#f87171'),
    (p_versao_id, 5, 5, 25, 'critico', 'Imediata', '#f87171')
  on conflict do nothing;

  -- 4. Inserir fatores padrão para os 3 frameworks (copsoq, hse, mte)
  -- COPSOQ
  insert into matriz_fatores_severidade (versao_id, framework, codigo, nome, severidade, justificativa, sugestao) values
    (p_versao_id, 'copsoq', 'SOBRECARGA', 'Excesso de demandas no trabalho (sobrecarga)', 4, 'Burnout, estresse crônico, afastamento prolongado', 'Burnout, estresse crônico, afastamento prolongado'),
    (p_versao_id, 'copsoq', 'CONTROLE', 'Baixo controle no trabalho / Falta de autonomia', 3, 'Estresse crônico, desmotivação, queda de engajamento', 'Estresse crônico, desmotivação, queda de engajamento'),
    (p_versao_id, 'copsoq', 'SUPORTE', 'Falta de suporte/apoio no trabalho', 4, 'Desamparo da liderança e colegas diante de demandas críticas', 'Desamparo da liderança e colegas diante de demandas críticas'),
    (p_versao_id, 'copsoq', 'RELACIONAMENTOS', 'Maus relacionamentos no local de trabalho', 4, 'Clima tóxico, atritos frequentes entre pares e lideranças', 'Clima tóxico, atritos frequentes entre pares e lideranças'),
    (p_versao_id, 'copsoq', 'RECOMPENSAS', 'Baixas recompensas e reconhecimento', 2, 'Insatisfação, turnover, baixa retenção de talentos', 'Insatisfação, turnover, baixa retenção de talentos'),
    (p_versao_id, 'copsoq', 'CLAREZA_PAPEL', 'Baixa clareza de papel/função', 3, 'Ambiguidade de papéis, retrabalho, conflitos interpessoais', 'Ambiguidade de papéis, retrabalho, conflitos interpessoais'),
    (p_versao_id, 'copsoq', 'JUSTICA', 'Baixa justiça organizacional', 4, 'Percepção de injustiça procedimental, desengajamento e litígios', 'Percepção de injustiça procedimental, desengajamento e litígios'),
    (p_versao_id, 'copsoq', 'MUDANCA_ORG', 'Má gestão de mudanças organizacionais', 4, 'Insegurança quanto ao futuro, ansiedade coletiva', 'Insegurança quanto ao futuro, ansiedade coletiva'),
    (p_versao_id, 'copsoq', 'WORK_CONTENT', 'Conteúdo do Trabalho e Exigências Emocionais', 3, 'Exaustão emocional decorrente do contato com público/tarefas críticas', 'Exaustão emocional decorrente do contato com público/tarefas críticas'),
    (p_versao_id, 'copsoq', 'WORK_LIFE', 'Interface Trabalho-Vida', 3, 'Conflito família-trabalho, fadiga acumulada e perda de recuperação', 'Conflito família-trabalho, fadiga acumulada e perda de recuperação')
  on conflict do nothing;

  -- HSE
  insert into matriz_fatores_severidade (versao_id, framework, codigo, nome, severidade, justificativa, sugestao) values
    (p_versao_id, 'hse', 'SOBRECARGA', 'Demandas (Excesso de carga e ritmo)', 4, 'Burnout, estresse crônico, risco cardiovascular', 'Burnout, estresse crônico, risco cardiovascular'),
    (p_versao_id, 'hse', 'CONTROLE', 'Controle (Autonomia e participação)', 3, 'Sensação de impotência e desmotivação funcional', 'Sensação de impotência e desmotivação funcional'),
    (p_versao_id, 'hse', 'SUPORTE', 'Apoio (Suporte de gestores e colegas)', 4, 'Isolamento profissional diante de sobrecargas operacionais', 'Isolamento profissional diante de sobrecargas operacionais'),
    (p_versao_id, 'hse', 'RELACIONAMENTOS', 'Relacionamentos (Conflitos e condutas inaceitáveis)', 4, 'Desgaste interpessoal e deterioração do clima de equipe', 'Desgaste interpessoal e deterioração do clima de equipe'),
    (p_versao_id, 'hse', 'CLAREZA_PAPEL', 'Papel (Compreensão da função e responsabilidades)', 3, 'Conflito de atribuições e insegurança operacional', 'Conflito de atribuições e insegurança operacional'),
    (p_versao_id, 'hse', 'MUDANCA_ORG', 'Mudança (Gestão e comunicação de transições)', 4, 'Incerteza, resistência e estresse adaptativo', 'Incerteza, resistência e estresse adaptativo')
  on conflict do nothing;

  -- MTE
  insert into matriz_fatores_severidade (versao_id, framework, codigo, nome, severidade, justificativa, sugestao) values
    (p_versao_id, 'mte', 'ASSEDIO', 'Assédio moral / sexual de qualquer natureza', 5, 'Risco jurídico, dano psíquico grave, passivo trabalhista', 'Risco jurídico, dano psíquico grave, passivo trabalhista'),
    (p_versao_id, 'mte', 'SOBRECARGA', 'Excesso de demandas no trabalho (sobrecarga)', 4, 'Burnout, afastamento prolongado, estresse crônico', 'Burnout, afastamento prolongado, estresse crônico'),
    (p_versao_id, 'mte', 'CONTROLE', 'Baixo controle no trabalho / Falta de autonomia', 3, 'Estresse crônico, queda de engajamento, desmotivação', 'Estresse crônico, queda de engajamento, desmotivação'),
    (p_versao_id, 'mte', 'RECOMPENSAS', 'Baixas recompensas e reconhecimento', 2, 'Insatisfação, turnover, baixa retenção de talentos', 'Insatisfação, turnover, baixa retenção de talentos'),
    (p_versao_id, 'mte', 'WORK_LIFE', 'Interface trabalho-vida', 3, 'Conflito família-trabalho, fadiga e desequilíbrio', 'Conflito família-trabalho, fadiga e desequilíbrio'),
    (p_versao_id, 'mte', 'CLAREZA_PAPEL', 'Baixa clareza de papel/função', 3, 'Ambiguidade de papéis, retrabalho, conflitos interpessoais', 'Ambiguidade de papéis, retrabalho, conflitos interpessoais'),
    (p_versao_id, 'mte', 'COMUNICACAO', 'Trabalho em condições de difícil comunicação', 3, 'Isolamento de equipes, ruídos de comunicação interna', 'Isolamento de equipes, ruídos de comunicação interna'),
    (p_versao_id, 'mte', 'ISOLAMENTO', 'Trabalho remoto e isolado', 3, 'Sensação de isolamento social e perda de pertencimento', 'Sensação de isolamento social e perda de pertencimento'),
    (p_versao_id, 'mte', 'JUSTICA', 'Baixa justiça organizacional', 4, 'Percepção de injustiça procedimental, desengajamento e litígios', 'Percepção de injustiça procedimental, desengajamento e litígios'),
    (p_versao_id, 'mte', 'MUDANCA_ORG', 'Má gestão de mudanças organizacionais', 4, 'Insegurança quanto ao futuro, resistência e ansiedade coletiva', 'Insegurança quanto ao futuro, resistência e ansiedade coletiva'),
    (p_versao_id, 'mte', 'RELACIONAMENTOS', 'Maus relacionamentos no local de trabalho', 4, 'Clima tóxico, atritos frequentes entre pares e lideranças', 'Clima tóxico, atritos frequentes entre pares e lideranças'),
    (p_versao_id, 'mte', 'SUBCARGA', 'Baixa demanda no trabalho (subcarga)', 2, 'Tédio laboral, subutilização de capacidades (boreout)', 'Tédio laboral, subutilização de capacidades (boreout)'),
    (p_versao_id, 'mte', 'SUPORTE', 'Falta de suporte/apoio no trabalho', 4, 'Desamparo da liderança e colegas diante de demandas críticas', 'Desamparo da liderança e colegas diante de demandas críticas'),
    (p_versao_id, 'mte', 'VIOLENCIA_TRAUMA', 'Eventos violentos ou traumáticos', 5, 'Estresse pós-traumático (TEPT), incidentes graves de segurança', 'Estresse pós-traumático (TEPT), incidentes graves de segurança'),
    (p_versao_id, 'mte', 'INSEGURANCA', 'Insegurança no emprego e instabilidade', 3, 'Insegurança financeira percebida, ansiedade crônica', 'Insegurança financeira percebida, ansiedade crônica')
  on conflict do nothing;
end;
$$;

-- Seeds demonstrativos para o cliente padrão 'loghaus' se existir
do $$
declare
  v_matriz_id text := 'matriz-loghaus';
  v_v1_id text := 'ver-loghaus-v1';
  v_v2_id text := 'ver-loghaus-v2';
begin
  if exists (select 1 from clients where id = 'loghaus') then
    insert into matrizes_risco (id, cliente_id, nome, descricao)
    values (v_matriz_id, 'loghaus', 'Matriz de Risco (PGR) — Loghaus Logística', 'Matriz 5x5 calibrada para operações logísticas e NR-01.')
    on conflict (cliente_id) do nothing;

    -- Versão 1.0 Publicada
    insert into matriz_versoes (id, matriz_id, cliente_id, versao, status, framework, criterios_pgr, publicada_em)
    values (v_v1_id, v_matriz_id, 'loghaus', 'v1.0', 'publicada', 'copsoq',
            'Matriz 5×5 padrão NR-01 / GRO. Cruzamento da Probabilidade de ocorrência (P1 a P5) apurada no diagnóstico COPSOQ com a Severidade do dano potencial (S1 a S5). Classificação em 5 faixas com priorização de medidas preventivas e corretivas.',
            now())
    on conflict (matriz_id, versao) do nothing;
    perform popular_configuracao_padrao_matriz(v_v1_id);

    -- Versão 2.0 Rascunho
    insert into matriz_versoes (id, matriz_id, cliente_id, versao, status, framework, criterios_pgr, publicada_em)
    values (v_v2_id, v_matriz_id, 'loghaus', 'v2.0', 'rascunho', 'copsoq',
            'Critérios calibrados para o Grau de Risco CNAE 3 considerando histórico de afastamentos por DORT e estresse ocupacional.',
            null)
    on conflict (matriz_id, versao) do nothing;
    perform popular_configuracao_padrao_matriz(v_v2_id);
  end if;
end;
$$;
