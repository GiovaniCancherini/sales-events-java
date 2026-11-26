# 10. Micro front-ends com Native Federation, em workspaces separados

- **Data:** 25/11/2026
- **Status:** aceita

## Contexto

O front da plataforma é dividido por domínio: um shell (layout e roteamento) e um remote por área (pedidos agora, catálogo depois). O ponto de micro front-end é cada remote ser construído e publicado sem rebuild do shell, e o projeto precisa mostrar isso funcionando, com os problemas que aparecem na prática.

## Decisão

- **Dois workspaces Angular separados**, `web-shell` (porta 4200) e `web-orders` (porta 4201), cada um com o seu `package.json`, build e dependências. É a mesma regra do back-end: serviço não compartilha build.
- **Angular 22.2.6, fixado em versão exata** nos dois. A 22.3 saiu em 18/11/2026 e a Native Federation 22.2.5 exige `~22.2.0`; com `^22.2.0`, o npm subiria para a 22.3 e quebraria a federação.
- **Native Federation** (`@angular-architects/native-federation` 22.2.5), que funciona com o build padrão do Angular (esbuild) e com ES modules e import maps do navegador.
- **O remote expõe rotas (`./routes`), não um componente.** O shell só diz "em `/orders`, carregue o que o remote oferecer"; o que fica abaixo de `/orders` é do remote. Os providers do remote (`HttpClient`, `OrdersApi`) ficam nas rotas expostas, porque dentro do shell o `app.config` do remote não é executado.
- **Shell com manifesto em runtime** (`federation.manifest.json`): o endereço do remote é lido quando o shell abre, então trocar a versão ou o endereço do remote não exige rebuild do shell.
- **Angular, RxJS e o router compartilhados como singleton com versão estrita.** Uma cópia só em runtime; versão divergente entre shell e remote vira erro explícito em vez de bug silencioso (dois `Router`, duas zonas de injeção).
- **Remote fora do ar não derruba o shell:** a rota `/orders` captura a falha do carregamento e mostra uma página de aviso.
- O front fala só com o `api-gateway` (ADR 9), que cuida do CORS e devolve o `X-Correlation-Id`, mostrado na tela de pedidos.

## Alternativas consideradas

- **Um workspace com dois apps:** um `node_modules` só e versão garantida pela estrutura, mas o problema de versões divergentes deixa de existir, e com ele a parte mais real de MFE. O build fica acoplado.
- **Monorepo Nx:** padrão em empresas grandes com MFE (geradores, cache, `affected`), mas é mais uma camada sobre o Angular e a federação, esconde o mecanismo e costuma atrasar no suporte a versões novas do Angular.
- **Webpack Module Federation** (`@angular-architects/module-federation`): o modelo anterior, que exige trocar o builder padrão (esbuild) por um builder de Webpack. Faz sentido em base Angular que ainda usa Webpack.
- **Web Components (Angular Elements) ou iframe:** isolam mais (frameworks e versões diferentes), mas sem compartilhar dependências: cada remote carrega o próprio Angular.

## Cenário legado

Muita empresa tem apps em Angular antigo (NgModule, build Webpack, versões anteriores à 12). Module federation exige Webpack 5, que só chegou no Angular 12, então um app desses não vira remote direto. Caminhos, do menos ao mais invasivo:

- Empacotar o app legado (ou partes dele) como Web Component e carregá-lo no shell novo, sem compartilhar dependências. Migra-se tela por tela.
- Atualizar o legado até uma versão com Webpack 5 e usar Webpack Module Federation, convivendo com remotes novos em Native Federation (as duas se comunicam com adaptadores).
- Reescrever por domínio, começando pelo que muda mais.

## Consequências

- Dois `node_modules` (algumas centenas de MB cada) e configuração duplicada; atualizar o Angular é trabalho em cada workspace, na mesma versão, ou a federação recusa.
- Shell e remote precisam subir juntos em desenvolvimento (`npm start` em cada um).
- A URL do gateway está fixa no remote (`http://localhost:8080`). Vira configuração em runtime quando houver deploy fora da máquina.
- Ainda sem Dockerfile para os fronts; entram quando o critério "`docker compose up` sobe tudo e o front roda" for atacado.
