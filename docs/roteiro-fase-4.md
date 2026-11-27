# Roteiro de teste manual: Fase 4 (observabilidade)

Seguir uma requisição do front até o consumidor do Kafka, vendo onde o tempo é gasto. Cada passo tem uma **aposta**: responda antes de olhar, e depois confira.

## Preparação

1. Suba tudo com as ferramentas (a primeira vez baixa a imagem `grafana/otel-lgtm`, perto de 1 GB):

   ```bash
   docker compose -f docker-compose.yml -f docker-compose.tools.yml up -d --build
   ```

2. Abra o Grafana em http://localhost:3000. No menu, `Explore`, e escolha a fonte `Tempo`.
3. Suba o front (`npm start` em `web-shell` e em `web-orders`) e abra http://localhost:4200/orders.

## 1. Etapa 1: o caminho de uma requisição (traces)

1. **Aposta:** quantos serviços aparecem no trace de um pedido criado pela tela? Em que ordem?
2. Crie um pedido pela tela. Anote o `X-Correlation-Id` que ela mostra.
3. No log do `order-service` (`docker compose logs order-service`), procure a linha do pedido. Entre colchetes estão `[traceId,spanId,X-Correlation-Id]`.
4. **Aposta:** o `traceId` no log do `inventory-service` para esse pedido é o mesmo ou outro? E o `X-Correlation-Id`?
5. Confira no log do `inventory-service` (`Pedido criado: cliente=...`).
6. No Grafana, `Explore` > `Tempo` > `Search`, cole o `traceId` e abra o trace.
7. Resultado esperado:
   - Um trace só, com spans do `api-gateway`, do `order-service` e do `inventory-service`.
   - Entre o fim do span do `order-service` e o começo do consumidor, um intervalo: é o tempo que o evento ficou no outbox esperando o relay (até 500 ms) mais a viagem pelo Kafka.
8. **Aposta:** qual span é o mais longo, e por quê?

**O que prova:** o contexto do trace atravessa HTTP, banco (a linha do outbox guarda o `traceparent`) e Kafka (o relay o põe no header da mensagem). Sem a coluna `trace_headers`, o consumidor começaria um trace novo, desligado da requisição.

**Para ver na fonte:** DBeaver, banco `orders`, tabela `outbox_events`, coluna `trace_headers` da linha do pedido. Kafka UI (http://localhost:8090), tópico `orders`, mensagem do pedido, aba `Headers`: `traceparent` e `X-Correlation-Id`.

## 2. Etapa 1: o `traceparent`

1. No header `traceparent` da mensagem, o formato é `00-<traceId>-<spanId>-<flags>`.
2. **Aposta:** o `spanId` da mensagem é o de qual span do trace?
3. Confira no Grafana.

**O que prova:** o consumidor vira filho do span do `order-service` que gravou o evento, mesmo tendo sido publicado por outra thread, segundos depois.

## 3. Etapa 2: logs no Loki, ligados ao trace

Reconstrua os serviços (`docker compose -f docker-compose.yml -f docker-compose.tools.yml up -d --build`) e crie um pedido novo pela tela.

1. Grafana, `Explore`, fonte `Loki`. Em `Label filters`, `service_name` = `order-service`, e `Run query`.
2. Abra a linha `Pedido criado`. **Aposta:** que campos o log tem que a linha do console não mostra?
3. Resultado esperado: além da mensagem, `trace_id`, `span_id` e o `X-Correlation-Id` (vindo do MDC), como campos, não como texto.
4. Na linha, o link para o `Tempo` abre o trace do pedido. No caminho inverso, no trace, o botão `Related logs` lista os logs de todos os serviços daquele trace.
5. **Aposta:** filtrando pelo `X-Correlation-Id` do pedido, aparecem logs de quais serviços?

**O que prova:** log, trace e id de correlação viram uma coisa só. A pergunta "o que aconteceu com este pedido?" começa em qualquer um dos três e chega aos outros dois por clique, sem procurar texto em arquivo de log.

**Por que o console continua em texto:** o Loki recebe o log estruturado pelo OTLP, então o console fica legível para quem lê `docker compose logs`. Quem coleta log pelo console (o caminho comum do ELK) troca para JSON sem mexer no código: `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`.

## 4. Etapa 3: métricas e dashboard

Reconstrua os serviços (`docker compose -f docker-compose.yml -f docker-compose.tools.yml up -d --build`).

1. **Pull:** abra http://localhost:8081/actuator/prometheus. É o texto que um Prometheus lê de tempos em tempos. Procure `http_server_requests_seconds_bucket`. **Aposta:** o que é o rótulo `le`?
2. **Push:** o LGTM recebe as mesmas métricas por OTLP, a cada 10 s. No Grafana, `Explore` > `Prometheus`, rode `http_server_requests_milliseconds_count`. **Aposta:** por que aqui é `milliseconds` e no passo 1 é `seconds`?
3. Importe o dashboard: `Dashboards` > `New` > `Import` > envie [docs/grafana/sales-events-dashboard.json](grafana/sales-events-dashboard.json).
4. Gere tráfego: crie e cancele alguns pedidos pela tela, e liste várias vezes.
5. Leia o dashboard por blocos:
   - **RED (por serviço):** requisições por segundo, erros 5xx e latência p95 e p99.
   - **USE (por recurso):** pool de conexões (uso, espera, timeouts), threads do Tomcat, heap e pausas de GC.
   - **Mensageria:** pendentes e idade do mais antigo no outbox, lag do consumidor e mensagens no DLT.
6. **Aposta:** pare o Kafka (`docker compose stop kafka`), crie um pedido e espere 1 minuto. Quais painéis mudam? Depois, `docker compose start kafka` e veja voltarem.
7. **Aposta:** mande para o DLT a mensagem do cenário 3 do [roteiro da fase 1](roteiro-fase-1.md). Qual painel acusa?

**O que prova:** RED diz se o usuário está sofrendo; USE diz qual recurso está no limite; as métricas de mensageria mostram atraso e perda que nenhum endpoint HTTP revela, porque o pedido já foi respondido com sucesso.
