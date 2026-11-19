# Escopo da plataforma de vendas (18/11/2026)

Objetivo: chegar na entrevista técnica da vaga Java, Spring Boot e Angular (micro front-ends, microsserviços, mensageria, dados) com algo construído, rodando e explicável. Cada parte do projeto responde a uma pergunta que a recrutadora já fez.

Regra: só entra no currículo e no LinkedIn o que existir no repositório, rodando. Estimativas de tempo são minhas, não medidas.

## Ponto de partida (o que já existe)

- Este repositório: `order-service` (Spring Boot, REST, PostgreSQL, publica evento no Kafka) e `order-consumer` (consome do tópico `orders` e só registra em log). Kafka em KRaft, Docker Compose, Testcontainers, ADRs em `docs/adr`.
- Repositório `sales-api-dotnet`: API de vendas em .NET 8 (DDD, CQRS, PostgreSQL, MongoDB, Redis, JWT).

Decisão: o núcleo é este repositório (`sales-events-java`), porque a vaga é Java. A `sales-api-dotnet` fica como extensão opcional (ver fase 4), para não estourar o escopo de um dia.

## Arquitetura alvo

```
Angular shell (host)
 ├─ MFE orders (lista e cria pedidos)   ──► order-service (REST) ──► PostgreSQL
 └─ MFE catalog ou customers            ──► (serviço ou mock)          │
                                                                       ▼ outbox
                                                                     Kafka  ──► order-consumer (idempotente)
```

## Fases

### Fase 1: consumidor de verdade e idempotência (Java, o que mais cai em entrevista)
- `order-consumer` passa a processar o pedido (ex.: reservar estoque ou registrar o pedido numa tabela própria), não só logar.
- Idempotência: tabela `processed_events` com o id do evento e chave única; mensagem duplicada é ignorada sem efeito colateral.
- Teste de integração (Testcontainers): publica o mesmo evento duas vezes e confirma um único efeito.
- Retry com limite e Dead Letter Topic para mensagem que falha.
- Responde: "mensagem duplicada numa transação", "como você usa mensageria".
- Estimativa: 2 a 3 horas.

### Fase 2: outbox no `order-service`
- Gravar o pedido e o evento numa mesma transação (tabela `outbox`) e um publicador que envia ao Kafka, para não perder nem duplicar evento quando o banco ou o Kafka falham.
- Responde: consistência entre banco e mensageria, entrega "pelo menos uma vez".
- Estimativa: 1 a 2 horas.

### Fase 3: micro front-ends em Angular (o ponto que a vaga destacou)
- Um `shell` (host) e um `orders` (remote) com Module Federation (Native Federation ou Webpack, o que sua versão do Angular suportar).
- O remote lista e cria pedidos chamando o `order-service` (CORS configurado, URL por variável de ambiente).
- Dois remotes se tiver tempo (`orders` e `catalog`), para mostrar comunicação entre MFEs.
- Responde: "problemas que teve com micro front-ends", "como funciona", versões compartilhadas, estilo, deploy independente.
- Estimativa: 3 a 4 horas para um shell e um remote funcionando.

### Fase 4: observabilidade e desempenho (back-end ou banco?)
- Spring Boot Actuator e Micrometer: tempo por endpoint e por chamada ao banco. Logs com id de correlação.
- Roteiro de diagnóstico no README: onde medir (aplicação, pool de conexões, `EXPLAIN` da consulta, índice), como diferenciar lentidão do back-end de lentidão do banco.
- Responde: "como você mediria se a requisição está lenta no back-end ou no banco".
- Estimativa: 1 hora.

### Fase 5 (opcional): ponte com a `sales-api-dotnet`
- O `catalog` MFE consome a Sales API .NET, ou o `order-consumer` publica um evento que a API .NET registra. Só se sobrar tempo.
- Mostra as duas stacks no mesmo ecossistema. Fora do escopo de hoje.

## Para hoje (corte realista)
Fase 1 e o início da Fase 3 (shell e um remote rodando), com README. O resto fica agendado.

## Mapa pergunta da entrevista, onde está no projeto

| Pergunta | Onde mostrar |
|---|---|
| Monolito ou microsserviço? | ADR novo explicando por que dois serviços e quando um monolito bastaria |
| Problemas com micro front-ends | Fase 3, seção "Lições" do README |
| Idempotência e mensagem duplicada | Fase 1, tabela `processed_events` e o teste |
| Quais bancos e mensageria | PostgreSQL e Kafka (que você usou); ActiveMQ só se você usou de verdade |
| Lentidão: back-end ou banco? | Fase 4, Actuator, Micrometer e `EXPLAIN` |

## Critérios de pronto
- [ ] `docker compose up` sobe tudo (Kafka, Postgres, serviços) e o front roda.
- [ ] Teste de integração da idempotência passando.
- [ ] README com diagrama, como rodar, decisões e "Lições aprendidas".
- [ ] Repositório público, sem segredo no código.

## Fora do escopo
Kubernetes, nuvem, autenticação completa, UI bonita. Isso vem depois, no degrau do projeto de nuvem.

## Perguntas em aberto
- Versão do Angular que você vai usar (define Native Federation ou Webpack).
- O consumidor da fase 1 faz o quê no domínio (estoque, notificação, faturamento)?
