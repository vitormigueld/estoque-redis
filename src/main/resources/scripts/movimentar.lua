-- KEYS: 1 produto, 2 stream mov:sku, 3 alerta, 4 ranking
-- ARGV: delta, tipo, quantidade, motivo, operador, sku
-- retorna: -1 produto inexistente | -2 saldo insuficiente | novo saldo
if redis.call('EXISTS', KEYS[1]) == 0 then return -1 end
local novo = tonumber(redis.call('HGET', KEYS[1], 'quantidade')) + tonumber(ARGV[1])
if novo < 0 then return -2 end               -- nunca permite saldo negativo
redis.call('HSET', KEYS[1], 'quantidade', novo)
redis.call('XADD', KEYS[2], '*', 'tipo', ARGV[2], 'quantidade', ARGV[3], 'motivo', ARGV[4], 'operador', ARGV[5])
if ARGV[2] == 'SAIDA' then redis.call('ZINCRBY', KEYS[4], ARGV[3], ARGV[6]) end
if novo <= tonumber(redis.call('HGET', KEYS[1], 'minimo')) then
  redis.call('SADD', KEYS[3], ARGV[6])
else
  redis.call('SREM', KEYS[3], ARGV[6])
end
return novo
