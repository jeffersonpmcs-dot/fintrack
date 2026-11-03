package com.jefferson.fintrack;

import com.jefferson.fintrack.model.Usuario;
import com.jefferson.fintrack.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UsuarioRepositoryTests {

    // Valor fictício: esta etapa não implementa geração nem verificação de hashes.
    private static final String HASH_DE_TESTE = "hash-ficticio-para-teste-de-persistencia";

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void salvaUsuarioERecuperaDadosDoBanco() {
        Usuario usuario = usuarios.saveAndFlush(criarUsuario("  Pessoa@Example.com  "));
        Long id = usuario.getId();
        assertNotNull(id);
        entityManager.clear();

        Usuario recuperado = usuarios.findById(id).orElseThrow();
        assertEquals("Pessoa de teste", recuperado.getNome());
        assertEquals("pessoa@example.com", recuperado.getEmail());
        assertEquals(HASH_DE_TESTE, recuperado.getSenhaHash());
    }

    @Test
    void consultaUsuarioPorEmailSemDiferenciarMaiusculas() {
        Usuario salvo = usuarios.saveAndFlush(criarUsuario("pessoa@example.com"));
        entityManager.clear();

        Usuario encontrado = usuarios.findByEmailIgnoreCase("PESSOA@EXAMPLE.COM").orElseThrow();

        assertEquals(salvo.getId(), encontrado.getId());
        assertTrue(usuarios.existsByEmailIgnoreCase("PESSOA@EXAMPLE.COM"));
    }

    @Test
    void emailInexistenteRetornaOptionalVazio() {
        assertTrue(usuarios.findByEmailIgnoreCase("ausente@example.com").isEmpty());
        assertFalse(usuarios.existsByEmailIgnoreCase("ausente@example.com"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"pessoa@example.com", "  PESSOA@EXAMPLE.COM  "})
    void bancoRejeitaEmailDuplicadoAposNormalizacao(String email) {
        usuarios.saveAndFlush(criarUsuario("pessoa@example.com"));

        assertThrows(DataIntegrityViolationException.class,
                () -> usuarios.saveAndFlush(criarUsuario(email)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"nome", "email", "senhaHash"})
    void rejeitaPersistenciaSemCampoObrigatorio(String campo) {
        Usuario usuario = criarUsuario("pessoa@example.com");
        switch (campo) {
            case "nome" -> usuario.setNome(" ");
            case "email" -> usuario.setEmail(" ");
            case "senhaHash" -> usuario.setSenhaHash(" ");
            default -> throw new IllegalArgumentException(campo);
        }

        assertThrows(ConstraintViolationException.class, () -> usuarios.saveAndFlush(usuario));
    }

    @Test
    void rejeitaPersistenciaComEmailInvalido() {
        assertThrows(ConstraintViolationException.class,
                () -> usuarios.saveAndFlush(criarUsuario("email-invalido")));
    }

    @Test
    void jsonDoUsuarioNaoExpoeHashDaSenha() throws Exception {
        String json = objectMapper.writeValueAsString(criarUsuario("pessoa@example.com"));

        assertTrue(json.contains("pessoa@example.com"));
        assertFalse(json.contains("senhaHash"));
        assertFalse(json.contains(HASH_DE_TESTE));
    }

    private Usuario criarUsuario(String email) {
        Usuario usuario = new Usuario();
        usuario.setNome("Pessoa de teste");
        usuario.setEmail(email);
        usuario.setSenhaHash(HASH_DE_TESTE);
        return usuario;
    }
}
