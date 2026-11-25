# 9. API gateway com Spring Cloud Gateway

- **Data:** 25/11/2026
- **Status:** aceita

## Contexto

O front em micro front-ends (fase 3) vai chamar mais de um serviço: o `order-service` agora e a `sales-api` (.NET) depois. Chamando cada serviço direto, o navegador precisaria conhecer o endereço de todos, cada serviço configuraria CORS para cada origem de front, e não haveria um ponto único para criar o id de correlação que a observabilidade (fase 4) vai seguir.

## Decisão

- Um serviço `api-gateway` com Spring Cloud Gateway (WebFlux), na porta 8080, é a única entrada do front.
- Rotas por prefixo: `/api/orders/**` vai para o `order-service` sem o `/api` (`StripPrefix=1`). O endereço do serviço vem de `ORDER_SERVICE_URL`.
- CORS só no gateway, para as origens dos front-ends (`FRONTEND_ORIGINS`), expondo o header `X-Correlation-Id`.
- Filtro global de correlação: toda requisição sai com `X-Correlation-Id`, repassado ao serviço e devolvido ao cliente. Um id enviado pelo cliente só é aceito se tiver até 64 caracteres entre letras, números e hífen; fora disso, o gateway gera um UUID, porque esse valor acaba em log e texto livre permitiria forjar linhas.
- Spring Cloud 2025.1.4, a linha compatível com o Spring Boot 4.1. A 2026.0 exige o Boot 4.2, lançado em 19/11/2026.

## Alternativas consideradas

- **Front chamando os serviços direto:** sem peça nova, mas CORS e endereços espalhados, e nenhum lugar para a correlação.
- **Gateway com Eureka e Config Server:** no Docker Compose, o DNS da rede já resolve o nome dos serviços, e cada serviço tem o próprio `application.properties`. As duas peças custariam memória sem resolver problema que o projeto tem.
- **Spring Cloud Gateway Server WebMVC:** mesmo modelo de programação dos serviços, mas o WebFlux é a versão mais usada do Gateway e a que mais aparece em material e em produção.
- **Nginx como proxy reverso:** resolve roteamento e CORS, mas o filtro de correlação e as rotas futuras (por exemplo, autenticação) ficariam em configuração de Nginx em vez de código testável.

## Consequências

- Um salto de rede a mais por requisição.
- O gateway vira ponto único de falha: no Compose é um container só; em produção, seriam várias instâncias atrás de um balanceador.
- Os serviços continuam acessíveis direto nas portas 8081 e 8082 do host, para teste e debug. Em produção, só o gateway seria exposto.
- Os serviços ainda não registram o `X-Correlation-Id` no log: entra na fase 4.
