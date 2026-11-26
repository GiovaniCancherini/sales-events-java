import { Routes } from '@angular/router';

// Rodando sozinho (porta 4201), o remote carrega as mesmas rotas que expõe ao shell.
export const routes: Routes = [
  { path: '', loadChildren: () => import('./orders/orders.routes').then((m) => m.ORDERS_ROUTES) },
];
