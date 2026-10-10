# Delivery System — Projeto Diamante 02

Checkpoint 5 · Java Advanced · FIAP · Turma 2TDSPX

Sistema de pedidos de delivery com microsserviços (Spring Boot 4, Spring Cloud 2025.1, Spring AI com Google Gemini, RabbitMQ).

## Integrantes

| Nome                      | RM     |
| ------------------------- | ------ |
| Lucas Grillo Alcântara    | 561413 |
| Pietro Abrahamian         | 561469 |

## Demonstração

> 🎥 **Vídeo de demonstração:** [https://www.youtube.com/watch?v=EvodFL-Tqoo](https://www.youtube.com/watch?v=EvodFL-Tqoo)

## Arquitetura

| Projeto           | Porta        | Responsabilidade                                                                  |
| ----------------- | ------------ | --------------------------------------------------------------------------------- |
| `eureka-server`   | 8761         | Descoberta de serviços                                                            |
| `order-service`   | 8080         | Pratos, pedidos (lock pessimista), produtor de avaliações, assistente (Spring AI + Gemini) |
| `payment-service` | 8081 e 8082  | Pagamento simulado (falha em ~50% das chamadas), duas instâncias                  |
| `review-service`  | 8083         | Consumidor RabbitMQ, buffer em memória, persistência agendada no H2 e ranking     |
| RabbitMQ          | 5672 / 15672 | Mensageria (`docker-compose.yml`)                                                 |

Todas as chamadas entre serviços usam o nome registrado no Eureka (`http://PAYMENT-SERVICE/payments`, via
`RestTemplate` com `@LoadBalanced`), nunca `localhost`.

### Decisões principais

- **Concorrência**: `DishRepository.findByIdForUpdate` usa `@Lock(PESSIMISTIC_WRITE)` dentro de `@Transactional`
  (`OrderService.create`). Com 50 requisições simultâneas para o prato promocional (estoque 10), exatamente 10
  pedidos são confirmados e 40 recebem `409`.
- **Retry**: `PaymentClient` é um bean separado com `@Retryable` (`maxRetries`, `delay`, `multiplier`, `jitter`,
  `maxDelay`, configurados em `payment.retry.*` no `application.yml`) e `@EnableResilientMethods`. Se todas as
  tentativas falham, a transação sofre rollback (estoque intacto) e a API responde `502`.
- **Backpressure**: `POST /reviews` apenas publica no RabbitMQ (`202`). O `review-service` acumula soma e contagem por
  prato em um `ConcurrentHashMap` e grava no H2 (`ReviewSummary`) a cada 5 s com `@Scheduled`, limpando o buffer.
  `GET /reviews/ranking` lê somente do banco.
- **Topologia RabbitMQ** (explícita): `TopicExchange delivery.exchange`, fila durável `reviews.queue`, routing key
  `reviews.new` e o `Binding` entre eles; mensagens em JSON (`dishId`, `dishName`, `rating`, `comment`).
- **Assistente**: `ChatService` usa `ChatClient` construído a partir de `ChatClient.Builder`, com mensagem de sistema
  de atendente (respostas curtas, em português, recusando assuntos fora do restaurante). A cada pergunta o cardápio
  atual (nome, preço, estoque e descrição) é lido do banco e incluído no prompt.
- **CORS**: `order-service` e `review-service` liberam requisições de navegador (filtro `CorsConfig`), necessário para o front web.
- **Bônus — rate limit**: `POST /orders` aceita no máximo 20 req/s (token bucket próprio, `RateLimitFilter`); as
  excedentes recebem `429` com `{"error": "..."}`. Configurável em `delivery.rate-limit.orders-per-second`.

## Pré-requisitos

- JDK 21+ para executar o Gradle (o projeto usa *toolchain* Java 25; se não houver JDK 25 instalado, o Gradle o baixa
  automaticamente).
- Docker (apenas para o RabbitMQ). Funciona tanto com Docker Desktop quanto com Docker instalado no WSL.
- Uma chave de API do Google Gemini (apenas para o assistente): https://aistudio.google.com/apikey

## Como executar

### Opção rápida (Windows): subir tudo com um comando

No PowerShell ou no Prompt de Comando, dentro da pasta `delivery-system`:

```powershell
.\start-all.bat      # sobe RabbitMQ, Eureka, 2x payment, review e order (leva ~1 minuto)
.\stop-all.bat       # derruba tudo
```

O script compila os projetos, inicia o RabbitMQ (`docker compose up`, pelo Docker Desktop ou pelo Docker no WSL, mantendo
uma sessão WSL oculta aberta), aguarda cada serviço ficar pronto e grava os logs em `logs/<serviço>.log`. Na primeira
execução, o download das dependências e do JDK 25 pode demorar alguns minutos.

> Os comandos `bootRun` do Gradle mostram `80% EXECUTING` o tempo todo enquanto o serviço está rodando: isso é normal e
> **não** significa que travou. Confirme em http://localhost:8761.

### Opção manual: um terminal por serviço


Abra um terminal para cada comando, na pasta `delivery-system`. No PowerShell use `.\gradlew.bat`; no Git Bash ou Linux/WSL use `./gradlew`.
O Java/Gradle roda no Windows (PowerShell); o WSL é usado apenas para o `docker compose up`.

```bash
# 1. RabbitMQ (painel em http://localhost:15672, usuário/senha: guest/guest)
docker compose up

# 2. Eureka (painel em http://localhost:8761)
./gradlew :eureka-server:bootRun

# 3. Payment service — duas instâncias
./gradlew :payment-service:bootRun                                  # porta 8081
./gradlew :payment-service:bootRun --args='--server.port=8082'      # porta 8082

# 4. Review service
./gradlew :review-service:bootRun

# 5. Order service
./gradlew :order-service:bootRun
```

### Assistente de IA (Google Gemini) e chave de API

O assistente usa o Gemini via Spring AI (`spring-ai-starter-model-google-genai`). A chave **nunca** vai para o
repositório: fica no arquivo `.env` da raiz (ignorado pelo git), lido automaticamente pelo `order-service`.

1. Gere uma chave em https://aistudio.google.com/apikey.
2. Se o `.env` não existir, copie o modelo: `cp .env.example .env`.
3. Preencha o valor, sem aspas e sem espaços:

```properties
GEMINI_API_KEY=sua_chave_aqui
# opcional (padrão: gemini-flash-lite-latest)
# GEMINI_MODEL=gemini-flash-lite-latest
```

Reinicie o `order-service` após alterar o `.env`. Também é possível usar variáveis de ambiente
(`$env:GEMINI_API_KEY = "..."` no PowerShell), que têm a mesma função.

Sem uma chave válida, `POST /assistant` responde `502 {"error": "Assistant is currently unavailable"}` e o motivo
(por exemplo `API key not valid`) aparece no log do `order-service`; os demais endpoints funcionam normalmente.

### Docker no WSL

Com o Docker instalado dentro do WSL, execute `docker compose up` **em um terminal WSL que permaneça aberto** (sem
`-d`): o WSL encerra a distribuição quando não há sessão ativa, o que derruba o Docker e o RabbitMQ. As portas 5672 e
15672 ficam acessíveis em `localhost` pelo Windows.

```powershell
wsl -e bash -lc "cd /mnt/c/caminho/para/delivery-system && docker compose up"
```

### Outras variáveis de ambiente (opcionais)

`EUREKA_URL` (padrão `http://localhost:8761/eureka`), `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USER`,
`RABBITMQ_PASSWORD`.

## Aplicativo React Native (front-end)

O aplicativo de referência é o [diamante-delivery](https://github.com/joaocarloslima/diamante-delivery), do professor,
que consome esta API. Para rodá-lo foram necessárias as alterações abaixo **apenas no front-end** (nada foi alterado em
`src/` nem em `App.js`; o back-end segue exatamente o contrato do enunciado).

### Alterações feitas no front

- **Problema original:** o `package.json` misturava versões incompatíveis (`expo ^44`, `react 19`,
  `react-native ^0.72` e pacotes do SDK 57), e por isso as dependências quebravam.
- **Correção das dependências:**
  - O projeto foi atualizado para o **Expo SDK 57** (`expo ~57.0.27`, versão estável atual).
  - `node_modules` e `package-lock.json` foram apagados e tudo foi reinstalado do zero.
  - `npx expo install` alinhou `react-native`, `react`, `react-dom`, `react-native-web`,
    `react-native-safe-area-context` e `expo-status-bar` às versões compatíveis com o SDK 57.
  - O `expo install` também adicionou o plugin `expo-status-bar` no `app.json`.
- **Verificação:** `npx expo-doctor` passou nas 21 verificações e o build web (`expo export --platform web`) compilou
  sem erros.
- **Porta do front (19006):** a porta padrão do Expo (8081) é a mesma da primeira instância do `payment-service`.
  Para evitar o conflito, o front foi configurado para a porta **19006** de duas formas:
  - arquivo `.env` na raiz do front com `RCT_METRO_PORT=19006` (com ele, `npx expo start` já sobe na 19006);
  - scripts do `package.json` com `--port 19006`: `start`, `web`, `android` e `ios`.

### Como rodar o front

Requisitos: Node.js e npm.

```bash
npm install
npx expo start        # ou: npm run web (abre direto no navegador)
```

Acesse em **http://localhost:19006** (com o back-end já no ar, ver "Como executar").

### Integração com o back-end

- O front deve apontar para o `order-service` em **http://localhost:8080** (a URL base fica em `src/api.js` do front;
  confira se ela corresponde a essa porta).
- Como o app roda no navegador em outra origem (`localhost:19006`), o back-end libera **CORS** no `order-service` e no
  `review-service`. Sem isso o navegador bloqueia as chamadas (`No 'Access-Control-Allow-Origin' header`). Para restringir
  as origens, defina `delivery.cors.allowed-origin-patterns` no `application.yml`.
- Em emulador Android, o `localhost` do computador é acessado por `10.0.2.2`; em celular físico, use o IP do computador
  na rede local.

### Observações

- O `npm install` avisa de 22 vulnerabilidades em dependências do front. Elas não foram tratadas, porque
  `npm audit fix --force` pode quebrar o projeto.
- O front foi testado apenas no build web; não foi testado em celular ou emulador.

## Endpoints

### Order Service — `:8080`

| Método | Endpoint       | Corpo                                            | Respostas                                           |
| ------ | -------------- | ------------------------------------------------ | --------------------------------------------------- |
| GET    | `/dishes`      | —                                                | `200`                                               |
| GET    | `/dishes/{id}` | —                                                | `200`, `404`                                        |
| POST   | `/orders`      | `{"dishId": 1, "quantity": 2}`                   | `201`, `400`, `404`, `409`, `429` (rate limit), `502` |
| GET    | `/orders/{id}` | —                                                | `200`, `404`                                        |
| POST   | `/reviews`     | `{"dishId": 1, "rating": 5, "comment": "Great"}` | `202` (sem corpo), `400`, `404`                     |
| POST   | `/assistant`   | `{"question": "Tem opção vegana?"}`              | `200` `{"answer": "..."}`                           |

Erros seguem o formato `{"error": "Dish out of stock"}`.

### Review Service — `:8083`

`GET /reviews/ranking` → `[{"dishId": 1, "dishName": "House Burger", "average": 4.6, "count": 128}]`

### Payment Service — `:8081` / `:8082` (interno)

`POST /payments` `{"amount": 79.80}` → `200 {"status": "APPROVED", "instance": 8081}` ou `500` (falha simulada).
O log de cada instância informa a porta que atendeu à requisição, tornando visível a alternância do balanceamento.

## Testes

```bash
./gradlew test
```

| Módulo            | O que é coberto                                                                                              |
| ----------------- | ------------------------------------------------------------------------------------------------------------ |
| `order-service`   | API de pratos/pedidos (201/400/404/409/502), **50 requisições simultâneas → exatamente 10 pedidos**, retry real com servidor de pagamento simulado (backoff, esgotamento → `502` e estoque intacto), topologia e JSON do RabbitMQ, `POST /reviews` (202/400/404), `ChatService` (mensagem de sistema + cardápio no prompt), `/assistant`, token bucket e `429` |
| `payment-service` | Resposta `APPROVED` com a porta da instância e falha simulada `500`                                          |
| `review-service`  | Buffer concorrente (sem perda durante `drain`), flush no H2, acúmulo entre flushes, ranking ordenado pela média e endpoint |
| `eureka-server`   | Contexto sobe e expõe o registro                                                                             |

Os testes não dependem de Docker, RabbitMQ nem de chave de IA (o broker, o provedor de IA e o serviço de pagamento
são simulados/stubados).
