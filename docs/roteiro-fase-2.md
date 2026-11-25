# Roteiro de teste manual: Fase 2 (outbox)

Ver o evento nascer no banco, junto com o pedido, e só depois sair para o Kafka. Usa as mesmas telas do [roteiro da fase 1](roteiro-fase-1.md), mais uma conexão no DBeaver para o banco de pedidos.

## Preparação

1. Suba com a versão nova do `order-service`:

   ```bash
   docker compose -f docker-compose.yml -f docker-compose.tools.yml up -d --build
   ```

2. DBeaver: conexão em `localhost:5433`, banco e usuário `orders`, senha `ORDER_POSTGRES_PASSWORD` do `.env`. Consulta usada em todos os passos:

   ```sql
   select id, type, message_key, created_at, sent_at from outbox_events order by created_at desc;
   ```

## 1. Caminho feliz

1. Insomnia, `03 Criar pedido`.
2. Resultado esperado:
   - DBeaver (`orders`): uma linha `created` em `outbox_events`, com `sent_at` preenchido menos de 1s depois de `created_at`.
   - Kafka UI: a mensagem em `orders` tem o `eventId` igual ao `id` da linha do outbox.
   - Log do `inventory-service`: `Estoque reservado`.

**O que prova:** o evento passa pela tabela antes de ir para o Kafka, e o id da linha é o mesmo que o consumidor usa para deduplicar.

## 2. Kafka fora do ar: o evento espera

1. Pare o Kafka:

   ```bash
   docker compose stop kafka
   ```

2. Crie dois pedidos pelo Insomnia. A API responde 201 normalmente: ela não depende mais do Kafka para aceitar o pedido.
3. DBeaver: as duas linhas novas estão com `sent_at` nulo. O log do `order-service` mostra o relay sem conseguir falar com o broker.
4. Suba o Kafka de novo:

   ```bash
   docker compose start kafka
   ```

5. Resultado esperado, em alguns segundos:
   - DBeaver: as duas linhas ganham `sent_at`.
   - Log do `inventory-service`: `Estoque reservado` para os dois pedidos, na ordem em que foram criados.

**O que prova:** antes do outbox, esse pedido seria gravado e o evento se perderia (ou o POST falharia depois de o pedido já estar no banco). Agora o evento fica guardado na mesma transação e sai quando o Kafka volta.

## 3. Transação desfeita não deixa evento

1. Crie um pedido e cancele (`06 Cancelar pedido`). Cancele de novo: 409.
2. DBeaver: só existem as linhas `created` e `cancelled` desse pedido, nenhuma a mais.

O caso mais forte, uma falha depois de o evento já estar gravado, está no teste automático `rolledBackTransactionLeavesNeitherOrderNorEvent`: o pedido e o evento são gravados e a transação é desfeita em seguida, e nenhum dos dois fica.
