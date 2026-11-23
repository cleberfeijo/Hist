# Integração entre o app (frontend) e o backend

- **Para:** Cleber (backend)
- **De:** Luan (frontend)
- **Data:** 23/11/2026
- **Branch do app:** `feat/frontend` (código em `frontend/`)

Este documento registra o que o app Android já consome do backend, o que ainda não usa, o que já foi testado
com o backend rodando e as mudanças que combinamos para o backend. As decisões completas estão na
spec do app: `docs/superpowers/specs/2026-11-20-frontend-android-design.md` (seção 9).

## 1. Resumo

O app consome praticamente toda a API voltada ao usuário final: **23 rotas REST e o WebSocket do chat**.
Ficam de fora só as rotas de administração de usuários, a edição de ponto pelo moderador e o histórico do chat,
que hoje está desligado.

Base usada pelo app: `.../v1/`. Em debug, o padrão é `http://10.0.2.2:8080/v1/` (o `localhost` da máquina vista
pelo emulador). Esse endereço pode ser trocado em `frontend/local.properties` (`HIST_DEBUG_API_BASE_URL`).

## 2. O que o app já consome

| Área | Rotas | Onde aparece no app |
|---|---|---|
| Autenticação | `POST /auth/login`, `POST /auth/register`, `POST /auth/logout` | Login, cadastro e sair |
| Perfil | `GET /users/me`, `PATCH /users/me`, `POST /users/me/password`, `DELETE /users/me` | Perfil: editar dados, trocar senha, excluir conta |
| Minhas contribuições | `GET /users/me/landmarks`, `GET /users/me/curiosities` | Lista do que a pessoa sugeriu, com selo Pendente / Aprovado / Rejeitado |
| Explorar | `GET /landmarks/nearby`, `GET /landmarks/{id}`, `GET /landmarks/{id}/curiosities` | Mapa, lista "Perto de você" e detalhe do ponto |
| Sugestões | `POST /landmarks`, `POST /landmarks/{id}/curiosities` | Botão "+" (sugerir ponto) e "Sugerir curiosidade" |
| Exclusão | `DELETE /landmarks/{id}`, `GET /curiosities/{id}`, `DELETE /curiosities/{id}` | Apagar uma sugestão própria |
| Moderação | `GET /moderation/landmarks`, `GET /moderation/curiosities`, `POST .../{id}/approve` e `POST .../{id}/reject` dos dois | Fila de moderação (só aparece para quem é moderador) |
| Chat | WebSocket `/chat/{id}?lat=&lon=` | Chat do ponto, liberado num raio de 200 m |

Onde isso está no código:

- REST: `frontend/app/src/main/java/br/uff/hist/core/network/HistApi.kt`
- WebSocket: `frontend/app/src/main/java/br/uff/hist/chat/OkHttpChatSocketFactory.kt`
- Protocolo do chat: `frontend/app/src/main/java/br/uff/hist/chat/ChatProtocol.kt`

### Detalhes de comportamento que dependem do backend

- **Token:** sempre no header `Authorization: Bearer <token>`, inclusive no WebSocket. O token nunca vai na URL.
- **Logout forçado:** o app descarta a sessão só quando recebe `401` com `code` `unauthorized` ou `session_revoked`,
  ou um `401` sem o envelope de erro. Outros erros não deslogam ninguém.
- **Revogação no logout:** se o `POST /auth/logout` falhar com `5xx` ou `429`, o app tenta de novo. Para qualquer outra
  resposta, ele desiste e limpa a sessão local mesmo assim.
- **Fechamentos do chat:** o app trata os códigos `4400`, `4401`, `4403` e `4404` como fim da conversa, com uma mensagem para cada.
  Outros fechamentos reconectam com espera de 1, 2, 4, 8, 16 e 30 s. Antes de cada nova tentativa, o app lê a posição de novo.
- **Ping do chat:** a cada 20 s.
- **Segundo plano:** o app tolera até 5 minutos com o app em segundo plano antes de encerrar o chat (decisão nossa).
- **Moderação:** se aprovar ou rejeitar devolver `404` ou `409` (`conflict`), o app mostra "Este item já foi revisado"
  e tira o item da fila. Ver o item 6 da seção 5.
- **Categorias:** o app só oferece a lista fixa da spec (seção 5.5). Um valor desconhecido ou vazio que venha do backend
  aparece como "Outro".

## 3. O que o backend tem e o app não usa

| Rota | Motivo |
|---|---|
| `PATCH /landmarks/{id}` | Edição do ponto pelo moderador. No app, o moderador só aprova ou rejeita. |
| `GET /users`, `GET /users/{id}`, `PATCH /users/{id}`, `DELETE /users/{id}` | Administração de usuários, papéis e plano. É coisa de painel, não do app. |
| `GET /chat/{id}/messages` | Histórico do chat (premium). Está desligado no backend e responde `404` `feature_disabled`. |
| `GET /health` | Só serve para o deploy. |

## 4. O que já foi testado com o backend rodando

Ambiente: backend local via `docker compose` (api, mongo, redis) e o app num emulador Android.

**Testado de ponta a ponta no emulador:**

- Login.
- Mapa e "Perto de você": marcadores por categoria, círculo de 200 m e um ponto a 99 m com o chat liberado.

**Feito seguindo o contrato, ainda não testado no emulador:**

- Cadastro, sugerir ponto e sugerir curiosidade.
- Moderação (aprovar e rejeitar).
- Chat com duas pessoas.
- Trocar senha e excluir conta.

Os dados de teste (pontos em Rio das Ostras, uma usuária comum e sugestões pendentes) foram criados por um script
direto na API, não pelo app. Os próximos testes de ponta a ponta serão desses fluxos.

## 5. O que falta do lado do backend

Itens 1 a 5 combinados na spec (seção 9); o item 6 apareceu na revisão final do app.

### 1. Número de pessoas no chat

- O `welcome` passa a trazer `member_count`, e os eventos `presence` também.
- A presença fica no Redis (hash `hist:chat:presence:{room}`, `user_id → nº de conexões`, com TTL renovado ou limpeza no boot).
  Com vários processos, o `hub.local_count()` não enxerga as conexões dos outros.
- `join` e `leave` só são publicados nas transições 0↔1, para que a mesma pessoa em dois aparelhos conte uma vez só.
- **Sem lista de nomes**, só o número.
- *No app hoje:* o campo já é lido. Se não vier, o contador "N pessoas aqui agora" fica escondido.

### 2. Foto nas sugestões de ponto

Proposta de contrato, a confirmar antes de eu fazer a parte do app:

- `POST /landmarks/{id}/image` (`multipart/form-data`, campo `file`). O autor envia enquanto o ponto estiver pendente,
  e o moderador também pode enviar. A resposta é o `LandmarkOut` atualizado.
- JPEG, PNG ou WebP de até 5 MB, regravados com o Pillow, o que **remove o EXIF**, inclusive o GPS.
  Redimensionar para cerca de 1280 px. Guardar num volume local em dev e no S3 em produção.
- **A foto é moderada separado do ponto:** o `LandmarkOut` ganha `image_status` (`pending` | `approved` | `rejected` |
  `null` quando não há foto) e `image_rejection_reason`, em todas as rotas que devolvem `LandmarkOut`.
- `POST /moderation/landmarks/{id}/approve` passa a aceitar um corpo opcional `{"reject_image_reason": "..."}`,
  que aprova o ponto e veta a foto. Sem corpo, aprova os dois.
- Uma foto vetada não aparece em `image_url` para quem não é o autor nem moderador.
- *No app hoje:* não há envio de foto. O app já mostra `image_url` quando ela vem.

### 3. Validar as categorias

- Validar `category` contra a lista fixa (`igreja`, `museu`, `monumento`, `edificio_historico`, `praca`, `fortificacao`,
  `sitio_arqueologico`, `ferrovia`, `outro`) e responder `422` para qualquer outro valor.
- Usar os mesmos identificadores no crawler.

### 4. Curiosidade enviada junto com o ponto

- Hoje, a curiosidade enviada no `POST /landmarks` vira um item pendente independente. Se o ponto é rejeitado,
  ela continua pendente, presa a um ponto que ninguém vê.
- Proposta: ao aprovar ou rejeitar um ponto, aplicar a mesma decisão às curiosidades **pendentes do mesmo autor**
  criadas junto com ele.
- *Até lá:* o moderador vê essas curiosidades na fila de curiosidades, que já mostra o `landmark_name`.

### 5. Exclusão de conta pela web

- A Play Store exige um link onde a pessoa consiga pedir a exclusão da conta sem o app.
- Pode ser uma página simples servida pelo backend, com login, usando o mesmo `DELETE /users/me`.
- Só é necessário na etapa de publicação.

### 6. Responder `409` ao decidir um item já decidido

- Hoje, `_set_landmark` e `_set_curiosity` (`backend/app/api/v1/moderation.py`) gravam o novo status sem conferir o atual.
  Se dois moderadores decidirem o mesmo item, vale a última decisão, e um item já rejeitado pode ser aprovado depois.
- Proposta: atualizar só se o status ainda for `pending` (filtro na própria atualização). Se não for, responder
  `409` com `code` `conflict`.
- *No app hoje:* já trata `409` `conflict` (e `404`) como "Este item já foi revisado".

## 6. Como testar localmente

1. Na raiz do repositório: `docker compose up -d` (precisa do `.env`, que não vai para o git).
2. No app: copiar `frontend/local.properties.example` para `frontend/local.properties` e preencher `MAPS_API_KEY`
   (e `HIST_DEBUG_API_BASE_URL` se o backend não estiver em `localhost:8080`). Detalhes no `README.md` da raiz.
3. Rodar o app no emulador (`./gradlew installDebug` dentro de `frontend/`).
