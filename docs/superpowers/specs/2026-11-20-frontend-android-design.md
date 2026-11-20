# Hist — Design do app Android (frontend)

- **Data:** 2026-11-20
- **Autores:** Luan Freire (frontend), Cleber Teixeira (backend)
- **Branch:** `feat/frontend` (a partir de `refactor/backend`)
- **Status:** aprovado em 2026-11-20

## 1. Intenção e porquê

O Hist é o TCC de Ciência da Computação (UFF Rio das Ostras): um app Android para descobrir o
patrimônio histórico urbano por geolocalização. O diferencial, e a hipótese do trabalho, é a combinação de três coisas:

1. ver os pontos históricos ao redor;
2. um **chat efêmero** por ponto, acessível só a quem está a até 200 m dele, que some ao sair;
3. **curadoria colaborativa moderada**: usuários sugerem pontos e curiosidades, e um moderador aprova.

O backend (FastAPI + MongoDB + Redis) está pronto e documentado em [`docs/api.md`](../../api.md).
Este documento descreve o app Android, que será feito **do zero** (não existe código anterior).

O app será avaliado por usuários testadores com um questionário baseado na ISO/IEC 25010
(funcionalidade, desempenho, **usabilidade**, confiabilidade), em escala Likert. Por isso o app
precisa ser **simples, minimalista, moderno e intuitivo**. Esse é o critério de desempate de todas as decisões.

Cronograma relevante: frontend em nov–dez/2026, hospedagem na AWS e publicação na Play Store em dez/2026.

## 2. Objetivos, não-objetivos e anti-objetivos

**Objetivos**
- Cobrir todos os fluxos do backend que fazem sentido para o usuário final e para o moderador.
- Demonstrar com clareza os três eixos do TCC (descoberta, chat efêmero por proximidade, curadoria).
- Estar em condição de publicação na Play Store (inclui exclusão de conta dentro do app).

**Não-objetivos (fora desta versão)**
- iOS. O app é **somente Android** por enquanto.
- Funcionamento em segundo plano: notificações de proximidade, geofencing com app fechado,
  permissão de localização "o tempo todo". O app só trabalha **aberto** (localização "durante o uso").
- Busca por nome no acervo inteiro (explorar à distância é feito arrastando o mapa).
- Gestão de usuários pelo app (promover moderador etc.): continua pelo script `create_admin` e Swagger.
- Histórico do chat / plano premium (o backend tem a estrutura, mas desligada).
- Modo visitante sem login (o backend exige token em todas as rotas de conteúdo).

**Anti-objetivos (seria fracasso mesmo funcionando)**
- Tela poluída, cheia de abas e botões que quase nunca são usados.
- Chat que pareça um chat comum: o usuário precisa entender que **só existe enquanto ele estiver no local**.
- Expor uma lista de quem está no chat (decisão de privacidade, ver 5.4).

## 3. Restrições

- **Tecnologias do documento do TCC1:** Kotlin, Jetpack Compose, FusedLocationProviderClient, Google Maps SDK.
- **Backend existente:** contrato em `docs/api.md` (REST `/v1`, JWT Bearer, WebSocket do chat).
  O app não deve depender de nada além disso, exceto as mudanças combinadas na seção 9.
- **Token sem refresh:** o JWT vale 7 dias. Qualquer `401` (ou fechamento `4401` no WebSocket) → descartar o token e voltar ao login.
- **Regras de raio são do servidor:** entrar no chat a até `join_radius_m` (200 m); a conexão cai acima de `leave_radius_m` (300 m).
  O app lê esses valores do `welcome` em vez de fixá-los no código.
- **Google Maps:** exige chave de API num projeto Google Cloud com faturamento ativo (o uso do Maps SDK no Android é gratuito).
  O Luan vai providenciar. Até lá, a chave fica em `local.properties` (fora do git).
- **Idioma:** só pt-BR, com todos os textos em `strings.xml`.
- **Código e repositório:** o app vive em `frontend/` na raiz do repositório, assim como o backend vive em `backend/` (o README hoje cita `android/` e deve ser atualizado).

## 4. Abordagens consideradas

| | Abordagem | Trade-off |
|---|---|---|
| **A (escolhida)** | **Módulo único**, organizado por funcionalidade, MVVM + Hilt + Retrofit/OkHttp | Padrão atual recomendado pelo Google; rápido de montar; fácil de explicar na banca. Menos "cerimônia" arquitetural. |
| B | Multi-módulo + Clean Architecture (use cases, `core:*`, `feature:*`) | Diagrama bonito, mas é muita estrutura para um app deste tamanho, feito por uma pessoa com prazo em dezembro. |
| C | Igual ao A, com Ktor no lugar de Retrofit/OkHttp | Só se paga se houver Kotlin Multiplatform/iOS, que está fora de escopo. `docs/api.md` já é escrito pensando em Retrofit/OkHttp. |

Também considerado para o mapa: **MapLibre + OpenStreetMap** (sem chave nem cartão). Descartado para manter o
Google Maps SDK, previsto no documento do TCC.

## 5. O que foi decidido (produto e UX)

### 5.1 Identidade visual

- **Tema claro "Patrimônio" (padrão):** fundo tom de papel (`#F6F1E9`), superfícies `#FBF8F3`, texto `#2A211B`,
  texto secundário `#857565`, linhas `#E6DCCD`, destaque **terracota `#B4532A`**.
  Títulos com serifa (**Fraunces**), texto corrido em **Inter**.
- **Tema escuro "Noturno":** fundo `#0F1115`, superfícies `#1C2029`, texto `#F2F4F7`, secundário `#8B93A3`,
  linhas `#2A2F3A`, destaque **âmbar `#F5B544`** (texto sobre o âmbar: `#1A1305`).
  *Decisão do Claude:* o modo escuro mantém a tipografia do tema claro (Fraunces + Inter), para preservar a identidade.
- Tema configurável pelo usuário: **Claro / Escuro / Seguir o sistema** (padrão: seguir o sistema).
- O mapa do Google recebe um estilo JSON para cada tema, com as cores da paleta correspondente.
- Cantos arredondados (~12 dp em cartões, 22 dp no topo do bottom sheet). Visual "Material 3 personalizado", não o Material padrão.

### 5.2 Navegação

Duas abas na barra inferior: **Explorar** e **Perfil**.

```
┌──────────────────────────┐
│  [ Mapa | Lista ]        │  ← seletor no topo (sem barra de busca)
│                          │
│        (mapa)        [+] │  ← botão flutuante: sugerir ponto
│ ╭──────────────────────╮ │
│ │ Perto de você        │ │  ← bottom sheet arrastável
│ │ ▢ Igreja Matriz 87 m [Chat]
│ │ ▢ Casa de Cultura 142 m [Chat]
│ │ ▢ Marco 410 m        │ │
│ ╰──────────────────────╯ │
│  Explorar   |   Perfil   │
└──────────────────────────┘
```

### 5.3 Telas e fluxos

**Entrada**
- Abre em **Login** (e-mail e senha) com link para **Cadastro** (nome, sobrenome, e-mail, senha 8–72).
  Se já houver token salvo, vai direto ao Explorar.
- Após o primeiro login: tela curta explicando por que o app usa a localização → pedido da permissão
  "durante o uso" (precisa e aproximada).
- **Permissão negada:** o mapa abre centralizado na última posição conhecida ou, sem ela, em Rio das Ostras,
  com um aviso discreto: "Ative a localização para ver o que há ao seu redor e entrar nos chats." O chat fica indisponível.

**Explorar: Mapa**
- Mapa com a posição do usuário, um círculo de 200 m ao redor dele e marcadores dos pontos aprovados.
- O usuário pode arrastar e dar zoom para **explorar qualquer lugar**. Ao parar a câmera, o app busca
  `/landmarks/nearby` centrado no meio da tela, com o raio da área visível (limitado a 50 km pelo backend).
- **Bottom sheet "Perto de você":** sempre baseado na **posição do usuário**, não na câmera. Usa uma segunda busca
  `/landmarks/nearby` centrada no usuário (raio de 1 km, atualizada quando ele se desloca ~25 m), ordenada por distância.
  Itens dentro de 200 m mostram o chip **Chat**; os demais mostram a distância. Assim, arrastar o mapa nunca muda
  o "Perto de você". *Decisão do Claude.*
  - A distância e o chip dos **marcadores do mapa** (busca da câmera) são recalculados no aparelho a partir da posição do usuário,
    porque o `distance_m` e o `chat_available` do backend se referem ao ponto consultado (o centro da câmera).
  - **Sem permissão de localização:** o sheet passa a se chamar "Nesta área", lista os pontos da câmera sem distância e sem chip de chat.
- Marcadores com **ícone por categoria** (ver 5.5).
- Tocar num marcador ou num item → **Detalhe do ponto**.

**Explorar: Lista**
- Os mesmos pontos do sheet em lista cheia (foto, nome, categoria, distância, nº de curiosidades, chip Chat).

**Detalhe do ponto**
- Foto no topo (com crédito/licença quando vier de `source`), nome (serifa), categoria · cidade · distância, descrição.
- **Botão do chat:**
  - perto (≤ 200 m): "Entrar no chat do local", em destaque;
  - longe: desabilitado, com "Chegue a 200 m para entrar no chat (faltam X m)";
  - sem permissão de localização: desabilitado, com convite para ativar.
- **Curiosidades** paginadas (mais recentes primeiro), com autor ou fonte e data relativa.
  "+ Sugerir" abre um campo de texto (10–2000 caracteres). Pode ser feito **de qualquer distância**.
  Após enviar: "Recebido! Vai aparecer depois de aprovada."
- Estado vazio: "Ninguém contou nada sobre este lugar ainda. Seja o primeiro."

**Chat**
- Cabeçalho: nome do ponto e "● N pessoas aqui agora" (depende da seção 9, item 1; sem ele, o contador não aparece).
- Aviso fixo: "As mensagens só existem enquanto você estiver aqui. Ao sair do local, a conversa some."
- Mensagens dos outros à esquerda, com o **nome do autor** em cima; as minhas à direita, em terracota.
- Eventos de presença no meio da conversa: "Bruno entrou" / "Carla saiu". O evento de entrada do próprio usuário é ignorado.
- **Alerta de afastamento:** acima de `join_radius_m`, aparece "Você está se afastando (X m). A partir de 300 m você sai do chat."
- **Ao sair** (por distância, por voltar ou por tempo fora do app): a conversa é apagada da memória. Quando a saída é por distância (`4403`),
  aparece "Você saiu da área do ponto. A conversa foi encerrada." e o app volta ao detalhe.
- **App em segundo plano (tela bloqueada, troca de app): tolerância de 5 minutos.** *Decisão do Luan e do Cleber.*
  - A conexão continua aberta (sem serviço em primeiro plano). As mensagens e os eventos que chegam ficam **guardados, sem exibição**.
  - **Ao voltar**, o app obtém uma posição nova e a envia (`location`) **antes** de mostrar qualquer coisa. Se estiver dentro de
    `leave_radius_m`, mostra o que foi guardado e segue; se não, encerra o chat e descarta o que foi guardado
    (mesma mensagem de saída por distância).
  - **Passados 5 minutos fora**, o app fecha a conexão por conta própria e descarta a conversa. Ao voltar, mostra "A conversa foi encerrada
    porque você ficou fora do app" e volta ao detalhe.
  - Os 5 minutos são um **teto, não uma garantia**: o Android (Doze, economia de bateria do fabricante) pode cortar a rede antes.
    Nesse caso vale a reconexão abaixo. **Limitação conhecida, a registrar no TCC.**
  - O valor fica numa constante, fácil de ajustar depois dos testes.
- **Queda de conexão** (rede instável, fechamento `1001` por reinício do servidor, conexão cortada pelo Android): o app mostra
  "Reconectando…" e tenta de novo automaticamente, com espera crescente (1 s, 2 s, 4 s… até ~30 s), enquanto a tela do chat estiver
  aberta e o usuário a até `join_radius_m`. Mensagens trocadas durante a queda são perdidas (coerente com o chat efêmero).
  Fechamentos `4401`, `4403` e `4404` **não** são reconectados.
- Campo de mensagem com contador quando passar de ~900 caracteres (limite de `max_message_length`).
- Erros que não fecham a conexão (`rate_limited`, `message_too_long`…) aparecem como aviso curto (snackbar).
- **Localização falsa (mock location):** se o Android indicar que a localização é simulada, o app não permite entrar no chat.
  *Decisão do Claude*, baseada na limitação registrada em `docs/api.md`.

**Sugerir ponto (botão +)**
- Tela com um mapa e um **alfinete fixo no centro**, começando na posição atual. O usuário arrasta o mapa para ajustar.
- Campos: **nome** (obrigatório, 2–200), **categoria** (seleção na lista fixa da 5.5, obrigatória), descrição,
  primeira curiosidade (opcional, 10–2000).
- **Foto: desta versão somente quando a seção 9, item 3 existir no backend.** Até lá, sem campo de foto.
  Quando existir: escolher da galeria ou tirar na hora, com envio após criar o ponto.
- Após enviar: "Recebido! Sua sugestão vai aparecer no mapa depois de aprovada." → volta ao Explorar.

**Perfil**
- Nome e e-mail.
- **Minhas contribuições:** duas seções (Pontos, Curiosidades), cada item com selo **Pendente / Aprovado / Rejeitado**.
  Rejeitado mostra o motivo. Quando a foto for vetada e o ponto aprovado, mostrar "Publicado sem a foto: <motivo>".
  O autor pode excluir um ponto próprio ainda não aprovado e uma curiosidade própria.
- **Moderação** (só aparece para `moderator`/`admin`): duas filas, Pontos e Curiosidades (ordem de chegada).
  Tocar abre o conteúdo completo (pontos com um mini-mapa da localização).
  - Curiosidade: **Aprovar** / **Rejeitar** (motivo obrigatório, 3–500).
  - Ponto sem foto: **Aprovar** / **Rejeitar**.
  - Ponto com foto: **Aprovar tudo** / **Aprovar sem a foto** (motivo do veto) / **Rejeitar**.
    *(Faz parte do plano separado da foto; ver seção 7.)*
- **Configurações:** tema, editar nome e sobrenome, alterar e-mail (pede a senha), trocar senha
  (o backend devolve um token novo, que substitui o salvo), **Sair** (`POST /auth/logout`) e
  **Excluir conta** (confirmação + senha, `DELETE /users/me`).

### 5.4 Privacidade no chat

- **Não existe lista de quem está na sala.** Só o **número** de pessoas.
- Os nomes aparecem apenas nos eventos de entrada e saída e em cima de cada mensagem (o backend já envia assim).

### 5.5 Categorias (lista fixa)

*Decisão do Luan e do Cleber.* O app só oferece estas categorias. O valor gravado no backend é o identificador;
o rótulo e o ícone ficam no app.

| Identificador | Rótulo |
|---|---|
| `igreja` | Igreja / templo |
| `museu` | Museu |
| `monumento` | Monumento / marco |
| `edificio_historico` | Casa / edifício histórico |
| `praca` | Praça / largo |
| `fortificacao` | Fortificação |
| `sitio_arqueologico` | Sítio arqueológico |
| `ferrovia` | Estação / ferrovia |
| `outro` | Outro |

Valores desconhecidos ou vazios vindos do backend (dados antigos, crawler) são exibidos como **Outro**.

## 6. Decisões técnicas

- **Linguagem e UI:** Kotlin + Jetpack Compose + Material 3 com tema próprio (5.1).
- **Arquitetura:** módulo único `:app`, MVVM, **pacotes por funcionalidade**:
  `core/` (tema, rede, sessão, localização, componentes compartilhados), `auth/`, `explore/`, `landmark/`, `chat/`,
  `contribute/` (sugerir ponto), `profile/`, `moderation/`. Cada tela: `Screen` (Compose) + `ViewModel` com um
  `UiState` imutável exposto por `StateFlow`. Repositórios entre os ViewModels e a API.
- **Bibliotecas:** Hilt (injeção de dependência); Retrofit + OkHttp (REST e **WebSocket**); kotlinx.serialization;
  Navigation Compose (rotas tipadas); DataStore (token, tema); Coil (imagens); maps-compose (Google Maps);
  play-services-location (**FusedLocationProviderClient**).
- **Localização:**
  - Explorar: prioridade balanceada, atualização a cada ~10 s, somente com a tela visível (respeitando o ciclo de vida).
  - Chat aberto: alta precisão, atualização a cada ~5 s. Envia `{"type":"location"}` ao servidor quando houver
    deslocamento relevante (~10 m) e `ping` a cada ~30 s.
  - Detecção de localização simulada: `Location.isMock` (API 31+) / `isFromMockProvider` (API 26–30).
- **Onde o chat vive:** a sessão do chat (WebSocket, buffer, timer de 5 min, reconexão) fica num componente próprio
  (`ChatSession`) com escopo da tela do chat no grafo de navegação, e não no ViewModel puro. Assim ela sobrevive à ida
  para segundo plano. Ela é encerrada ao sair da tela, ao estourar os 5 minutos ou ao ser fechada pelo servidor.
  Escuta o ciclo de vida do processo (`ProcessLifecycleOwner`) para saber quando o app saiu ou voltou.
- **Rede:**
  - Um interceptor adiciona o `Bearer`. Um tratador central converte o envelope `{"error":{code,message,details}}`
    em erros tipados. Só os códigos `unauthorized` e `session_revoked` (HTTP 401 ou fechamento 4401) causam logout local
    e volta ao login. `invalid_credentials` (login errado) e `invalid_password` (senha atual errada) são erros de formulário.
  - O login usa `form-urlencoded` (`username`/`password`). `DELETE /users/me` com corpo usa `@HTTP(hasBody = true)`.
  - A URL base vem do `BuildConfig`: debug aponta para `http://10.0.2.2:8080/v1` (emulador → máquina), com
    tráfego HTTP liberado **apenas no build de debug**; release aponta para a URL de produção (AWS, a definir).
- **Versões do Android:** `minSdk 26` (Android 8), `targetSdk` igual ao exigido pela Play Store na data da publicação.
- **Fontes:** Fraunces e Inter empacotadas no app (licença OFL), não baixadas em tempo de execução.
- **Testes:**
  - unitários dos ViewModels e repositórios com fakes;
  - testes da camada de rede com `MockWebServer` (incluindo o protocolo do WebSocket: welcome, message, presence, error e fechamentos `4401`/`4403`/`4404`);
  - alguns testes de UI em Compose para os fluxos críticos (login, entrar e sair do chat).
- **CI:** um workflow do GitHub Actions para `frontend/` (lint, testes unitários, `assembleDebug`), no mesmo espírito do `backend.yml`.

## 7. Escopo deste plano e o backend atual

*Decisões do Claude.*

- **Este plano só constrói o app** (`frontend/`) e consome o backend. As mudanças da seção 9 são feitas pelo Cleber.
- **Contador de pessoas:** o app já trata o campo `member_count`. Se ele não vier no `welcome`, o contador fica oculto.
  Não depende de nenhuma configuração extra.
- **Foto nas sugestões e veto da foto:** ficam **fora deste plano**. Viram um plano curto separado quando o contrato
  da seção 9, item 2, existir no backend. Até lá, o formulário não tem campo de foto e a moderação de pontos tem só Aprovar/Rejeitar.
  O app já exibe `image_url` normalmente (pontos do crawler têm foto).
- **Publicação na Play Store fica fora deste plano.** Ele termina num **AAB de release assinável** (configuração de
  assinatura lendo de `local.properties`/variáveis de ambiente, nada versionado), com a exclusão de conta dentro do app.
  Ficam para a etapa de publicação do cronograma: ficha da loja, política de privacidade, formulário de
  Segurança dos Dados (localização precisa, nome, e-mail, conteúdo do usuário), URL de produção e o
  **link web para pedir a exclusão da conta**, que a Play Store exige além da exclusão no app (ver seção 9, item 4).

## 8. Fica a critério de quem constrói

- Ícones (Material Symbols ou similares, com traço consistente) e o ícone do app.
- Animações e transições (discretas).
- Paginação/rolagem infinita das listas.
- Ordem de implementação dentro do plano, desde que login → explorar → detalhe → chat venha antes de perfil e moderação.
- Textos exatos de mensagens de erro, desde que curtos, em pt-BR e sem detalhes técnicos.
- Estratégia de cache em memória (ex.: não refazer a busca do `nearby` se a câmera mal se mexeu).

## 9. Mudanças combinadas com o backend (Cleber)

1. **Contagem de pessoas no chat**
   - O `welcome` passa a trazer `member_count`, e os eventos `presence` também.
   - A presença fica no Redis (hash `hist:chat:presence:{room}`, `user_id → nº de conexões`, com TTL renovado ou limpeza no boot),
     porque com vários processos o `hub.local_count()` não enxerga as outras conexões.
   - `join`/`leave` só são publicados nas transições 0↔1, para que a mesma pessoa em dois aparelhos conte uma vez só.
   - **Sem lista de nomes.**
2. **Foto nas sugestões de ponto** (proposta de contrato, a confirmar com o Cleber antes do plano da foto)
   - `POST /landmarks/{id}/image` (`multipart/form-data`, campo `file`): o autor envia enquanto o ponto estiver pendente;
     o moderador também pode enviar. Resposta: o `LandmarkOut` atualizado.
   - JPEG, PNG ou WebP de até 5 MB, regravados com o Pillow, o que **remove o EXIF**, inclusive o GPS.
   - Redimensionamento para ~1280 px. Armazenamento em volume local (dev) e S3 (produção).
   - **Moderação separada da foto:** `LandmarkOut` ganha `image_status` (`pending` | `approved` | `rejected` | `null` quando não há foto)
     e `image_rejection_reason`. Isso vale em todas as rotas que devolvem `LandmarkOut` (`/users/me/landmarks`, filas de moderação).
   - `POST /moderation/landmarks/{id}/approve` aceita um corpo opcional `{"reject_image_reason": "..."}`: aprova o ponto e veta a foto.
     Sem corpo, aprova o ponto e a foto juntos.
   - Foto vetada não aparece em `image_url` para quem não é o autor nem moderador.
3. **Categorias fixas:** validar `category` contra a lista da seção 5.5 (`422` para valor fora dela) e usar os mesmos
   identificadores no crawler.
4. **Curiosidade enviada junto com o ponto:** hoje ela vira um item pendente independente. Se o ponto é rejeitado, ela fica
   pendente e sem ponto visível. Proposta: ao aprovar ou rejeitar um ponto, aplicar a mesma decisão às curiosidades
   **pendentes do mesmo autor** criadas junto com ele. Até isso existir, o moderador as vê na fila de curiosidades
   (que já mostra o `landmark_name`).
5. **Exclusão de conta pela web** (para a etapa de publicação): a Play Store exige um link onde o usuário possa pedir a exclusão
   sem o app. Pode ser uma página simples servida pelo backend, usando o mesmo `DELETE /users/me` com login.

~~Busca por nome~~: retirada desta versão.
