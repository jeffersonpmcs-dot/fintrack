package com.jefferson.fintrack;

import com.jefferson.fintrack.repository.UsuarioRepository;
import com.jefferson.fintrack.service.UsuarioAtualService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class UsuarioAtualServiceTests {

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void exigeAutenticacaoMesmoQuandoChamadoForaDoController() {
        SecurityContextHolder.clearContext();
        UsuarioRepository repository = mock(UsuarioRepository.class);
        assertThrows(AuthenticationCredentialsNotFoundException.class,
                () -> new UsuarioAtualService(repository).obter());
        verifyNoInteractions(repository);
    }

    @Test
    void contextoAnonimoNaoPodeSelecionarUmUsuario() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "teste", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        UsuarioRepository repository = mock(UsuarioRepository.class);
        assertThrows(AuthenticationCredentialsNotFoundException.class,
                () -> new UsuarioAtualService(repository).obter());
        verifyNoInteractions(repository);
    }

    @Test
    void contaAusenteNoBancoNaoPermiteOperacaoFinanceira() {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "ausente@example.com", null, List.of(new SimpleGrantedAuthority("ROLE_USUARIO"))));
        UsuarioRepository repository = mock(UsuarioRepository.class);
        assertThrows(AuthenticationCredentialsNotFoundException.class,
                () -> new UsuarioAtualService(repository).obter());
    }
}
