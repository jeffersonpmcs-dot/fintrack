package com.jefferson.fintrack;

import com.jefferson.fintrack.model.Usuario;
import com.jefferson.fintrack.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

abstract class ContextoUsuarioTests {

    @Autowired
    private UsuarioRepository usuarios;

    protected Usuario usuarioAtual;

    @BeforeEach
    void configurarContaDeTeste() {
        usuarioAtual = new Usuario();
        usuarioAtual.setNome("Pessoa de teste");
        usuarioAtual.setEmail("contexto@example.com");
        usuarioAtual.setSenhaHash("hash-ficticio-para-fixtures");
        usuarioAtual = usuarios.saveAndFlush(usuarioAtual);

        var contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                usuarioAtual.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_USUARIO"))));
        SecurityContextHolder.setContext(contexto);
    }

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }
}
