import { provideHttpClient } from '@angular/common/http';
import { Routes } from '@angular/router';
import { OrdersApi } from './orders-api';
import { OrdersPage } from './orders-page';

// O que o remote expõe ao shell (federation.config.mjs, './routes'). Os providers ficam aqui,
// e não no app.config: dentro do shell, o app.config do remote nem é executado.
export const ORDERS_ROUTES: Routes = [
  {
    path: '',
    providers: [provideHttpClient(), OrdersApi],
    component: OrdersPage,
  },
];
