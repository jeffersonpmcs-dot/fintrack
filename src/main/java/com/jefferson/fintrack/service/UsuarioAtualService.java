package com.jefferson.fintrack.service;

import com.jefferson.fintrack.model.Usuario;
import com.jefferson.fintrack.repository.UsuarioRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioAtualService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioAtualService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional(readOnly = true)
    public Usuario obter() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("Autenticação necessária");
        }

        return usuarioRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("Autenticação necessária"));
    }
}
