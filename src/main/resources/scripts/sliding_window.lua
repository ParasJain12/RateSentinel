-- KEYS[1] = Redis key
-- ARGV[1] = limit
-- ARGV[2] = window size in seconds
-- ARGV[3] = current timestamp in milliseconds

local key = KEYS[1]
local limit = tonumber(ARGV[1])
local window = tonumber(ARGV[2]) * 1000
local now = tonumber(ARGV[3])
local clearBefore = now - window

-- Remove old entries outside the window
redis.call('ZREMRANGEBYSCORE', key, 0, clearBefore)

-- Count current entries in window
local current = redis.call('ZCARD', key)

if current >= limit then
    local oldestEntry = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
    local retryAfter = 0
    if oldestEntry and oldestEntry[2] then
        retryAfter = math.ceil((tonumber(oldestEntry[2]) + window - now) / 1000)
    end
    return {0, 0, retryAfter}
end

-- Add current request timestamp
redis.call('ZADD', key, now, now .. '-' .. math.random(1000000))

-- Set expiry on the key
redis.call('EXPIRE', key, tonumber(ARGV[2]) + 1)

local remaining = limit - current - 1
return {1, remaining, 0}