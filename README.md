# Sales Events — Java, Spring Boot, Kafka

Sistema de pedidos com dois serviços independentes, comunicando por eventos via Kafka. Mesmo domínio (vendas/pedidos) que o projeto [abi-gth-omnia-developer-evaluation-NET2025](../abi-gth-omnia-developer-evaluation-NET2025), agora implementado com stack Java e arquitetura orientada a eventos.

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

Dois serviços deployáveis separadamente, cada um com seu próprio `pom.xml` e `Dockerfile` — não é um projeto Maven multi-módulo de propósito: microsserviço de verdade não compartilha build.

## Stack

Java 17, Spring Boot 4, Spring Web, Spring Data JPA, Spring Kafka, PostgreSQL, Docker Compose. Kafka roda em modo KRaft (sem Zookeeper).

## Como rodar

```bash
docker compose up --build
```

Isso sobe Postgres, Kafka (KRaft, 1 broker) e os dois serviços.

- `order-service`: http://localhost:8081
- `order-consumer`: http://localhost:8082 (só expõe health check; o trabalho dele aparece no log)

### Testar o fluxo

```bash
curl -X POST http://localhost:8081/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"cliente-1","product":"Notebook","totalAmount":4500.00}'
```

Acompanhe o log do `order-consumer` (`docker compose logs -f order-consumer`) — o evento `OrderCreatedEvent` deve aparecer processado em segundos.

Atualizar um pedido:

```bash
curl -X PUT http://localhost:8081/orders/{id} \
  -H "Content-Type: application/json" \
  -d '{"totalAmount":4800.00}'
```

## Decisões de arquitetura (e seus trade-offs)

**Por que Kafka em vez de REST síncrono entre os dois serviços?**
Desacopla o `order-service` de precisar saber quem consome o evento, e quantos consumidores existem. Se `order-consumer` cair, o evento fica retido no tópico até ele voltar — numa chamada REST síncrona, isso seria uma falha imediata.

**Por que a key do Kafka é o `customerId`?**
Kafka garante ordem *dentro* de uma partição, não entre partições. Usar `customerId` como key garante que todos os eventos do mesmo cliente caem sempre na mesma partição, preservando a ordem onde ela importa — sem perder o paralelismo entre clientes diferentes.

**Por que 3 partições?**
Permite até 3 consumidores em paralelo no mesmo consumer group, sem quebrar a garantia de ordem por cliente. Número escolhido pelo tamanho do projeto, não por medição de carga real.

**Por que sem schema registry (Avro)?**
O contrato do evento (`OrderCreatedEvent`/`OrderUpdatedEvent`) está duplicado manualmente entre os dois serviços como POJOs Java. Funciona pelo tamanho do projeto; numa arquitetura maior com mais times e mais consumidores, isso viraria uma fonte real de bug de contrato quebrado — a correção certa é Avro ou Protobuf com Schema Registry, validando compatibilidade de schema no CI.

**Por que o consumidor lê como `String` em vez de usar o `JsonDeserializer` tipado do Spring Kafka?**
O tópico carrega dois tipos de evento (criação e atualização), e o `JsonDeserializer` padrão do Spring Kafka usa um header com o nome completo da classe do *produtor* para decidir o tipo — que não bate com o pacote do *consumidor*, já que são serviços diferentes. Ler como texto e decidir o tipo pelo conteúdo (presença do campo `product`) evita essa armadilha sem precisar configurar mapeamento de tipos entre pacotes.

**Gap conhecido, não resolvido de propósito: dual write.**
`OrderService.create()` salva no Postgres e publica no Kafka na mesma chamada, sem garantia atômica entre os dois. Se o commit no banco for bem-sucedido mas o Kafka falhar (ou vice-versa), o estado diverge. A correção correta é o **padrão Outbox** (grava o evento numa tabela na mesma transação do banco, e um processo separado publica no Kafka a partir dessa tabela). Não implementado aqui por escopo do projeto — mas é a primeira coisa que eu mudaria numa versão de produção.

**Docker Compose em vez de Kubernetes de verdade.**
Para o tamanho deste projeto, Compose já resolve. Um manifest básico de Kubernetes (Deployment + Service) pode ser adicionado depois — o valor dele aparece em self-healing, scaling horizontal e config como código, nenhum dos quais este projeto precisa de fato pra funcionar.

## Estrutura

```
sales-events-java/
├── docker-compose.yml
├── order-service/       # API REST + produtor Kafka + Postgres
└── order-consumer/      # consumidor Kafka
```
