import { Component } from '@angular/core';
import { Routes } from '@angular/router';
import { loadRemoteModule } from '@angular-architects/native-federation';

@Component({
  selector: 'shell-home',
  template: `<h2>Plataforma de vendas</h2>
    <p>O shell só tem o layout e o roteamento. A tela de pedidos vem do remote <code>web-orders</code>,
      carregado em tempo de execução pelo endereço do <code>federation.manifest.json</code>.</p>`,
})
class HomePage {}

@Component({
  selector: 'shell-remote-unavailable',
  template: `<h2>Pedidos indisponível</h2>
    <p>O remote <code>web-orders</code> não respondeu. O resto do shell continua funcionando.</p>`,
})
class RemoteUnavailablePage {}

export const routes: Routes = [
  { path: '', component: HomePage },
  {
    path: 'orders',
    // Se o remote estiver fora do ar, a rota cai numa página de aviso em vez de quebrar o shell.
    loadChildren: () =>
      loadRemoteModule('web-orders', './routes')
        .then((remote) => remote.ORDERS_ROUTES)
        .catch((failure) => {
          console.error('Falha ao carregar web-orders', failure);
          return [{ path: '**', component: RemoteUnavailablePage }];
        }),
  },
];
