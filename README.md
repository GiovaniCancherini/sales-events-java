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
                                                              ┌──────────────────────┐
                                                              │  inventory-service    │
                                                              │  (reserva estoque,    │
                                                              │   idempotente)        │
                                                              └───┬──────────────┬───┘
                                                                  │              │ falhou 3x
                                                                  ▼              ▼
                                                           Postgres próprio   orders.DLT
```

Dois serviços deployáveis separadamente, cada um com seu próprio `pom.xml` e `Dockerfile`. Não é um projeto Maven multi-módulo, de propósito: microsserviço de verdade não compartilha build.

## Stack

Java 17, Spring Boot 4, Spring Web, Spring Data JPA, Spring Kafka, PostgreSQL, Docker Compose, Testcontainers. Kafka roda em modo KRaft (sem Zookeeper).

## Como rodar

A senha do Postgres não fica no repositório. Ela vem de um arquivo `.env` na raiz, ignorado pelo git. Crie a partir do modelo e preencha o valor:

```bash
cp .env.example .env
```

O `.env.example` mostra como gerar um valor aleatório. Sem o `.env`, o `docker compose` para com uma mensagem dizendo o que falta.

```bash
docker compose up --build
```

Isso sobe dois Postgres (um por serviço), Kafka (KRaft, 1 broker) e os dois serviços.

- `order-service`: http://localhost:8081
- `inventory-service`: http://localhost:8082 (só expõe health check; o trabalho dele aparece no log e no banco `inventory`, em `localhost:5434`)

### Testar o fluxo

Antes de criar pedido, confirme que a API subiu:

```
curl http://localhost:8081/actuator/health
```

Deve responder `{"status":"UP"}`. Os comandos abaixo estão em uma linha só, com aspas duplas escapadas, e funcionam tanto no CMD do Windows quanto no Git Bash/Linux/macOS:

```
curl -X POST http://localhost:8081/orders -H "Content-Type: application/json" -d "{\"customerId\":\"cliente-1\",\"product\":\"Notebook\",\"totalAmount\":4500.00}"
```

Acompanhe o log do `inventory-service` (`docker compose logs -f inventory-service`): em segundos aparece `Estoque reservado`, e a tabela `reservations` ganha uma linha `RESERVED`. Cancelar o pedido muda a reserva para `RELEASED`.

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

Depois rode `OrderServiceApplication` ou `InventoryServiceApplication` pela IDE. O `order-service` precisa da variável de ambiente `DB_PASSWORD` com o mesmo valor de `ORDER_POSTGRES_PASSWORD` do `.env`, e o `inventory-service`, com o valor de `INVENTORY_POSTGRES_PASSWORD` (no IntelliJ, em `Run > Edit Configurations > Environment variables`). Os valores padrão do `application.properties` já apontam para as portas que o compose publica no host: Postgres de pedidos em `localhost:5433` (e não 5432, para não colidir com um Postgres local), o de estoque em `localhost:5434` e Kafka em `localhost:29092`. Quando o serviço roda dentro do compose, as variáveis `DB_HOST`, `DB_PORT` e `KAFKA_BOOTSTRAP` trocam esses valores pelos endereços da rede interna. O tópico `orders` é criado pelo `order-service`, que por isso não sobe sem o Kafka no ar. Se o consumidor subir antes dele, espera até o tópico existir, com avisos `UNKNOWN_TOPIC_OR_PARTITION` no log.

### Debug remoto (opcional)

```bash
docker compose -f docker-compose.yml -f docker-compose.debug.yml up --build
```

Sobe o mesmo ambiente, com o `order-service` aceitando um debugger remoto (JDWP) na porta 5005. No IntelliJ: `Run > Edit Configurations > Remote JVM Debug`, host `localhost`, porta `5005`.

## Testes

```bash
cd order-service && ./mvnw test
cd ../inventory-service && ./mvnw test
```

No CMD do Windows, use `mvnw` sem o `./`. Só precisa do Docker rodando: com Testcontainers, os testes sobem containers próprios em portas aleatórias (Postgres e Kafka em cada serviço), então não dependem do compose nem de porta livre na máquina. Hoje há o teste de contexto em cada serviço, testes de domínio do `Order` (escala do valor e regras do cancelamento) e, no `inventory-service`, testes pelo Kafka de verdade: o tipo pelo header e o valor com as 2 casas; um `created` duplicado que chega depois do `cancelled` não volta a reservar; uma mensagem que não desserializa cai no `orders.DLT`; e uma falha de processamento tem 3 tentativas antes de ir para o DLT, com o header de tipo mantido.

### Teste manual

O [roteiro da fase 1](docs/roteiro-fase-1.md) percorre os mesmos casos à mão: caminho feliz, mensagem duplicada, mensagem que não desserializa e retry até o DLT. Usa a coleção do Insomnia em [docs/insomnia](docs/insomnia/sales-events.json) e o Kafka UI, que sobe só quando pedido:

```bash
docker compose -f docker-compose.yml -f docker-compose.tools.yml up -d
```

O Kafka UI fica em http://localhost:8090: mostra mensagens e headers dos tópicos e permite produzir mensagens à mão.

## Verificação de segredos

Todo commit passa pelo [gitleaks](https://github.com/gitleaks/gitleaks), num hook de pre-commit versionado. Além das regras padrão, o [`.gitleaks.toml`](.gitleaks.toml) acusa senha literal em arquivo de configuração (`docker-compose*.yml`, `.properties`, `.env`) e credencial dentro de URL de conexão. Ative uma vez por clone:

```bash
git config core.hooksPath .githooks
```

Um commit com segredo é recusado, mostrando arquivo e linha. Para varrer o histórico inteiro: `gitleaks git --log-opts="--all"`.

## Decisões de arquitetura (e seus trade-offs)

Aqui fica o resumo. As decisões mais recentes têm registro completo, com contexto, alternativas e consequências, em [docs/adr](docs/adr/). O que quebrou no caminho, e por quê, está no [APRENDIZADO.md](APRENDIZADO.md).

**Por que Kafka em vez de REST síncrono entre os dois serviços?**
Desacopla o `order-service` de precisar saber quem consome o evento, e quantos consumidores existem. Se o `inventory-service` cair, o evento fica retido no tópico até ele voltar. Numa chamada REST síncrona, isso seria uma falha imediata.

**Por que a key do Kafka é o `customerId`?**
Kafka garante ordem *dentro* de uma partição, não entre partições. Usar `customerId` como key garante que todos os eventos do mesmo cliente caem sempre na mesma partição, preservando a ordem onde ela importa, sem perder o paralelismo entre clientes diferentes.

**Por que 3 partições?**
Permite até 3 consumidores em paralelo no mesmo consumer group, sem quebrar a garantia de ordem por cliente. Número escolhido pelo tamanho do projeto, não por medição de carga real.

**Por que a criação automática de tópicos está desligada?**
O tópico `orders` tem dono: quem o declara é o `order-service`, com 3 partições (`NewTopic`). Com a criação automática ligada, que é o padrão do broker, o consumidor sobe mais rápido e cria o tópico só por assiná-lo, com o padrão de 1 partição. O `order-service` aumenta para 3 logo depois, mas o consumidor só enxerga as partições novas na próxima atualização de metadados, até 5 minutos depois, e os eventos que caem nelas ficam parados até lá. Desligada, como costuma ser em produção, a criação fica só com o dono: o consumidor espera o tópico existir (são os avisos `UNKNOWN_TOPIC_OR_PARTITION` na subida) e já entra com as 3 partições. Como o tópico só nasce na subida do `order-service`, ele usa `spring.kafka.admin.fail-fast=true`: se não conseguir criar o tópico, não sobe. Sem isso, ficaria no ar com health UP e todo POST falhando depois de 60s. No compose, os dois serviços esperam o healthcheck do Kafka.

**Por que sem schema registry (Avro)?**
O contrato dos eventos (`OrderCreatedEvent`, `OrderUpdatedEvent` e `OrderCancelledEvent`, mais os tokens do header de tipo) está duplicado manualmente entre os dois serviços como records Java. Funciona pelo tamanho do projeto; numa arquitetura maior com mais times e mais consumidores, isso viraria uma fonte real de bug de contrato quebrado. A correção certa é Avro ou Protobuf com Schema Registry, validando compatibilidade de schema no CI.

**Como o consumidor sabe o tipo do evento?**
O tópico carrega três tipos de evento (criação, atualização e cancelamento). Por padrão, o `JacksonJsonSerializer` grava no header `__TypeId__` o nome completo da classe do *produtor*, que não existe no *consumidor*, já que são serviços diferentes. Por isso os dois lados configuram `spring.json.type.mapping` com os mesmos tokens (`created`, `updated`, `cancelled`): o header leva só o token, e cada serviço o traduz para a própria classe. O consumidor desserializa direto no tipo certo, com um `@KafkaHandler` por evento, e o `ErrorHandlingDeserializer` impede que uma mensagem que não desserializa trave a partição: ela vai para o `orders.DLT`. Antes, o consumidor lia texto e decidia pela presença do campo `product`, o que funcionava com dois tipos e quebraria no terceiro.

**Como o consumidor lida com mensagem duplicada?**
O Kafka entrega pelo menos uma vez: se o serviço cair depois de gravar e antes de confirmar o offset, a mensagem volta. Cada evento tem um `eventId`, e o `inventory-service` o grava em `processed_events` com `INSERT ... ON CONFLICT DO NOTHING`, na mesma transação do efeito. Se nenhuma linha entra, o evento já foi processado e é ignorado. Sem isso, um `created` reentregue depois do cancelamento voltaria a reservar estoque de um pedido cancelado; o teste de integração reproduz esse caso. Falha de processamento tem 3 tentativas e depois vai para o `orders.DLT`, com o motivo nos headers. Ver ADR 7.

**Por que um Postgres para cada serviço?**
Cada serviço é dono dos seus dados: o `inventory-service` não lê a tabela de pedidos, só os eventos. A senha de cada banco só vale quando ele é criado (o dado fica no volume `orders-data` ou `inventory-data`). Trocou a senha no `.env`? Rode `docker compose down -v` antes de subir de novo, o que apaga os dados locais.

**Gap conhecido, não resolvido de propósito: dual write.**
`OrderService.create()` salva no Postgres e publica no Kafka na mesma chamada, sem garantia atômica entre os dois. Se o commit no banco for bem-sucedido mas o Kafka falhar (ou vice-versa), o estado diverge. O flush antes de publicar diminui essa janela, porque erro de banco ou conflito de versão aparece antes de o evento sair, mas não a fecha. A correção correta é o **padrão Outbox** (grava o evento numa tabela na mesma transação do banco, e um processo separado publica no Kafka a partir dessa tabela). Não implementado aqui por escopo do projeto, mas é a primeira coisa que eu mudaria numa versão de produção.

**Docker Compose em vez de Kubernetes de verdade.**
Para o tamanho deste projeto, Compose já resolve. Um manifest básico de Kubernetes (Deployment + Service) pode ser adicionado depois. O valor dele aparece em self-healing, scaling horizontal e config como código, nenhum dos quais este projeto precisa de fato pra funcionar.

## Estrutura

```
sales-events-java/
├── APRENDIZADO.md       # o que quebrou, por quê e o que ficou de cada correção
├── docker-compose.yml
├── docker-compose.debug.yml   # override opcional: debug remoto do order-service (porta 5005)
├── docker-compose.tools.yml   # override opcional: Kafka UI (porta 8090)
├── docs/adr/            # registros de decisão de arquitetura (ADRs)
├── order-service/       # API REST + produtor Kafka + Postgres
└── inventory-service/   # consumidor Kafka idempotente + Postgres próprio
```
