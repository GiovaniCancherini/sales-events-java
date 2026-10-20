# 5. Tipo do evento no header, com consumo tipado

- **Data:** 19/10/2026
- **Status:** aceita

## Contexto

O tópico `orders` passou a ter três tipos de evento: criação, alteração e cancelamento. O consumidor lia o payload como texto e decidia o tipo pela presença do campo `product`. O cancelamento também não tem `product` e seria lido como alteração. E, por padrão, o `JacksonJsonSerializer` grava no header `__TypeId__` o nome completo da classe do produtor, que não existe no consumidor.

## Decisão

- Os dois serviços configuram `spring.json.type.mapping` com os mesmos tokens: `created`, `updated`, `cancelled`. O header leva só o token, e cada lado o traduz para a própria classe.
- O consumidor desserializa direto no tipo (`JacksonJsonDeserializer`), com um `@KafkaHandler` por evento.
- O `ErrorHandlingDeserializer` envolve o desserializador: uma mensagem que não desserializa vira erro tratado, é logada uma vez e pulada, sem travar a partição.

## Alternativas consideradas

- **Header próprio (`eventType`) lido à mão, payload como texto e um `switch`:** mudança menor, mas é código próprio fazendo o que o Spring Kafka já faz, e tipo desconhecido precisa ser tratado na mão.
- **Campo de tipo dentro do JSON:** funciona, mas obriga a abrir o payload para descobrir como abri-lo.
- **Schema Registry com Avro ou Protobuf:** o caminho certo com mais times e consumidores. Está registrado no README como evolução.

## Consequências

- Os tokens viram parte do contrato: um token novo precisa chegar ao consumidor antes de o produtor começar a mandá-lo. Senão, a mensagem cai no erro de desserialização.
- A propriedade `use-big-decimal-for-floats` deixou de ser necessária: a desserialização tipada lê o valor direto em `BigDecimal`, sem passar por `double`, e mantém a escala (`4500.00`). Um teste de ida e volta pelo Kafka trava isso.
- Mensagens antigas, com o nome da classe no header, não seriam lidas. O Kafka do compose é descartável, então não houve migração.
