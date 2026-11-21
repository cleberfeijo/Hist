# Hist

Aplicativo Android para descoberta do patrimônio histórico urbano por
geolocalização: o usuário vê os pontos históricos ao redor, lê curiosidades,
contribui com novas (sujeitas a moderação) e conversa em um chat efêmero com
quem está fisicamente no mesmo lugar.

Projeto de TCC — Ciência da Computação, UFF (Rio das Ostras).

## Estrutura

| Pasta | Conteúdo |
|---|---|
| `backend/` | API (FastAPI + MongoDB + Redis), worker Celery e testes |
| `docs/` | Referência da API e do protocolo do chat para o app |
| `frontend/` | App Android (Kotlin/Jetpack Compose) *(em construção)* |

## Rodando localmente

Pré-requisito: Docker com Docker Compose.

```bash
cp .env.example .env
# preencha JWT_SECRET, MONGO_ROOT_PASSWORD, REDIS_PASSWORD e (opcional) ADMIN_EMAIL/ADMIN_PASSWORD
docker compose up -d --build
```

- API: http://localhost:8080
- Swagger: http://localhost:8080/docs
- Saúde: http://localhost:8080/health

O administrador inicial é criado no primeiro boot a partir de `ADMIN_EMAIL` e
`ADMIN_PASSWORD`. Para criar/promover outro depois:

```bash
docker compose exec api python -m app.scripts.create_admin pessoa@exemplo.com --name Nome
```

Worker Celery (usado pelo crawler) e RabbitMQ ficam no perfil `worker`:

```bash
docker compose --profile worker up -d
```

## Testes

Os testes usam MongoDB e Redis reais (as consultas geográficas não funcionam
com mocks):

```bash
docker compose --profile test run --rm --build tests
```

Ou fora do Docker, com Mongo/Redis acessíveis:

```bash
cd backend
pip install -r requirements-dev.txt
MONGO_URI=mongodb://localhost:27017 REDIS_URL_TEST=redis://localhost:6379/0 pytest
ruff check app tests
```

O GitHub Actions roda lint, testes e build da imagem a cada push em `backend/`.

## Papéis e moderação

| Papel | Pode |
|---|---|
| `user` | ver pontos e curiosidades aprovados, sugerir pontos e curiosidades (entram como **pendentes**), conversar no chat, gerenciar/excluir a própria conta |
| `moderator` | tudo de `user` + aprovar/rejeitar conteúdo, editar pontos; o que publica já entra aprovado |
| `admin` | tudo de `moderator` + gerenciar usuários e excluir pontos |

Conteúdo importado pelo crawler entra aprovado, com a fonte (`source`) registrada.

Detalhes dos endpoints e do protocolo do chat: [`docs/api.md`](docs/api.md).

## Rodando o app

Pré-requisitos: JDK 17 e Android SDK (plataforma 36). Suba a API (seção acima) e, a partir de `frontend/`:

```bash
cp local.properties.example local.properties   # não versionado; veja os comentários no arquivo
./gradlew :app:testDebugUnitTest               # testes
./gradlew :app:lintDebug :app:assembleDebug    # lint + APK de debug
```

- `MAPS_API_KEY` (em `local.properties` ou no ambiente): chave da Maps SDK for Android; sem ela o mapa não carrega.
- **Emulador:** nada a mudar, o debug fala com `http://10.0.2.2:8080/v1/` (o host visto pelo emulador).
- **Aparelho físico na mesma rede:** defina `HIST_DEBUG_API_BASE_URL=http://<IP-da-máquina>:8080/v1/`
  em `frontend/local.properties` (vale para o debug) e `HIST_API_BASE_URL` para o release.

### Gerar o AAB de release

```bash
./gradlew :app:bundleRelease
```

O arquivo sai em `frontend/app/build/outputs/bundle/release/app-release.aab`. Defina
`HIST_API_BASE_URL` com a URL real da API. Sem `HIST_KEYSTORE_PATH` o AAB sai **sem assinatura**;
para assinar, informe `HIST_KEYSTORE_PATH` (arquivo existente), `HIST_KEYSTORE_PASSWORD`,
`HIST_KEY_ALIAS` e `HIST_KEY_PASSWORD` (se o caminho existir e faltar alguma das três, o build falha).
Nunca versione a keystore nem o `local.properties`.

### Limitações conhecidas

- A tolerância de 5 minutos do chat em segundo plano é um teto, não uma garantia: o Android pode
  cortar a rede antes. O app reconecta se o usuário estiver a até 200 m, e as mensagens trocadas
  durante a lacuna são perdidas.
- GPS falso pode burlar o raio; o app bloqueia as localizações simuladas que consegue detectar.
- O token de sessão fica no armazenamento privado do app, sem criptografia adicional (backup desativado).
- O mapa, o comportamento do teclado e o fluxo real de GPS não foram exercitados em aparelho pelos
  testes automatizados: precisam de conferência manual, com uma chave da Maps API.
