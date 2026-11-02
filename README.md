# Áudio de Resumo — Android nativo

Aplicativo Android nativo de **microaprendizagem em áudio**: resumos de livros, PDFs e EPUBs com narração por TTS, mapa mental, flashcards, quiz e plano de revisão. Convertido do app web React/Express para **Kotlin + XML + Material 3**, com dados locais e IA opcional.

![Home](docs/screenshots/home.png)

## Recursos

- **Biblioteca curada** com 12 resumos (Hábitos Atômicos, A Arte da Guerra, Sapiens, Inteligência Emocional e outros), busca, categorias, favoritos e progresso.
- **Player de áudio nativo** com `TextToSpeech` em pt-BR:
  - reprodução em segundo plano com notificação e controles;
  - velocidade 0,75x–2,0x, soneca, marcadores e transcrição sincronizada;
  - retomada de onde parou e progresso salvo.
- **Resumo com IA (opcional)**:
  - cole um texto ou importe um arquivo e gere resumo, mapa mental, flashcards e quiz com Gemini;
  - **modo local offline** (extrativo) quando não há chave configurada;
  - chave e modelo configuráveis em **Perfil** (armazenados apenas no aparelho).
- **Importação local de EPUB e PDF**:
  - botão **+** e cartão **“Adicionar EPUB ou PDF local”** na Biblioteca;
  - EPUB: leitura de `container.xml`, OPF, spine e capítulos XHTML;
  - PDF: extração de texto com PDFBox;
  - PDFs digitalizados (sem texto) podem ser enviados ao Gemini quando há chave;
  - o resumo é salvo na biblioteca e aberto em seguida.
- **Aprendizagem ativa**: mapa mental radial, flashcards com virar de cartão e quiz com correção/explicação e pontuação salva.
- **Plano de revisão espaçada** em 1, 3, 7 e 14 dias.
- **Perfil**: estatísticas, plano Premium demonstrativo, painel administrativo com métricas e limpeza de histórico/cache.
- Tema escuro, tipografia Plus Jakarta Sans, capas geradas por gradiente.

| Biblioteca | Criar | Aprender |
|---|---|---|
| ![Biblioteca](docs/screenshots/library.png) | ![Criar](docs/screenshots/create.png) | ![Aprender](docs/screenshots/learn.png) |

| Detalhe | Player | Mapa mental |
|---|---|---|
| ![Detalhe](docs/screenshots/detail.png) | ![Player](docs/screenshots/player.png) | ![Mapa](docs/screenshots/mindmap.png) |

## Como usar

1. Abra o app e navegue pela **Biblioteca**.
2. Toque em um resumo para ver a visão geral, seções e conceitos.
3. Toque em **Ouvir resumo** para iniciar a narração.
4. Em **Criar**, cole um texto (ou toque em **Usar exemplo**) e gere um resumo. Sem chave, o modo local funciona offline.
5. Na **Biblioteca**, toque no **+** para importar **PDF**, **EPUB** ou **TXT** do aparelho.
6. Em **Aprender**, estude com mapa mental, flashcards, quiz e plano de revisão.
7. Em **Perfil**, configure a chave Gemini, o modelo, a velocidade da narração e o plano.

### Chave Gemini (opcional)

- Obtenha uma chave em <https://aistudio.google.com/apikey>.
- No app: **Perfil → Chave Gemini**.
- Sem chave, o app continua funcionando com o resumidor local e a biblioteca curada.

## Arquitetura

- Kotlin, `minSdk 24`, `targetSdk 35`, AGP 8.7.3, Kotlin 2.0.21.
- Activities + Fragments, ViewBinding, Material 3.
- Persistência local em `SharedPreferences` (JSON) — sem conta obrigatória e sem servidor.
- `SpeechService` em primeiro plano com `TextToSpeech`, notificação e `SpeechController` (StateFlow).
- `GeminiClient` (OkHttp + JSON) e `LocalSummarizer` como fallback offline.
- `ImportFlow` (PDFBox + parser EPUB) para arquivos locais.
- Testes Robolectric: biblioteca, resumidor local, leitura de EPUB/PDF, player/conteúdo, favoritos/quiz e screenshots.

```
app/src/main/java/com/audioresumo/app/
├── data/        modelos, Store (JSON), biblioteca
├── service/     SpeechService, SpeechController, parágrafos
├── ui/          Main, Splash, Detail, Player, MindMap, Flashcards, Quiz, ReviewPlan
├── ui/fragments Home, Library, Create, Learn, Profile
├── ui/widgets/  CoverView, MindMapView
└── util/        GeminiClient, LocalSummarizer, ImportFlow, Ui, ThemeUtils
```

## Build

Requisitos: JDK 17+ e Android SDK 35.

```bash
export JAVA_HOME=/caminho/para/jdk-17
gradle :app:assembleDebug
```

APK de release assinado:

```bash
export AURORA_KEYSTORE_PATH=/caminho/aurora.jks
export AURORA_KEYSTORE_PASS=...
export AURORA_KEY_ALIAS=aurora
export AURORA_KEY_PASS=...
gradle :app:assembleRelease
```

O CI (`.github/workflows/build.yml`) usa os mesmos secrets `AURORA_*` dos outros apps e publica o APK como artifact.

## Privacidade

- Biblioteca, progresso, marcadores e textos importados ficam no aparelho.
- A chave Gemini fica nas preferências locais e só é usada quando você gera um resumo com IA.
- Nenhum PDF/EPUB é enviado a servidores no modo local; com IA, o conteúdo é enviado à API do Google Gemini.

## Limitações

- A voz e o offline do TTS dependem do motor de síntese instalado no aparelho.
- EPUBs com DRM não são suportados.
- PDFs digitalizados sem camada de texto exigem Gemini.
- O plano Premium é demonstrativo (sem cobrança real) nesta versão.
- A sincronização em nuvem/Firebase do app web não foi portada; os dados são locais.

## Versão

**1.0.0** — primeira versão Android nativa, com biblioteca, IA opcional, importação local de EPUB/PDF, player TTS e módulo de aprendizagem.

APK: veja a página de **Releases** do repositório.
