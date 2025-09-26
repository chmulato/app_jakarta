package com.caracore.hub_town.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
        Produto produto = new Produto();
        produto.setNome("Produto Teste");
        produto.setDescricao("Descrição do produto");
        produto.setPreco(new BigDecimal("99.99"));
        produto.setEstoque(10);
        Set<ConstraintViolation<Produto>> violations = validator.validate(produto);
        assertThat(violations).isEmpty();
    }
}
