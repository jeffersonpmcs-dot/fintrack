package com.jefferson.fintrack.controller;

import com.jefferson.fintrack.dto.UsuarioCadastroRequest;
import com.jefferson.fintrack.dto.UsuarioResponse;
import com.jefferson.fintrack.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @PostMapping
    public ResponseEntity<UsuarioResponse> cadastrar(@Valid @RequestBody UsuarioCadastroRequest request) {
        return ResponseEntity.status(201).body(usuarioService.cadastrar(request));
    }
}
