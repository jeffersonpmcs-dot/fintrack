package com.jefferson.fintrack;

import com.jefferson.fintrack.controller.CategoriaController;
import com.jefferson.fintrack.exception.GlobalExceptionHandler;
import com.jefferson.fintrack.model.Categoria;
import com.jefferson.fintrack.repository.CategoriaRepository;
import com.jefferson.fintrack.service.CategoriaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CategoriaBuscaTests {

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
    void buscaInexistenteRetorna404ComMensagem() throws Exception {
        when(categorias.findById(99L)).thenReturn(Optional.empty());

        mvc.perform(get("/categorias/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").value("Categoria não encontrada"));
    }

    @Test
    void buscaExistenteContinuaRetornando200() throws Exception {
        Categoria categoria = new Categoria();
        categoria.setId(1L);
        categoria.setNome("Alimentação");
        when(categorias.findById(1L)).thenReturn(Optional.of(categoria));

        mvc.perform(get("/categorias/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nome").value("Alimentação"));
    }
}
