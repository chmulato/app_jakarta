package com.exemplo.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Testes da entidade Produto")
class ProdutoTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("Deve criar produto válido")
    void deveCriarProdutoValido() {
        // Given
        Produto produto = new Produto();
        produto.setNome("Produto Teste");
        produto.setDescricao("Descrição do produto");
        produto.setPreco(new BigDecimal("99.99"));
        produto.setEstoque(10);

        // When
        Set<ConstraintViolation<Produto>> violations = validator.validate(produto);

        // Then
        assertThat(violations).isEmpty();
        assertThat(produto.getNome()).isEqualTo("Produto Teste");
        assertThat(produto.getDescricao()).isEqualTo("Descrição do produto");
        assertThat(produto.getPreco()).isEqualTo(new BigDecimal("99.99"));
        assertThat(produto.getEstoque()).isEqualTo(10);
        assertThat(produto.isAtivo()).isTrue(); // valor padrão
    }

    @Test
    @DisplayName("Não deve aceitar nome nulo")
    void naoDeveAceitarNomeNulo() {
        // Given
        Produto produto = new Produto();
        produto.setDescricao("Descrição");
        produto.setPreco(new BigDecimal("99.99"));
        produto.setEstoque(10);

        // When
        Set<ConstraintViolation<Produto>> violations = validator.validate(produto);

        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .isEqualTo("Nome é obrigatório");
    }

    @Test
    @DisplayName("Não deve aceitar preço negativo")
    void naoDeveAceitarPrecoNegativo() {
        // Given
        Produto produto = new Produto();
        produto.setNome("Produto Teste");
        produto.setDescricao("Descrição");
        produto.setPreco(new BigDecimal("-10.00"));
        produto.setEstoque(10);

        // When
        Set<ConstraintViolation<Produto>> violations = validator.validate(produto);

        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .isEqualTo("Preço deve ser maior que zero");
    }

    @Test
    @DisplayName("Não deve aceitar estoque negativo")
    void naoDeveAceitarEstoqueNegativo() {
        // Given
        Produto produto = new Produto();
        produto.setNome("Produto Teste");
        produto.setDescricao("Descrição");
        produto.setPreco(new BigDecimal("99.99"));
        produto.setEstoque(-5);

        // When
        Set<ConstraintViolation<Produto>> violations = validator.validate(produto);

        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .isEqualTo("Estoque não pode ser negativo");
    }

    @Test
    @DisplayName("Deve verificar se produto está disponível")
    void deveVerificarSeProdutoEstaDisponivel() {
        // Given
        Produto produtoDisponivel = new Produto();
        produtoDisponivel.setEstoque(10);
        produtoDisponivel.setAtivo(true);

        Produto produtoSemEstoque = new Produto();
        produtoSemEstoque.setEstoque(0);
        produtoSemEstoque.setAtivo(true);

        Produto produtoInativo = new Produto();
        produtoInativo.setEstoque(10);
        produtoInativo.setAtivo(false);

        // Then
        assertThat(produtoDisponivel.isDisponivel()).isTrue();
        assertThat(produtoSemEstoque.isDisponivel()).isFalse();
        assertThat(produtoInativo.isDisponivel()).isFalse();
    }

    @Test
    @DisplayName("Deve aumentar estoque corretamente")
    void deveAumentarEstoqueCorretamente() {
        // Given
        Produto produto = new Produto();
        produto.setEstoque(10);

        // When
        produto.aumentarEstoque(5);

        // Then
        assertThat(produto.getEstoque()).isEqualTo(15);
    }

    @Test
    @DisplayName("Deve reduzir estoque corretamente")
    void deveReduzirEstoqueCorretamente() {
        // Given
        Produto produto = new Produto();
        produto.setEstoque(10);

        // When
        produto.reduzirEstoque(3);

        // Then
        assertThat(produto.getEstoque()).isEqualTo(7);
    }

    @Test
    @DisplayName("Deve lançar exceção ao reduzir mais estoque do que disponível")
    void deveLancarExcecaoAoReduzirMaisEstoqueDoQueDisponivel() {
        // Given
        Produto produto = new Produto();
        produto.setEstoque(5);

        // When & Then
        assertThatThrownBy(() -> produto.reduzirEstoque(10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Estoque insuficiente");
        
        assertThat(produto.getEstoque()).isEqualTo(5); // não deve alterar
    }

    @Test
    @DisplayName("Deve testar método equals")
    void deveTestarMetodoEquals() {
        // Given
        Produto produto1 = new Produto();
        produto1.setId(1L);
        produto1.setNome("Produto 1");

        Produto produto2 = new Produto();
        produto2.setId(1L);
        produto2.setNome("Produto 1");

        Produto produto3 = new Produto();
        produto3.setId(2L);
        produto3.setNome("Produto 2");

        // Then
        assertThat(produto1).isEqualTo(produto2);
        assertThat(produto1).isNotEqualTo(produto3);
        assertThat(produto1).isNotEqualTo(null);
        assertThat(produto1).isNotEqualTo("string");
    }

    @Test
    @DisplayName("Deve testar método hashCode")
    void deveTestarMetodoHashCode() {
        // Given
        Produto produto1 = new Produto();
        produto1.setId(1L);
        produto1.setNome("Produto 1");

        Produto produto2 = new Produto();
        produto2.setId(1L);
        produto2.setNome("Produto 1");

        // Then
        assertThat(produto1.hashCode()).isEqualTo(produto2.hashCode());
    }

    @Test
    @DisplayName("Deve testar método toString")
    void deveTestarMetodoToString() {
        // Given
        Produto produto = new Produto();
        produto.setId(1L);
        produto.setNome("Produto Teste");
        produto.setPreco(new BigDecimal("99.99"));

        // When
        String toString = produto.toString();

        // Then
        assertThat(toString).contains("Produto Teste");
        assertThat(toString).contains("99.99");
    }

    @Test
    @DisplayName("Deve ter construtores funcionais")
    void deveTerConstrutoresFuncionais() {
        // Given & When
        Produto produto1 = new Produto();
        Produto produto2 = new Produto("Produto", new BigDecimal("50.00"));
        Produto produto3 = new Produto("Produto Completo", "Descrição detalhada", 
                                      new BigDecimal("99.99"), "Categoria", 10);

        // Then
        assertThat(produto1.isAtivo()).isTrue();
        assertThat(produto1.getEstoque()).isZero();
        
        assertThat(produto2.getNome()).isEqualTo("Produto");
        assertThat(produto2.getPreco()).isEqualTo(new BigDecimal("50.00"));
        
        assertThat(produto3.getNome()).isEqualTo("Produto Completo");
        assertThat(produto3.getDescricao()).isEqualTo("Descrição detalhada");
        assertThat(produto3.getPreco()).isEqualTo(new BigDecimal("99.99"));
        assertThat(produto3.getCategoria()).isEqualTo("Categoria");
        assertThat(produto3.getEstoque()).isEqualTo(10);
    }
}