-- KEYS[1] = current window key
-- KEYS[2] = previous window key
-- ARGV[1] = limit
-- ARGV[2] = window size in seconds
-- ARGV[3] = current timestamp in seconds
-- ARGV[4] = current window start timestamp

local currentKey = KEYS[1]
local previousKey = KEYS[2]
local limit = tonumber(ARGV[1])
local windowSize = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local windowStart = tonumber(ARGV[4])

-- How far we are into current window (0.0 to 1.0)
local elapsedInWindow = now - windowStart
local elapsedFraction = elapsedInWindow / windowSize

-- Get counts from both windows
local currentCount = tonumber(redis.call('GET', currentKey)) or 0
local previousCount = tonumber(redis.call('GET', previousKey)) or 0

-- Weighted count formula used by Cloudflare in production
-- previous_count * (1 - elapsed_fraction) + current_count
local weightedCount = previousCount * (1 - elapsedFraction) + currentCount

if weightedCount >= limit then
-- Calculate retry after
	local retryAfter = math.ceil(windowSize - elapsedInWindow)
	return {0, 0, retryAfter}
end

-- Increment current window counter
redis.call('INCR', currentKey)
redis.call('EXPIRE', currentKey, windowSize * 2)

local remaining = math.floor(limit - weightedCount - 1)
local ttl = windowSize - elapsedInWindow

return {1, remaining, math.ceil(ttl)}