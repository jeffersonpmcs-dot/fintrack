package com.jefferson.fintrack;

import com.jefferson.fintrack.controller.CategoriaController;
import com.jefferson.fintrack.exception.GlobalExceptionHandler;
import com.jefferson.fintrack.model.Categoria;
import com.jefferson.fintrack.repository.CategoriaRepository;
import com.jefferson.fintrack.service.CategoriaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CategoriaAtualizacaoTests {

    private CategoriaRepository categorias;
    private MockMvc mvc;

    @BeforeEach
    void configurar() {
        categorias = mock(CategoriaRepository.class);
        mvc = MockMvcBuilders.standaloneSetup(new CategoriaController(
                        new CategoriaService(categorias)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void atualizacaoExistenteRetorna200MantendoIdDaCategoria() throws Exception {
        Categoria existente = new Categoria();
        existente.setId(1L);
        existente.setNome("Original");
        when(categorias.findById(1L)).thenReturn(Optional.of(existente));
        when(categorias.save(any(Categoria.class)))
                .thenAnswer(invocacao -> invocacao.getArgument(0));

        mvc.perform(put("/categorias/1").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":99,"nome":"Alimentação mensal"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nome").value("Alimentação mensal"));

        assertEquals(1L, existente.getId());
        assertEquals("Alimentação mensal", existente.getNome());
        verify(categorias).save(existente);
    }

    @Test
    void atualizacaoInexistenteRetorna404SemCriarCategoria() throws Exception {
        when(categorias.findById(99L)).thenReturn(Optional.empty());

        mvc.perform(put("/categorias/99").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Alimentação\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").value("Categoria não encontrada"));

        verify(categorias, never()).save(any(Categoria.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"nome\":\"\"}", "{\"nome\":\"   \"}", "{\"nome\":null}", "{}"})
    void nomeInvalidoRetorna400SemAlterarCategoria(String corpo) throws Exception {
        mvc.perform(put("/categorias/1").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.nome").value("O nome da categoria é obrigatório"));

        verifyNoInteractions(categorias);
    }
}
