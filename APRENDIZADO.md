# Aprendizado

O que quebrou, por que quebrou e o que ficou de cada correção. As decisões de desenho, com alternativas e consequências, ficam em [docs/adr](docs/adr/).

## 19/10/2026: o teste falhava por senha, mas o problema era a porta

- **O que quebrou:** fora do Docker, o teste do `order-service` falhava com `password authentication failed for user "orders"`.
- **Por quê:** a URL do banco tinha a porta 5432 fixa, e na máquina a 5432 era de outro Postgres, de outro projeto. O teste conectava no banco errado, e o erro apontava para a senha.
- **O que mudou:** a porta virou configurável (`DB_PORT`, padrão 5433, a que o compose publica), e os testes passaram a usar Testcontainers, com Postgres e Kafka próprios em portas aleatórias.
- **O que ficou:** o erro fala do banco em que a conexão chegou, não de por que ela chegou nele. E teste não deve depender do que está rodando na máquina.

## 19/10/2026: a JVM dos testes levava 30s para encerrar

- **O que quebrou:** com os containers em campos `static` com `@Container`, o Surefire avisava `Surefire is going to kill self fork JVM` e esperava 30s no fim do teste do consumidor.
- **Por quê:** o JUnit parava o Kafka antes de o Spring fechar o contexto, e o consumidor ficava tentando falar com um broker que já não existia.
- **O que mudou:** os containers viraram `@Bean` com `@ServiceConnection` numa `@TestConfiguration`. Assim o Spring cuida do ciclo de vida e só para os containers depois de quem depende deles.
- **O que ficou:** quem cria o recurso deve ser quem o encerra, na ordem certa.

## 19/10/2026: o workaround do Jackson 2 que sobreviveu à migração

- **O que quebrou:** um bean de `ProducerFactory` e o comentário dele explicavam um problema que já não existia.
- **Por quê:** com o Jackson 2, o serializer do Spring Kafka não serializava `java.time.Instant`, e a publicação falhava só em runtime (`InvalidDefinitionException`). A correção da época foi esse bean, com o módulo `jackson-datatype-jsr310`. O projeto migrou para o Jackson 3, padrão do Spring Boot 4, que já traz `java.time` embutido, e o bean ficou.
- **O que mudou:** um `JsonMapper` do Jackson 3, testado isolado, gravou e leu `Instant`. O bean saiu, o serializer virou uma linha de configuração, e o fluxo foi validado de ponta a ponta, conferindo a mensagem crua no tópico.
- **O que ficou:** workaround precisa ser reavaliado quando o motivo dele some. E erro de serialização só aparece em runtime, então o teste que vale é o fluxo de verdade.

## 19/10/2026: o consumidor recebia 4500.0 em vez de 4500.00

- **O que quebrou:** o produtor mandava `4500.00`, e o consumidor logava `4500.0`.
- **Por quê:** o consumidor lia o JSON com `readTree`, que por padrão guarda decimal como `double` e perde a escala. E o `JsonMapper` era criado com `new`, então nem a configuração `spring.jackson.*` valia para ele.
- **O que mudou:** primeiro, o `JsonMapper` do Spring passou a ser injetado, com `use-big-decimal-for-floats`. Depois, com a desserialização tipada (ADR 5), o valor passou a ser lido direto em `BigDecimal`, e a propriedade saiu. Um teste de ida e volta pelo Kafka trava a escala.
- **O que ficou:** dinheiro não pode passar por `double` em nenhum ponto do caminho. E objeto criado com `new` não recebe a configuração do framework: é a injeção que faz a configuração valer.

## 19/10/2026: o evento levava 5 minutos para chegar numa subida limpa

- **O que quebrou:** depois de `docker compose down` e `up`, o primeiro pedido só aparecia no consumidor 5 minutos depois.
- **Por quê:** o consumidor assinava o tópico antes de ele existir, e o broker o criava com 1 partição. O `order-service` aumentava para 3, mas o consumidor só via as partições novas na próxima atualização de metadados. A chave `cliente-1` cai na partição 1, que o consumidor ainda não tinha.
- **Como foi diagnosticado:** `kafka-topics.sh --describe` mostrava 3 partições, `kafka-consumer-groups.sh --describe` mostrava o grupo só com a partição 0, e o log do `order-service` dizia `exists but has a different partition count: 1 not 3`. O hash da chave (murmur2, o mesmo do produtor) confirmou a partição.
- **O que ficou:** bug de ordem de subida só aparece em ambiente limpo, por isso a validação começa de um `down`. Ver ADR 1.

## 19/10/2026: a correção da criação de tópico deixou o serviço UP e quebrado

- **O que quebrou:** com a criação automática desligada, o `order-service` subindo antes do Kafka ficava com health UP, e todo POST falhava com 500 depois de 60s, mesmo com o Kafka já no ar.
- **Por quê:** o tópico só é criado na subida. Se o Kafka não responde nessa hora, o `KafkaAdmin` só loga o erro, e ninguém mais cria o tópico. Antes, o próprio envio criava o tópico (errado, com 1 partição), e isso escondia a dependência.
- **O que mudou:** `spring.kafka.admin.fail-fast=true` e healthcheck do Kafka no compose. Sem Kafka, o serviço agora sai com código 1 na subida.
- **O que ficou:** tirar um comportamento implícito muda o jeito de falhar. Depois de uma correção, testar também o cenário que ela pode ter piorado.

## 19/10/2026: a resposta dizia 10.555 e o banco gravava 10.56

- **O que quebrou:** um POST com `10.555` respondia `10.555` e publicava `10.555` no evento, mas o GET seguinte devolvia `10.56`.
- **Por quê:** sem precisão definida, o Hibernate cria a coluna como `numeric(38,2)`, e o Postgres arredonda na gravação. A resposta e o evento usavam o valor que estava em memória.
- **O que mudou:** `@Digits` recusa mais de 2 casas com 400, a coluna virou `numeric(18,2)` e o domínio normaliza a escala. Ver ADR 2.
- **O que ficou:** API, evento e banco precisam concordar na escala do dinheiro. E o padrão do ORM é uma decisão de desenho, mesmo quando ninguém a tomou.

## 19/10/2026: a propriedade do consumer group não fazia nada

- **O que quebrou:** nada visível. O `groupId` estava no `@KafkaListener` e também em `spring.kafka.consumer.group-id`.
- **Por quê:** o valor da anotação sobrescreve a propriedade. Quem mudasse só a propriedade não mudaria nada.
- **O que mudou:** o `groupId` saiu da anotação e ficou só na propriedade.
- **O que ficou:** configuração num lugar só.

## 19/11/2026: a senha nova não valia depois de recriar o container

- **O que quebrou:** depois de trocar a senha do inventory no `.env` e rodar `docker compose up --force-recreate`, o DBeaver falhava com `password authentication failed for user "inventory"`.
- **Por quê:** a imagem do Postgres declara um volume para `/var/lib/postgresql/data`. Sem volume no compose, ele vira um volume anônimo, e o `--force-recreate` o reaproveita. O banco já existia, e o `POSTGRES_PASSWORD` só vale quando o banco é criado.
- **Como foi diagnosticado:** havia dois processos na porta 5434 (`com.docker.backend` e `wslrelay`), mas os dois levavam ao mesmo container, então não era conflito de porta. O `docker inspect` mostrou o volume criado antes do container, e um `psql` pela rede do compose com a senha do `.env` reproduziu o erro.
- **O que mudou:** os dois Postgres ganharam volume nomeado (`orders-data` e `inventory-data`), e o README diz que, depois de trocar a senha, é preciso rodar `docker compose down -v`.
- **O que ficou:** recriar o container não recria o dado. E o erro de senha fala da senha que o banco tem, não da que está no `.env`.

## 19/11/2026: o renome da variável apagou a senha do container

- **O que quebrou:** com o volume renovado, o `postgres-inventory` saía com código 1: `Database is uninitialized and superuser password is not specified`.
- **Por quê:** a variável do `.env` foi renomeada de `POSTGRES_PASSWORD` para `ORDER_POSTGRES_PASSWORD` com localizar e substituir, e a troca pegou também a chave dentro dos containers. A imagem só lê `POSTGRES_PASSWORD` e recebia vazio. O banco antigo continuava aceitando conexão porque já estava criado, o que escondeu o problema.
- **O que mudou:** a chave voltou a ser `POSTGRES_PASSWORD`, com o valor vindo de `ORDER_POSTGRES_PASSWORD` e `INVENTORY_POSTGRES_PASSWORD`. O `.env.example` usa os nomes novos.
- **O que ficou:** no compose, o lado esquerdo é contrato da imagem e o lado direito é escolha do projeto. Renome só no lado direito.

