package br.edu.campus.estoque;

import br.edu.campus.estoque.Dtos.*;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class EstoqueController {
    private static final int MAX_LIMITE_HISTORICO = 100;
    private static final int MAX_TOP_VENDIDOS = 100;

    private final EstoqueRepository repo;

    public EstoqueController(EstoqueRepository repo) { this.repo = repo; }

    @PostMapping("/produtos") @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> criar(@Valid @RequestBody ProdutoIn p) { return repo.criarProduto(p); }

    @GetMapping("/produtos")
    public List<Map<String, Object>> listar(@RequestParam(required = false) String categoria) {
        if (categoria == null) return repo.todos();
        String normalizada = categoria.trim().toUpperCase(Locale.ROOT);
        if (!List.of(Dtos.CATEGORIAS.split("\\|")).contains(normalizada)) {
            throw new IllegalArgumentException("Categoria inválida. Use: " + Dtos.CATEGORIAS.replace("|", ", "));
        }
        return repo.porCategoria(normalizada);
    }

    @GetMapping("/produtos/{sku}")
    public Map<String, Object> obter(@PathVariable String sku) { return repo.buscar(sku); }

    @PutMapping("/produtos/{sku}")
    public Map<String, Object> atualizar(@PathVariable String sku, @Valid @RequestBody ProdutoUp u) {
        if (u.vazio()) throw new IllegalArgumentException("Nenhum campo para atualizar");
        return repo.atualizar(sku, u);
    }

    @DeleteMapping("/produtos/{sku}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@PathVariable String sku) { repo.remover(sku); }

    @PostMapping("/produtos/{sku}/movimentos") @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> movimentar(@PathVariable String sku, @Valid @RequestBody MovimentoIn m) {
        return repo.movimentar(sku, m);
    }

    @GetMapping("/produtos/{sku}/movimentos")
    public List<Map<String, Object>> historico(@PathVariable String sku, @RequestParam(defaultValue = "20") int limite) {
        if (limite < 1 || limite > MAX_LIMITE_HISTORICO) {
            throw new IllegalArgumentException("O limite do histórico deve estar entre 1 e " + MAX_LIMITE_HISTORICO + ".");
        }
        return repo.historico(sku, limite);
    }

    @GetMapping("/alertas/estoque-baixo")
    public List<Map<String, Object>> alertas() { return repo.alertasEstoqueBaixo(); }

    @GetMapping("/relatorios/mais-vendidos")
    public List<Map<String, Object>> maisVendidos(@RequestParam(defaultValue = "5") int top) {
        if (top < 1 || top > MAX_TOP_VENDIDOS) {
            throw new IllegalArgumentException("O parâmetro top deve estar entre 1 e " + MAX_TOP_VENDIDOS + ".");
        }
        return repo.maisVendidos(top);
    }
}

/*
==========================================================
ENCERRAMENTO DA APRESENTAÇÃO
==========================================================

Para finalizar, nosso projeto demonstra como o Redis
pode ser utilizado para resolver um problema real
de controle de estoque.

Com ele, conseguimos cadastrar produtos, controlar
entradas e saídas, registrar o histórico de movimentações,
identificar produtos com estoque baixo e acompanhar
os produtos mais vendidos.

Assim, conseguimos demonstrar na prática como as
estruturas de dados do Redis podem ser utilizadas
para desenvolver um sistema rápido e eficiente.

Obrigado pela atenção!
Estamos à disposição para perguntas.
==========================================================
*/
