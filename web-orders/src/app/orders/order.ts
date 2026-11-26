// Contrato com o order-service (OrderResponse e OrderCreateRequest), visto pelo gateway.
export interface Order {
  id: string;
  customerId: string;
  product: string;
  totalAmountInCents: number;
  status: 'CREATED' | 'CANCELLED';
  createdAt: string;
  updatedAt: string;
}

export interface OrderPage {
  content: Order[];
  page: { size: number; number: number; totalElements: number; totalPages: number };
}

export interface OrderCreateRequest {
  customerId: string;
  product: string;
  totalAmountInCents: number;
}
