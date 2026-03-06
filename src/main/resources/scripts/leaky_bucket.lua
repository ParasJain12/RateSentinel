-- KEYS[1] = Redis key for bucket
-- ARGV[1] = bucket capacity
-- ARGV[2] = leak rate (requests per second)
-- ARGV[3] = current timestamp in milliseconds

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local leakRate = tonumber(ARGV[2])
local now = tonumber(ARGV[3])

-- Get current bucket state
local bucket = redis.call('HMGET', key, 'water', 'lastLeak')
local water = tonumber(bucket[1])
local lastLeak = tonumber(bucket[2])

if water == nil then
-- First request — initialize bucket with 1 unit of water
	redis.call('HMSET', key, 'water', 1, 'lastLeak', now)
	redis.call('EXPIRE', key, 3600)
	local remaining = capacity - 1
	return {1, remaining, 0}
end

-- Calculate how much water leaked since last check
local elapsed = (now - lastLeak) / 1000.0
local leaked = elapsed * leakRate
water = math.max(0, water - leaked)

if water >= capacity then
-- Bucket is full — reject request
	local waitTime = math.ceil((water - capacity + 1) / leakRate)
	return {0, 0, waitTime}
end

-- Add water (process request)
water = water + 1
redis.call('HMSET', key, 'water', water, 'lastLeak', now)
redis.call('EXPIRE', key, 3600)

local remaining = math.floor(capacity - water)
return {1, remaining, 0}