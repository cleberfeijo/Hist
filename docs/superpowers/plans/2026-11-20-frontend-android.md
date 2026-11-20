# Hist — App Android (frontend) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Construir do zero, em `frontend/`, o app Android do Hist: mapa de pontos históricos, detalhe com curiosidades, chat efêmero por proximidade, sugestões, perfil e moderação, consumindo o backend existente.

**Architecture:** Módulo único `:app`, MVVM, pacotes por funcionalidade (`core/`, `auth/`, `explore/`, `landmark/`, `chat/`, `contribute/`, `profile/`, `moderation/`). Cada tela tem um `XxxRoute` (liga ao ViewModel) e um `XxxContent` sem estado (testável). Repositórios devolvem `ApiResult<T>`; a sessão do chat é uma máquina de estados (`ChatSession`) independente de Android, testada com tempo virtual.

**Tech Stack:** Kotlin, Jetpack Compose + Material 3, Hilt (KSP), Retrofit + OkHttp (REST e WebSocket), kotlinx.serialization, Navigation Compose (rotas tipadas), DataStore Preferences, Coil 3, maps-compose, play-services-location. Testes: JUnit4, kotlinx-coroutines-test, Turbine, OkHttp MockWebServer, Robolectric + compose ui-test.

**Spec:** [`docs/superpowers/specs/2026-11-20-frontend-android-design.md`](../specs/2026-11-20-frontend-android-design.md). Contrato do backend: [`docs/api.md`](../../api.md).

## Global Constraints

- Todo o app vive em `frontend/` (projeto Gradle raiz = `frontend/`, módulo `:app`). Não alterar `backend/`.
- **Nunca** versionar `Hist_PAI_v*.docx`: commits com caminhos explícitos, nunca `git add -A` / `git add .`.
- Mensagens de commit em pt-BR, terminando com a linha `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Pacote e `applicationId`: `br.uff.hist`. `minSdk 26`, `compileSdk 36`, `targetSdk 36`. JDK 17. Gradle wrapper **8.14.3** (já em `~/.gradle/wrapper/dists`), AGP 8.13.x, Kotlin 2.2.x. Versões num `gradle/libs.versions.toml`.
- URL base: `BuildConfig.API_BASE_URL` = `"http://10.0.2.2:8080/v1/"` no debug; release lê `HIST_API_BASE_URL` de `local.properties`/env (padrão `"https://api.hist.invalid/v1/"`). HTTP em texto puro liberado **só no debug** (network security config em `src/debug`).
- Chave do Google Maps: `MAPS_API_KEY` de `local.properties` → `manifestPlaceholders`, padrão `"MISSING_KEY"`. Nada de segredos versionados.
- Todo texto visível em `res/values/strings.xml`, pt-BR. Textos fixados pela spec (usar exatamente):
  - `Perto de você` · `Nesta área` · `Chat`
  - `Ative a localização para ver o que há ao seu redor e entrar nos chats.`
  - `Entrar no chat do local` · `Chegue a %1$d m para entrar no chat (faltam %2$d m)`
  - `Ninguém contou nada sobre este lugar ainda. Seja o primeiro.`
  - `Recebido! Vai aparecer depois de aprovada.`
  - `Recebido! Sua sugestão vai aparecer no mapa depois de aprovada.`
  - `As mensagens só existem enquanto você estiver aqui. Ao sair do local, a conversa some.`
  - `%1$s entrou` · `%1$s saiu` · plural `%d pessoa aqui agora` / `%d pessoas aqui agora`
  - `Você está se afastando (%1$d m). A partir de %2$d m você sai do chat.`
  - `Você saiu da área do ponto. A conversa foi encerrada.`
  - `A conversa foi encerrada porque você ficou fora do app` · `Reconectando…`
  - Selos: `Pendente` · `Aprovado` · `Rejeitado`
- Cores (spec 5.1). Claro: bg `#F6F1E9`, surface `#FBF8F3`, onSurface `#2A211B`, secondary text `#857565`, outline `#E6DCCD`, primary `#B4532A`, onPrimary `#FFFFFF`. Escuro: bg `#0F1115`, surface `#1C2029`, onSurface `#F2F4F7`, secondary text `#8B93A3`, outline `#2A2F3A`, primary `#F5B544`, onPrimary `#1A1305`. Títulos em Fraunces, corpo em Inter (fontes em `res/font`, OFL), nos dois temas.
- Raios e tempos (constantes nomeadas, não números soltos): `CHAT_JOIN_RADIUS_M = 200` (padrão do cliente; o `welcome` manda), `USER_NEARBY_RADIUS_M = 1000`, `USER_REFETCH_DISTANCE_M = 25.0`, `MAX_NEARBY_RADIUS_M = 50_000`, `NEARBY_LIMIT = 200`, fallback do mapa Rio das Ostras `GeoPoint(-22.5269, -41.9450)`, `BACKGROUND_TOLERANCE_MS = 5 * 60_000`, `PING_INTERVAL_MS = 30_000`, `LOCATION_MIN_DISPLACEMENT_M = 10.0`, backoff de reconexão `1s, 2s, 4s, 8s, 16s, 30s` (fica em 30s).
- Logout automático **somente** nos códigos `unauthorized` e `session_revoked` (HTTP 401 ou fechamento WS 4401). `invalid_credentials` e `invalid_password` são erros de formulário.
- Testes de UI rodam como testes locais com Robolectric (sem emulador). O `GoogleMap` não é testado em UI; a lógica do mapa vive no ViewModel.
- Verificação padrão (de `frontend/`): `./gradlew :app:testDebugUnitTest` e, no fim de cada task, `./gradlew :app:lintDebug :app:assembleDebug`.

## Review Focus

1. **Sem posição ainda** (GPS ligado, mas sem fix: ambiente fechado, app recém-aberto): o botão do chat mostra "Procurando sua localização…" desabilitado; nunca conecta com posição velha ou nula. Teste na Task 9.
2. **Sessão revogada no meio do uso** (senha trocada em outro aparelho): qualquer chamada com `session_revoked` limpa a sessão e leva ao Login de qualquer tela, sem loop nem crash. Testes nas Tasks 4 e 6.
3. **Backend fora do ar / sem internet** ao abrir o Explorar ou o detalhe: estado de erro com "Tentar de novo", nunca carregamento infinito. Teste na Task 8.
4. **Mensagem em branco ou longa demais** (só espaços, > `max_message_length`, emojis): envio bloqueado no cliente; `message_too_long`/`rate_limited` do servidor viram aviso, sem derrubar o chat. Teste na Task 11.
5. **Toque duplo em "Enviar"** em formulários (sugestão de ponto, curiosidade, rejeição): uma única requisição; botão desabilitado enquanto envia. Testes nas Tasks 9 e 13.

---

## Estrutura de arquivos

```
frontend/
  settings.gradle.kts  build.gradle.kts  gradle.properties  gradlew  gradle/{libs.versions.toml,wrapper/}
  app/build.gradle.kts
  app/src/main/AndroidManifest.xml
  app/src/main/res/{values/strings.xml, values/themes.xml, font/, raw/map_style_light.json, raw/map_style_dark.json, drawable/ic_cat_*.xml}
  app/src/debug/res/xml/network_security_config.xml
  app/src/main/java/br/uff/hist/
    HistApp.kt  MainActivity.kt
    core/designsystem/   Color.kt Type.kt Theme.kt components/ (StatusBadge, PrimaryButton, ErrorState, LoadingState)
    core/model/          Models.kt Category.kt Geo.kt
    core/network/        HistApi.kt Dtos.kt Mappers.kt ApiResult.kt ApiCaller.kt AuthInterceptor.kt NetworkModule.kt
    core/session/        SessionStore.kt DataStoreSessionStore.kt SessionEvents.kt SessionModule.kt
    core/location/       LocationProvider.kt FusedLocationProvider.kt LocationModule.kt
    core/navigation/     Routes.kt HistNavHost.kt MainScaffold.kt
    auth/                AuthRepository.kt LoginScreen.kt LoginViewModel.kt RegisterScreen.kt RegisterViewModel.kt
    onboarding/          LocationOnboardingScreen.kt
    explore/             LandmarkRepository.kt ExploreViewModel.kt ExploreScreen.kt ExploreMap.kt LandmarkList.kt
    landmark/            LandmarkDetailViewModel.kt LandmarkDetailScreen.kt
    chat/                ChatProtocol.kt ChatSocket.kt OkHttpChatSocketFactory.kt ChatSession.kt ChatViewModel.kt ChatScreen.kt
    contribute/          SuggestLandmarkViewModel.kt SuggestLandmarkScreen.kt
    profile/             AccountRepository.kt ProfileScreen.kt MyContributionsViewModel.kt MyContributionsScreen.kt SettingsViewModel.kt SettingsScreen.kt
    moderation/          ModerationRepository.kt ModerationViewModel.kt ModerationQueueScreen.kt ModerationDetailScreen.kt
  app/src/test/java/br/uff/hist/...   (espelha os pacotes; fakes em testutil/)
.github/workflows/frontend.yml
```

---

### Task 1: Projeto base, build e CI

**Files:**
- Create: tudo em `frontend/` listado acima até `MainActivity.kt` (Gradle, manifest, `HistApp`, `MainActivity` exibindo um `Text("Hist")`), `app/src/debug/res/xml/network_security_config.xml`, `.github/workflows/frontend.yml`
- Modify: `README.md` (linha da tabela `android/` → `frontend/`, seção "Rodando o app"), `.gitignore` (`frontend/local.properties`, `frontend/.gradle/`, `frontend/**/build/`, `*.jks`, `*.keystore`)
- Test: `frontend/app/src/test/java/br/uff/hist/BuildConfigTest.kt`

**Interfaces:**
- Produces: `BuildConfig.API_BASE_URL: String`; `@HiltAndroidApp class HistApp : Application`; `@AndroidEntryPoint class MainActivity : ComponentActivity`; todas as dependências do Tech Stack declaradas no catálogo; `testOptions.unitTests.isIncludeAndroidResources = true`.

- [ ] **Step 1:** Gerar o wrapper: `cd frontend && ~/.gradle/wrapper/dists/gradle-8.14.3-all/*/gradle-8.14.3/bin/gradle wrapper --gradle-version 8.14.3 --distribution-type all`. Esperado: `gradlew` e `gradle/wrapper/` criados.
- [ ] **Step 2: Teste que falha** — `BuildConfigTest.debugBaseUrlPointsToEmulatorHost()`: `assertEquals("http://10.0.2.2:8080/v1/", BuildConfig.API_BASE_URL)`.
- [ ] **Step 3:** Rodar `./gradlew :app:testDebugUnitTest --tests br.uff.hist.BuildConfigTest`. Esperado: FAIL (compilação: `API_BASE_URL` não existe).
- [ ] **Step 4:** Escrever os arquivos Gradle (catálogo, plugins Hilt/KSP/serialization/compose, `buildConfigField` por build type, `manifestPlaceholders["MAPS_API_KEY"]`, `signingConfigs.release` lido de `local.properties`/env `HIST_KEYSTORE_PATH`, `HIST_KEYSTORE_PASSWORD`, `HIST_KEY_ALIAS`, `HIST_KEY_PASSWORD` — aplicado só se o caminho existir), manifest (permissões `INTERNET`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`; meta-data `com.google.android.geo.API_KEY`), `HistApp`, `MainActivity`.
- [ ] **Step 5:** Rodar o teste. Esperado: PASS. Depois `./gradlew :app:lintDebug :app:assembleDebug`. Esperado: BUILD SUCCESSFUL.
- [ ] **Step 6:** `frontend.yml`: em push/PR que toquem `frontend/**`, `actions/setup-java` (Temurin 17), `working-directory: frontend`, rodar `./gradlew :app:lintDebug :app:testDebugUnitTest :app:assembleDebug`. Atualizar README e `.gitignore`.
- [ ] **Step 7: Commit** — `git add frontend .github/workflows/frontend.yml README.md .gitignore && git commit -m "Cria o projeto Android do app em frontend/"` (+ trailer).

---

### Task 2: Tema "Patrimônio" / "Noturno"

**Files:**
- Create: `core/designsystem/Color.kt`, `Type.kt`, `Theme.kt`, `components/StatusBadge.kt`, `components/ErrorState.kt`, `components/LoadingState.kt`; `res/font/` (Fraunces SemiBold/Medium, Inter Regular/Medium/SemiBold, `.ttf`, OFL; licença em `res/raw/ofl_fonts.txt` ou `frontend/app/src/main/assets/licenses/`)
- Test: `core/designsystem/ThemeTest.kt`, `core/designsystem/StatusBadgeTest.kt` (Robolectric)

**Interfaces:**
- Produces:
  - `enum class ThemePreference { LIGHT, DARK, SYSTEM }`
  - `@Composable fun HistTheme(preference: ThemePreference = ThemePreference.SYSTEM, content: @Composable () -> Unit)`
  - `val HistColors.light: ColorScheme`, `val HistColors.dark: ColorScheme` (objeto `HistColors`)
  - `val LocalSecondaryText: ProvidableCompositionLocal<Color>` (texto secundário de cada tema)
  - `@Composable fun StatusBadge(status: ModerationStatus)` (Task 3 define `ModerationStatus`; nesta task criar o enum já em `core/model/Models.kt`)
  - `@Composable fun ErrorState(message: String, onRetry: () -> Unit)` com botão `Tentar de novo`; `@Composable fun LoadingState()`

- [ ] **Step 1: Testes que falham** — `ThemeTest`: `HistColors.light.primary == Color(0xFFB4532A)`, `HistColors.light.background == Color(0xFFF6F1E9)`, `HistColors.dark.primary == Color(0xFFF5B544)`, `HistColors.dark.onPrimary == Color(0xFF1A1305)`; `HistTheme(ThemePreference.DARK)` expõe `MaterialTheme.colorScheme.background == Color(0xFF0F1115)` mesmo com o sistema em modo claro. `StatusBadgeTest`: `StatusBadge(PENDING)` exibe `Pendente`; `APPROVED` → `Aprovado`; `REJECTED` → `Rejeitado`. `ErrorState` exibe `Tentar de novo` e o clique chama `onRetry`.
- [ ] **Step 2:** Rodar `./gradlew :app:testDebugUnitTest --tests 'br.uff.hist.core.designsystem.*'`. Esperado: FAIL.
- [ ] **Step 3:** Implementar com os valores das Global Constraints. Tipografia: `displaySmall/headlineSmall/titleLarge/titleMedium` em Fraunces; demais em Inter.
- [ ] **Step 4:** Rodar os testes. Esperado: PASS. Envolver o conteúdo do `MainActivity` em `HistTheme`.
- [ ] **Step 5: Commit** — `Adiciona o tema visual do app (claro e noturno)`.

---

### Task 3: Modelos, categorias e geodistância

**Files:**
- Create/Modify: `core/model/Models.kt`, `core/model/Category.kt`, `core/model/Geo.kt`, `res/drawable/ic_cat_<id>.xml` (9 ícones vetoriais)
- Test: `core/model/CategoryTest.kt`, `core/model/GeoTest.kt`

**Interfaces:**
- Produces:
  - `data class GeoPoint(val latitude: Double, val longitude: Double)`
  - `fun distanceMeters(a: GeoPoint, b: GeoPoint): Double` (haversine, raio 6_371_000.0, igual a `backend/app/chat/geo.py`)
  - `enum class ModerationStatus { PENDING, APPROVED, REJECTED }`
  - `enum class Role { USER, MODERATOR, ADMIN }`
  - `enum class Category(val id: String, @StringRes val label: Int, @DrawableRes val icon: Int)` com `IGREJA("igreja")`, `MUSEU("museu")`, `MONUMENTO("monumento")`, `EDIFICIO_HISTORICO("edificio_historico")`, `PRACA("praca")`, `FORTIFICACAO("fortificacao")`, `SITIO_ARQUEOLOGICO("sitio_arqueologico")`, `FERROVIA("ferrovia")`, `OUTRO("outro")`; rótulos da spec 5.5; `companion fun fromId(id: String?): Category` (desconhecido/nulo/vazio → `OUTRO`; comparação sem diferenciar maiúsculas)
  - `data class Source(val name: String, val url: String?, val license: String?)`
  - `data class User(val id: String, val name: String, val lastName: String, val email: String, val roles: Set<Role>)` com `val isModerator: Boolean` (MODERATOR ou ADMIN)
  - `data class Landmark(val id: String, val name: String, val description: String?, val location: GeoPoint, val category: Category, val city: String?, val state: String?, val imageUrl: String?, val source: Source?, val status: ModerationStatus, val rejectionReason: String?, val curiosityCount: Int, val createdBy: String?)`
  - `data class Curiosity(val id: String, val landmarkId: String, val landmarkName: String?, val text: String, val authorName: String?, val source: Source?, val status: ModerationStatus, val rejectionReason: String?, val createdAt: Instant)`
  - `data class Page<T>(val items: List<T>, val total: Int, val page: Int, val pageSize: Int)` com `val hasMore: Boolean = page * pageSize < total`

- [ ] **Step 1: Testes que falham** — `CategoryTest`: `fromId("igreja") == IGREJA`, `fromId("IGREJA") == IGREJA`, `fromId("capela") == OUTRO`, `fromId(null) == OUTRO`, `fromId("") == OUTRO`, `Category.entries.size == 9`. `GeoTest`: distância entre `GeoPoint(-22.5269,-41.9450)` e `GeoPoint(-22.5269,-41.9450)` == 0.0; entre `(0.0,0.0)` e `(0.0,1.0)` ≈ 111_195 m (±1 m); simetria `d(a,b) == d(b,a)`.
- [ ] **Step 2:** Rodar `--tests 'br.uff.hist.core.model.*'`. Esperado: FAIL.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** Rodar. Esperado: PASS.
- [ ] **Step 5: Commit** — `Adiciona modelos de domínio, categorias e cálculo de distância`.

---

### Task 4: Camada de rede e tratamento de erros

**Files:**
- Create: `core/network/Dtos.kt`, `Mappers.kt`, `HistApi.kt`, `ApiResult.kt`, `ApiCaller.kt`, `AuthInterceptor.kt`, `NetworkModule.kt`, `core/session/SessionEvents.kt`
- Test: `core/network/ApiCallerTest.kt`, `core/network/HistApiTest.kt` (MockWebServer)

**Interfaces:**
- Consumes: modelos da Task 3.
- Produces:
  - DTOs `@Serializable` em snake_case com `@SerialName`, espelhando `docs/api.md` (`TokenResponseDto`, `UserDto`, `LandmarkDto`, `NearbyLandmarkDto`, `CuriosityDto`, `PageDto<T>`, `ErrorEnvelopeDto`, requests `RegisterRequest`, `NewLandmarkRequest`, `NewCuriosityRequest`, `UpdateMeRequest`, `ChangePasswordRequest`, `PasswordBody`, `RejectRequest`). `Json { ignoreUnknownKeys = true; explicitNulls = false }`.
  - `fun LandmarkDto.toDomain(): Landmark`, `CuriosityDto.toDomain()`, `UserDto.toDomain()`, `PageDto<D>.toDomain(map: (D) -> T): Page<T>`. `CuriosityDto.author?.name` → `authorName`.
  - `interface HistApi` (Retrofit, `suspend`): `login(@Field username, @Field password)` (`@FormUrlEncoded POST auth/login`), `register`, `logout`, `me`, `updateMe`, `changePassword`, `deleteMe(@Body PasswordBody)` via `@HTTP(method = "DELETE", path = "users/me", hasBody = true)`, `myLandmarks(status, page, pageSize)`, `myCuriosities(...)`, `nearby(lat, lon, radius, limit)`, `landmark(id)`, `landmarkCuriosities(id, page, pageSize)`, `createLandmark`, `createCuriosity(id, body)`, `deleteLandmark(id)`, `curiosity(id)`, `deleteCuriosity(id)`, `moderationLandmarks(status, page, pageSize)`, `approveLandmark(id)`, `rejectLandmark(id, RejectRequest)`, `moderationCuriosities(...)`, `approveCuriosity(id)`, `rejectCuriosity(id, RejectRequest)`. Rotas `204` retornam `Response<Unit>`.
  - `sealed interface AppError { data class Api(val code: String, val message: String, val fieldErrors: Map<String, String>) ; data object Network ; data object SessionExpired ; data object Unknown }`
  - `sealed interface ApiResult<out T> { data class Success<T>(val value: T) ; data class Failure(val error: AppError) }` + `inline fun <T, R> ApiResult<T>.map(f: (T) -> R): ApiResult<R>`
  - `class SessionEvents @Inject constructor() { val expired: SharedFlow<Unit>; fun notifyExpired() }` (`@Singleton`, `MutableSharedFlow(extraBufferCapacity = 1)`)
  - `class ApiCaller @Inject constructor(json: Json, sessionEvents: SessionEvents) { suspend fun <T> call(block: suspend () -> T): ApiResult<T> }` — `HttpException` → lê o envelope `{"error":{code,message,details}}`; `details[].field/message` → `fieldErrors`; `code in {"unauthorized","session_revoked"}` → `notifyExpired()` + `SessionExpired`; `IOException` → `Network`; corpo ilegível → `Unknown`.
  - `class AuthInterceptor @Inject constructor(tokenProvider: TokenProvider)` adiciona `Authorization: Bearer <token>` quando houver token; `fun interface TokenProvider { fun currentToken(): String? }`.
  - `NetworkModule` (Hilt) provê `Json`, `OkHttpClient` (interceptor + `pingInterval` **não** configurado aqui), `Retrofit` com `BuildConfig.API_BASE_URL`, `HistApi`.

- [ ] **Step 1: Testes que falham** — `ApiCallerTest` (MockWebServer + Retrofit real):
  - `404 {"error":{"code":"not_found","message":"Ponto histórico não encontrado.","details":[]}}` → `Failure(Api("not_found","Ponto histórico não encontrado.", emptyMap()))`, sem evento de sessão.
  - `422` com `details:[{"field":"name","message":"curto"}]` → `fieldErrors == mapOf("name" to "curto")`.
  - `401 code=session_revoked` → `Failure(SessionExpired)` **e** `sessionEvents.expired` emite (Turbine).
  - `401 code=invalid_credentials` → `Failure(Api("invalid_credentials", …))` e **nenhum** evento de sessão.
  - servidor desligado (`shutdown()`) → `Failure(Network)`.
  - `HistApiTest`: `login("ana@ex.com","segredo123")` envia `Content-Type: application/x-www-form-urlencoded` e corpo `username=ana%40ex.com&password=segredo123`; `deleteMe(PasswordBody("x"))` envia método `DELETE` com corpo `{"password":"x"}`; com `TokenProvider { "abc" }` o header é `Bearer abc`; um `nearby` de exemplo (JSON do `docs/api.md`) desserializa com `category="igreja"` → `Category.IGREJA` após `toDomain()`.
- [ ] **Step 2:** Rodar `--tests 'br.uff.hist.core.network.*'`. Esperado: FAIL.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** Rodar. Esperado: PASS.
- [ ] **Step 5: Commit** — `Adiciona cliente da API com tratamento de erros e sessão expirada`.

---

### Task 5: Sessão e preferências (DataStore)

**Files:**
- Create: `core/session/SessionStore.kt`, `DataStoreSessionStore.kt`, `SessionModule.kt`
- Test: `core/session/DataStoreSessionStoreTest.kt`, `testutil/FakeSessionStore.kt`

**Interfaces:**
- Consumes: `User`, `ThemePreference`, `TokenProvider`.
- Produces:
  - `data class Session(val token: String, val user: User)`
  - `interface SessionStore : TokenProvider { val session: StateFlow<Session?>; val theme: Flow<ThemePreference>; val locationOnboardingDone: Flow<Boolean>; suspend fun save(session: Session); suspend fun updateUser(user: User); suspend fun replaceToken(token: String); suspend fun clear(); suspend fun setTheme(p: ThemePreference); suspend fun setLocationOnboardingDone() }`
  - `DataStoreSessionStore` (`@Singleton`): persiste token + usuário (JSON) + tema + flag de onboarding; `currentToken()` lê de um cache em memória mantido a partir do `session`. Um coletor de `SessionEvents.expired` chama `clear()` (montado em `HistApp`/módulo com um `CoroutineScope` de aplicação).
  - `FakeSessionStore` em `testutil/` para as demais tasks.

- [ ] **Step 1: Testes que falham** (DataStore em arquivo temporário, `runTest`): após `save(Session("t1", user))`, uma **nova** instância sobre o mesmo arquivo expõe `session.value?.token == "t1"` e o mesmo usuário; `currentToken() == "t1"`; `replaceToken("t2")` → `currentToken() == "t2"` e usuário preservado; `clear()` → `session.value == null` mas `theme` preservado; `theme` padrão é `SYSTEM`; `locationOnboardingDone` padrão `false`.
- [ ] **Step 2:** Rodar `--tests 'br.uff.hist.core.session.*'`. Esperado: FAIL.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** Rodar. Esperado: PASS.
- [ ] **Step 5: Commit** — `Adiciona armazenamento da sessão e preferências`.

---

### Task 6: Login, cadastro e navegação raiz

**Files:**
- Create: `auth/AuthRepository.kt`, `auth/LoginViewModel.kt`, `auth/LoginScreen.kt`, `auth/RegisterViewModel.kt`, `auth/RegisterScreen.kt`, `core/navigation/Routes.kt`, `core/navigation/HistNavHost.kt`, `core/navigation/MainScaffold.kt` (barra inferior Explorar | Perfil com telas provisórias)
- Modify: `MainActivity.kt` (aplica `HistTheme(theme)` lido do `SessionStore` e mostra `HistNavHost`)
- Test: `auth/LoginViewModelTest.kt`, `auth/RegisterViewModelTest.kt`, `core/navigation/RootDestinationTest.kt`

**Interfaces:**
- Consumes: `HistApi`, `ApiCaller`, `SessionStore`.
- Produces:
  - `class AuthRepository @Inject constructor(api, caller, store) { suspend fun login(email: String, password: String): ApiResult<Unit>; suspend fun register(name: String, lastName: String, email: String, password: String): ApiResult<Unit>; suspend fun logout(): ApiResult<Unit> }` — login/cadastro salvam a `Session`; `logout` chama `POST auth/logout` e **sempre** faz `store.clear()`.
  - Rotas `@Serializable`: `Login`, `Register`, `LocationOnboarding`, `Main`, `LandmarkDetail(val id: String)`, `Chat(val landmarkId: String)`, `SuggestLandmark`, `MyContributions`, `Settings`, `ModerationQueue`, `ModerationLandmark(val id: String)`, `ModerationCuriosity(val id: String)`.
  - `fun rootDestination(session: Session?, onboardingDone: Boolean): Any` → `Login` sem sessão; `LocationOnboarding` com sessão e sem onboarding; senão `Main`.
  - `HistNavHost`: observa `store.session`; quando vira `null` estando fora de `Login/Register`, navega para `Login` limpando a pilha.
  - `LoginUiState(email, password, isSubmitting, error: String?)`; `RegisterUiState(name, lastName, email, password, isSubmitting, fieldErrors: Map<String,String>, error: String?)`; ambos com `fun submit()`, eventos de sucesso via `StateFlow<Boolean> done`.

- [ ] **Step 1: Testes que falham** —
  - `RootDestinationTest`: `(null,false) → Login`, `(session,false) → LocationOnboarding`, `(session,true) → Main`.
  - `LoginViewModelTest` (fakes de API/store): sucesso salva sessão e `done = true`; `invalid_credentials` → `error` = mensagem do servidor, sessão continua nula (**não** é logout); `submit()` chamado duas vezes seguidas faz **uma** requisição (Review Focus 5); e-mail vazio não chama a API.
  - `RegisterViewModelTest`: senha com 7 caracteres → erro local `password` sem chamada; 73 caracteres → erro; `email_taken` (409) → `fieldErrors["email"]`; `422` com `details` vira `fieldErrors`.
  - Sessão revogada (Review Focus 2): com `FakeSessionStore` em sessão ativa, emitir `SessionEvents.notifyExpired()` → `store.session.value == null` e `rootDestination(...) == Login`.
- [ ] **Step 2:** Rodar `--tests 'br.uff.hist.auth.*' --tests 'br.uff.hist.core.navigation.*'`. Esperado: FAIL.
- [ ] **Step 3:** Implementar repositório, ViewModels, telas (`LoginContent`/`RegisterContent` sem estado, campos com `KeyboardType` adequado, senha com alternar visibilidade), rotas e `HistNavHost`.
- [ ] **Step 4:** Rodar. Esperado: PASS. `./gradlew :app:assembleDebug` OK.
- [ ] **Step 5: Commit** — `Adiciona login, cadastro e navegação principal`.

---

### Task 7: Localização e onboarding de permissão

**Files:**
- Create: `core/location/LocationProvider.kt`, `FusedLocationProvider.kt`, `LocationModule.kt`, `onboarding/LocationOnboardingScreen.kt`, `testutil/FakeLocationProvider.kt`
- Test: `core/location/LocationFixTest.kt`, `onboarding/LocationOnboardingTest.kt` (Robolectric)

**Interfaces:**
- Produces:
  - `data class LocationFix(val point: GeoPoint, val accuracyM: Float, val isMock: Boolean)`
  - `enum class LocationPriority { BALANCED, HIGH }`
  - `interface LocationProvider { fun hasPermission(): Boolean; fun updates(priority: LocationPriority, intervalMs: Long): Flow<LocationFix>; suspend fun current(priority: LocationPriority): LocationFix?; suspend fun lastKnown(): LocationFix? }`
  - `fun android.location.Location.toFix(): LocationFix` — `isMock` via `isMock` (API 31+) ou `isFromMockProvider` (API 26–30).
  - Intervalos: `EXPLORE_INTERVAL_MS = 10_000` (BALANCED), `CHAT_INTERVAL_MS = 5_000` (HIGH).
  - `FakeLocationProvider` com `emit(fix)`, `setPermission(Boolean)`, `currentResult`.
  - `LocationOnboardingScreen(onDone: () -> Unit)`: texto curto explicando o uso da localização + botão que dispara `ActivityResultContracts.RequestMultiplePermissions` (FINE + COARSE); concedida **ou** negada → `store.setLocationOnboardingDone()` e `onDone()`.

- [ ] **Step 1: Testes que falham** — `LocationFixTest` (Robolectric): `Location` com `isMock = true` (via `setMock`, SDK 31) → `isMock == true`; SDK 30 com `setIsFromMockProvider` (reflexão/shadow do Robolectric) → `true`; precisão copiada. `LocationOnboardingTest`: tocar no botão com permissão negada (Robolectric `ShadowApplication.denyPermissions`) ainda chama `onDone` e marca o onboarding.
- [ ] **Step 2:** Rodar `--tests 'br.uff.hist.core.location.*' --tests 'br.uff.hist.onboarding.*'`. Esperado: FAIL.
- [ ] **Step 3:** Implementar `FusedLocationProvider` com `FusedLocationProviderClient` (`callbackFlow` + `removeLocationUpdates` no `awaitClose`; `getCurrentLocation` com `Priority.PRIORITY_HIGH_ACCURACY`/`PRIORITY_BALANCED_POWER_ACCURACY`).
- [ ] **Step 4:** Rodar. Esperado: PASS.
- [ ] **Step 5: Commit** — `Adiciona localização (FusedLocationProviderClient) e onboarding de permissão`.

---

### Task 8: Explorar (mapa, painel e lista)

**Files:**
- Create: `explore/LandmarkRepository.kt`, `explore/ExploreViewModel.kt`, `explore/ExploreScreen.kt`, `explore/ExploreMap.kt`, `explore/LandmarkList.kt`, `res/raw/map_style_light.json`, `res/raw/map_style_dark.json`
- Modify: `core/navigation/MainScaffold.kt` (aba Explorar real; FAB "+" → `SuggestLandmark`; item → `LandmarkDetail(id)`)
- Test: `explore/ExploreViewModelTest.kt`, `explore/LandmarkListTest.kt` (Robolectric)

**Interfaces:**
- Consumes: `HistApi`, `ApiCaller`, `LocationProvider`, `distanceMeters`, constantes globais.
- Produces:
  - `class LandmarkRepository @Inject constructor(api, caller) { suspend fun nearby(center: GeoPoint, radiusM: Int, limit: Int = NEARBY_LIMIT): ApiResult<List<Landmark>>; suspend fun get(id: String): ApiResult<Landmark>; suspend fun curiosities(id: String, page: Int): ApiResult<Page<Curiosity>>; suspend fun suggestCuriosity(id: String, text: String): ApiResult<Curiosity>; suspend fun suggestLandmark(req: NewLandmarkRequest): ApiResult<Landmark> }` (o `distance_m` do servidor é ignorado de propósito; ver spec 5.3)
  - `data class LandmarkItem(val landmark: Landmark, val distanceM: Double?, val chatAvailable: Boolean)`
  - `fun toItems(landmarks: List<Landmark>, user: GeoPoint?): List<LandmarkItem>` — distância a partir do **usuário**; `chatAvailable = distanceM != null && distanceM <= CHAT_JOIN_RADIUS_M`; ordena por distância (sem usuário: mantém a ordem recebida).
  - `enum class ExploreMode { MAP, LIST }`; `enum class SheetTitle { NEAR_YOU, THIS_AREA }`
  - `data class ExploreUiState(val mode: ExploreMode, val hasPermission: Boolean, val user: GeoPoint?, val initialCamera: GeoPoint, val sheetTitle: SheetTitle, val sheetItems: List<LandmarkItem>, val markers: List<LandmarkItem>, val loading: Boolean, val error: AppError?)`
  - `ExploreViewModel`: `fun onCameraIdle(center: GeoPoint, visibleRadiusM: Double)`, `fun setMode(mode: ExploreMode)`, `fun onPermissionResult(granted: Boolean)`, `fun retry()`.
    - Com permissão: coleta `updates(BALANCED, EXPLORE_INTERVAL_MS)`; refaz `nearby(user, USER_NEARBY_RADIUS_M)` na primeira posição e sempre que o usuário andar ≥ `USER_REFETCH_DISTANCE_M` desde a última busca → `sheetItems`, `sheetTitle = NEAR_YOU`.
    - `onCameraIdle` → `nearby(center, min(ceil(visibleRadiusM), MAX_NEARBY_RADIUS_M))` → `markers` (distâncias recalculadas com a posição do usuário).
    - Sem permissão: `initialCamera = lastKnown() ?: fallback de Rio das Ostras`; `sheetTitle = THIS_AREA`; `sheetItems = markers` sem distância e com `chatAvailable = false`.
  - `ExploreScreen`: seletor `Mapa | Lista` no topo (sem busca); `ExploreMap` (maps-compose, estilo JSON conforme o tema, círculo de 200 m no usuário, marcadores com ícone da categoria, `onCameraIdle` com o raio = distância do centro ao canto da `visibleRegion`); `ModalBottomSheet`/`BottomSheetScaffold` com `SheetTitle` (`Perto de você`/`Nesta área`); `LandmarkList` (foto Coil, nome, categoria, distância, nº curiosidades, chip `Chat`); faixa com `Ative a localização para ver o que há ao seu redor e entrar nos chats.` quando sem permissão; `ErrorState` em erro.

- [ ] **Step 1: Testes que falham** (`ExploreViewModelTest` com fakes e `StandardTestDispatcher`):
  - primeira posição `(-22.5269,-41.9450)` → uma chamada `nearby(center = posição, radius = 1000)` e `sheetTitle == NEAR_YOU`;
  - posição nova a 10 m → nenhuma nova chamada; a 30 m → nova chamada;
  - itens: ponto a ~87 m → `chatAvailable = true`; ~410 m → `false`; lista ordenada por distância;
  - `onCameraIdle(center a 40 km do usuário, visibleRadiusM = 80_000.0)` → chamada com `radius = 50_000`; `sheetItems` **não** muda;
  - sem permissão e sem `lastKnown` → `initialCamera == GeoPoint(-22.5269,-41.9450)`, `sheetTitle == THIS_AREA`, todos `chatAvailable == false`;
  - Review Focus 3: `nearby` devolve `Failure(Network)` → `loading == false` e `error == Network`; `retry()` repete a chamada e limpa o erro no sucesso.
  - `LandmarkListTest`: item com `chatAvailable` mostra `Chat`; sem, mostra `410 m`.
- [ ] **Step 2:** Rodar `--tests 'br.uff.hist.explore.*'`. Esperado: FAIL.
- [ ] **Step 3:** Implementar repositório, ViewModel e telas; estilos JSON do mapa com a paleta de cada tema (ruas = surface, quadras = outline, água/parques discretos, POIs comerciais ocultos).
- [ ] **Step 4:** Rodar os testes. Esperado: PASS. `./gradlew :app:lintDebug :app:assembleDebug` OK.
- [ ] **Step 5: Commit** — `Adiciona a tela Explorar com mapa, painel e lista`.

---

### Task 9: Detalhe do ponto e curiosidades

**Files:**
- Create: `landmark/LandmarkDetailViewModel.kt`, `landmark/LandmarkDetailScreen.kt`
- Test: `landmark/LandmarkDetailViewModelTest.kt`, `landmark/LandmarkDetailContentTest.kt` (Robolectric)

**Interfaces:**
- Consumes: `LandmarkRepository`, `LocationProvider`, `distanceMeters`.
- Produces:
  - `sealed interface ChatAccess { data object Available ; data class TooFar(val missingM: Int) ; data object NoPermission ; data object Locating ; data object MockLocation }`
  - `data class LandmarkDetailUiState(val landmark: Landmark?, val distanceM: Double?, val chatAccess: ChatAccess, val curiosities: List<Curiosity>, val hasMore: Boolean, val loading: Boolean, val error: AppError?, val suggestion: SuggestionState)`
  - `data class SuggestionState(val open: Boolean, val text: String, val submitting: Boolean, val sent: Boolean, val error: String?)` — texto válido: `trim().length in 10..2000`.
  - `LandmarkDetailViewModel(savedStateHandle → id)`: `fun loadMore()`, `fun retry()`, `fun openSuggestion()`, `fun onSuggestionText(t: String)`, `fun submitSuggestion()`, `fun dismissSuggestion()`.
    - `chatAccess`: sem permissão → `NoPermission`; com permissão e ainda sem fix → `Locating`; fix com `isMock` → `MockLocation`; distância ≤ 200 → `Available`; senão `TooFar(ceil(d - 200))`. Atualiza com `updates(BALANCED, EXPLORE_INTERVAL_MS)`.
  - Tela: foto (Coil, crédito `Foto: <source.name> · <license>` quando houver `source`), título em Fraunces, `Categoria · cidade, UF · a X m`, descrição, botão de chat conforme `ChatAccess` (`Entrar no chat do local` → `Chat(id)`; `Chegue a 200 m para entrar no chat (faltam %d m)`; `Procurando sua localização…`; `Localização simulada detectada. Desative-a para entrar no chat.`; para `NoPermission`, convite para ativar), seção `Curiosidades · N` com `+ Sugerir`, estado vazio da spec, autor ou `Fonte: <source.name>`, data relativa.

- [ ] **Step 1: Testes que falham** —
  - ponto a 87 m → `Available`; a 410 m → `TooFar(210)`; fix com `isMock = true` a 50 m → `MockLocation`; sem permissão → `NoPermission`;
  - Review Focus 1: com permissão e nenhum fix emitido → `Locating` (e a UI mostra o botão desabilitado com `Procurando sua localização…`);
  - sugestão com `"curta"` → erro local, sem chamada; texto válido → uma chamada, `sent = true`, aviso `Recebido! Vai aparecer depois de aprovada.`; Review Focus 5: `submitSuggestion()` duas vezes seguidas → uma requisição;
  - `loadMore()` com `hasMore = false` não chama a API; página 2 é anexada;
  - `LandmarkDetailContentTest`: lista vazia mostra `Ninguém contou nada sobre este lugar ainda. Seja o primeiro.`; `Available` mostra o botão habilitado `Entrar no chat do local`.
- [ ] **Step 2:** Rodar `--tests 'br.uff.hist.landmark.*'`. Esperado: FAIL.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** Rodar. Esperado: PASS.
- [ ] **Step 5: Commit** — `Adiciona o detalhe do ponto com curiosidades e acesso ao chat`.

---

### Task 10: Protocolo e socket do chat

**Files:**
- Create: `chat/ChatProtocol.kt`, `chat/ChatSocket.kt`, `chat/OkHttpChatSocketFactory.kt`, `testutil/FakeChatSocketFactory.kt`
- Test: `chat/ChatProtocolTest.kt`, `chat/OkHttpChatSocketFactoryTest.kt` (MockWebServer com `withWebSocketUpgrade`)

**Interfaces:**
- Produces:
  - `data class ChatUser(val id: String, val name: String)`
  - `sealed interface ServerEvent { data class Welcome(val room: String, val landmarkName: String, val me: ChatUser, val joinRadiusM: Int, val leaveRadiusM: Int, val maxMessageLength: Int, val memberCount: Int?) ; data class Message(val id: String, val user: ChatUser, val text: String, val sentAt: Instant) ; data class Presence(val joined: Boolean, val user: ChatUser, val memberCount: Int?) ; data class Error(val code: String, val message: String) ; data object Pong }`
  - `fun parseServerEvent(raw: String): ServerEvent?` — tipo desconhecido ou JSON inválido → `null` (nunca lança).
  - `fun encodeMessage(text: String): String`, `fun encodeLocation(p: GeoPoint): String` (`{"type":"location","lat":…,"lon":…}`), `fun encodePing(): String`.
  - `sealed interface SocketEvent { data object Opened ; data class Text(val raw: String) ; data class Closed(val code: Int, val reason: String) ; data class Failure(val error: Throwable) }`
  - `interface ChatConnection { val events: Flow<SocketEvent>; fun send(text: String): Boolean; fun close(code: Int = 1000, reason: String = "") }`
  - `fun interface ChatSocketFactory { fun connect(landmarkId: String, token: String, at: GeoPoint): ChatConnection }`
  - `OkHttpChatSocketFactory @Inject constructor(client: OkHttpClient)`: URL = `API_BASE_URL` com `http→ws`/`https→wss` + `chat/{id}?token=&lat=&lon=`; `events` é um `Channel`/`callbackFlow` fechado após `Closed`/`Failure`. Close codes como constantes: `CLOSE_BAD_REQUEST = 4400`, `CLOSE_UNAUTHORIZED = 4401`, `CLOSE_OUT_OF_RANGE = 4403`, `CLOSE_NOT_FOUND = 4404`.
  - `FakeChatSocketFactory`/`FakeChatConnection`: grava `sent`, permite `serverSend(json)`, `serverClose(code)`, `fail()`, conta `connectCount`.

- [ ] **Step 1: Testes que falham** —
  - `ChatProtocolTest`: o `welcome` do `docs/api.md` → `Welcome(joinRadiusM=200, leaveRadiusM=300, maxMessageLength=1000, memberCount=null)`; o mesmo com `"member_count":3` → `memberCount=3`; `presence` `event:"leave"` → `joined=false`; `message` com `sent_at` ISO → `Instant`; `{"type":"novo"}` → `null`; `"lixo"` → `null`; `encodeLocation(GeoPoint(-22.52,-41.94))` decodifica com `type=location`, `lat=-22.52`, `lon=-41.94`.
  - `OkHttpChatSocketFactoryTest`: com base `http://host/v1/`, a requisição de upgrade tem caminho `/v1/chat/abc?token=t&lat=-22.5&lon=-41.9`; texto do servidor chega como `Text`; servidor fecha com `4403` → `Closed(4403, …)` e o fluxo termina; `send` chega ao servidor.
- [ ] **Step 2:** Rodar `--tests 'br.uff.hist.chat.ChatProtocolTest' --tests 'br.uff.hist.chat.OkHttpChatSocketFactoryTest'`. Esperado: FAIL.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** Rodar. Esperado: PASS.
- [ ] **Step 5: Commit** — `Adiciona o protocolo e a conexão WebSocket do chat`.

---

### Task 11: Sessão do chat (máquina de estados)

**Files:**
- Create: `chat/ChatSession.kt`
- Test: `chat/ChatSessionTest.kt` (tempo virtual: `runTest` + `StandardTestDispatcher`, fakes de socket, localização e sessão)

**Interfaces:**
- Consumes: `ChatSocketFactory`, `ChatConnection`, `ServerEvent`, `LocationProvider`, `SessionStore`, `SessionEvents`, `distanceMeters`.
- Produces:
  - `data class ChatConfig(val backgroundToleranceMs: Long = BACKGROUND_TOLERANCE_MS, val pingIntervalMs: Long = PING_INTERVAL_MS, val minDisplacementM: Double = LOCATION_MIN_DISPLACEMENT_M, val backoffMs: List<Long> = listOf(1_000, 2_000, 4_000, 8_000, 16_000, 30_000))`
  - `enum class EndReason { OUT_OF_RANGE, BACKGROUND_TIMEOUT, SESSION_EXPIRED, NOT_FOUND, MOCK_LOCATION, LEFT }`
  - `sealed interface ChatPhase { data object Connecting ; data object Active ; data object Paused ; data object Reconnecting ; data class Ended(val reason: EndReason) }`
  - `sealed interface ChatItem { val key: String ; data class Msg(override val key: String, val author: String, val text: String, val mine: Boolean, val sentAt: Instant) : ChatItem ; data class PresenceNote(override val key: String, val name: String, val joined: Boolean) : ChatItem }`
  - `data class ChatState(val phase: ChatPhase, val landmarkName: String?, val memberCount: Int?, val items: List<ChatItem>, val distanceM: Double?, val warnLeaving: Boolean, val joinRadiusM: Int, val leaveRadiusM: Int, val maxMessageLength: Int, val notice: String?)` (`notice` = aviso curto de erro não fatal; `consumeNotice()` limpa)
  - `enum class SendResult { SENT, BLANK, TOO_LONG, NOT_ACTIVE }`
  - `class ChatSession(landmarkId: String, landmarkPoint: GeoPoint, factory: ChatSocketFactory, location: LocationProvider, store: SessionStore, sessionEvents: SessionEvents, scope: CoroutineScope, config: ChatConfig = ChatConfig())` com `val state: StateFlow<ChatState>`, `fun start()`, `fun send(text: String): SendResult`, `fun onAppBackground()`, `fun onAppForeground()`, `fun leave()`.
- Regras (spec 5.3 "Chat"):
  - `start()`: `current(HIGH)`; `null` → espera o primeiro fix de `updates(HIGH, CHAT_INTERVAL_MS)`; fix `isMock` → `Ended(MOCK_LOCATION)` sem conectar; conecta com o token de `store.currentToken()`.
  - `Welcome` → `Active`, guarda raios/limite/`memberCount`, zera o backoff. `Presence` do próprio `me.id` com `joined = true` é ignorada; demais viram `PresenceNote` e atualizam `memberCount` quando vier. `Message` → `Msg(mine = user.id == me.id)`. `Error` → `notice` (não muda a fase).
  - Durante `Active`: a cada fix, atualiza `distanceM` e `warnLeaving = distanceM > joinRadiusM`; envia `location` quando se deslocou ≥ `minDisplacementM` desde o último envio; fix `isMock` → fecha e `Ended(MOCK_LOCATION)`. `ping` a cada `pingIntervalMs`.
  - Fechamentos: `4403` → `Ended(OUT_OF_RANGE)`; `4401` → `sessionEvents.notifyExpired()` + `Ended(SESSION_EXPIRED)`; `4404`/`4400` → `Ended(NOT_FOUND)`; qualquer outro fechamento ou `Failure` não pedido pelo app → `Reconnecting`: antes de cada tentativa, `current(HIGH)`; se distância > `joinRadiusM` → `Ended(OUT_OF_RANGE)`; senão espera `backoffMs[min(tentativa, last)]` e reconecta.
  - `onAppBackground()` (só se `Active`/`Reconnecting`): `Paused`; eventos recebidos vão para um buffer **não exibido**; agenda timer de `backgroundToleranceMs` → `close(1000)` + `Ended(BACKGROUND_TIMEOUT)` + descarta buffer. Durante `Paused` não há tentativas de reconexão.
  - `onAppForeground()` (antes do timer): cancela timer; `current(HIGH)`; distância > `leaveRadiusM` → `close(1000)` + `Ended(OUT_OF_RANGE)` + descarta buffer; senão envia `location` primeiro e depois aplica o buffer em ordem → `Active`. Se a conexão caiu durante `Paused` → segue as regras de reconexão.
  - `leave()` → `close(1000)` + `Ended(LEFT)`; em qualquer `Ended` a lista `items` é esvaziada.
  - `send`: `text.isBlank()` → `BLANK`; `text.trim().length > maxMessageLength` → `TOO_LONG`; fase ≠ `Active` → `NOT_ACTIVE`; senão envia `encodeMessage(text.trim())`.

- [ ] **Step 1: Testes que falham** (um teste por regra; nomes descritivos):
  - `connectsWithFreshFixAndToken`, `mockFixAtStartEndsWithoutConnecting`, `welcomeActivatesAndExposesRadii`, `ownJoinPresenceIsIgnored`, `presenceUpdatesMemberCountWhenPresent`, `memberCountStaysNullWithoutField`, `serverErrorBecomesNoticeWithoutEndingChat` (`rate_limited`), `warnsWhenBeyondJoinRadius` (fix a 260 m → `warnLeaving`), `sendsLocationOnlyAfterTenMeters`, `pingsEveryThirtySeconds` (avançar 90 s → 3 pings);
  - `close4403EndsOutOfRange`, `close4401ExpiresSessionAndDoesNotReconnect`, `abnormalCloseReconnectsWithBackoff` (falhas seguidas → esperas 1 s, 2 s, 4 s; `connectCount` confere), `reconnectStopsIfUserLeftJoinRadius`, `backoffResetsAfterWelcome`;
  - `backgroundBuffersWithoutShowing` (mensagem chega em `Paused` → `items` inalterado), `foregroundWithinRangeFlushesBufferAfterSendingLocation` (ordem: `location` enviado antes de o buffer aparecer), `foregroundOutOfRangeEndsAndDiscards` (fix a 320 m), `backgroundTimeoutEndsAfterFiveMinutes` (4:59 ainda `Paused`; 5:00 → `Ended(BACKGROUND_TIMEOUT)` e `close(1000)`), `noReconnectAttemptsWhilePaused`;
  - Review Focus 4: `send("   ") == BLANK`, `send("a".repeat(1001)) == TOO_LONG`, `send("👍")` com `maxMessageLength = 1000` → `SENT`, `send` antes do `welcome` → `NOT_ACTIVE`; `message_too_long` do servidor vira `notice` e a fase continua `Active`.
- [ ] **Step 2:** Rodar `--tests 'br.uff.hist.chat.ChatSessionTest'`. Esperado: FAIL.
- [ ] **Step 3:** Implementar `ChatSession` (um único `Job` de conexão por vez; estado em `MutableStateFlow`; todas as transições num só lugar).
- [ ] **Step 4:** Rodar. Esperado: PASS (todos).
- [ ] **Step 5: Commit** — `Adiciona a sessão do chat com tolerância de 5 minutos e reconexão`.

---

### Task 12: Tela do chat

**Files:**
- Create: `chat/ChatViewModel.kt`, `chat/ChatScreen.kt`
- Modify: `core/navigation/HistNavHost.kt` (rota `Chat(landmarkId)`; ao terminar, volta para `LandmarkDetail` mostrando a mensagem de encerramento em snackbar)
- Test: `chat/ChatViewModelTest.kt`, `chat/ChatContentTest.kt` (Robolectric)

**Interfaces:**
- Consumes: `ChatSession`, `LandmarkRepository.get` (para nome e coordenadas do ponto), `ProcessLifecycleOwner`.
- Produces:
  - `ChatViewModel(savedStateHandle → landmarkId, …)`: cria a `ChatSession` no `viewModelScope` após carregar o ponto; observa `ProcessLifecycleOwner.get().lifecycle` (`ON_STOP` → `onAppBackground()`, `ON_START` → `onAppForeground()`) por meio de uma abstração injetável `fun interface AppForegroundSignal { fun events(): Flow<Boolean> }` (true = primeiro plano); `onCleared()` → `session.leave()`.
  - `fun endMessage(reason: EndReason): Int?` (string res): `OUT_OF_RANGE` → `Você saiu da área do ponto. A conversa foi encerrada.`; `BACKGROUND_TIMEOUT` → `A conversa foi encerrada porque você ficou fora do app`; `MOCK_LOCATION` → texto de localização simulada da Task 9; `NOT_FOUND` → `Este ponto não está mais disponível.`; `SESSION_EXPIRED`/`LEFT` → `null`.
  - Tela (`ChatContent` sem estado): topo com nome (Fraunces) e `● N pessoa(s) aqui agora` **só se** `memberCount != null`; aviso fixo da spec; bolhas (outros à esquerda com nome do autor em destaque; minhas à direita em `primary`); `PresenceNote` centralizada (`%s entrou`/`%s saiu`); faixa `Você está se afastando (X m). A partir de 300 m você sai do chat.` quando `warnLeaving`; faixa `Reconectando…` em `Reconnecting`; campo com contador visível a partir de `maxMessageLength - 100` caracteres e botão enviar desabilitado se vazio/excedido; `notice` em snackbar.

- [ ] **Step 1: Testes que falham** — `ChatViewModelTest`: sinal de segundo plano → `session` recebe `onAppBackground()`; retorno → `onAppForeground()`; `onCleared` → `leave()`; `endMessage(OUT_OF_RANGE)` aponta para a string correta. `ChatContentTest`: com `memberCount = null` não existe texto `aqui agora`; com `3` mostra `3 pessoas aqui agora`, com `1` mostra `1 pessoa aqui agora`; `warnLeaving` com `distanceM = 260.0` mostra `Você está se afastando (260 m). A partir de 300 m você sai do chat.`; `Reconnecting` mostra `Reconectando…`; aviso efêmero sempre presente.
- [ ] **Step 2:** Rodar `--tests 'br.uff.hist.chat.ChatViewModelTest' --tests 'br.uff.hist.chat.ChatContentTest'`. Esperado: FAIL.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** Rodar. Esperado: PASS. `./gradlew :app:lintDebug :app:assembleDebug` OK.
- [ ] **Step 5: Commit** — `Adiciona a tela do chat efêmero`.

---

### Task 13: Sugerir ponto

**Files:**
- Create: `contribute/SuggestLandmarkViewModel.kt`, `contribute/SuggestLandmarkScreen.kt`
- Test: `contribute/SuggestLandmarkViewModelTest.kt`

**Interfaces:**
- Consumes: `LandmarkRepository.suggestLandmark`, `LocationProvider`, `Category`.
- Produces:
  - `data class SuggestLandmarkUiState(val pin: GeoPoint?, val name: String, val category: Category?, val description: String, val curiosity: String, val submitting: Boolean, val fieldErrors: Map<String, String>, val done: Boolean, val error: String?)`
  - `SuggestLandmarkViewModel`: `fun onCameraMoved(center: GeoPoint)`, setters dos campos, `fun submit()`. Pino inicial = `current(BALANCED) ?: lastKnown() ?: fallback`.
  - Validação local: nome `trim().length in 2..200`; categoria obrigatória; descrição ≤ 5000; curiosidade vazia **ou** `trim().length in 10..2000`. Requisição: `NewLandmarkRequest(name, latitude, longitude, description?, category = category.id, curiosity?)` (campos vazios → `null`).
  - Tela: mapa com **alfinete fixo no centro** (o mapa se move por baixo), seletor de categoria com os 9 itens (ícone + rótulo), campos, botão enviar; ao terminar, `Recebido! Sua sugestão vai aparecer no mapa depois de aprovada.` e volta ao Explorar. Sem campo de foto (spec 7).

- [ ] **Step 1: Testes que falham** — nome `"A"` → erro `name`, sem chamada; sem categoria → erro `category`; curiosidade `"curta"` → erro `curiosity`; envio válido manda `category = "edificio_historico"` quando `EDIFICIO_HISTORICO` e as coordenadas do último `onCameraMoved`; descrição em branco vai como `null`; Review Focus 5: `submit()` duas vezes → uma requisição; `422` do servidor com `details` → `fieldErrors`.
- [ ] **Step 2:** Rodar `--tests 'br.uff.hist.contribute.*'`. Esperado: FAIL.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** Rodar. Esperado: PASS.
- [ ] **Step 5: Commit** — `Adiciona o fluxo de sugerir ponto histórico`.

---

### Task 14: Perfil, minhas contribuições e configurações

**Files:**
- Create: `profile/AccountRepository.kt`, `profile/ProfileScreen.kt`, `profile/MyContributionsViewModel.kt`, `profile/MyContributionsScreen.kt`, `profile/SettingsViewModel.kt`, `profile/SettingsScreen.kt`
- Modify: `core/navigation/MainScaffold.kt` (aba Perfil real), `core/navigation/HistNavHost.kt`
- Test: `profile/MyContributionsViewModelTest.kt`, `profile/SettingsViewModelTest.kt`, `profile/ProfileContentTest.kt`

**Interfaces:**
- Consumes: `HistApi`, `ApiCaller`, `SessionStore`, `AuthRepository.logout`.
- Produces:
  - `class AccountRepository @Inject constructor(api, caller, store) { suspend fun refreshMe(): ApiResult<User>; suspend fun updateName(name: String, lastName: String): ApiResult<User>; suspend fun updateEmail(email: String, currentPassword: String): ApiResult<User>; suspend fun changePassword(current: String, new: String): ApiResult<Unit>; suspend fun deleteAccount(password: String): ApiResult<Unit>; suspend fun myLandmarks(page: Int): ApiResult<Page<Landmark>>; suspend fun myCuriosities(page: Int): ApiResult<Page<Curiosity>>; suspend fun deleteLandmark(id: String): ApiResult<Unit>; suspend fun deleteCuriosity(id: String): ApiResult<Unit> }` — `updateName/updateEmail/refreshMe` chamam `store.updateUser`; `changePassword` chama `store.replaceToken(novo token)`; `deleteAccount` chama `store.clear()` no sucesso.
  - `ProfileScreen`: nome, e-mail, itens `Minhas contribuições`, `Moderação` (**só** se `user.isModerator`), `Configurações`.
  - `MyContributionsViewModel`: abas Pontos/Curiosidades, cada item com `StatusBadge` e, se rejeitado, o motivo; exclusão permitida para ponto próprio **não aprovado** e para qualquer curiosidade própria (com confirmação).
  - `SettingsViewModel`: tema (`Claro`/`Escuro`/`Seguir o sistema` → `store.setTheme`), editar nome/sobrenome, alterar e-mail (pede senha), trocar senha (8–72), `Sair` (`AuthRepository.logout`), `Excluir conta` (diálogo de confirmação + senha). `invalid_password` → erro no campo de senha, **sem** logout.

- [ ] **Step 1: Testes que falham** —
  - `ProfileContentTest`: usuário com `roles = {USER}` não vê `Moderação`; com `{MODERATOR}` vê.
  - `MyContributionsViewModelTest`: ponto `REJECTED` com motivo `"duplicado"` expõe o motivo; ponto `APPROVED` não oferece excluir; ponto `PENDING` oferece e, ao confirmar, chama `deleteLandmark` e remove da lista.
  - `SettingsViewModelTest`: `changePassword` com sucesso substitui o token no store (`currentToken()` = token novo); `invalid_password` em `deleteAccount` → erro de campo e sessão intacta; `deleteAccount` com sucesso → `session == null`; `setTheme(DARK)` persiste; `Sair` limpa a sessão mesmo com `Failure(Network)` no `logout`.
- [ ] **Step 2:** Rodar `--tests 'br.uff.hist.profile.*'`. Esperado: FAIL.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** Rodar. Esperado: PASS.
- [ ] **Step 5: Commit** — `Adiciona perfil, minhas contribuições e configurações`.

---

### Task 15: Moderação

**Files:**
- Create: `moderation/ModerationRepository.kt`, `moderation/ModerationViewModel.kt`, `moderation/ModerationQueueScreen.kt`, `moderation/ModerationDetailScreen.kt`
- Modify: `core/navigation/HistNavHost.kt` (rotas de moderação; guarda: sem `isModerator` → volta ao Perfil)
- Test: `moderation/ModerationViewModelTest.kt`

**Interfaces:**
- Consumes: `HistApi`, `ApiCaller`, `SessionStore`.
- Produces:
  - `class ModerationRepository @Inject constructor(api, caller) { suspend fun landmarkQueue(page: Int): ApiResult<Page<Landmark>>; suspend fun curiosityQueue(page: Int): ApiResult<Page<Curiosity>>; suspend fun landmark(id: String): ApiResult<Landmark>; suspend fun curiosity(id: String): ApiResult<Curiosity>; suspend fun approveLandmark(id: String): ApiResult<Unit>; suspend fun rejectLandmark(id: String, reason: String): ApiResult<Unit>; suspend fun approveCuriosity(id: String): ApiResult<Unit>; suspend fun rejectCuriosity(id: String, reason: String): ApiResult<Unit> }` (filas com `status=pending`, ordem de chegada do servidor)
  - `ModerationViewModel`: filas paginadas (abas Pontos/Curiosidades); detalhe com `fun approve()`, `fun reject(reason: String)`; motivo válido `trim().length in 3..500`; após decisão, item sai da fila e a tela volta.
  - Telas: fila com nome/trecho, autor e data; detalhe de ponto com mini-mapa (`GoogleMap` estático com o marcador), categoria, descrição; detalhe de curiosidade com o `landmark_name`; botões `Aprovar` / `Rejeitar` (diálogo pedindo o motivo). Sem "Aprovar sem a foto" (plano separado, spec 7).

- [ ] **Step 1: Testes que falham** — motivo `"ok"` → erro local, sem chamada; motivo válido → `rejectLandmark(id, motivo.trim())` uma única vez (Review Focus 5: duplo toque); `approve()` remove o item da fila; `not_found` ao aprovar (outro moderador já decidiu) → item removido da fila e aviso, sem crash; fila vazia expõe estado vazio.
- [ ] **Step 2:** Rodar `--tests 'br.uff.hist.moderation.*'`. Esperado: FAIL.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** Rodar. Esperado: PASS.
- [ ] **Step 5: Commit** — `Adiciona as telas de moderação`.

---

### Task 16: Verificação final e build de release

**Files:**
- Modify: `README.md` (como rodar o app contra o backend local: `cp local.properties.example local.properties`, `MAPS_API_KEY`, emulador → `10.0.2.2`; aparelho físico na mesma rede → `HIST_API_BASE_URL` com o IP da máquina; como gerar o AAB)
- Create: `frontend/local.properties.example` (sem valores reais)

- [ ] **Step 1:** Rodar tudo: `./gradlew :app:lintDebug :app:testDebugUnitTest :app:assembleDebug`. Esperado: BUILD SUCCESSFUL, zero falhas, lint sem erros (avisos aceitáveis listados no resumo da task).
- [ ] **Step 2:** `./gradlew :app:bundleRelease` sem keystore configurada. Esperado: AAB gerado sem assinatura em `app/build/outputs/bundle/release/` (assinatura entra só quando `HIST_KEYSTORE_PATH` existir).
- [ ] **Step 3:** Conferir com `git status` que `Hist_PAI_v17correçao.docx`, `local.properties` e chaves não estão no índice.
- [ ] **Step 4: Commit** — `Documenta como rodar e gerar o app`.
