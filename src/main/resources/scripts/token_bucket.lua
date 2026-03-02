-- KEYS[1] = Redis key for bucket
-- ARGV[1] = bucket capacity (max tokens)
-- ARGV[2] = refill rate (tokens per second)
-- ARGV[3] = current timestamp in seconds

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refillRate = tonumber(ARGV[2])
local now = tonumber(ARGV[3])

-- Get current bucket state
local bucket = redis.call('HMGET', key, 'tokens', 'lastRefill')
local tokens = tonumber(bucket[1])
local lastRefill = tonumber(bucket[2])

if tokens == nil then
-- First request, initialize bucket
	tokens = capacity - 1
	redis.call('HMSET', key, 'tokens', tokens, 'lastRefill', now)
	redis.call('EXPIRE', key, 3600)
	return {1, tokens, 0}
end

-- Calculate how many tokens to add since last refill
local elapsed = now - lastRefill
local tokensToAdd = elapsed * refillRate
tokens = math.min(capacity, tokens + tokensToAdd)

if tokens < 1 then
-- No tokens available
	local waitTime = math.ceil((1 - tokens) / refillRate)
	return {0, 0, waitTime}
end

-- Consume one token
tokens = tokens - 1
redis.call('HMSET', key, 'tokens', tokens, 'lastRefill', now)
redis.call('EXPIRE', key, 3600)

return {1, math.floor(tokens), 0}