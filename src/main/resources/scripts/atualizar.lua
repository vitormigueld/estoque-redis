-- KEYS: 1 produto, 2 alerta | ARGV: campo1, valor1, campo2, valor2, ...
if redis.call('EXISTS', KEYS[1]) == 0 then return 0 end
local sku = redis.call('HGET', KEYS[1], 'sku')
local antiga = redis.call('HGET', KEYS[1], 'categoria')
for i = 1, #ARGV, 2 do redis.call('HSET', KEYS[1], ARGV[i], ARGV[i + 1]) end
local nova = redis.call('HGET', KEYS[1], 'categoria')
if nova ~= antiga then                       -- mantém o índice secundário por categoria
  redis.call('SREM', 'idx:categoria:' .. antiga, sku)
  redis.call('SADD', 'idx:categoria:' .. nova, sku)
end
local q, m = tonumber(redis.call('HGET', KEYS[1], 'quantidade')), tonumber(redis.call('HGET', KEYS[1], 'minimo'))
if q <= m then redis.call('SADD', KEYS[2], sku) else redis.call('SREM', KEYS[2], sku) end
return 1
