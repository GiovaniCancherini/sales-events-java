import { HttpClient, HttpResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Order, OrderCreateRequest, OrderPage } from './order';

// ponytail: endereço fixo do gateway; vira configuração em runtime quando houver deploy fora da máquina
const API_BASE_URL = 'http://localhost:8080/api/orders';

// Sem providedIn: 'root'. O serviço é registrado nas rotas do remote, então funciona igual
// rodando sozinho ou dentro do shell, sem o shell precisar saber que ele existe.
@Injectable()
export class OrdersApi {
  private readonly http = inject(HttpClient);

  // observe: 'response' para ler o X-Correlation-Id que o gateway devolve (exposto no CORS).
  list(): Observable<HttpResponse<OrderPage>> {
    return this.http.get<OrderPage>(`${API_BASE_URL}?page=0&size=20`, { observe: 'response' });
  }

  create(request: OrderCreateRequest): Observable<HttpResponse<Order>> {
    return this.http.post<Order>(API_BASE_URL, request, { observe: 'response' });
  }

  cancel(id: string): Observable<HttpResponse<Order>> {
    return this.http.post<Order>(`${API_BASE_URL}/${id}/cancel`, null, { observe: 'response' });
  }
}
