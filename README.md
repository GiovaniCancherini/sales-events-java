# Sales Events: Java, Spring Boot, Kafka

Sistema de pedidos com dois serviços independentes, comunicando por eventos via Kafka. Mesmo domínio (vendas/pedidos) que o projeto [abi-gth-omnia-developer-evaluation-NET2025](https://github.com/GiovaniCancherini/abi-gth-omnia-developer-evaluation-NET2025), agora implementado com stack Java e arquitetura orientada a eventos.

## Arquitetura

```
                POST /orders
                     │
                     ▼
            ┌─────────────────┐        publica evento         ┌──────────────────┐
            │  order-service    │ ──────────────────────────► │      Kafka        │
            │  (REST + Postgres)│      tópico "orders"         │  (3 partições)    │
            └─────────────────┘                              └─────────┬──────────┘
                                                                        │ consome
                                                                        ▼
                                                              ┌──────────────────┐
                                                              │  order-consumer   │
                                                              │  (loga o evento)  │
                                                              └──────────────────┘
```

Dois serviços deployáveis separadamente, cada um com seu próprio `pom.xml` e `Dockerfile`. Não é um projeto Maven multi-módulo, de propósito: microsserviço de verdade não compartilha build.

## Stack

Java 17, Spring Boot 4, Spring Web, Spring Data JPA, Spring Kafka, PostgreSQL, Docker Compose, Testcontainers. Kafka roda em modo KRaft (sem Zookeeper).

## Como rodar

```bash
docker compose up --build
```

Isso sobe Postgres, Kafka (KRaft, 1 broker) e os dois serviços.

- `order-service`: http://localhost:8081
- `order-consumer`: http://localhost:8082 (só expõe health check; o trabalho dele aparece no log)

### Testar o fluxo

Antes de criar pedido, confirme que a API subiu:

```
curl http://localhost:8081/actuator/health
```

Deve responder `{"status":"UP"}`. Os comandos abaixo estão em uma linha só, com aspas duplas escapadas, e funcionam tanto no CMD do Windows quanto no Git Bash/Linux/macOS:

```
curl -X POST http://localhost:8081/orders -H "Content-Type: application/json" -d "{\"customerId\":\"cliente-1\",\"product\":\"Notebook\",\"totalAmount\":4500.00}"
```

Acompanhe o log do `order-consumer` (`docker compose logs -f order-consumer`): o evento `OrderCreatedEvent` deve aparecer processado em segundos.

Atualizar um pedido (use o `id` devolvido na criação):

```
curl -X PUT http://localhost:8081/orders/{id} -H "Content-Type: application/json" -d "{\"totalAmount\":4800.00}"
```

Listar pedidos, paginado (padrão: 20 por página, mais recentes primeiro):

```
curl "http://localhost:8081/orders?page=0&size=10"
```

Cancelar um pedido (depois disso, alterar ou cancelar de novo devolve 409):

```
curl -X POST http://localhost:8081/orders/{id}/cancel
```

### Rodar um serviço pela IDE

Suba só a infraestrutura:

```bash
docker compose up -d postgres kafka
```

Depois rode `OrderServiceApplication` ou `OrderConsumerApplication` pela IDE. Os valores padrão do `application.properties` já apontam para as portas que o compose publica no host: Postgres em `localhost:5433` (e não 5432, para não colidir com um Postgres local) e Kafka em `localhost:29092`. Quando o serviço roda dentro do compose, as variáveis `DB_HOST`, `DB_PORT` e `KAFKA_BOOTSTRAP` trocam esses valores pelos endereços da rede interna. O tópico `orders` é criado pelo `order-service`, que por isso não sobe sem o Kafka no ar. Se o consumidor subir antes dele, espera até o tópico existir, com avisos `UNKNOWN_TOPIC_OR_PARTITION` no log.

### Debug remoto (opcional)

```bash
docker compose -f docker-compose.yml -f docker-compose.debug.yml up --build
```

Sobe o mesmo ambiente, com o `order-service` aceitando um debugger remoto (JDWP) na porta 5005. No IntelliJ: `Run > Edit Configurations > Remote JVM Debug`, host `localhost`, porta `5005`.

## Testes

```bash
cd order-service && ./mvnw test
cd ../order-consumer && ./mvnw test
```

No CMD do Windows, use `mvnw` sem o `./`. Só precisa do Docker rodando: com Testcontainers, os testes sobem containers próprios em portas aleatórias (Postgres e Kafka no `order-service`, só Kafka no `order-consumer`), então não dependem do compose nem de porta livre na máquina. Hoje há o teste de contexto em cada serviço, testes de domínio do `Order` (escala do valor e regras do cancelamento) e, no consumidor, um teste de ida e volta pelo Kafka que confirma o tipo pelo header e o valor com as 2 casas.

## Decisões de arquitetura (e seus trade-offs)

**Por que Kafka em vez de REST síncrono entre os dois serviços?**
Desacopla o `order-service` de precisar saber quem consome o evento, e quantos consumidores existem. Se `order-consumer` cair, o evento fica retido no tópico até ele voltar. Numa chamada REST síncrona, isso seria uma falha imediata.

**Por que a key do Kafka é o `customerId`?**
Kafka garante ordem *dentro* de uma partição, não entre partições. Usar `customerId` como key garante que todos os eventos do mesmo cliente caem sempre na mesma partição, preservando a ordem onde ela importa, sem perder o paralelismo entre clientes diferentes.

**Por que 3 partições?**
Permite até 3 consumidores em paralelo no mesmo consumer group, sem quebrar a garantia de ordem por cliente. Número escolhido pelo tamanho do projeto, não por medição de carga real.

**Por que a criação automática de tópicos está desligada?**
O tópico `orders` tem dono: quem o declara é o `order-service`, com 3 partições (`NewTopic`). Com a criação automática ligada, que é o padrão do broker, o consumidor sobe mais rápido e cria o tópico só por assiná-lo, com o padrão de 1 partição. O `order-service` aumenta para 3 logo depois, mas o consumidor só enxerga as partições novas na próxima atualização de metadados, até 5 minutos depois, e os eventos que caem nelas ficam parados até lá. Desligada, como costuma ser em produção, a criação fica só com o dono: o consumidor espera o tópico existir (são os avisos `UNKNOWN_TOPIC_OR_PARTITION` na subida) e já entra com as 3 partições. Como o tópico só nasce na subida do `order-service`, ele usa `spring.kafka.admin.fail-fast=true`: se não conseguir criar o tópico, não sobe. Sem isso, ficaria no ar com health UP e todo POST falhando depois de 60s. No compose, os dois serviços esperam o healthcheck do Kafka.

**Por que sem schema registry (Avro)?**
O contrato dos eventos (`OrderCreatedEvent`, `OrderUpdatedEvent` e `OrderCancelledEvent`, mais os tokens do header de tipo) está duplicado manualmente entre os dois serviços como records Java. Funciona pelo tamanho do projeto; numa arquitetura maior com mais times e mais consumidores, isso viraria uma fonte real de bug de contrato quebrado. A correção certa é Avro ou Protobuf com Schema Registry, validando compatibilidade de schema no CI.

**Como o consumidor sabe o tipo do evento?**
O tópico carrega três tipos de evento (criação, atualização e cancelamento). Por padrão, o `JacksonJsonSerializer` grava no header `__TypeId__` o nome completo da classe do *produtor*, que não existe no *consumidor*, já que são serviços diferentes. Por isso os dois lados configuram `spring.json.type.mapping` com os mesmos tokens (`created`, `updated`, `cancelled`): o header leva só o token, e cada serviço o traduz para a própria classe. O consumidor desserializa direto no tipo certo, com um `@KafkaHandler` por evento, e o `ErrorHandlingDeserializer` impede que uma mensagem que não desserializa trave a partição: ela é logada e pulada. Antes, o consumidor lia texto e decidia pela presença do campo `product`, o que funcionava com dois tipos e quebraria no terceiro.

**Gap conhecido, não resolvido de propósito: dual write.**
`OrderService.create()` salva no Postgres e publica no Kafka na mesma chamada, sem garantia atômica entre os dois. Se o commit no banco for bem-sucedido mas o Kafka falhar (ou vice-versa), o estado diverge. A correção correta é o **padrão Outbox** (grava o evento numa tabela na mesma transação do banco, e um processo separado publica no Kafka a partir dessa tabela). Não implementado aqui por escopo do projeto, mas é a primeira coisa que eu mudaria numa versão de produção.

**Docker Compose em vez de Kubernetes de verdade.**
Para o tamanho deste projeto, Compose já resolve. Um manifest básico de Kubernetes (Deployment + Service) pode ser adicionado depois. O valor dele aparece em self-healing, scaling horizontal e config como código, nenhum dos quais este projeto precisa de fato pra funcionar.

## Estrutura

```
sales-events-java/
├── docker-compose.yml
├── docker-compose.debug.yml   # override opcional: debug remoto do order-service (porta 5005)
├── order-service/       # API REST + produtor Kafka + Postgres
└── order-consumer/      # consumidor Kafka
```
