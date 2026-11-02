package br.edu.campus.estoque;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public final class Dtos {
    private Dtos() {}

    static final String CATEGORIAS = "BEBIDAS|SALGADOS|DOCES|LANCHES|OUTROS";

    public record ProdutoIn(
            @NotBlank(message = "é obrigatório")
            @Pattern(regexp = "^[A-Z0-9-]{3,20}$", message = "deve ter de 3 a 20 letras maiúsculas, números ou hífens")
            String sku,
            @NotBlank(message = "é obrigatório") String nome,
            @NotNull(message = "é obrigatória")
            @Pattern(regexp = "^(" + CATEGORIAS + ")$", message = "deve ser BEBIDAS, SALGADOS, DOCES, LANCHES ou OUTROS")
            String categoria,
            @NotNull(message = "é obrigatório") @DecimalMin(value = "0.01", message = "deve ser no mínimo 0,01")
            BigDecimal preco,
            @Min(value = 0, message = "deve ser zero ou maior") Integer minimo,
            @Min(value = 0, message = "deve ser zero ou maior") Integer quantidade) {}

    public record ProdutoUp(
            @Pattern(regexp = ".*\\S.*", message = "não pode ficar em branco") String nome,
            @Pattern(regexp = "^(" + CATEGORIAS + ")$", message = "deve ser BEBIDAS, SALGADOS, DOCES, LANCHES ou OUTROS")
            String categoria,
            @DecimalMin(value = "0.01", message = "deve ser no mínimo 0,01") BigDecimal preco,
            @Min(value = 0, message = "deve ser zero ou maior") Integer minimo) {
        boolean vazio() { return nome == null && categoria == null && preco == null && minimo == null; }
    }

    public record MovimentoIn(
            @NotNull(message = "é obrigatório")
            @Pattern(regexp = "^(ENTRADA|SAIDA)$", message = "deve ser ENTRADA ou SAIDA")
            String tipo,
            @NotNull(message = "é obrigatória") @Min(value = 1, message = "deve ser pelo menos 1") Integer quantidade,
            String motivo,
            String operador) {}
}
