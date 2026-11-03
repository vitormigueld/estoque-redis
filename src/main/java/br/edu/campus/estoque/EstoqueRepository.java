package br.edu.campus.estoque;

import br.edu.campus.estoque.Dtos.*;
import br.edu.campus.estoque.Erros.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.connection.Limit;
import org.springframework.stereotype.Repository;

/**
 * Modelagem em Redis (estruturas de dados):
 *  produto:{sku}            HASH    dados do produto (documento plano)
 *  idx:produtos             SET     todos os SKUs (evita KEYS/SCAN para listar)
 *  idx:categoria:{CAT}      SET     índice secundário manual por categoria
 *  mov:{sku}                STREAM  histórico imutável de movimentos (ID = timestamp)
 *  alerta:estoque-baixo     SET     SKUs com quantidade <= mínimo (mantido atomicamente)
 *  ranking:vendas           ZSET    SKU -> total vendido (ranking em O(log N))
 */
@Repository
public class EstoqueRepository {
    static final String IDX = "idx:produtos", CAT = "idx:categoria:", ALERTA = "alerta:estoque-baixo",
            RANKING = "ranking:vendas";

    private final StringRedisTemplate redis;
    private final RedisScript<Long> criar = script("criar"), atualizar = script("atualizar"),
            movimentar = script("movimentar"), remover = script("remover");

    public EstoqueRepository(StringRedisTemplate redis) { this.redis = redis; }

    // ---------- helpers ----------
    private static RedisScript<Long> script(String nome) {
        var s = new DefaultRedisScript<Long>();
        s.setLocation(new ClassPathResource("scripts/" + nome + ".lua"));
        s.setResultType(Long.class);
        return s;
    }

    private static String prod(String sku) { return "produto:" + sku; }
    private static String mov(String sku) { return "mov:" + sku; }

    private Long run(RedisScript<Long> s, List<String> keys, Object... args) {
        // StringRedisTemplate só serializa String: converte todos os argumentos
        return redis.execute(s, keys, Arrays.stream(args).map(String::valueOf).toArray());
    }

    private static Map<String, Object> converter(Map<Object, Object> h) {
        var m = new LinkedHashMap<String, Object>();
        h.forEach((k, v) -> {
            String campo = k.toString(), valor = v.toString();
            m.put(campo, switch (campo) {
                case "preco" -> new BigDecimal(valor);
                case "quantidade", "minimo" -> Long.valueOf(valor);
                default -> valor;
            });
        });
        return m;
    }
/*
==========================================================
 VANTAGENS E DESVANTAGENS DO REDIS
==========================================================

Agora vamos falar sobre as vantagens e desvantagens
do Redis.

Uma das principais vantagens é a velocidade,
pois ele trabalha principalmente com dados armazenados
na memória RAM.

Outra vantagem são suas estruturas de dados prontas,
como Hash, Set, Sorted Set e Stream.

No nosso projeto, utilizamos essas estruturas para
armazenar produtos, organizar categorias, registrar
movimentações e criar o ranking dos mais vendidos.

Também temos as operações atômicas, que são importantes
para evitar inconsistências no estoque.

Por exemplo, se dois caixas tentarem vender a última
unidade de um produto ao mesmo tempo, o sistema consegue
controlar essa operação e impedir que o estoque fique
negativo.

==========================================================
DESVANTAGENS
==========================================================

Apesar das vantagens, o Redis também possui limitações.

A primeira é que a memória RAM possui um custo maior
e uma capacidade limitada em comparação com discos.

Outra desvantagem é que ele não trabalha com consultas
relacionais como os bancos SQL, ou seja, não possui
operações JOIN da mesma maneira.

Além disso, alguns índices precisam ser criados
e mantidos manualmente, como fizemos com as categorias
e os alertas de estoque baixo.
*/
    // ---------- CREATE ----------
    public Map<String, Object> criarProduto(ProdutoIn p) {
        Long ok = run(criar, List.of(prod(p.sku()), IDX, CAT + p.categoria(), ALERTA),
                p.sku(), p.nome(), p.categoria(), p.preco().toPlainString(),
                p.minimo() == null ? 5 : p.minimo(), p.quantidade() == null ? 0 : p.quantidade(), Instant.now());
        if (ok == null || ok == 0) throw new Conflito("SKU já cadastrado");
        return buscar(p.sku());
    }

    // ---------- READ ----------
    public Map<String, Object> buscar(String sku) {                       // HGETALL
        var h = redis.opsForHash().entries(prod(sku));
        if (h.isEmpty()) throw new NaoEncontrado("Produto não encontrado");
        return converter(h);
    }

    private List<Map<String, Object>> porSkus(Set<String> skus) {
        var out = new ArrayList<Map<String, Object>>();
        if (skus == null) return out;
        for (String sku : new TreeSet<>(skus)) {
            var h = redis.opsForHash().entries(prod(sku));
            if (!h.isEmpty()) out.add(converter(h));
        }
        return out;
    }

    public List<Map<String, Object>> porCategoria(String c) { return porSkus(redis.opsForSet().members(CAT + c.toUpperCase())); }
    public List<Map<String, Object>> todos() { return porSkus(redis.opsForSet().members(IDX)); }
    public List<Map<String, Object>> alertasEstoqueBaixo() { return porSkus(redis.opsForSet().members(ALERTA)); }

    public List<Map<String, Object>> historico(String sku, int limite) {
        if (limite < 1 || limite > 100) throw new IllegalArgumentException("Limite do histórico deve estar entre 1 e 100.");
        if (!Boolean.TRUE.equals(redis.hasKey(prod(sku)))) throw new NaoEncontrado("Produto não encontrado");
        var recs = redis.opsForStream().reverseRange(mov(sku), Range.unbounded(), Limit.limit().count(limite)); // XREVRANGE
        var out = new ArrayList<Map<String, Object>>();
        if (recs == null) return out;
        for (var r : recs) {
            var m = new LinkedHashMap<String, Object>();
            m.put("id", r.getId().getValue());
            m.put("quando", Instant.ofEpochMilli(r.getId().getTimestamp()).toString());
            r.getValue().forEach((k, v) -> m.put(k.toString(), v.toString()));
            out.add(m);
        }
        return out;
    }

    public List<Map<String, Object>> maisVendidos(int top) {              // ZREVRANGE WITHSCORES
        if (top < 1 || top > 100) throw new IllegalArgumentException("Top deve estar entre 1 e 100.");
        var out = new ArrayList<Map<String, Object>>();
        Set<TypedTuple<String>> r = redis.opsForZSet().reverseRangeWithScores(RANKING, 0, top - 1);
        if (r == null) return out;
        for (var t : r) {
            var m = new LinkedHashMap<String, Object>();
            m.put("sku", t.getValue());
            m.put("nome", redis.opsForHash().get(prod(t.getValue()), "nome"));
            m.put("vendidos", t.getScore().longValue());
            out.add(m);
        }
        return out;
    }
/*
==========================================================
 MELHORIAS FUTURAS
==========================================================

Pensando em melhorias futuras para o nosso projeto,
existem algumas tecnologias que poderiam ser adicionadas.

Uma delas é o RedisJSON, que permite armazenar dados
no formato JSON, facilitando o trabalho com documentos.

Também temos o RediSearch, que permite realizar pesquisas
mais avançadas e criar índices para encontrar informações.

Outra possibilidade é utilizar o Pub/Sub, que permite
enviar notificações automaticamente.

Por exemplo, quando um produto atingir o estoque mínimo,
o sistema poderia enviar um aviso informando que é
necessário realizar uma reposição.

Também poderíamos implementar o Pipeline para melhorar
o desempenho das consultas.

Com essas melhorias, nosso sistema poderia se tornar
ainda mais completo e eficiente.
*/

    // ---------- UPDATE ----------
    public Map<String, Object> atualizar(String sku, ProdutoUp u) {
        var args = new ArrayList<Object>();
        if (u.nome() != null) { args.add("nome"); args.add(u.nome()); }
        if (u.categoria() != null) { args.add("categoria"); args.add(u.categoria()); }
        if (u.preco() != null) { args.add("preco"); args.add(u.preco().toPlainString()); }
        if (u.minimo() != null) { args.add("minimo"); args.add(u.minimo()); }
        Long ok = run(atualizar, List.of(prod(sku), ALERTA), args.toArray());
        if (ok == null || ok == 0) throw new NaoEncontrado("Produto não encontrado");
        return buscar(sku);
    }

    /** Entrada/saída atômica: saldo + stream + alerta + ranking em UM script Lua (sem race condition). */
    public Map<String, Object> movimentar(String sku, MovimentoIn m) {
        int q = m.quantidade();
        Long r = run(movimentar, List.of(prod(sku), mov(sku), ALERTA, RANKING),
                m.tipo().equals("SAIDA") ? -q : q, m.tipo(), q,
                m.motivo() == null ? "-" : m.motivo(), m.operador() == null ? "sistema" : m.operador(), sku);
        if (r == null || r == -1) throw new NaoEncontrado("Produto não encontrado");
        if (r == -2) throw new EstoqueInsuficiente("Saldo insuficiente para a saída");
        return buscar(sku);
    }

    // ---------- DELETE ----------
    public void remover(String sku) {
        Long ok = run(remover, List.of(prod(sku), mov(sku), ALERTA, IDX, RANKING));
        if (ok == null || ok == 0) throw new NaoEncontrado("Produto não encontrado");
    }
}
