# Roteiro de teste manual: Fase 3 (gateway e micro front-ends)

Ver o front entrando por um ponto só: o `api-gateway` roteia, controla o CORS e marca cada requisição com um id de correlação. Depois, o shell em Angular carregando o remote de pedidos em tempo de execução.

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

**O que prova:** só as origens dos front-ends podem chamar a API pelo navegador. O cenário 7 repete isso no navegador de verdade.

## 5. Remote rodando sozinho

1. Em dois terminais, a partir da raiz do repo (na primeira vez, rode `npm install` em cada pasta):

   ```bash
   cd web-orders && npm start
   cd web-shell && npm start
   ```

2. Abra http://localhost:4201: a tela de pedidos aparece com a faixa "web-orders rodando sozinho". Crie e cancele um pedido.
   - A tela mostra o `X-Correlation-Id` da última requisição.
   - Log do `inventory-service`: `Estoque reservado` e `Reserva liberada`.

**O que prova:** o remote é um app completo, que um time desenvolve e testa sem o shell.

## 6. Shell carregando o remote em tempo de execução

1. Abra http://localhost:4200 e clique em `Pedidos`.
2. DevTools (F12) > `Network`: ao clicar, o navegador baixa `http://localhost:4201/remoteEntry.json` e os arquivos do remote. Antes do clique, nada do remote é baixado.
3. Ainda na aba `Network`, recarregue a página com o filtro `_angular_core.` (o build renomeia `@angular/core` para `_angular_core.<hash>-dev.js`): aparece uma linha só, vinda de `localhost:4200`. O remote também tem a sua cópia no `dist`, mas o navegador nunca baixa: shell e remote usam a mesma (singleton).

**O que prova:** o shell não conhece o código do remote em build; só o endereço, lido do `federation.manifest.json` quando a página abre. Publicar uma versão nova do remote não exige rebuild do shell.

## 7. CORS no navegador

1. Em http://localhost:4200, na tela de pedidos, crie um pedido: funciona, porque `localhost:4200` está nas origens liberadas do gateway.
2. Pare o gateway (`docker compose stop api-gateway`) e clique em `Atualizar lista`: aparece "Sem resposta do gateway". Suba de novo (`docker compose start api-gateway`).

## 8. Remote fora do ar

1. Pare o `web-orders` (Ctrl+C no terminal dele).
2. Em http://localhost:4200, recarregue a página e clique em `Pedidos`: aparece "Pedidos indisponível", e o menu e a página inicial continuam funcionando.
3. Suba o `web-orders` de novo e recarregue: a tela volta.

**O que prova:** um remote com problema não derruba o shell. É o tipo de falha que só existe em micro front-end: a tela depende de um deploy que não é o seu.
