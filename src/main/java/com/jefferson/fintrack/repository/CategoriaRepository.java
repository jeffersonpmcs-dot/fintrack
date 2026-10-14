package com.jefferson.fintrack.repository;

import com.jefferson.fintrack.model.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {

}