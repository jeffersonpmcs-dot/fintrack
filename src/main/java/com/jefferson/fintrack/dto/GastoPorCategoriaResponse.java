package com.jefferson.fintrack.dto;

import java.math.BigDecimal;

public record GastoPorCategoriaResponse(Long categoriaId, String categoriaNome, BigDecimal totalDespesas) {
}
