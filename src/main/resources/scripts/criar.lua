-- KEYS: 1 produto, 2 idx:produtos, 3 idx:categoria:X, 4 alerta
-- ARGV: sku, nome, categoria, preco, minimo, quantidade, criadoEm
if redis.call('EXISTS', KEYS[1]) == 1 then return 0 end
redis.call('HSET', KEYS[1], 'sku', ARGV[1], 'nome', ARGV[2], 'categoria', ARGV[3],
           'preco', ARGV[4], 'minimo', ARGV[5], 'quantidade', ARGV[6], 'criadoEm', ARGV[7])
redis.call('SADD', KEYS[2], ARGV[1])
redis.call('SADD', KEYS[3], ARGV[1])
if tonumber(ARGV[6]) <= tonumber(ARGV[5]) then redis.call('SADD', KEYS[4], ARGV[1]) end
return 1
