-- Jornada do cliente atualizada: Proposta e Contrato passaram para o pipeline comercial, e entraram
-- Estrutura Organizacional, Matriz de risco, Resultados e Plano de Ação (mesmos textos de CLIENTE_ETAPAS
-- no data.jsx). Os números das etapas não mudam, então o progresso já gravado continua válido.
update steps set label = 'Estrutura Organizacional',
                 description = 'Unidades, setores, diretorias, cargos, ambientes e GHE/GES'
 where number = 2;

update steps set label = 'Matriz de risco',
                 description = 'Matriz de risco PGR (NR-01) com critérios e severidade'
 where number = 3;

update steps set label = 'Resultados',
                 description = 'Resultados consolidados após diagnóstico e entrevistas'
 where number = 7;

update steps set label = 'Plano de Ação',
                 description = 'Plano de ação psicossocial e intervenções prioritárias'
 where number = 8;
