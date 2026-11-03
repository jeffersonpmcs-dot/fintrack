package com.jefferson.fintrack.dto;

import java.math.BigDecimal;

public record EvolucaoMensalResponse(int ano, int mes, BigDecimal totalReceitas,
                                    BigDecimal totalDespesas, BigDecimal saldo) {
}
