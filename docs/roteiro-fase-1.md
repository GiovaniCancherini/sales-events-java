# Roteiro de teste manual: Fase 1

Testar à mão o consumidor idempotente, o retry e o DLT, vendo cada passo em três telas: o Insomnia (API), o Kafka UI (mensagens) e o DBeaver (banco do estoque). Os testes automáticos cobrem os mesmos casos em `InventoryServiceApplicationTests`.

## Preparação

1. Recrie o ambiente do zero (apaga os dados locais; necessário uma vez, porque os bancos passaram a usar volume nomeado):

   ```bash
   docker compose down -v
   docker compose -f docker-compose.yml -f docker-compose.tools.yml up -d --build
   ```

2. Abra as três telas:
   - **Insomnia:** `Import` > arquivo [docs/insomnia/sales-events.json](insomnia/sales-events.json). Rode `01` e `02`: os dois devem responder `UP`.
   - **Kafka UI:** http://localhost:8090 > cluster `local` > `Topics`. Devem existir `orders` e `orders.DLT`, cada um com 3 partições.
   - **DBeaver:** conexão em `localhost:5434`, banco e usuário `inventory`, senha `INVENTORY_POSTGRES_PASSWORD` do `.env`.

3. Deixe o log do consumidor aberto num terminal:

   ```bash
   docker compose logs -f inventory-service
   ```

Consultas para o DBeaver, usadas em vários passos:

```sql
select order_id, product, status, updated_at from reservations order by updated_at desc;
select event_id, processed_at from processed_events order by processed_at desc;
```

## 1. Caminho feliz: criar e cancelar

1. Insomnia, `03 Criar pedido`. Copie o `id` da resposta para `order_id` no ambiente (`Ctrl+E`).
   - Log: `Pedido criado` e `Estoque reservado`.
   - DBeaver: uma reserva `RESERVED` e uma linha em `processed_events`.
   - Kafka UI: `orders` > `Messages`. Abra a mensagem: a key é `cliente-1`, o header `__TypeId__` vale `created`, e o valor tem `eventId`.
2. `05 Alterar valor`.
   - Log: `Pedido atualizado`. Nada muda no banco: alteração de valor não mexe em estoque.
3. `06 Cancelar pedido`.
   - Log: `Reserva liberada`. DBeaver: a reserva vira `RELEASED`; `processed_events` ganha a linha do cancelamento.
4. `06 Cancelar pedido` de novo: 409, o pedido já está cancelado (regra do `order-service`, ADR 6). Nenhum evento sai.

**O que prova:** o evento sai do `order-service`, chega ao consumidor pelo tipo do header e produz efeito no banco próprio do estoque.

## 2. Mensagem duplicada (o caso clássico)

Simula o Kafka entregando de novo o `created` do passo 1, depois do cancelamento. Acontece de verdade quando o consumidor cai depois de gravar e antes de confirmar o offset.

1. Kafka UI: em `orders` > `Messages`, abra o `created` do passo 1 e copie o valor (o JSON inteiro, com o mesmo `eventId`).
2. `Produce Message` no tópico `orders`:
   - Key: `cliente-1`
   - Value: o JSON copiado
   - Headers: `{"__TypeId__": "created"}`
3. Resultado esperado:
   - Log: `Evento duplicado ignorado: eventId=...`
   - DBeaver: a reserva continua `RELEASED`, e `processed_events` não ganhou linha nova.

**O que prova:** sem a tabela `processed_events`, o `save` regravaria a reserva como `RESERVED`, e o estoque de um pedido cancelado voltaria a ficar preso. Para comparar, produza o mesmo JSON trocando só o `eventId` por outro UUID: o consumidor o trata como evento novo e a reserva volta a `RESERVED`. É por isso que o id tem que ser do evento, gerado uma vez na origem, e não da mensagem.

## 3. Mensagem que não desserializa

1. `Produce Message` em `orders`: key `cliente-1`, value `isto nao e json`, header `{"__TypeId__": "created"}`.
2. Resultado esperado:
   - Não há retry: tentar desserializar de novo não muda o resultado, então ela vai direto para o DLT.
   - Kafka UI: a mensagem aparece em `orders.DLT`, crua, com os headers `kafka_dlt-exception-fqcn` e `kafka_dlt-exception-message`.
3. Crie outro pedido pelo Insomnia (`03 Criar pedido`, pode manter `cliente-1`): ele é processado normalmente.

**O que prova:** a mensagem envenenada não trava a partição.

## 4. Falha de processamento: retry e depois DLT

1. `Produce Message` em `orders`, key `cliente-retry`, header `{"__TypeId__": "created"}`, value sem `eventId` (é de propósito: simula um produtor que quebrou o contrato):

   ```json
   {"orderId":"6f1c2a8e-0000-4000-8000-000000000099","customerId":"cliente-retry","product":"Notebook","totalAmount":100.00,"occurredAt":"2026-11-25T12:00:00Z"}
   ```

2. Resultado esperado:
   - Log: `Pedido criado: cliente=cliente-retry` três vezes, com cerca de 1s entre elas, cada uma seguida de `null value in column "event_id" ... violates not-null constraint`. As exceptions são o esperado: é a falha que o cenário provoca.
   - Kafka UI: a mensagem em `orders.DLT`, com o header `__TypeId__` = `created` mantido e `kafka_dlt-exception-fqcn` = `ListenerExecutionFailedException`. O conteúdo aparece com `"eventId":null`: em falha de processamento a mensagem já foi desserializada, e o DLT recebe o objeto serializado de novo, não os bytes originais (no cenário 3, que nem virou objeto, vão os bytes crus).
   - DBeaver: nenhuma reserva para esse `orderId`. A transação desfez tudo em cada tentativa.

**O que prova:** falha que pode ser passageira (banco fora, por exemplo) ganha novas tentativas; se persistir, a mensagem sai do caminho e fica guardada com o motivo e o tipo, pronta para ser reprocessada depois da correção.

## Para desligar

```bash
docker compose -f docker-compose.yml -f docker-compose.tools.yml down
```

Sem `-v`, os dados dos bancos ficam nos volumes para a próxima subida.
