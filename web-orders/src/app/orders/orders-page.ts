import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { Order } from './order';
import { OrdersApi } from './orders-api';

@Component({
  selector: 'orders-page',
  imports: [FormsModule, CurrencyPipe, DatePipe],
  templateUrl: './orders-page.html',
  styleUrl: './orders-page.css',
})
export class OrdersPage implements OnInit {
  private readonly api = inject(OrdersApi);

  protected readonly orders = signal<Order[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly lastCorrelationId = signal<string | null>(null);

  protected customerId = 'cliente-1';
  protected product = 'Notebook';
  protected totalAmount = 4500;

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.call(this.api.list(), (page) => this.orders.set(page?.content ?? []));
  }

  protected create(): void {
    const request = { customerId: this.customerId, product: this.product, totalAmount: this.totalAmount };
    this.call(this.api.create(request), () => this.load());
  }

  protected cancel(order: Order): void {
    this.call(this.api.cancel(order.id), () => this.load());
  }

  private call<T>(request: Observable<HttpResponse<T>>, onSuccess: (body: T | null) => void): void {
    this.error.set(null);
    request.subscribe({
      next: (response) => {
        this.lastCorrelationId.set(response.headers.get('X-Correlation-Id'));
        onSuccess(response.body);
      },
      error: (failure: HttpErrorResponse) => {
        this.lastCorrelationId.set(failure.headers?.get('X-Correlation-Id') ?? null);
        this.error.set(failure.status === 0
          ? 'Sem resposta do gateway (fora do ar ou bloqueado por CORS).'
          : `${failure.status}: ${failure.error?.message ?? failure.message}`);
      },
    });
  }
}
