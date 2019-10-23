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
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.RateLimiter;
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.properties.RateLimitProperties.Policy;

import java.util.Date;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Abstract implementation for {@link RateLimiter}.
 *
 * @author Liel Chayoun
 * @author Marcos Barbero
 * @since 2017-08-28
 */
public abstract class AbstractRateLimiter implements RateLimiter {

    private final RateLimiterErrorHandler rateLimiterErrorHandler;

    protected AbstractRateLimiter(RateLimiterErrorHandler rateLimiterErrorHandler) {
        this.rateLimiterErrorHandler = rateLimiterErrorHandler;
    }

    protected abstract Rate getRate(String key);

    protected abstract void saveRate(Rate rate);

    /**
     * 使用了synchronized，会不会影响性能？
     * <p>
     * 消费：即调用一次请求
     *
     * @param policy      Template for which rates should be created in case there's no rate limit associated with the
     *                    key
     * @param key         Unique key that identifies a request  唯一性的请求key
     * @param requestTime The total time it took to handle the request  处理请求的耗时时间，单位毫秒
     * @return
     */
    @Override
    public synchronized Rate consume(final Policy policy, final String key, final Long requestTime) {
        //获取Rate，如果过期，就重新构建新的Rate
        Rate rate = this.create(policy, key);
        //修改Rate对象的信息
        updateRate(policy, rate, requestTime);
        try {
            //把Rate持久化，新增或更新数据到持久化，比如JPA或Redis
            saveRate(rate);
        } catch (RuntimeException e) {
            rateLimiterErrorHandler.handleSaveError(key, e);
        }
        return rate;
    }

    /**
     * 创建一个限流策略
     *
     * @param policy 策略
     * @param key    限流的key值
     * @return
     */
    private Rate create(final Policy policy, final String key) {
        Rate rate = null;
        try {
            //查询Rate
            rate = this.getRate(key);
        } catch (RuntimeException e) {
            rateLimiterErrorHandler.handleFetchError(key, e);
        }

        /**
         * 判断是否已过期，如果未过期，返回
         */
        if (!isExpired(rate)) {
            return rate;
        }
        //单位时间窗口内的请求次数
        Long limit = policy.getLimit();
        //单位时间窗口内的请求时间，单位毫秒
        Long quota = policy.getQuota() != null ? SECONDS.toMillis(policy.getQuota()) : null;
        //单位时间窗口，单位毫秒
        Long refreshInterval = SECONDS.toMillis(policy.getRefreshInterval());
        //过期时间（当前时间加上单位时间窗口）
        Date expiration = new Date(System.currentTimeMillis() + refreshInterval);

        return new Rate(key, limit, quota, refreshInterval, expiration);
    }

    /**
     * 更新Rate。 两个Filter都会触发执行，一个filter更新次数，一个filter更新剩余时间
     *
     * @param policy
     * @param rate
     * @param requestTime 请求耗时时间
     */
    private void updateRate(final Policy policy, final Rate rate, final Long requestTime) {
        if (rate.getReset() > 0) {
            Long reset = rate.getExpiration().getTime() - System.currentTimeMillis();
            rate.setReset(reset);
        }
        //更新剩余次数，在前置过滤器执行，那时requestTime才是空
        if (policy.getLimit() != null && requestTime == null) {
            rate.setRemaining(Math.max(-1, rate.getRemaining() - 1));
        }
        //更新剩余时间，在后置过滤器执行，这时requestTime不为空
        if (policy.getQuota() != null && requestTime != null) {
            rate.setRemainingQuota(Math.max(-1, rate.getRemainingQuota() - requestTime));
        }
    }

    private boolean isExpired(final Rate rate) {
        //rate为空为过期
        //过期时间小于当前时间，就过期
        return rate == null || (rate.getExpiration().getTime() < System.currentTimeMillis());
    }
}
