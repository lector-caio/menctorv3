-- Estrutura persistente de Instrumentos, Dimensões e Questões de Avaliação
create table instrumentos (
  id           text primary key,
  nome         text not null,
  tipo         text not null,
  descricao    text,
  versao       text not null default '1.0.0',
  escala_tipo  text not null default 'frequencia',
  ativo        boolean not null default true,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now()
);

create table instrumento_dimensoes (
  id                 text primary key default gen_random_uuid()::text,
  instrumento_id     text not null constraint fk_dimensoes_instrumento references instrumentos (id) on delete cascade,
  codigo             text not null,
  nome               text not null,
  categoria          text,
  fator_mte_mapeado  text,
  severidade_padrao  integer not null default 3,
  ordem              integer not null default 0,
  ativo              boolean not null default true,
  created_at         timestamptz not null default now()
);

create index idx_dimensoes_instrumento on instrumento_dimensoes (instrumento_id);

create table instrumento_questoes (
  id           text primary key default gen_random_uuid()::text,
  dimensao_id  text not null constraint fk_questoes_dimensao references instrumento_dimensoes (id) on delete cascade,
  texto        text not null,
  ordem        integer not null default 0,
  ativo        boolean not null default true,
  invertida    boolean not null default false,
  created_at   timestamptz not null default now()
);

create index idx_questoes_dimensao on instrumento_questoes (dimensao_id);

-- ── 1. Inserir Instrumentos ────────────────────────────────────
insert into instrumentos (id, nome, tipo, descricao, versao, escala_tipo, ativo) values
  ('copsoq', 'COPSOQ II — Riscos Psicossociais', 'COPSOQ II', 'Framework COPSOQ-inspired autoral para mapeamento de riscos psicossociais no trabalho.', '1.0.0', 'frequencia', true),
  ('hse',    'HSE Indicator Tool',               'HSE-IT',    'Ferramenta indicadora de estresse do Health and Safety Executive britânico.', '1.0.0', 'frequencia', true),
  ('mte',    'MTE Psicossociais — Padrão NR-01', 'MTE',       'Catálogo oficial de fatores de risco psicossocial (NR-01 / GRO) com 13 dimensões.', '1.0.0', 'frequencia', true),
  ('clima',  'Pesquisa de Clima Organizacional', 'Clima',     'Combina dimensões de clima, engajamento e bem-estar corporativo.', '1.0.0', 'concordancia', true),
  ('drps',   'Diagnóstico de Riscos Psicossociais (DRPS)', 'DRPS', 'Diagnóstico estruturado para mapeamento de fatores preventivos (aguardando cadastro de itens).', '1.0.0', 'concordancia', true)
on conflict (id) do nothing;

-- ── 2. Inserir Dimensões COPSOQ ──────────────────────────────
insert into instrumento_dimensoes (id, instrumento_id, codigo, nome, categoria, fator_mte_mapeado, severidade_padrao, ordem, ativo) values
  ('dim-copsoq-1', 'copsoq', 'SOBRECARGA',      'Carga de trabalho',             'conteudo',       'Excesso de demandas no trabalho (sobrecarga)', 4, 1, true),
  ('dim-copsoq-2', 'copsoq', 'BURNOUT',         'Burnout',                       'conteudo',       'Exaustão e esgotamento profissional',           4, 2, true),
  ('dim-copsoq-3', 'copsoq', 'ESTRESSE',        'Estresse',                      'conteudo',       'Tensão e sobrecarga psicológica',              4, 3, true),
  ('dim-copsoq-4', 'copsoq', 'WORK_LIFE',       'Conflito trabalho-família',     'organizacional', 'Interface trabalho-vida',                      3, 4, true),
  ('dim-copsoq-5', 'copsoq', 'RITMO',           'Ritmo de trabalho',             'conteudo',       'Excesso de demandas no trabalho (sobrecarga)', 3, 5, true),
  ('dim-copsoq-6', 'copsoq', 'RECOMPENSAS',     'Reconhecimento',                'organizacional', 'Baixas recompensas e reconhecimento',          2, 6, true),
  ('dim-copsoq-7', 'copsoq', 'SUPORTE',         'Suporte social',                'relacional',     'Falta de suporte/apoio no trabalho',           4, 7, true),
  ('dim-copsoq-8', 'copsoq', 'LIDERANCA',       'Qualidade da liderança',        'relacional',     'Falta de suporte/apoio no trabalho',           3, 8, true),
  ('dim-copsoq-9', 'copsoq', 'JUSTICA',         'Justiça e respeito',            'organizacional', 'Baixa justiça organizacional',                 4, 9, true),
  ('dim-copsoq-10','copsoq', 'CONTROLE',        'Influência no trabalho',        'organizacional', 'Baixo controle no trabalho / Falta de autonomia', 3, 10, true)
on conflict (id) do nothing;

-- Questões COPSOQ
insert into instrumento_questoes (dimensao_id, texto, ordem, ativo) values
  ('dim-copsoq-1', 'Você precisa trabalhar muito rapidamente?', 1, true),
  ('dim-copsoq-1', 'Sua carga de trabalho é distribuída de forma desigual, causando acúmulo de tarefas?', 2, true),
  ('dim-copsoq-1', 'Você tem tempo suficiente para realizar suas tarefas?', 3, true),
  ('dim-copsoq-1', 'Você leva trabalho para casa com frequência?', 4, true),

  ('dim-copsoq-2', 'Você se sente fisicamente esgotado(a) ao final do dia de trabalho?', 1, true),
  ('dim-copsoq-2', 'Você se sente emocionalmente exausto(a) pelo trabalho?', 2, true),
  ('dim-copsoq-2', 'Você sente que não tem mais energia para lidar com outra pessoa?', 3, true),
  ('dim-copsoq-2', 'Você acorda cansado(a) mesmo após uma noite de sono?', 4, true),

  ('dim-copsoq-3', 'Você se sente tenso(a) ou irritado(a) durante o trabalho?', 1, true),
  ('dim-copsoq-3', 'Você tem dificuldade para relaxar após o expediente?', 2, true),
  ('dim-copsoq-3', 'Você sente que está sob pressão constante no trabalho?', 3, true),
  ('dim-copsoq-3', 'Você tem sentido dores de cabeça, tensão muscular ou insônia relacionadas ao trabalho?', 4, true),

  ('dim-copsoq-4', 'As exigências do seu trabalho interferem na sua vida pessoal/familiar?', 1, true),
  ('dim-copsoq-4', 'Você sente falta de tempo para a família por causa do trabalho?', 2, true),
  ('dim-copsoq-4', 'Você leva as preocupações do trabalho para casa?', 3, true),

  ('dim-copsoq-5', 'O ritmo do seu trabalho é definido por prazos apertados?', 1, true),
  ('dim-copsoq-5', 'Você precisa acelerar o ritmo de trabalho para cumprir metas?', 2, true),
  ('dim-copsoq-5', 'Seu ritmo de trabalho é irregular ao longo do dia?', 3, true),

  ('dim-copsoq-6', 'Seu trabalho é reconhecido e valorizado pela gestão?', 1, true),
  ('dim-copsoq-6', 'Você recebe feedback adequado sobre seu desempenho?', 2, true),
  ('dim-copsoq-6', 'Você sente que seus esforços são recompensados de forma justa?', 3, true),

  ('dim-copsoq-7', 'Você pode contar com seus colegas quando precisa de ajuda no trabalho?', 1, true),
  ('dim-copsoq-7', 'Existe um bom espírito de equipe no seu setor?', 2, true),
  ('dim-copsoq-7', 'Você se sente à vontade para pedir ajuda a colegas quando necessário?', 3, true),

  ('dim-copsoq-8', 'Sua liderança direta garante boas condições para o desenvolvimento do seu trabalho?', 1, true),
  ('dim-copsoq-8', 'Sua liderança resolve conflitos de forma justa?', 2, true),
  ('dim-copsoq-8', 'Sua liderança dá prioridade à satisfação no trabalho?', 3, true),
  ('dim-copsoq-8', 'Você recebe orientações claras da sua liderança?', 4, true),

  ('dim-copsoq-9', 'Os conflitos são resolvidos de forma justa no seu ambiente de trabalho?', 1, true),
  ('dim-copsoq-9', 'Você é tratado(a) de forma justa no seu local de trabalho?', 2, true),
  ('dim-copsoq-9', 'As decisões que afetam você são tomadas de forma transparente?', 3, true),

  ('dim-copsoq-10', 'Você tem influência sobre a quantidade de trabalho atribuída a você?', 1, true),
  ('dim-copsoq-10', 'Você pode decidir como realizar suas tarefas?', 2, true),
  ('dim-copsoq-10', 'Você participa das decisões sobre o seu próprio trabalho?', 3, true),
  ('dim-copsoq-10', 'Você tem autonomia para organizar sua rotina de trabalho?', 4, true)
on conflict do nothing;

-- ── 3. Inserir Dimensões e Questões HSE ───────────────────────
insert into instrumento_dimensoes (id, instrumento_id, codigo, nome, categoria, fator_mte_mapeado, severidade_padrao, ordem, ativo) values
  ('dim-hse-1', 'hse', 'SOBRECARGA',     'Demandas',           'conteudo',       'Excesso de demandas no trabalho (sobrecarga)', 4, 1, true),
  ('dim-hse-2', 'hse', 'CONTROLE',       'Controle',           'organizacional', 'Baixo controle no trabalho / Falta de autonomia', 3, 2, true),
  ('dim-hse-3', 'hse', 'SUPORTE_CHEFIA', 'Apoio da chefia',    'relacional',     'Falta de suporte/apoio no trabalho',           4, 3, true),
  ('dim-hse-4', 'hse', 'SUPORTE_COLEGAS','Apoio dos colegas',  'relacional',     'Falta de suporte/apoio no trabalho',           3, 4, true),
  ('dim-hse-5', 'hse', 'RELACIONAMENTOS','Relacionamentos',    'relacional',     'Maus relacionamentos no local de trabalho',     4, 5, true),
  ('dim-hse-6', 'hse', 'CLAREZA_PAPEL',  'Cargo e função',     'organizacional', 'Baixa clareza de papel/função',                 3, 6, true)
on conflict (id) do nothing;

insert into instrumento_questoes (dimensao_id, texto, ordem, ativo) values
  ('dim-hse-1', 'Sou pressionado(a) a trabalhar horas extras.', 1, true),
  ('dim-hse-1', 'Diferentes grupos no trabalho exigem coisas difíceis de conciliar.', 2, true),
  ('dim-hse-1', 'Tenho prazos impossíveis de cumprir.', 3, true),
  ('dim-hse-1', 'Preciso negligenciar algumas tarefas porque tenho coisas demais para fazer.', 4, true),
  ('dim-hse-1', 'Sou obrigado(a) a trabalhar muito intensamente.', 5, true),

  ('dim-hse-2', 'Posso decidir quando fazer uma pausa.', 1, true),
  ('dim-hse-2', 'Tenho liberdade para decidir como fazer meu trabalho.', 2, true),
  ('dim-hse-2', 'Meu horário de trabalho pode ser flexível.', 3, true),
  ('dim-hse-2', 'Posso decidir a ordem em que realizo minhas tarefas.', 4, true),
  ('dim-hse-2', 'Tenho alguma escolha ao decidir o que fazer no trabalho.', 5, true),

  ('dim-hse-3', 'Recebo feedback útil sobre meu trabalho por parte da liderança.', 1, true),
  ('dim-hse-3', 'Posso contar com minha liderança para me ajudar diante de um problema no trabalho.', 2, true),
  ('dim-hse-3', 'Sou incentivado(a) pela minha liderança no trabalho.', 3, true),
  ('dim-hse-3', 'Minha liderança me apoia diante de um problema emocional no trabalho.', 4, true),
  ('dim-hse-3', 'Sinto que minha liderança respeita meu trabalho.', 5, true),

  ('dim-hse-4', 'Se o trabalho fica difícil, meus colegas me ajudam.', 1, true),
  ('dim-hse-4', 'Recebo a ajuda e o apoio que preciso dos colegas.', 2, true),
  ('dim-hse-4', 'Meus colegas estão dispostos a ouvir meus problemas relacionados ao trabalho.', 3, true),
  ('dim-hse-4', 'Existe um bom relacionamento entre eu e meus colegas de trabalho.', 4, true),
  ('dim-hse-4', 'Sinto-me apoiado(a) pelos meus pares diante de sobrecargas operacionais.', 5, true),

  ('dim-hse-5', 'Sou vítima de atitudes hostis ou de bullying no trabalho.', 1, true),
  ('dim-hse-5', 'Há atrito ou raiva entre colegas de trabalho.', 2, true),
  ('dim-hse-5', 'Os relacionamentos no trabalho são tensos.', 3, true),
  ('dim-hse-5', 'Sinto-me exposto(a) a conflitos interpessoais no trabalho.', 4, true),
  ('dim-hse-5', 'Existem comportamentos desrespeitosos recorrentes na minha equipe.', 5, true),

  ('dim-hse-6', 'Sei claramente o que se espera de mim no trabalho.', 1, true),
  ('dim-hse-6', 'Sei como realizar meu trabalho corretamente.', 2, true),
  ('dim-hse-6', 'Tenho clareza sobre meus objetivos e metas no trabalho.', 3, true),
  ('dim-hse-6', 'Entendo como meu trabalho se encaixa no propósito geral da organização.', 4, true),
  ('dim-hse-6', 'Sei quais são minhas responsabilidades no trabalho.', 5, true)
on conflict do nothing;

-- ── 4. Inserir Dimensões e Questões MTE Psicossociais (13 Fatores) ──
insert into instrumento_dimensoes (id, instrumento_id, codigo, nome, categoria, fator_mte_mapeado, severidade_padrao, ordem, ativo) values
  ('dim-mte-1',  'mte', 'VIOLENCIA_TRAUMA', 'Eventos violentos ou traumáticos',          'relacional',     'Eventos violentos ou traumáticos',          5, 1,  true),
  ('dim-mte-2',  'mte', 'ASSEDIO',          'Assédio de qualquer natureza no trabalho',   'relacional',     'Assédio de qualquer natureza no trabalho',   5, 2,  true),
  ('dim-mte-3',  'mte', 'SOBRECARGA',       'Excesso de demandas no trabalho (sobrecarga)','conteudo',       'Excesso de demandas no trabalho (sobrecarga)',4, 3,  true),
  ('dim-mte-4',  'mte', 'RELACIONAMENTOS',  'Maus relacionamentos no local de trabalho', 'relacional',     'Maus relacionamentos no local de trabalho', 4, 4,  true),
  ('dim-mte-5',  'mte', 'SUPORTE',          'Falta de suporte/apoio no trabalho',        'organizacional', 'Falta de suporte/apoio no trabalho',        4, 5,  true),
  ('dim-mte-6',  'mte', 'JUSTICA',          'Baixa justiça organizacional',              'organizacional', 'Baixa justiça organizacional',              4, 6,  true),
  ('dim-mte-7',  'mte', 'MUDANCA_ORG',      'Má gestão de mudanças organizacionais',     'organizacional', 'Má gestão de mudanças organizacionais',     4, 7,  true),
  ('dim-mte-8',  'mte', 'CONTROLE',         'Baixo controle / Falta de autonomia',       'organizacional', 'Baixo controle no trabalho / Falta de autonomia', 4, 8, true),
  ('dim-mte-9',  'mte', 'CLAREZA_PAPEL',    'Baixa clareza de papel/função',             'organizacional', 'Baixa clareza de papel/função',             4, 9,  true),
  ('dim-mte-10', 'mte', 'RECOMPENSAS',      'Baixas recompensas e reconhecimento',       'organizacional', 'Baixas recompensas e reconhecimento',       3, 10, true),
  ('dim-mte-11', 'mte', 'COMUNICACAO',      'Trabalho em condições de difícil comunicação','organizacional','Trabalho em condições de difícil comunicação',3, 11, true),
  ('dim-mte-12', 'mte', 'SUBCARGA',         'Baixa demanda no trabalho (subcarga)',      'conteudo',       'Baixa demanda no trabalho (subcarga)',      3, 12, true),
  ('dim-mte-13', 'mte', 'ISOLAMENTO',       'Trabalho remoto e isolado',                 'conteudo',       'Trabalho remoto e isolado',                 3, 13, true)
on conflict (id) do nothing;

insert into instrumento_questoes (dimensao_id, texto, ordem, ativo) values
  ('dim-mte-1', 'Já presenciei ou estive exposto(a) a situações de violência física ou verbal no trabalho.', 1, true),
  ('dim-mte-1', 'Sinto que minha segurança pessoal está sob risco frequente na realização das tarefas.', 2, true),
  ('dim-mte-1', 'Fui exposto(a) a incidentes críticos ou traumáticos no exercício da minha profissão.', 3, true),
  ('dim-mte-1', 'A organização possui suporte imediato para colaboradores expostos a eventos violentos.', 4, true),

  ('dim-mte-2', 'No meu ambiente de trabalho, sinto-me respeitado(a) por colegas e superiores.', 1, true),
  ('dim-mte-2', 'Já presenciei situações em que alguém foi tratado de forma humilhante ou intimidadora.', 2, true),
  ('dim-mte-2', 'Sinto que posso reportar comportamentos inadequados sem medo de retaliação.', 3, true),
  ('dim-mte-2', 'Existem práticas recorrentes de pressão excessiva ou constrangimento no meu setor.', 4, true),

  ('dim-mte-3', 'Frequentemente preciso trabalhar além do horário para cumprir minhas tarefas.', 1, true),
  ('dim-mte-3', 'O volume de trabalho que recebo é compatível com minha capacidade.', 2, true),
  ('dim-mte-3', 'Sinto-me pressionado(a) por prazos constantemente apertados.', 3, true),
  ('dim-mte-3', 'Consigo realizar pausas adequadas durante minha jornada de trabalho.', 4, true),

  ('dim-mte-4', 'Existe espírito de colaboração e respeito mútuo entre os colegas.', 1, true),
  ('dim-mte-4', 'Conflitos entre pessoas ou áreas são resolvidos de forma madura e construtiva.', 2, true),
  ('dim-mte-4', 'O clima de relacionamento da minha equipe é tenso ou desgastante.', 3, true),
  ('dim-mte-4', 'Posso expressar minhas opiniões técnicas sem ser ridicularizado(a).', 4, true),

  ('dim-mte-5', 'Minha liderança imediata está disponível para orientar diante de dificuldades.', 1, true),
  ('dim-mte-5', 'Recebo apoio técnico e emocional dos meus gestores quando necessário.', 2, true),
  ('dim-mte-5', 'A organização disponibiliza canais estruturados de apoio à saúde mental.', 3, true),
  ('dim-mte-5', 'Sinto-me isolado(a) e desamparado(a) para resolver problemas operacionais complexos.', 4, true),

  ('dim-mte-6', 'As decisões sobre promoções, metas e reconhecimentos são justas e transparentes.', 1, true),
  ('dim-mte-6', 'Todos os colaboradores são tratados com o mesmo padrão ético na empresa.', 2, true),
  ('dim-mte-6', 'Sinto que regras são aplicadas de forma arbitrária ou com privilégios.', 3, true),
  ('dim-mte-6', 'Existe canal seguro para contestar avaliações ou decisões injustas.', 4, true),

  ('dim-mte-7', 'Mudanças nos processos ou estruturas são comunicadas com antecedência.', 1, true),
  ('dim-mte-7', 'Recebo treinamento suficiente para me adaptar às novidades e sistemas.', 2, true),
  ('dim-mte-7', 'As transições organizacionais geram clima de insegurança e instabilidade.', 3, true),
  ('dim-mte-7', 'A liderança acompanha o impacto das mudanças no bem-estar dos times.', 4, true),

  ('dim-mte-8', 'Tenho liberdade para escolher como executar minhas atividades diárias.', 1, true),
  ('dim-mte-8', 'Minhas sugestões de melhoria nos processos são ouvidas e consideradas.', 2, true),
  ('dim-mte-8', 'Meu trabalho é submetido a microgerenciamento e controle excessivo.', 3, true),
  ('dim-mte-8', 'Posso ajustar o ritmo do meu trabalho conforme a complexidade da demanda.', 4, true),

  ('dim-mte-9', 'Minhas atribuições e limites de responsabilidade são claros e bem definidos.', 1, true),
  ('dim-mte-9', 'Recebo demandas conflitantes de pessoas diferentes sem prioridade definida.', 2, true),
  ('dim-mte-9', 'Sei exatamente qual é o impacto do meu trabalho nos resultados da organização.', 3, true),
  ('dim-mte-9', 'Ocorrem retrabalhos frequentes por falta de alinhamento das funções.', 4, true),

  ('dim-mte-10', 'Minha remuneração e benefícios são compatíveis com o esforço exigido.', 1, true),
  ('dim-mte-10', 'A empresa reconhece formalmente as contribuições excepcionais dos times.', 2, true),
  ('dim-mte-10', 'Sinto que meu trabalho é desvalorizado em comparação com o mercado.', 3, true),
  ('dim-mte-10', 'Existem oportunidades reais de crescimento profissional para minha função.', 4, true),

  ('dim-mte-11', 'Trabalho em locais ou condições com ruído, sinal precário ou barreiras de contato.', 1, true),
  ('dim-mte-11', 'Consigo me comunicar com agilidade com outros setores quando necessário.', 2, true),
  ('dim-mte-11', 'Informações críticas para a segurança chegam a tempo de prevenir falhas.', 3, true),
  ('dim-mte-11', 'As ferramentas digitais de comunicação da empresa facilitam minha rotina.', 4, true),

  ('dim-mte-12', 'Minhas atividades de trabalho são repetitivas e monótonas na maior parte do tempo.', 1, true),
  ('dim-mte-12', 'Sinto que meu potencial técnico e criativo é subutilizado na minha função.', 2, true),
  ('dim-mte-12', 'Existe falta de desafios estimulantes no meu trabalho diário.', 3, true),
  ('dim-mte-12', 'Tenho períodos ociosos prolongados com sensação de tédio funcional.', 4, true),

  ('dim-mte-13', 'Trabalho isolado(a) fisicamente de outros colegas por períodos prolongados.', 1, true),
  ('dim-mte-13', 'A empresa fornece recursos adequados para integração de equipes remotas.', 2, true),
  ('dim-mte-13', 'Sinto falta de contato social e proximidade com a equipe de trabalho.', 3, true),
  ('dim-mte-13', 'Recebo acompanhamento regular da liderança mesmo trabalhando à distância.', 4, true)
on conflict do nothing;
