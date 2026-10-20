# 6. Ciclo de vida do pedido e cancelamento

- **Data:** 19/10/2026
- **Status:** aceita

## Contexto

O `OrderStatus` tinha `CREATED`, `UPDATED` e `CANCELLED`. `UPDATED` descrevia um evento, não um estado de negócio, e `CANCELLED` não tinha fluxo nenhum.

## Decisão

- Estados: `CREATED` e `CANCELLED`. Alterar o valor não muda o estado, só o `updatedAt`.
- Cancelamento em `POST /orders/{id}/cancel`, que publica `OrderCancelledEvent`.
- Alterar ou cancelar um pedido já cancelado é regra do domínio: `Order.updateTotalAmount()` e `Order.cancel()` lançam `OrderAlreadyCancelledException`, e a API devolve 409. É o mesmo desenho do projeto .NET deste domínio.

## Alternativas consideradas

- **`DELETE /orders/{id}`:** sugere remoção, e o pedido cancelado precisa continuar consultável.
- **`PATCH` com `{"status":"CANCELLED"}`:** expõe o estado como campo editável, e a regra de transição vazaria para quem chama a API.

## Consequências

- Linhas antigas com `UPDATED` quebrariam a leitura do enum. O banco do compose é descartável e ainda não há migrations, então não houve migração.
- Quando o pedido ganhar mais estados (confirmação, por exemplo), as transições continuam dentro do `Order`.
