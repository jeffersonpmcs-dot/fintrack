package com.jefferson.fintrack;

import com.jefferson.fintrack.controller.TransacaoController;
import com.jefferson.fintrack.exception.GlobalExceptionHandler;
import com.jefferson.fintrack.model.Categoria;
import com.jefferson.fintrack.model.Transacao;
import com.jefferson.fintrack.repository.CategoriaRepository;
import com.jefferson.fintrack.repository.TransacaoRepository;
import com.jefferson.fintrack.service.TransacaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TransacaoCategoriaTests {

    private TransacaoRepository transacoes;
    private CategoriaRepository categorias;
    private MockMvc mvc;

    private static final String REQUEST = """
            {"descricao":"Supermercado","valor":200.00,"data":"2026-10-15",
             "tipo":"DESPESA","categoriaId":999}
            """;

    @BeforeEach
    void configurar() {
        transacoes = mock(TransacaoRepository.class);
        categorias = mock(CategoriaRepository.class);
        mvc = MockMvcBuilders.standaloneSetup(new TransacaoController(
                        new TransacaoService(transacoes, categorias)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void cadastroComCategoriaInexistenteRetorna404SemSalvar() throws Exception {
        when(categorias.findById(999L)).thenReturn(Optional.empty());
        mvc.perform(post("/transacoes").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").value("Categoria não encontrada"));
        verify(transacoes, never()).save(any());
    }

    @Test
    void atualizacaoComCategoriaInexistenteRetorna404SemAlterarOuSalvar() throws Exception {
        Transacao existente = new Transacao();
        existente.setDescricao("Original");
        when(transacoes.findById(1L)).thenReturn(Optional.of(existente));
        when(categorias.findById(999L)).thenReturn(Optional.empty());
        mvc.perform(put("/transacoes/1").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").value("Categoria não encontrada"));
        org.junit.jupiter.api.Assertions.assertEquals("Original", existente.getDescricao());
        verify(transacoes, never()).save(any());
    }

    @Test
    void cadastroComCategoriaExistenteContinuaRetornando201() throws Exception {
        when(categorias.findById(999L)).thenReturn(Optional.of(new Categoria()));
        when(transacoes.save(any())).thenAnswer(invocacao -> invocacao.getArgument(0));
        mvc.perform(post("/transacoes").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.descricao").value("Supermercado"));
        verify(transacoes).save(any());
    }

    @Test
    void atualizacaoComCategoriaExistenteContinuaRetornando200() throws Exception {
        when(transacoes.findById(1L)).thenReturn(Optional.of(new Transacao()));
        when(categorias.findById(999L)).thenReturn(Optional.of(new Categoria()));
        when(transacoes.save(any())).thenAnswer(invocacao -> invocacao.getArgument(0));
        mvc.perform(put("/transacoes/1").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descricao").value("Supermercado"));
        verify(transacoes).save(any());
    }

    @Test
    void transacaoInexistenteContinuaRetornando404() throws Exception {
        when(transacoes.findById(1L)).thenReturn(Optional.empty());
        mvc.perform(put("/transacoes/1").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").value("Transação não encontrada"));
        verifyNoInteractions(categorias);
        verify(transacoes, never()).save(any());
    }

    @Test
    void categoriaNaoInformadaContinuaRetornando400() throws Exception {
        mvc.perform(post("/transacoes").contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST.replace("\"categoriaId\":999", "\"categoriaId\":null")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.categoriaId").value("A categoria é obrigatória"));
        verifyNoInteractions(transacoes, categorias);
    }
}
