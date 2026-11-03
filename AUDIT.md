# Auditoria — Áudio de Resumo (antes da atualização de livros)

Data: 2026-11-03 · versão analisada: 1.0.0

## O que existe

- Biblioteca com 12 **resumos curados** (conteúdo editorial, não livros importados).
- Importação de **PDF (texto), EPUB e TXT** em `ImportFlow`, gerando **um único resumo** por arquivo.
- Geração de resumo com Gemini (opcional) e fallback local extrativo.
- Player TTS de resumo, mapa mental, flashcards, quiz e plano de revisão para o resumo.
- Busca por título/autor de resumos, favoritos e progresso do resumo.

## Recursos bloqueados, incompletos ou inexistentes

| Recurso | Situação |
|---|---|
| Livro como entidade persistida | **Inexistente** — o arquivo vira um `AudioSummary`; capítulos e texto original são descartados. |
| EPUB completo com capítulos/índice | **Incompleto** — o parser lê o spine, mas concatena tudo e corta em 300 mil caracteres. |
| PDF com texto | **Parcial** — extrai o texto inteiro de uma vez; sem capítulos, sem OCR. |
| PDF digitalizado (OCR) | **Inexistente**. |
| DOCX | **Inexistente**. |
| Texto colado como livro | **Parcial** — só gera um resumo avulso. |
| Resumo do livro inteiro | **Inexistente** — limite de 60 mil caracteres no Gemini e de 120 frases no modo local. |
| Resumo por capítulo | **Inexistente**. |
| Áudio por capítulo | **Inexistente** — o TTS só narra o resumo. |
| Leitura/retomada por capítulo | **Inexistente**. |
| Perguntas e respostas sobre o livro | **Inexistente**. |
| Biblioteca de livros com renomear/excluir/organizar | **Inexistente** — a biblioteca lista apenas resumos. |
| Busca no conteúdo dos livros | **Inexistente** — busca apenas metadados de resumos. |
| Progresso real de importação/processamento | **Incompleto** — diálogos indeterminados; sem persistência de progresso. |
| Processamento em blocos/map-reduce | **Inexistente** — truncamento silencioso. |
| Memória em livros grandes | **Risco** — texto e PDF inteiros em memória; EPUB cortado. |
| Retomada após falha | **Inexistente**. |
| Opções de duração 5/10/15/20 min | **Parcial** — o app tem 2/5/10/20; conteúdo de 5 min é o menor formato. Não há bloqueio/paywall no nativo. |
| Dados fictícios | Os 12 resumos são **curadoria**, não livros processados; a nova biblioteca de livros começa vazia. |

## Plano de implementação

1. `Book`, `BookChapter` e `BookStore` persistentes (metadados + texto por capítulo em arquivo).
2. `BookImporter`: EPUB por capítulo, PDF com texto + OCR (ML Kit/PdfRenderer), TXT, DOCX e texto colado; progresso real, sem limite artificial de páginas.
3. `BookProcessor`: resumo do livro por map-reduce em blocos, resumo por capítulo, Q&A com citação de capítulos; fallback local claramente rotulado.
4. Serviço em primeiro plano para resumo do livro (progresso persistido, retomada, cancelamento).
5. UI: biblioteca de livros (busca, favoritos, renomear, excluir, ordenar), detalhe do livro, leitor TTS por capítulo, perguntas e respostas.
6. Durações 5/10/15/20 min em todo o fluxo, sem bloqueios.
7. Testes de importação (EPUB/TXT/DOCX), persistência, processamento e UI; release 2.0.0.

## Status após a implementação 2.0.0

| Item | Status |
|---|---|
| Livro persistido com capítulos e texto em arquivos | ✅ `Book`, `BookStore` |
| EPUB completo por capítulos/OPF/spine | ✅ `BookImporter` (sem truncamento de 300 mil) |
| PDF com texto | ✅ PDFBox página a página |
| PDF digitalizado com OCR | ✅ ML Kit + PdfRenderer, uma página por vez |
| DOCX | ✅ parser de `word/document.xml` + metadados |
| TXT e texto colado | ✅ com capítulos por títulos ou blocos explícitos |
| Resumo do livro inteiro | ✅ map-reduce por blocos + serviço em primeiro plano + retomada |
| Resumo por capítulo | ✅ sob demanda, com fallback local |
| Áudio por capítulo | ✅ `BookReaderActivity` (TTS, pausa, velocidade, avanço automático) |
| Leitura e retomada | ✅ último capítulo e posição de rolagem persistidos |
| Q&A sobre o livro | ✅ recuperação por capítulos + citação; modo local |
| Biblioteca de livros | ✅ aba Livros, busca full-text, favoritos, ordenação |
| Renomear/excluir/organizar | ✅ menus e ordenação |
| Progresso real | ✅ importação por página/capítulo; resumo por bloco; persistido |
| Limites artificiais | ✅ removidos; apenas limite técnico de 250 MB por PDF |
| Memória | ✅ capítulos em arquivos separados, OCR página a página, blocos de processamento |
| Dados fictícios como livros | ✅ nenhum: a biblioteca de livros começa vazia; os 12 resumos são curadoria rotulada |
| Durações 5/10/15/20 | ✅ em Criar e no resumo do livro, sem paywall |

### Pendências conhecidas

- OCR depende da qualidade do PDF e do modelo ML Kit instalado; não há tradução automática.
- Detecção de capítulos em PDFs sem sumário é heurística (títulos/páginas); quando não encontra títulos, cria blocos rotulados.
- Sincronização em nuvem/Firebase do app web continua não portada.
