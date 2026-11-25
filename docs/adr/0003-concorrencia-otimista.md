# 3. Concorrência otimista na alteração do pedido

- **Data:** 19/10/2026
- **Status:** aceita; o flush antes de publicar foi substituído pelo outbox ([ADR 8](0008-outbox-no-order-service.md)). O `@Version` continua.

## Contexto

Com dois PUTs simultâneos no mesmo pedido, cada um lia, alterava e gravava, e o último commit vencia sem aviso. Como o evento saía antes do commit, os eventos podiam chegar ao consumidor numa ordem diferente da dos commits, deixando o consumidor com um valor diferente do banco.

## Decisão

- `@Version` no `Order`: o UPDATE passa a exigir a versão lida (`WHERE id = ? AND version = ?`). Se outra transação alterou o pedido no meio, a escrita falha e a API devolve 409.
- Flush antes de publicar (`saveAndFlush` na criação, `flush` na alteração e no cancelamento): a escrita, e com ela a checagem de versão, acontece antes de o evento sair. Um PUT recusado por conflito não publica nada.

Validado com outra transação segurando a linha do pedido e incrementando a versão: o PUT esperou, recebeu 409, o banco manteve o valor da outra transação e nenhum evento saiu.

## Alternativas consideradas

- **Lock pessimista (`SELECT ... FOR UPDATE`):** serializa as escritas, mas segura a linha e a conexão durante a requisição inteira. Para um pedido que raramente é alterado por duas pessoas ao mesmo tempo, o otimista custa menos.
- **Não fazer nada:** perda silenciosa de atualização.

## Consequências

- Protege transações que se sobrepõem no tempo. Para proteger o intervalo entre o GET e o PUT de dois usuários, o cliente teria que mandar a versão que leu (ETag e `If-Match`). Não implementado.
- O flush diminui a janela de dual write, mas não fecha: o commit ainda pode falhar depois do envio, e o envio pode falhar depois do commit. Isso continua sendo trabalho para o Outbox.
- A versão é a base para o consumidor descartar evento duplicado ou atrasado quando ele passar a guardar estado.
