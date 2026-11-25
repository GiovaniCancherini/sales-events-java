# Roteiro de teste manual: Fase 3 (gateway e micro front-ends)

Ver o front entrando por um ponto só: o `api-gateway` roteia, controla o CORS e marca cada requisição com um id de correlação. A parte do Angular (shell e remote) entra neste roteiro quando existir.

## Preparação

1. Suba com o gateway novo:

   ```bash
   docker compose -f docker-compose.yml -f docker-compose.tools.yml up -d --build
   ```

2. Insomnia: reimporte [docs/insomnia/sales-events.json](insomnia/sales-events.json). A coleção ganhou a variável `api_gateway` (`http://localhost:8080`) e as requisições `09` e `10`.

## 1. Rota: `/api/orders` chega ao serviço como `/orders`

1. `09 Listar pedidos pelo gateway`.
2. Resultado esperado: a mesma lista de `07 Listar pedidos`, que chama o `order-service` direto na 8081.

**O que prova:** o front só conhece o gateway e o prefixo `/api`; o caminho real do serviço fica escondido atrás dele.

## 2. Pedido criado pelo gateway percorre o caminho inteiro

1. `10 Criar pedido pelo gateway`.
2. Resultado esperado:
   - Resposta 201 com o pedido.
   - DBeaver (`orders`): a linha `created` em `outbox_events`, com `sent_at` preenchido.
   - Log do `inventory-service`: `Estoque reservado` para `cliente-gateway`.

**O que prova:** o gateway é só a porta de entrada; outbox e consumidor seguem iguais.

## 3. Id de correlação

1. `09` manda o header `X-Correlation-Id: teste-insomnia-1`. Na aba de headers da resposta, o mesmo valor volta.
2. Apague o header e rode de novo: volta um UUID gerado pelo gateway.
3. Troque o valor por `teste com espaço` e rode: volta um UUID novo, não o texto enviado.

**O que prova:** toda requisição tem um id que a fase 4 vai seguir pelos logs dos serviços. O gateway só aceita do cliente um id curto, com letras, números e hífen, porque esse valor vai parar em log e texto livre permitiria forjar linhas.

## 4. CORS

O Insomnia não aplica CORS (quem aplica é o navegador), então aqui dá para ver só a resposta do preflight:

```bash
curl -i -X OPTIONS http://localhost:8080/api/orders -H "Origin: http://localhost:4200" -H "Access-Control-Request-Method: POST"
curl -i -X OPTIONS http://localhost:8080/api/orders -H "Origin: https://site-qualquer.example" -H "Access-Control-Request-Method: POST"
```

Resultado esperado: o primeiro responde 200 com `Access-Control-Allow-Origin: http://localhost:4200`; o segundo, 403.

**O que prova:** só as origens dos front-ends podem chamar a API pelo navegador. O teste de verdade, com o shell em Angular, entra quando ele existir.
