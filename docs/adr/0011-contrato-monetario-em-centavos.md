# 11. Contrato monetário em centavos, domínio em decimal

- **Data:** 25/11/2026
- **Status:** aceita

## Contexto

O valor do pedido é `BigDecimal` no Java e `numeric(18,2)` no Postgres, decimais exatos (ADR 2). No JSON, porém, ele trafegava como número (`"totalAmount": 4500.00`), e todo consumidor em JavaScript (o front da fase 3, a Kafka UI, um script) lê número como `double`. Exibir funciona; a primeira conta não (`0.1 + 0.2` dá `0.30000000000000004`). Java e .NET têm decimal exato, JavaScript não tem.

## Decisão

- **Tudo o que trafega entre sistemas vai em centavos inteiros:** `totalAmountInCents` na API (request e response) e nos eventos do tópico `orders`. É a mesma regra para qualquer consumidor, front, Java ou .NET.
- **Dentro do serviço, decimal exato:** domínio em `BigDecimal` com 2 casas e banco em `numeric(18,2)`, sem migração.
- **A conversão fica num lugar só**, a classe `Cents` do `order-service`, usada pelos DTOs e pela montagem dos eventos. `longValueExact` faz um valor com mais de 2 casas virar exceção em vez de arredondamento silencioso.
- **Limite de 15 dígitos** (`Cents.MAXIMUM`, R$ 9.999.999.999.999,99): o maior inteiro que um `double` representa sem perda, para o contrato continuar exato em JavaScript.
- **Centavo fracionado é recusado:** `spring.jackson.deserialization.accept-float-as-int=false`. Sem isso, o Jackson trunca `1055.5` para `1055` sem avisar.
- O front guarda o valor em centavos desde a digitação e só divide por 100 para formatar (`Intl.NumberFormat`, pt-BR).

## Alternativas consideradas

- **Decimal como número no JSON (como estava):** exato entre Java e .NET, mas vira `double` em JavaScript.
- **Decimal como string (`"4500.00"`):** exato em qualquer linguagem, mas conta em JavaScript exige biblioteca (`big.js`, `decimal.js`).
- **Centavos em todo lugar (`long` no domínio, `bigint` no banco):** uma representação só, sem conversão. O custo aparece em porcentagem, imposto e juros: a divisão de `long` trunca, e o arredondamento, a escala das taxas e o overflow viram código próprio, um pequeno `BigDecimal` feito à mão. O faturamento da fase 5 é justamente esse tipo de cálculo.

## Consequências

- Centavos assumem 2 casas, o que vale para BRL. Outra moeda exige o campo `currency` no contrato, e as casas passam a depender dela.
- Mudar o contrato de um evento quebra consumidores. Aqui o único consumidor é nosso e sobe junto; em produção seria expandir e contrair: publicar os dois campos, migrar os consumidores, depois remover o antigo.
- Eventos antigos no tópico têm `totalAmount` e não `totalAmountInCents`. Os offsets do `inventory-service` já passaram deles, e o valor só aparece em log.
