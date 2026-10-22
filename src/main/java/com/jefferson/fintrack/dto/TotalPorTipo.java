package com.jefferson.fintrack.dto;

import com.jefferson.fintrack.model.TipoTransacao;

import java.math.BigDecimal;

public record TotalPorTipo(TipoTransacao tipo, BigDecimal total) {
}
