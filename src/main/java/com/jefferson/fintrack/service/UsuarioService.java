package com.jefferson.fintrack.service;

import com.jefferson.fintrack.dto.UsuarioCadastroRequest;
import com.jefferson.fintrack.dto.UsuarioResponse;
import com.jefferson.fintrack.exception.EmailJaCadastradoException;
import com.jefferson.fintrack.model.Usuario;
import com.jefferson.fintrack.repository.UsuarioRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UsuarioResponse cadastrar(UsuarioCadastroRequest request) {
        Usuario usuario = new Usuario();
        usuario.setNome(request.getNome());
        usuario.setEmail(request.getEmail());

        if (usuarioRepository.existsByEmailIgnoreCase(usuario.getEmail())) {
            throw new EmailJaCadastradoException();
        }

        usuario.setSenhaHash(passwordEncoder.encode(request.getSenha()));
        try {
            usuario = usuarioRepository.saveAndFlush(usuario);
        } catch (DataIntegrityViolationException exception) {
            // Outra requisição pode ter cadastrado o e-mail após a consulta inicial.
            if (violouUnicidadeDoEmail(exception)) {
                throw new EmailJaCadastradoException();
            }
            throw exception;
        }

        return new UsuarioResponse(usuario.getId(), usuario.getNome(), usuario.getEmail());
    }

    private boolean violouUnicidadeDoEmail(DataIntegrityViolationException exception) {
        for (Throwable causa = exception; causa != null; causa = causa.getCause()) {
            if (causa instanceof ConstraintViolationException violacao
                    && "23505".equals(violacao.getSQLState())
                    && violacao.getConstraintName() != null) {
                // H2 pode retornar o nome do índice associado à constraint.
                String nome = violacao.getConstraintName().replace("\"", "")
                        .toLowerCase(Locale.ROOT);
                nome = nome.substring(nome.lastIndexOf('.') + 1);
                return nome.equals("uk_usuarios_email")
                        || nome.startsWith("uk_usuarios_email_index_");
            }
        }
        return false;
    }
}
