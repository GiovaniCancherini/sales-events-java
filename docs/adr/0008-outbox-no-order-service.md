# 8. Outbox no order-service

- **Data:** 25/11/2026
- **Status:** aceita

## Contexto

O `order-service` gravava o pedido no Postgres e publicava o evento no Kafka na mesma chamada, sem atomicidade entre os dois (dual write). O flush antes de publicar (ADR 3) diminuía a janela, mas não a fechava. Se o commit falhasse depois do envio, saía evento de pedido que não existe. Se o Kafka falhasse depois do commit, o pedido ficava sem evento, e o estoque nunca era reservado.

## Decisão

- O `OrderService` grava o evento na tabela `outbox_events` na mesma transação do pedido (`OrderEventOutbox`, com `Propagation.MANDATORY`, que recusa ser chamado fora de uma transação). Ou ficam os dois, ou nenhum.
- O id da linha é o `eventId` do payload (ADR 7), então o consumidor deduplica pelo mesmo valor que o outbox usa.
- A linha guarda o token de tipo (`created`, `updated`, `cancelled`), a key (`customerId`) e o payload já em JSON.
- O `OutboxRelay` roda a cada 500ms (`@Scheduled`) no próprio serviço: trava até 100 linhas pendentes com `SELECT ... FOR UPDATE SKIP LOCKED`, em ordem de criação, publica uma a uma esperando o ack do broker e marca `sent_at`. Tudo numa transação: se o Kafka falhar, nenhuma marcação fica, e as linhas voltam na próxima rodada.
- O produtor passou a usar `StringSerializer`: o JSON já sai pronto do outbox, e o token de tipo vai no header `__TypeId__`, como antes. O consumidor não mudou.
- Sem flush antes de publicar: a checagem de versão (ADR 3) acontece no commit, e um conflito desfaz o pedido e o evento juntos.

## Alternativas consideradas

- **CDC com Debezium:** lê o log de transações do Postgres e publica sem polling, com latência menor. Custa mais dois containers Java (Kafka Connect e o conector) e mais configuração. Fica como evolução se o polling pesar no banco.
- **Spring Modulith (externalização de eventos):** resolve com menos código, mas esconde o mecanismo.
- **Transação do Kafka junto com a do banco:** não existe transação atômica entre os dois sistemas sem um coordenador (2PC), que o Kafka não oferece com o Postgres.

## Consequências

- Entrega pelo menos uma vez: se o processo cair depois do envio e antes do commit do relay, a linha é publicada de novo. O consumidor idempotente (ADR 7) absorve a duplicata.
- O evento sai até ~500ms depois do commit, em vez de imediatamente.
- Com mais de uma instância, o `SKIP LOCKED` evita que dois relays publiquem a mesma linha. A ordem por cliente vale entre eventos de um mesmo pedido, que o `@Version` serializa; dois pedidos diferentes do mesmo cliente, em transações simultâneas, podem sair numa ordem diferente da dos commits.
- `outbox_events` cresce sem limite. Numa versão de produção, apagar as linhas enviadas mais antigas que alguns dias resolve.
- O relay segura a transação do banco enquanto espera o ack do Kafka. Com lotes de 100 e o broker local, isso não pesa; com o Kafka lento, a conexão fica ocupada mais tempo.
