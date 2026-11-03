package com.jefferson.fintrack;

import com.jefferson.fintrack.controller.UsuarioController;
import com.jefferson.fintrack.exception.GlobalExceptionHandler;
import com.jefferson.fintrack.model.Usuario;
import com.jefferson.fintrack.repository.UsuarioRepository;
import com.jefferson.fintrack.service.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UsuarioCadastroTests {

    private static final String SENHA = "SenhaDeTeste123!";

    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private UsuarioService service;
    @Autowired
    private PasswordEncoder encoder;
    @Autowired
    private ObjectMapper mapper;

    private MockMvc mvc;

    @BeforeEach
    void configurar() {
        mvc = MockMvcBuilders.standaloneSetup(new UsuarioController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void cadastraNormalizaEmailEDevolveSomenteDadosPublicos() throws Exception {
        mvc.perform(post("/usuarios").contentType(MediaType.APPLICATION_JSON)
                        .content(json("  Pessoa@Example.com  ", SENHA)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", aMapWithSize(3)))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.nome").value("Pessoa de teste"))
                .andExpect(jsonPath("$.email").value("pessoa@example.com"))
                .andExpect(jsonPath("$.senha").doesNotExist())
                .andExpect(jsonPath("$.senhaHash").doesNotExist());

        Usuario salvo = usuarios.findByEmailIgnoreCase("pessoa@example.com").orElseThrow();
        assertNotEquals(SENHA, salvo.getSenhaHash());
        assertTrue(encoder.matches(SENHA, salvo.getSenhaHash()));
        assertFalse(encoder.matches("OutraSenha123!", salvo.getSenhaHash()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"pessoa@example.com", "  PESSOA@EXAMPLE.COM  "})
    void emailDuplicadoRetorna409SemCriarOutraConta(String email) throws Exception {
        mvc.perform(post("/usuarios").contentType(MediaType.APPLICATION_JSON)
                        .content(json("pessoa@example.com", SENHA)))
                .andExpect(status().isCreated());

        mvc.perform(post("/usuarios").contentType(MediaType.APPLICATION_JSON)
                        .content(json(email, SENHA)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem").value("E-mail já cadastrado"));

        assertEquals(1, usuarios.count());
    }

    @Test
    void mesmaSenhaEmContasDiferentesProduzHashesDiferentes() throws Exception {
        for (String email : new String[]{"primeiro@example.com", "segundo@example.com"}) {
            mvc.perform(post("/usuarios").contentType(MediaType.APPLICATION_JSON)
                            .content(json(email, SENHA)))
                    .andExpect(status().isCreated());
        }

        String primeiro = usuarios.findByEmailIgnoreCase("primeiro@example.com").orElseThrow().getSenhaHash();
        String segundo = usuarios.findByEmailIgnoreCase("segundo@example.com").orElseThrow().getSenhaHash();
        assertNotEquals(primeiro, segundo);
        assertTrue(encoder.matches(SENHA, primeiro));
        assertTrue(encoder.matches(SENHA, segundo));
    }

    @ParameterizedTest
    @MethodSource("dadosInvalidos")
    void dadosInvalidosRetornam400SemPersistir(String campo, String valor) throws Exception {
        Map<String, String> dados = dados("pessoa@example.com", SENHA);
        dados.put(campo, valor);

        mvc.perform(post("/usuarios").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dados)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$." + campo).isString());

        assertEquals(0, usuarios.count());
    }

    @ParameterizedTest
    @MethodSource("senhasAceitas")
    void aceitaLimitesEUnicodeSemAlterarASenha(String senha) throws Exception {
        mvc.perform(post("/usuarios").contentType(MediaType.APPLICATION_JSON)
                        .content(json("pessoa@example.com", senha)))
                .andExpect(status().isCreated());

        String hash = usuarios.findByEmailIgnoreCase("pessoa@example.com").orElseThrow().getSenhaHash();
        assertTrue(encoder.matches(senha, hash));
        assertFalse(encoder.matches(senha + "!", hash));
    }

    private static Stream<Arguments> dadosInvalidos() {
        return Stream.of(
                Arguments.of("nome", " "), Arguments.of("nome", null),
                Arguments.of("nome", "a".repeat(101)),
                Arguments.of("email", " "), Arguments.of("email", null),
                Arguments.of("email", "invalido"),
                Arguments.of("email", "a".repeat(243) + "@example.com"),
                Arguments.of("senha", " "), Arguments.of("senha", null),
                Arguments.of("senha", "a".repeat(11)),
                Arguments.of("senha", "a".repeat(129)));
    }

    private static Stream<String> senhasAceitas() {
        return Stream.of("a".repeat(12), "a".repeat(128), "  Senha com acentuação 🔐  ");
    }

    private String json(String email, String senha) throws Exception {
        return mapper.writeValueAsString(dados(email, senha));
    }

    private Map<String, String> dados(String email, String senha) {
        Map<String, String> dados = new HashMap<>();
        dados.put("nome", "  Pessoa de teste  ");
        dados.put("email", email);
        dados.put("senha", senha);
        return dados;
    }
}
