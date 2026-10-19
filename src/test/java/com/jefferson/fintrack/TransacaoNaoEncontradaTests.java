package com.jefferson.fintrack;

import com.jefferson.fintrack.controller.TransacaoController;
import com.jefferson.fintrack.exception.GlobalExceptionHandler;
import com.jefferson.fintrack.model.Transacao;
import com.jefferson.fintrack.repository.CategoriaRepository;
import com.jefferson.fintrack.repository.TransacaoRepository;
import com.jefferson.fintrack.service.TransacaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TransacaoNaoEncontradaTests {

    private TransacaoRepository transacoes;
    private MockMvc mvc;

    @BeforeEach
    void configurar() {
        transacoes = mock(TransacaoRepository.class);
        CategoriaRepository categorias = mock(CategoriaRepository.class);
        mvc = MockMvcBuilders.standaloneSetup(new TransacaoController(
                        new TransacaoService(transacoes, categorias)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void buscaInexistenteRetorna404ComMensagem() throws Exception {
        when(transacoes.findById(99L)).thenReturn(Optional.empty());
        mvc.perform(get("/transacoes/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").value("Transação não encontrada"));
    }

    @Test
    void exclusaoInexistenteRetorna404SemExcluir() throws Exception {
        when(transacoes.findById(99L)).thenReturn(Optional.empty());
        mvc.perform(delete("/transacoes/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").value("Transação não encontrada"));
        verify(transacoes, never()).delete(any(Transacao.class));
        verify(transacoes, never()).deleteById(anyLong());
    }

    @Test
    void buscaExistenteContinuaRetornando200() throws Exception {
        Transacao transacao = new Transacao();
        transacao.setId(1L);
        transacao.setDescricao("Supermercado");
        when(transacoes.findById(1L)).thenReturn(Optional.of(transacao));
        mvc.perform(get("/transacoes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.descricao").value("Supermercado"));
    }

    @Test
    void exclusaoExistenteContinuaRetornando204() throws Exception {
        Transacao transacao = new Transacao();
        when(transacoes.findById(1L)).thenReturn(Optional.of(transacao));
        mvc.perform(delete("/transacoes/1"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(transacoes).delete(transacao);
        verify(transacoes, times(1)).findById(1L);
    }
}
