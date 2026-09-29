# Menctor Backend (Quarkus)

Backend do Menctor em **Quarkus 3.33 (LTS) / Java 21**. Substitui o antigo `server.py` (Flask) e o
`gerar_relatorio_psicossocial.py` (ReportLab) mantendo o mesmo contrato HTTP, então o frontend
não mudou.

| Método | Caminho | O que faz |
|---|---|---|
| GET | `/health` | Verificação de saúde |
| POST | `/api/gerar-relatorio` | Recebe o JSON da avaliação e devolve o relatório psicossocial em PDF |
| GET | `/api/teste` | Relatório de demonstração em PDF |
| POST | `/api/send-email` | Envia e-mail pelo SMTP configurado (`to`, `subject`, `html`/`text`, `replyTo`) |
| GET | `/*` | Frontend estático (`index.html`, `.jsx`, assets), com fallback de SPA igual ao `vercel.json` |

## Rodando

Pré-requisito: JDK 21 (`brew install openjdk@21` e `export JAVA_HOME=/opt/homebrew/opt/openjdk@21`).
O Maven é baixado pelo wrapper (`./mvnw`).

```bash
cd backend
./mvnw quarkus:dev      # http://localhost:5000, com recarga automática do código Java
./mvnw test             # testes (inclui a comparação com os PDFs do gerador Python)
./mvnw package          # gera target/quarkus-app/quarkus-run.jar
```

O modo dev roda a partir da raiz do repositório: lê o `.env` de lá e serve o frontend.

## Configuração

Variáveis de ambiente (ou no `.env` da raiz, no modo dev):

| Variável | Padrão | Uso |
|---|---|---|
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM_NAME` | porta `587`, nome `Menctor` | SMTP (STARTTLS e login obrigatórios, timeout de 20 s) |
| `PORT` | `5000` | Porta HTTP |
| `MENCTOR_CORS_ORIGINS` | qualquer origem | Origens liberadas no CORS, separadas por vírgula |
| `MENCTOR_FRONTEND_DIR` | diretório atual ou o pai | Pasta com o `index.html` do frontend |

Sem `MAIL_*`, o `/api/send-email` responde 500 "SMTP nao configurado", como antes. O certificado TLS
do servidor SMTP é verificado (o `smtplib` do Python não verificava).

## Docker

```bash
# a partir da RAIZ do repositório (a imagem também leva o frontend)
docker build -f backend/Dockerfile -t menctor-backend .
docker run --rm -p 8080:8080 --env-file .env menctor-backend
```

## Relatório em PDF

`relatorio/RelatorioPsicossocialPdf` é um port linha a linha do gerador Python. Ele roda sobre o
pacote `pdf/`, uma reimplementação enxuta do motor de layout do ReportLab em cima do Apache PDFBox,
que reproduz a paginação, a justificação, as tabelas e as fontes. O `RelatorioParidadeTest` exige
que o PDF gerado seja idêntico (texto, posição de cada glifo e imagem) aos PDFs de referência do
gerador original. Mudanças intencionais no relatório precisam atualizar essas referências.

Diferenças em relação ao Python, todas correções:

- relatórios com **13 ou mais dimensões** funcionam (o Python falhava com `LayoutError`);
- textos vindos da API são tratados como texto puro (no ReportLab, `<`, `&` e afins eram
  interpretados como marcação e podiam sumir ou quebrar o PDF);
- entrada inválida responde 400 com mensagem clara, e conteúdo que não cabe numa página responde
  422 (antes, tudo virava 500);
- nenhum arquivo oculto é servido (o `server.py` servia até o `.env`, se existisse).
