-- KEYS[1] = Redis key (e.g. "rate:fixed:user123:/api/search")
-- ARGV[1] = limit (max requests allowed)
-- ARGV[2] = window size in seconds

local key = KEYS[1]
local limit = tonumber(ARGV[1])
local window = tonumber(ARGV[2])

-- Get current count
local current = redis.call('GET', key)

if current == false then
-- First request, set counter to 1 with expiry
	redis.call('SET', key, 1, 'EX', window)
	return {1, limit - 1, window}
end

current = tonumber(current)

if current >= limit then
-- Limit exceeded, get remaining TTL for retry-after
	local ttl = redis.call('TTL', key)
	return {0, 0, ttl}
end

-- Increment and return
redis.call('INCR', key)
local remaining = limit - current - 1
local ttl = redis.call('TTL', key)
return {1, remaining, ttl}