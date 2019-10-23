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

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Bucket4j rate limiter configuration.
 *
 * @author Liel Chayoun
 * @since 2018-04-06
 */
public abstract class AbstractCacheRateLimiter implements RateLimiter {

    /**
     * Filter调用。消耗调用次数或调用耗时。 配置文件配置了两个重要参数，一个是请求次数，一个是请求耗时时长
     * <p>
     * 此处每调用一次总次数就减1，总时长也会递减。 在请求到来时如果已过期会重置Rate，重置总次数和总时长
     *
     * @param policy      Template for which rates should be created in case there's no rate limit associated with the
     *                    key  用户配置的限流策略
     * @param key         Unique key that identifies a request  唯一性的请求key
     * @param requestTime The total time it took to handle the request  处理请求的耗时时间，单位毫秒
     * @return a view of a user's rate request limit 返回key对应的Rate信息
     */
    @Override
    public synchronized Rate consume(Policy policy, String key, Long requestTime) {
        final Long refreshInterval = policy.getRefreshInterval();
        final Long quota = policy.getQuota() != null ? SECONDS.toMillis(policy.getQuota()) : null;
        final Rate rate = new Rate(key, policy.getLimit(), quota, null, null);

        calcRemainingLimit(policy.getLimit(), refreshInterval, requestTime, key, rate);
        calcRemainingQuota(quota, refreshInterval, requestTime, key, rate);

        return rate;
    }

    /**
     * @param limit           单位时间窗口内的总次数
     * @param refreshInterval 单位时间窗口
     * @param requestTime     单位时间窗口内的总耗时
     * @param key             限流的key
     * @param rate            限流key对应的信息
     */
    protected abstract void calcRemainingLimit(Long limit, Long refreshInterval, Long requestTime, String key, Rate rate);

    /**
     * @param quota           单位时间窗口内总时长
     * @param refreshInterval 单位时间窗口，单位秒
     * @param requestTime     当次请求的耗时，单位毫秒
     * @param key             限流的key
     * @param rate            限流key对应的信息
     */
    protected abstract void calcRemainingQuota(Long quota, Long refreshInterval, Long requestTime, String key, Rate rate);
}
