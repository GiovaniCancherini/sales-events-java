# 2. Valor monetário com duas casas

- **Data:** 19/10/2026
- **Status:** aceita

## Contexto

O `totalAmount` era um `BigDecimal` sem precisão definida, e o Hibernate cria a coluna como `numeric(38,2)` por padrão. A API aceitava qualquer número de casas: um POST com `10.555` respondia `10.555` e publicava `10.555` no evento, mas o Postgres gravava `10.56`. Resposta, evento e banco divergiam.

## Decisão

- Recusar na entrada: `@Digits(integer = 16, fraction = 2)` nos requests de criação e de atualização, que devolve 400.
- Coluna `numeric(18,2)`, a mesma precisão do projeto .NET deste domínio (`decimal(18,2)`).
- O domínio normaliza a escala com `setScale(2)`, sem modo de arredondamento: `4500.5` vira `4500.50`, e mais de 2 casas ali lança exceção, porque só chegaria se alguém pulasse a validação.

## Alternativas consideradas

- **Arredondar no servidor (`HALF_EVEN`):** esconde do cliente que o valor gravado não é o que ele mandou. Arredondamento é regra de negócio e, quando existir (desconto, por exemplo), fica explícito no domínio.
- **Manter `numeric(38,2)` sem validação:** é exatamente o bug.

## Consequências

- Resposta, evento e banco mostram o mesmo valor, com a mesma escala.
- Quando o pedido ganhar itens e desconto, isso vira um value object de dinheiro.
