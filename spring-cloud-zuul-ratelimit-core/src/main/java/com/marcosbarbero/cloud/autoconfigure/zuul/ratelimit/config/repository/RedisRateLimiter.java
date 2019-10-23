/*
 * Copyright 2012-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.repository;

import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.Rate;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Objects;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * 限流的信息存储在redis
 *
 * @author Marcos Barbero
 * @author Liel Chayoun
 */
@SuppressWarnings("unchecked")
public class RedisRateLimiter extends AbstractCacheRateLimiter {

    /**
     * 限流错误处理器
     */
    private final RateLimiterErrorHandler rateLimiterErrorHandler;
    /**
     * 操作redis的client
     */
    private final RedisTemplate redisTemplate;

    public RedisRateLimiter(RateLimiterErrorHandler rateLimiterErrorHandler, RedisTemplate redisTemplate) {
        this.rateLimiterErrorHandler = rateLimiterErrorHandler;
        this.redisTemplate = redisTemplate;
    }

    /**
     * @param limit           单位时间窗口内的总次数
     * @param refreshInterval 单位时间窗口
     * @param requestTime     单位时间窗口内的总耗时
     * @param key             限流的key
     * @param rate            限流key对应的信息
     */
    @Override
    protected void calcRemainingLimit(final Long limit, final Long refreshInterval,
                                      final Long requestTime, final String key, final Rate rate) {
        if (Objects.nonNull(limit)) {
            //通过此值标记是否计算一次请求。当请求耗时时间为空时，则说明是RateLimitPreFilter在执行，就代表使用了1次。
            //如果是RateLimitPostFilter执行，那么requestTime不为空。则usage为0。就不能计算剩余次数
            long usage = requestTime == null ? 1L : 0L;
            //计算剩余次数
            Long remaining = calcRemaining(limit, refreshInterval, usage, key, rate);
            //更新rate对象
            rate.setRemaining(remaining);
        }
    }

    /**
     * @param quota           单位时间窗口内总时长
     * @param refreshInterval 单位时间窗口，单位秒
     * @param requestTime     当次请求的耗时，单位毫秒
     * @param key             限流的key
     * @param rate            限流key对应的信息
     */
    @Override
    protected void calcRemainingQuota(final Long quota, final Long refreshInterval,
                                      final Long requestTime, final String key, final Rate rate) {
        if (Objects.nonNull(quota)) {
            String quotaKey = key + QUOTA_SUFFIX;
            long usage = requestTime != null ? requestTime : 0L;
            Long remaining = calcRemaining(quota, refreshInterval, usage, quotaKey, rate);
            rate.setRemainingQuota(remaining);
        }
    }

    /**
     * 计算剩余次数
     *
     * @param limit           单位时间窗口内的总次数
     * @param refreshInterval 单位时间窗口
     * @param usage           是否计算
     * @param key
     * @param rate
     * @return
     */
    private Long calcRemaining(Long limit, Long refreshInterval, long usage,
                               String key, Rate rate) {
        rate.setReset(SECONDS.toMillis(refreshInterval));
        //key对应的请求次数
        Long current = 0L;
        try {
            //在redis增加usage值，1或0
            current = redisTemplate.opsForValue().increment(key, usage);
            // Redis returns the value of key after the increment, check for the first increment, and the expiration time is set
            // 判断如果是第一次请求，则增加超时时间。 仅有一次机会
            if (current != null && current.equals(usage)) {
                //设置超时时间
                handleExpiration(key, refreshInterval);
            }
        } catch (RuntimeException e) {
            String msg = "Failed retrieving rate for " + key + ", will return the current value";
            rateLimiterErrorHandler.handleError(msg, e);
        }
        //返回剩余次数。即总次数减去总的请求次数
        return Math.max(-1, limit - (current != null ? current : 0L));
    }

    /**
     * 判断是否已超时
     *
     * @param key             限流的key
     * @param refreshInterval 单位时间窗口时间，单位秒
     */
    private void handleExpiration(String key, Long refreshInterval) {
        try {
            this.redisTemplate.expire(key, refreshInterval, SECONDS);
        } catch (RuntimeException e) {
            String msg = "Failed retrieving expiration for " + key + ", will reset now";
            rateLimiterErrorHandler.handleError(msg, e);
        }
    }
}
