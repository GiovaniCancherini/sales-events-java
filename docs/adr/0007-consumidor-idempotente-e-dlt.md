# 7. Consumidor idempotente, retry e Dead Letter Topic

- **Data:** 19/11/2026
- **Status:** aceita

## Contexto

O consumidor só registrava os eventos em log. Para ter efeito de verdade, passou a ser o `inventory-service`, que reserva estoque quando o pedido é criado e libera quando é cancelado. O Kafka entrega pelo menos uma vez: se o serviço cair depois de gravar no banco e antes de confirmar o offset, a mesma mensagem volta. E o outbox da fase 2 também pode duplicar. Os eventos não tinham id próprio, só o `orderId`, que se repete entre criação, alteração e cancelamento.

Sem proteção, um `created` reentregue depois do `cancelled` regrava a reserva como `RESERVED`, e o estoque de um pedido cancelado volta a ficar preso. O teste de integração mostra isso: sem a checagem, ele falha com `expected: RELEASED but was: RESERVED`.

## Decisão

- Cada evento ganha um `eventId` (UUID) no payload, gerado no `order-service`. Na fase 2, virou o id da linha do outbox (ADR 8).
- O `inventory-service` grava o `eventId` em `processed_events` com `INSERT ... ON CONFLICT (event_id) DO NOTHING`, na mesma transação do efeito. Se nenhuma linha for inserida, o evento é duplicado e é ignorado sem erro. Quem decide é a chave primária, então duas entregas simultâneas não passam as duas.
- Falha no processamento: 3 tentativas com 1s de intervalo (`DefaultErrorHandler` com `FixedBackOff`), depois `orders.DLT` com o motivo nos headers. Erro de desserialização não é retentável e vai direto. A partição segue andando.
- O `orders.DLT` é do `inventory-service` (ADR 1), com as mesmas 3 partições do `orders`, porque a mensagem vai para a mesma partição de onde veio. Mensagem que não desserializou vai em bytes crus; evento vai em JSON, com o header de tipo original, para poder ser reprocessado.
- Banco próprio (`postgres-inventory`): cada serviço é dono dos seus dados.

## Alternativas consideradas

- **Checar com `existsById` antes de gravar:** duas entregas simultâneas passariam pela checagem antes de qualquer gravação.
- **Contar só com a chave natural (`orderId` na reserva):** funciona para o `created`, mas o `save` do JPA faz merge e sobrescreve em vez de falhar, e o `updated` não tem chave natural.
- **`eventId` em header do Kafka:** o contrato do payload não mudaria, mas o id se perde fácil ao republicar ou reprocessar a mensagem.
- **Retry não bloqueante (tópicos de retry):** não trava a partição durante as tentativas, mas quebra a ordem por cliente, que o projeto usa (o `created` sempre antes do `cancelled`).

## Consequências

- O `eventId` faz parte do contrato: mensagem sem ele falha no `INSERT` e vai para o DLT depois das tentativas.
- `processed_events` cresce sem limite. Numa versão de produção, apagar linhas mais antigas que a retenção do tópico resolve, porque depois disso a mensagem não volta mais.
- Durante as 3 tentativas, a partição fica parada uns 2 segundos. Aceitável no tamanho do projeto.
- Não há reprocessamento automático do DLT: reler `orders.DLT` é manual.
