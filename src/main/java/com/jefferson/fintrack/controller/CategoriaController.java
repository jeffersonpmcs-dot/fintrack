package com.jefferson.fintrack.controller;

import com.jefferson.fintrack.service.CategoriaService;
import org.springframework.web.bind.annotation.*;
import com.jefferson.fintrack.model.Categoria;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;

import java.util.List;

@RestController
@RequestMapping("/categorias")
public class CategoriaController {

    private final CategoriaService categoriaService;

    public CategoriaController(CategoriaService categoriaService) {
        this.categoriaService = categoriaService;
    }

    @GetMapping
    public List<Categoria> listarTodas() {
        return categoriaService.listarTodas();
    }

    @PostMapping
    public ResponseEntity<Categoria> salvar(
            @Valid @RequestBody Categoria categoria) {

        Categoria categoriaSalva = categoriaService.salvar(categoria);

        return ResponseEntity.status(201).body(categoriaSalva);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Categoria> buscarPorId(@PathVariable Long id) {

        Categoria categoria = categoriaService.buscarPorId(id);

        return ResponseEntity.ok(categoria);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Categoria> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody Categoria categoria) {

        Categoria categoriaAtualizada = categoriaService.atualizar(id, categoria);

        return ResponseEntity.ok(categoriaAtualizada);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        categoriaService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
