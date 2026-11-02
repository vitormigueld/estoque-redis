-- KEYS: 1 produto, 2 stream, 3 alerta, 4 idx:produtos, 5 ranking
if redis.call('EXISTS', KEYS[1]) == 0 then return 0 end
local sku = redis.call('HGET', KEYS[1], 'sku')
local cat = redis.call('HGET', KEYS[1], 'categoria')
redis.call('DEL', KEYS[1], KEYS[2])
redis.call('SREM', KEYS[3], sku)
redis.call('SREM', KEYS[4], sku)
redis.call('SREM', 'idx:categoria:' .. cat, sku)
redis.call('ZREM', KEYS[5], sku)
return 1
