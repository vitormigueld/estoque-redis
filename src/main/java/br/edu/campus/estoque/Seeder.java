package br.edu.campus.estoque;

import br.edu.campus.estoque.Dtos.*;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Executar com: mvn spring-boot:run -Dspring-boot.run.arguments=--app.seed=true */
@Component
@Order(1)
@ConditionalOnProperty(name = "app.seed", havingValue = "true")
public class Seeder implements CommandLineRunner {
    private record P(String sku, String nome, String cat, String preco, int min, int qtd) {}

    private final EstoqueRepository repo;
    public Seeder(EstoqueRepository repo) { this.repo = repo; }

    @Override
    public void run(String... args) {
        try {
            var produtos = List.of(
                new P("BEB-001", "Coca-Cola Lata 350ml", "BEBIDAS", "5.00", 12, 48),
                new P("BEB-002", "Água Mineral 500ml", "BEBIDAS", "3.00", 20, 80),
                new P("BEB-003", "Suco de Caju 300ml", "BEBIDAS", "4.50", 10, 6),
                new P("SAL-001", "Coxinha de Frango", "SALGADOS", "6.00", 10, 25),
                new P("SAL-002", "Pastel de Queijo", "SALGADOS", "7.00", 10, 4),
                new P("DOC-001", "Brigadeiro", "DOCES", "3.50", 10, 30),
                new P("DOC-002", "Barra de Chocolate", "DOCES", "5.50", 8, 15),
                new P("LAN-001", "Sanduíche Natural", "LANCHES", "9.00", 6, 12),
                new P("LAN-002", "X-Burguer", "LANCHES", "14.00", 6, 3),
                new P("OUT-001", "Café 100ml", "OUTROS", "2.50", 30, 100));
            for (var p : produtos) {
                try {
                    repo.criarProduto(new ProdutoIn(p.sku(), p.nome(), p.cat(), new BigDecimal(p.preco()), p.min(), 0));
                } catch (Erros.Conflito e) { continue; }
                repo.movimentar(p.sku(), new MovimentoIn("ENTRADA", p.qtd(), "estoque inicial", "seed"));
            }
            repo.movimentar("BEB-001", new MovimentoIn("SAIDA", 5, "venda", "caixa-01"));
            System.out.println("Seed concluído.");
        } catch (DataAccessException e) {
            throw new IllegalStateException(
                    "Não foi possível popular os dados: Redis indisponível. Inicie-o com 'docker compose up -d' e tente novamente.",
                    e);
        }
    }
}
