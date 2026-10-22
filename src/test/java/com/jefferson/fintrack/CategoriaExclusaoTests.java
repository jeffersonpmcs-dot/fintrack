package com.jefferson.fintrack;

import com.jefferson.fintrack.controller.CategoriaController;
import com.jefferson.fintrack.exception.GlobalExceptionHandler;
import com.jefferson.fintrack.model.Categoria;
import com.jefferson.fintrack.model.Transacao;
import com.jefferson.fintrack.repository.CategoriaRepository;
import com.jefferson.fintrack.repository.TransacaoRepository;
import com.jefferson.fintrack.service.CategoriaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CategoriaExclusaoTests {

    private CategoriaRepository categorias;
    private TransacaoRepository transacoes;
    private MockMvc mvc;

    @BeforeEach
    void configurar() {
        categorias = mock(CategoriaRepository.class);
        transacoes = mock(TransacaoRepository.class);
        mvc = MockMvcBuilders.standaloneSetup(new CategoriaController(
                        new CategoriaService(categorias, transacoes)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void exclusaoSemTransacoesRetorna204() throws Exception {
        Categoria categoria = new Categoria();
        categoria.setId(1L);
        when(categorias.findById(1L)).thenReturn(Optional.of(categoria));
        when(transacoes.existsByCategoriaId(1L)).thenReturn(false);

        mvc.perform(delete("/categorias/1"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(categorias).delete(categoria);
        verify(categorias).flush();
    }

    @Test
    void exclusaoInexistenteRetorna404SemExcluir() throws Exception {
        when(categorias.findById(99L)).thenReturn(Optional.empty());

        mvc.perform(delete("/categorias/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").value("Categoria não encontrada"));

        verifyNoInteractions(transacoes);
        verify(categorias, never()).delete(any(Categoria.class));
        verify(categorias, never()).flush();
    }

    @Test
    void exclusaoComTransacoesRetorna409SemExcluir() throws Exception {
        when(categorias.findById(1L)).thenReturn(Optional.of(new Categoria()));
        when(transacoes.existsByCategoriaId(1L)).thenReturn(true);

        mvc.perform(delete("/categorias/1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem").value(
                        "Não é possível excluir uma categoria com transações vinculadas"));

        verify(categorias, never()).delete(any(Categoria.class));
        verify(categorias, never()).flush();
        verify(transacoes, never()).delete(any(Transacao.class));
    }

    @Test
    void vinculoDetectadoPeloBancoTambemRetorna409() throws Exception {
        when(categorias.findById(1L)).thenReturn(Optional.of(new Categoria()));
        when(transacoes.existsByCategoriaId(1L)).thenReturn(false);
        doThrow(new DataIntegrityViolationException("Restrição de chave estrangeira"))
                .when(categorias).flush();

        mvc.perform(delete("/categorias/1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem").value(
                        "Não é possível excluir uma categoria com transações vinculadas"));

        verify(transacoes, never()).delete(any(Transacao.class));
    }
}
