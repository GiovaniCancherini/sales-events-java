# 1. Tópico com dono

- **Data:** 19/10/2026
- **Status:** aceita

## Contexto

O tópico `orders` é declarado pelo `order-service` (`NewTopic`, 3 partições). Com `auto.create.topics.enable` ligado, que é o padrão do broker, o consumidor sobe mais rápido e criava o tópico só por assiná-lo, com 1 partição. O `KafkaAdmin` do `order-service` aumentava para 3 logo depois (`Topic 'orders' exists but has a different partition count: 1 not 3`), mas o consumidor só enxergava as partições novas na atualização de metadados seguinte (`metadata.max.age.ms`, 5 minutos). Numa subida limpa, o exemplo do README (`cliente-1`, que cai na partição 1) levava 5 minutos para aparecer no consumidor.

## Decisão

- Criação automática desligada no broker (`KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"`): só o dono cria o tópico.
- `spring.kafka.admin.fail-fast=true` no `order-service`: se não conseguir criar o tópico na subida, a aplicação não sobe.
- Healthcheck no Kafka do compose (`kafka-broker-api-versions.sh`), com os dois serviços esperando `service_healthy`.

## Alternativas consideradas

- **`allow.auto.create.topics=false` só no consumidor:** resolve para esse consumidor, mas qualquer outro cliente (um `kafka-console-consumer` no debug, por exemplo) ainda criaria o tópico com 1 partição.
- **Diminuir `metadata.max.age.ms`:** só encurta a espera, e o tópico continuaria nascendo errado.
- **Criar o tópico fora do serviço (script de init ou infraestrutura como código):** é o comum em produção, mas tira do serviço a declaração do próprio tópico. Fica para quando houver infraestrutura como código.

## Consequências

- O consumidor loga avisos `UNKNOWN_TOPIC_OR_PARTITION` enquanto espera o tópico existir. É esperado.
- Desligar a criação automática sem o fail-fast criou uma regressão: com o `order-service` subindo antes do Kafka, o `KafkaAdmin` só logava `Could not configure topics`, o health continuava UP e todo POST falhava com 500 depois de 60s (`Topic orders not present in metadata`), até alguém reiniciar o serviço. Com o fail-fast, o processo sai com código 1 e o problema aparece na subida.
- Rodando pela IDE, o Kafka precisa estar no ar antes do `order-service`.
