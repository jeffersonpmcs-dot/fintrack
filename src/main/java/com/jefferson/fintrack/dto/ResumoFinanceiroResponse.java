package com.jefferson.fintrack.dto;

import java.math.BigDecimal;

public record ResumoFinanceiroResponse(
        BigDecimal totalReceitas,
        BigDecimal totalDespesas,
        BigDecimal saldo) {
}
