package com.giovanicancherini.orderservice;

import java.math.BigDecimal;

/**
 * Conversão na borda: o contrato (API e eventos) trafega centavos inteiros,
 * o domínio e o banco usam decimal exato com 2 casas (ADR 11).
 */
public final class Cents {

    // 15 dígitos: o maior inteiro que um consumidor em JavaScript (double) lê sem perder precisão.
    public static final long MAXIMUM = 999_999_999_999_999L;

    private Cents() {
    }

    public static long fromAmount(BigDecimal amount) {
        return amount.movePointRight(2).longValueExact();
    }

    public static BigDecimal toAmount(long cents) {
        return BigDecimal.valueOf(cents, 2);
    }
}
