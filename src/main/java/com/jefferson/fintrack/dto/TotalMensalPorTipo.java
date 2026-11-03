package com.jefferson.fintrack.dto;

import com.jefferson.fintrack.model.TipoTransacao;

import java.math.BigDecimal;

public record TotalMensalPorTipo(Integer mes, TipoTransacao tipo, BigDecimal total) {
}
