package com.jefferson.fintrack;

import com.jefferson.fintrack.model.Usuario;
import com.jefferson.fintrack.service.UsuarioAtualService;

import static org.mockito.Mockito.*;

final class UsuarioAtualMock {

    private UsuarioAtualMock() {
    }

    static UsuarioAtualService criar() {
        Usuario usuario = mock(Usuario.class);
        when(usuario.getId()).thenReturn(1L);
        UsuarioAtualService service = mock(UsuarioAtualService.class);
        when(service.obter()).thenReturn(usuario);
        return service;
    }
}
