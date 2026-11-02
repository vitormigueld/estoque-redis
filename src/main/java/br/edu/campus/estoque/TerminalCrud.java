package br.edu.campus.estoque;

import br.edu.campus.estoque.Dtos.MovimentoIn;
import br.edu.campus.estoque.Dtos.ProdutoIn;
import br.edu.campus.estoque.Dtos.ProdutoUp;
import br.edu.campus.estoque.Erros.Conflito;
import br.edu.campus.estoque.Erros.EstoqueInsuficiente;
import br.edu.campus.estoque.Erros.NaoEncontrado;
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/** Menu de demonstração do CRUD pela entrada e saída padrão do terminal. */
@Component
@Order(2)
@ConditionalOnProperty(name = "app.terminal", havingValue = "true")
public class TerminalCrud implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(TerminalCrud.class);
    private static final List<String> CATEGORIAS = List.of("BEBIDAS", "SALGADOS", "DOCES", "LANCHES", "OUTROS");

    private final EstoqueRepository repo;
    private final String portaServidor;
    private final BufferedReader entrada = new BufferedReader(
            new InputStreamReader(System.in, StandardCharsets.UTF_8));

    public TerminalCrud(EstoqueRepository repo, @Value("${server.port:8081}") String portaServidor) {
        this.repo = repo;
        this.portaServidor = portaServidor;
    }

    @Override
    public void run(String... args) {
        System.out.println("\n=== ESTOQUE DO CAMPUS | CRUD COM REDIS ===");
        while (true) {
            imprimirMenu();
            String opcao;
            try {
                opcao = entrada.readLine();
            } catch (IOException e) {
                log.error("Não foi possível ler a entrada do menu.", e);
                System.out.println("Não consegui ler o terminal. A API continua ativa na porta " + portaServidor + ".");
                return;
            }

            if (opcao == null || opcao.trim().equals("0")) {
                System.out.println("Menu encerrado. A API continua ativa na porta " + portaServidor + ".");
                return;
            }

            try {
                executar(opcao.trim());
            } catch (EOFException e) {
                System.out.println("\nEntrada encerrada. Menu finalizado; a API continua ativa.");
                return;
            } catch (IOException e) {
                log.error("Falha de leitura durante uma operação do menu.", e);
                System.out.println("Falha ao ler os dados do terminal. A API continua ativa na porta " + portaServidor + ".");
                return;
            } catch (NaoEncontrado e) {
                System.out.println("Não encontrado: " + e.getMessage());
            } catch (Conflito e) {
                System.out.println("Conflito: " + e.getMessage());
            } catch (EstoqueInsuficiente e) {
                System.out.println("Movimentação recusada: " + e.getMessage());
            } catch (IllegalArgumentException e) {
                System.out.println("Dados inválidos: " + e.getMessage());
            } catch (DataAccessException e) {
                log.error("Falha ao acessar o Redis durante uma operação do menu.", e);
                System.out.println("Redis indisponível. Confira se o container está ativo e tente novamente.");
            } catch (RuntimeException e) {
                log.error("Erro inesperado durante uma operação do menu.", e);
                System.out.println("Ocorreu um erro inesperado. A operação não foi concluída; tente novamente.");
            }
        }
    }

    private void executar(String opcao) throws IOException {
        switch (opcao) {
            case "1" -> cadastrar();
            case "2" -> buscar();
            case "3" -> listar();
            case "4" -> atualizar();
            case "5" -> remover();
            case "6" -> movimentar();
            case "7" -> historico();
            case "8" -> alertas();
            case "9" -> ranking();
            default -> System.out.println("Opção inválida. Escolha um número de 0 a 9.");
        }
    }

    private void imprimirMenu() {
        System.out.println("\n------------------------------------------");
        System.out.println("1 - Cadastrar produto       (CREATE)");
        System.out.println("2 - Consultar por SKU       (READ)");
        System.out.println("3 - Listar produtos         (READ)");
        System.out.println("4 - Atualizar produto       (UPDATE)");
        System.out.println("5 - Remover produto         (DELETE)");
        System.out.println("6 - Registrar entrada/saída de estoque");
        System.out.println("7 - Consultar histórico de movimentações");
        System.out.println("8 - Ver alertas de estoque baixo");
        System.out.println("9 - Ver ranking de vendas");
        System.out.println("0 - Sair do menu");
        System.out.print("Opção: ");
    }

    private void cadastrar() throws IOException {
        System.out.println("\n--- CADASTRAR PRODUTO ---");
        String sku = lerSku("SKU (ex.: BEB-010): ");
        String nome = lerObrigatorio("Nome: ");
        String categoria = lerCategoria("Categoria (BEBIDAS, SALGADOS, DOCES, LANCHES, OUTROS): ");
        BigDecimal preco = lerPreco("Preço (ex.: 5,50): ");
        int minimo = lerInteiro("Estoque mínimo: ", 0);
        int quantidade = lerInteiro("Quantidade inicial: ", 0);

        Map<String, Object> produto = repo.criarProduto(
                new ProdutoIn(sku, nome, categoria, preco, minimo, quantidade));
        System.out.println("Produto cadastrado no Redis:");
        exibirProduto(produto);
    }

    private void buscar() throws IOException {
        System.out.println("\n--- CONSULTAR PRODUTO ---");
        exibirProduto(repo.buscar(lerSku("SKU: ")));
    }

    private void listar() {
        System.out.println("\n--- PRODUTOS CADASTRADOS ---");
        exibirProdutos(repo.todos());
    }

    private void atualizar() throws IOException {
        System.out.println("\n--- ATUALIZAR PRODUTO ---");
        String sku = lerSku("SKU do produto: ");
        System.out.println("Deixe em branco os campos que não deseja alterar.");

        String nome = lerOpcional("Novo nome: ");
        String categoria = lerCategoriaOpcional("Nova categoria: ");
        BigDecimal preco = lerPrecoOpcional("Novo preço: ");
        Integer minimo = lerInteiroOpcional("Novo estoque mínimo: ", 0);
        if (nome == null && categoria == null && preco == null && minimo == null) {
            throw new IllegalArgumentException("Informe ao menos um campo para atualizar.");
        }

        System.out.println("Produto atualizado no Redis:");
        exibirProduto(repo.atualizar(sku, new ProdutoUp(nome, categoria, preco, minimo)));
    }

    private void remover() throws IOException {
        System.out.println("\n--- REMOVER PRODUTO ---");
        String sku = lerSku("SKU do produto: ");
        if (!confirmar("Confirma a remoção? (S/N): ")) {
            System.out.println("Remoção cancelada.");
            return;
        }
        repo.remover(sku);
        System.out.println("Produto " + sku + " removido do Redis.");
    }

    private void movimentar() throws IOException {
        System.out.println("\n--- MOVIMENTAR ESTOQUE ---");
        String sku = lerSku("SKU: ");
        String tipo = lerTipoMovimento();
        int quantidade = lerInteiro("Quantidade: ", 1);
        String motivo = lerOpcional("Motivo (opcional): ");
        String operador = lerOpcional("Operador (opcional): ");
        System.out.println("Movimentação registrada. Saldo atualizado:");
        exibirProduto(repo.movimentar(sku, new MovimentoIn(tipo, quantidade, motivo, operador)));
    }

    private void historico() throws IOException {
        System.out.println("\n--- HISTÓRICO DE MOVIMENTAÇÕES ---");
        String sku = lerSku("SKU: ");
        List<Map<String, Object>> movimentos = repo.historico(sku, 20);
        if (movimentos.isEmpty()) {
            System.out.println("Nenhuma movimentação registrada.");
            return;
        }
        movimentos.forEach(m -> System.out.println(m));
    }

    private void alertas() {
        System.out.println("\n--- ALERTAS: ESTOQUE BAIXO ---");
        exibirProdutos(repo.alertasEstoqueBaixo());
    }

    private void ranking() {
        System.out.println("\n--- 5 PRODUTOS MAIS VENDIDOS ---");
        List<Map<String, Object>> produtos = repo.maisVendidos(5);
        if (produtos.isEmpty()) {
            System.out.println("Ainda não há vendas registradas.");
            return;
        }
        for (Map<String, Object> produto : produtos) {
            System.out.printf("SKU: %s | Produto: %s | Vendidos: %s%n",
                    produto.get("sku"), produto.get("nome"), produto.get("vendidos"));
        }
    }

    private boolean confirmar(String pergunta) throws IOException {
        while (true) {
            String resposta = lerObrigatorio(pergunta);
            if (resposta.equalsIgnoreCase("S")) return true;
            if (resposta.equalsIgnoreCase("N")) return false;
            System.out.println("Responda S para sim ou N para não.");
        }
    }

    private String lerTipoMovimento() throws IOException {
        while (true) {
            String tipo = lerObrigatorio("Tipo (ENTRADA/SAIDA): ").toUpperCase(Locale.ROOT);
            if (tipo.equals("ENTRADA") || tipo.equals("SAIDA")) return tipo;
            System.out.println("Tipo inválido. Digite ENTRADA ou SAIDA.");
        }
    }

    private void exibirProdutos(List<Map<String, Object>> produtos) {
        if (produtos.isEmpty()) {
            System.out.println("Nenhum produto encontrado.");
            return;
        }
        System.out.printf("%-12s | %-28s | %-10s | %10s | %8s | %8s%n",
                "SKU", "NOME", "CATEGORIA", "PREÇO", "SALDO", "MÍNIMO");
        System.out.println("-".repeat(92));
        for (Map<String, Object> produto : produtos) {
            System.out.printf("%-12.12s | %-28.28s | %-10.10s | %10s | %8s | %8s%n",
                    produto.get("sku"), produto.get("nome"), produto.get("categoria"),
                    produto.get("preco"), produto.get("quantidade"), produto.get("minimo"));
        }
    }

    private void exibirProduto(Map<String, Object> produto) {
        System.out.printf("SKU: %s | Nome: %s | Categoria: %s | Preço: R$ %s | Saldo: %s | Mínimo: %s%n",
                produto.get("sku"), produto.get("nome"), produto.get("categoria"),
                produto.get("preco"), produto.get("quantidade"), produto.get("minimo"));
    }

    private String lerSku(String pergunta) throws IOException {
        while (true) {
            String sku = lerObrigatorio(pergunta).toUpperCase(Locale.ROOT);
            if (sku.matches("^[A-Z0-9-]{3,20}$")) return sku;
            System.out.println("SKU inválido. Use de 3 a 20 letras, números ou hífens.");
        }
    }

    private String lerCategoria(String pergunta) throws IOException {
        while (true) {
            String categoria = lerObrigatorio(pergunta).toUpperCase(Locale.ROOT);
            if (CATEGORIAS.contains(categoria)) return categoria;
            mostrarCategoriasInvalidas();
        }
    }

    private String lerCategoriaOpcional(String pergunta) throws IOException {
        while (true) {
            String valor = lerOpcional(pergunta);
            if (valor == null) return null;
            String categoria = valor.toUpperCase(Locale.ROOT);
            if (CATEGORIAS.contains(categoria)) return categoria;
            mostrarCategoriasInvalidas();
        }
    }

    private void mostrarCategoriasInvalidas() {
        System.out.println("Categoria inválida. Use: " + String.join(", ", CATEGORIAS));
    }

    private BigDecimal lerPreco(String pergunta) throws IOException {
        while (true) {
            String valor = lerObrigatorio(pergunta);
            try {
                return converterPreco(valor);
            } catch (IllegalArgumentException e) {
                System.out.println(e.getMessage());
            }
        }
    }

    private BigDecimal lerPrecoOpcional(String pergunta) throws IOException {
        while (true) {
            String valor = lerOpcional(pergunta);
            if (valor == null) return null;
            try {
                return converterPreco(valor);
            } catch (IllegalArgumentException e) {
                System.out.println(e.getMessage());
            }
        }
    }

    private BigDecimal converterPreco(String valor) {
        try {
            BigDecimal preco = new BigDecimal(valor.trim().replace(',', '.'));
            if (preco.compareTo(new BigDecimal("0.01")) < 0) {
                throw new IllegalArgumentException("O preço mínimo é 0,01. Tente novamente.");
            }
            return preco;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Preço inválido. Digite, por exemplo, 5,50.");
        }
    }

    private int lerInteiro(String pergunta, int minimo) throws IOException {
        while (true) {
            String valor = lerObrigatorio(pergunta);
            try {
                return converterInteiro(valor, minimo);
            } catch (IllegalArgumentException e) {
                System.out.println(e.getMessage());
            }
        }
    }

    private Integer lerInteiroOpcional(String pergunta, int minimo) throws IOException {
        while (true) {
            String valor = lerOpcional(pergunta);
            if (valor == null) return null;
            try {
                return converterInteiro(valor, minimo);
            } catch (IllegalArgumentException e) {
                System.out.println(e.getMessage());
            }
        }
    }

    private int converterInteiro(String valor, int minimo) {
        try {
            int numero = Integer.parseInt(valor.trim());
            if (numero < minimo) {
                throw new IllegalArgumentException("O valor mínimo permitido é " + minimo + ". Tente novamente.");
            }
            return numero;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Digite um número inteiro válido.");
        }
    }

    private String lerObrigatorio(String pergunta) throws IOException {
        while (true) {
            System.out.print(pergunta);
            String valor = entrada.readLine();
            if (valor == null) throw new EOFException();
            if (!valor.isBlank()) return valor.trim();
            System.out.println("Este campo é obrigatório. Tente novamente.");
        }
    }

    private String lerOpcional(String pergunta) throws IOException {
        System.out.print(pergunta);
        String valor = entrada.readLine();
        if (valor == null) throw new EOFException();
        valor = valor.trim();
        return valor.isEmpty() ? null : valor;
    }
}
