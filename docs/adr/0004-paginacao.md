# 4. Paginação na listagem de pedidos

- **Data:** 19/10/2026
- **Status:** aceita

## Contexto

O `GET /orders` devolvia o `findAll()` inteiro, sem limite.

## Decisão

- Paginação por offset com o `Pageable` do Spring Data: 20 por página por padrão, ordenado por `createdAt` decrescente. Sem uma ordenação, a ordem entre as páginas não é garantida.
- A resposta usa `PagedModel`, o formato estável do Spring Data. Serializar o `PageImpl` direto não tem formato garantido entre versões.
- Campo de ordenação inexistente (`?sort=foo`) devolve 400, em vez de 500.

## Alternativas consideradas

- **Paginação por cursor (keyset, `WHERE created_at < ?`):** não fica lenta em páginas profundas e não pula nem repete itens quando entram pedidos novos entre uma página e outra. Para o volume deste projeto, offset basta, e cursor é o próximo passo se o volume crescer.
- **Só limitar, sem paginar:** esconde dados sem dar ao cliente um jeito de pedir o resto.

## Consequências

- O tamanho máximo de página fica no padrão do Spring Data (2000).
