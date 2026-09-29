# PDFs de referência do gerador Python

Estes PDFs foram gerados pelo antigo `gerar_relatorio_psicossocial.py` (ReportLab 4.0.7,
commit `28971d0`), com a montagem de configuração do antigo `server.py`, a partir dos JSONs
desta pasta. O `exemplo.pdf` corresponde ao `CONFIG` padrão (o mesmo de `GET /api/teste`).

O `RelatorioParidadeTest` gera os mesmos relatórios em Java e exige página a página o mesmo
texto, os mesmos glifos nas mesmas posições (tolerância de 0,01 pt) e a mesma imagem renderizada.

## Casos com 13 dimensões ou mais

O gerador Python original falhava com `LayoutError` em relatórios com 13 dimensões ou mais: ele
montava o PDF em duas passadas reaproveitando os mesmos objetos, e a marca `_postponed` que a
primeira passada deixava numa barra do ranking derrubava a segunda. As referências
`dims_013.pdf`, `dims_030.pdf` e `scores_edge.pdf` foram geradas com uma única correção: limpar
essa marca entre as passadas. Todo o resto do algoritmo é o original.
