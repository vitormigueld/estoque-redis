# Estoque da Lanchonete do Campus — Redis

**Problema:** a lanchonete do campus controla estoque em caderno/planilha: não sabe o que está acabando, não tem histórico e vende acima do saldo real.
**Solução:** API REST (Java + Spring Boot) com estoque em Redis: cadastro de produtos, entradas/saídas **atômicas** com histórico, alertas de reposição e ranking de mais vendidos.
**Banco:** Redis 7 (NoSQL em memória, chave-valor / multi-modelo).
**Equipe:** Kauã Lopes, Rafael Lima, Vitor Miguel

> **Classificação:** Redis é nativamente *chave-valor com estruturas de dados* (Hash, Set, Sorted Set, Stream). O enunciado pede "documentos ou grafos": confirme com o professor. Se exigir documento puro, a mesma modelagem migra para **Redis Stack (RedisJSON + RediSearch)**, que guarda documentos JSON e cria índices secundários.

## 1. Como rodar
Requisitos: Java 17+, Maven 3.9+, Docker.
```bash
docker compose up -d                                              # Redis (:6379) + RedisInsight (:5540)
mvn spring-boot:run "-Dspring-boot.run.arguments=--app.seed=true" # 1ª vez: popula dados e abre o menu
mvn spring-boot:run                                               # próximas execuções; abre o menu CRUD
```
O menu interativo no terminal permite cadastrar, consultar, listar, atualizar e remover produtos, além de registrar movimentações e consultar relatórios. Digite `0` para fechar o menu. A API REST continua disponível em http://localhost:8081; painel visual do Redis em http://localhost:5540 (conectar em `redis:6379`). A porta pode ser alterada pela variável `SERVER_PORT`.
Para executar somente a API, sem o menu no terminal: `mvn spring-boot:run "-Dspring-boot.run.arguments=--app.terminal=false"`.
Inspeção direta: `docker compose exec redis redis-cli`.

## 2. Modelagem (estrutura de dados certa para cada padrão de acesso)
| Chave | Tipo | Conteúdo | Padrão de acesso |
|---|---|---|---|
| `produto:{sku}` | HASH | nome, categoria, preco, minimo, quantidade | AP1 produto por SKU (`HGETALL`, O(1)) |
| `idx:produtos` | SET | todos os SKUs | listagem geral sem `KEYS`/`SCAN` |
| `idx:categoria:{CAT}` | SET | SKUs da categoria | AP2 por categoria (índice secundário manual) |
| `mov:{sku}` | STREAM | tipo, quantidade, motivo, operador | AP3 histórico (`XREVRANGE`, ID = timestamp) |
| `alerta:estoque-baixo` | SET | SKUs com qtd ≤ mínimo | AP4 alertas (`SMEMBERS`) |
| `ranking:vendas` | SORTED SET | SKU → total vendido | AP5 mais vendidos (`ZREVRANGE`) |

**Decisões de projeto**
- **Redis não tem índices secundários nativos:** nós os mantemos (sets por categoria e de alertas). É o custo da velocidade.
- **Atomicidade via Lua:** cada script roda sem intercalar outros comandos. `movimentar.lua` valida saldo, grava o Stream, atualiza o alerta e o ranking de uma vez. Dois caixas vendendo o último item simultaneamente: apenas um passa, sem lock.
- **Alerta sempre consistente** com o saldo (no DynamoDB isso exigia uma segunda escrita).
- **Stream** como log imutável: ordenado por tempo, imutável e com leitura por faixa.

## 3. CRUD
```bash
# CREATE
curl -X POST localhost:8081/produtos -H 'content-type: application/json' \
 -d '{"sku":"SAL-003","nome":"Esfiha de Carne","categoria":"SALGADOS","preco":6.5,"minimo":8}'
# READ
curl localhost:8081/produtos/SAL-003
curl "localhost:8081/produtos?categoria=bebidas"
curl localhost:8081/produtos/BEB-001/movimentos
curl localhost:8081/alertas/estoque-baixo
curl "localhost:8081/relatorios/mais-vendidos?top=3"
# UPDATE
curl -X PUT localhost:8081/produtos/SAL-003 -H 'content-type: application/json' -d '{"preco":7.0}'
curl -X POST localhost:8081/produtos/SAL-003/movimentos -H 'content-type: application/json' \
 -d '{"tipo":"ENTRADA","quantidade":40,"motivo":"compra fornecedor"}'
curl -X POST localhost:8081/produtos/SAL-003/movimentos -H 'content-type: application/json' \
 -d '{"tipo":"SAIDA","quantidade":999}'        # => 409 saldo insuficiente
# DELETE
curl -X DELETE localhost:8081/produtos/SAL-003
```
Equivalentes em `redis-cli`:
```
HSET produto:X-1 nome "Água" quantidade 10          # create/update
HGETALL produto:X-1                                  # read
HINCRBY produto:X-1 quantidade -2                    # update atômico
XADD mov:X-1 * tipo SAIDA quantidade 2               # histórico
XREVRANGE mov:X-1 + - COUNT 5                        # últimos 5
SMEMBERS alerta:estoque-baixo
ZREVRANGE ranking:vendas 0 2 WITHSCORES
DEL produto:X-1                                      # delete
```

## 4. Conteúdo teórico para a apresentação
### Principais características
- Dados **em memória** → latência de microssegundos, centenas de milhares de ops/s por nó.
- Estruturas ricas: String, Hash, List, Set, Sorted Set, Stream, Bitmap, HyperLogLog, Geo.
- Execução de comandos em **thread única** (atomicidade natural; I/O multithread nas versões recentes).
- **Lua scripts**, `MULTI/EXEC`, Pub/Sub, **TTL** por chave, políticas de despejo (LRU/LFU).
- Persistência opcional (RDB/AOF), replicação, Sentinel e Cluster.

### Nichos de aplicação
Cache, sessões de login, filas e jobs, rankings/placar, rate limiting, contadores em tempo real, Pub/Sub e mensageria leve, geolocalização. Menos indicado como banco principal de dados muito maiores que a RAM ou com consultas complexas.

### Prós e contras
| Prós | Contras |
|---|---|
| Velocidade extrema (RAM) | Capacidade limitada pela memória (custo/GB alto) |
| Estruturas de dados prontas | Sem JOIN, sem consultas ad hoc; índices secundários são manuais |
| Operações atômicas e Lua | Durabilidade fraca por padrão (janela de perda no AOF `everysec`/RDB) |
| Simples de instalar e operar | Transações sem rollback (comandos já executados permanecem) |
| Ecossistema e clientes em todas as linguagens | Cluster limita scripts/multi-chaves a chaves no mesmo slot |

### Capacidades (redundância e backup)
- **Persistência:** RDB (snapshots periódicos, compacto) e AOF (log de escritas; `appendfsync everysec` perde ≤ 1 s). Usamos AOF + snapshot em `docker-compose.yml`.
- **Replicação** assíncrona primário→réplicas (`replicaof host porta`).
- **Sentinel:** monitora e faz *failover* automático.
- **Cluster:** shards (16384 hash slots) com réplicas por shard.
- **Backup:** copiar o `dump.rdb`/AOF (`BGSAVE`) ou `redis-cli --rdb arquivo.rdb`.

### Instalação / spin-up
`docker compose up -d` (Redis 7 + RedisInsight). Alternativas: `apt install redis`, `brew install redis`, Redis Cloud (plano gratuito). Teste: `redis-cli ping` → `PONG`.

### Tuning e performance
- `maxmemory` + `maxmemory-policy` (ex.: `allkeys-lru` para cache; `noeviction` para dados que não podem sumir, nosso caso).
- `appendfsync`: `always` (seguro/lento), `everysec` (equilíbrio), `no` (rápido).
- **Pipelining** e `MGET`/`HMGET` para reduzir round-trips (a listagem geral aqui faz N `HGETALL`; é o ponto a otimizar com pipeline).
- **Nunca `KEYS *`** em produção; use `SCAN` (usado no dump).
- Evitar *big keys*; escolher a estrutura adequada (Sorted Set para ranking, não ordenar na aplicação).
- Lua para juntar vários comandos em 1 round-trip; pool de conexões (Lettuce).
- Observar com `INFO`, `SLOWLOG GET`, `redis-cli --latency`.

### Dump dos dados
```bash
curl -X POST "localhost:8081/admin/dump?arquivo=estoque.jsonl"      # SCAN + DUMP -> backups/estoque.jsonl
curl -X POST "localhost:8081/admin/restore?arquivo=estoque.jsonl"   # RESTORE ... REPLACE
docker compose exec redis redis-cli BGSAVE                          # snapshot RDB em ./data
docker compose exec redis redis-cli --rdb /data/backup.rdb          # dump via protocolo de replicação
```

## 5. Estrutura
```
docker-compose.yml                          Redis (AOF) + RedisInsight
pom.xml                                     Spring Boot + Spring Data Redis
src/main/resources/scripts/*.lua            operações atômicas (criar, atualizar, movimentar, remover)
.../EstoqueRepository.java                  estruturas de dados e acesso ao Redis
.../EstoqueController.java                  API REST
.../Dtos.java, Erros.java                   validação e erros (400/404/409)
.../Seeder.java                             dados de exemplo
.../BackupController.java                   dump/restore (DUMP/RESTORE)
```
**Limitação conhecida:** os scripts acessam `idx:categoria:*` sem declará-las em `KEYS`; funciona em nó único/Sentinel. Em Redis Cluster seria preciso *hash tags* (`{sku}`) ou outra estratégia.

## 6. Melhorias futuras
Redis Stack (RedisJSON/RediSearch) para busca por texto, TTL em lotes com validade, Pub/Sub para notificar reposição, pipeline na listagem, autenticação por perfil.

## 7. Texto sugerido para o comentário da postagem
> **Equipe:** (nomes) · **BD:** Redis (chave-valor/multi-modelo) · **Aplicação:** controle de estoque da lanchonete do campus, com movimentações atômicas via Lua, histórico em Streams, alertas e ranking de vendas.
