package com.jefferson.fintrack;

import com.jefferson.fintrack.dto.UsuarioCadastroRequest;
import com.jefferson.fintrack.exception.EmailJaCadastradoException;
import com.jefferson.fintrack.model.Usuario;
import com.jefferson.fintrack.repository.UsuarioRepository;
import com.jefferson.fintrack.service.UsuarioService;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UsuarioCadastroConflitoTests {

    @ParameterizedTest
    @ValueSource(strings = {"uk_usuarios_email", "PUBLIC.UK_USUARIOS_EMAIL_INDEX_2"})
    void traduzConflitoDeEmailSurgidoNaGravacao(String constraint) {
        UsuarioRepository repository = mock(UsuarioRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode(any())).thenReturn("hash-de-teste");
        when(repository.saveAndFlush(any(Usuario.class))).thenThrow(falha(constraint));

        assertThrows(EmailJaCadastradoException.class,
                () -> new UsuarioService(repository, encoder).cadastrar(request()));
    }

    @Test
    void naoDisfarcaOutraFalhaDeIntegridadeComoEmailDuplicado() {
        UsuarioRepository repository = mock(UsuarioRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        DataIntegrityViolationException falha = falha("outra_constraint");
        when(encoder.encode(any())).thenReturn("hash-de-teste");
        when(repository.saveAndFlush(any(Usuario.class))).thenThrow(falha);

        assertSame(falha, assertThrows(DataIntegrityViolationException.class,
                () -> new UsuarioService(repository, encoder).cadastrar(request())));
    }

    private DataIntegrityViolationException falha(String constraint) {
        return new DataIntegrityViolationException("Conflito de teste",
                new ConstraintViolationException("Conflito de teste",
                        new SQLException("Conflito de teste", "23505"), constraint));
    }

    private UsuarioCadastroRequest request() {
        UsuarioCadastroRequest request = new UsuarioCadastroRequest();
        request.setNome("Pessoa de teste");
        request.setEmail("pessoa@example.com");
        request.setSenha("SenhaDeTeste123!");
        return request;
    }
}
