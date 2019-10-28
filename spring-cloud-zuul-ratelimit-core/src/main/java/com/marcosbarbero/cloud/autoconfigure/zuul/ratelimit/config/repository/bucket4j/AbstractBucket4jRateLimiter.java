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

package com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.repository.bucket4j;

import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.Rate;
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.repository.AbstractCacheRateLimiter;
import io.github.bucket4j.*;
import io.github.bucket4j.grid.ProxyManager;

import java.time.Duration;
import java.util.function.Supplier;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * 使用令牌桶算法
 * <p>
 * <p>
 * Bucket4j rate limiter configuration.
 *
 * @author Liel Chayoun
 * @since 2018-04-06
 */
abstract class AbstractBucket4jRateLimiter<T extends AbstractBucketBuilder<T>, E extends Extension<T>> extends AbstractCacheRateLimiter {

    private final Class<E> extension;
    private ProxyManager<String> buckets;

    AbstractBucket4jRateLimiter(final Class<E> extension) {
        this.extension = extension;
    }

    void init() {

        buckets = getProxyManager(getExtension());
    }

    private E getExtension() {
        return Bucket4j.extension(extension);
    }

    protected abstract ProxyManager<String> getProxyManager(E extension);

    /**
     * 获取时间的令牌桶
     *
     * @param key             限流的key值
     * @param quota
     * @param refreshInterval 单位时间窗口，单位秒
     * @return
     */
    private Bucket getQuotaBucket(String key, Long quota, Long refreshInterval) {
        return buckets.getProxy(key + QUOTA_SUFFIX, getBucketConfiguration(quota, refreshInterval));
    }

    /**
     * 获取大次数的令牌桶
     *
     * @param key
     * @param limit           单位时间窗口范围的最大次数
     * @param refreshInterval 单位时间窗口，单位秒
     * @return
     */
    private Bucket getLimitBucket(String key, Long limit, Long refreshInterval) {
        return buckets.getProxy(key, getBucketConfiguration(limit, refreshInterval));
    }

    /**
     * @param capacity 令牌桶里的容量大小
     * @param period   单位时间窗口，单位秒
     * @return
     */
    private Supplier<BucketConfiguration> getBucketConfiguration(Long capacity, Long period) {
        return () -> Bucket4j.configurationBuilder()
                .addLimit(Bandwidth.simple(capacity, Duration.ofSeconds(period)))
                .build();
    }

    /**
     * 设置剩余值
     *
     * @param rate      限流信息
     * @param remaining 剩余值
     * @param isQuota   isQuota=true时remaining剩余值为剩余时间，isQuota=true时remaining剩余值时为剩余次数
     */
    private void setRemaining(Rate rate, long remaining, boolean isQuota) {
        if (isQuota) {
            rate.setRemainingQuota(remaining);
        } else {
            rate.setRemaining(remaining);
        }
    }

    /**
     * // 毫秒
     * Bucket4j.builder().withMillisecondPrecision().build;
     * // 微秒
     * Bucket4j.builder().withNanosecondPrecision().build()
     * <p>
     * 计算并且设置剩余的令牌
     *
     * @param consume 消费次数
     * @param rate    限流的信息
     * @param bucket
     * @param isQuota
     */
    private void calcAndSetRemainingBucket(Long consume, Rate rate, Bucket bucket, boolean isQuota) {
        //尝试消费consume个令牌
        ConsumptionProbe consumptionProbe = bucket.tryConsumeAndReturnRemaining(consume);
        long nanosToWaitForRefill = consumptionProbe.getNanosToWaitForRefill();
        rate.setReset(NANOSECONDS.toMillis(nanosToWaitForRefill));
        //判断是否能消耗
        if (consumptionProbe.isConsumed()) {
            //剩余次数
            long remainingTokens = consumptionProbe.getRemainingTokens();
            //更新rate中的剩余次数或剩余时间
            setRemaining(rate, remainingTokens, isQuota);
        }
        //没有令牌
        else {
            //更新rate中的剩余次数或剩余时间。 设置为-1，用完了
            setRemaining(rate, -1L, isQuota);
            //告知令牌桶，要增加令牌了
            bucket.tryConsumeAsMuchAsPossible(consume);
        }
    }

    private void calcAndSetRemainingBucket(Bucket bucket, Rate rate, boolean isQuota) {
        long availableTokens = bucket.getAvailableTokens();
        long remaining = availableTokens > 0 ? availableTokens : -1;
        setRemaining(rate, remaining, isQuota);
    }

    /**
     * 计算次数令牌桶的剩余次数值
     *
     * @param limit           单位时间窗口内的总次数
     * @param refreshInterval 单位时间窗口
     * @param requestTime     单位时间窗口内的总耗时
     * @param key             限流的key
     * @param rate            限流key对应的信息
     */
    @Override
    protected void calcRemainingLimit(final Long limit, final Long refreshInterval, final Long requestTime,
                                      final String key, final Rate rate) {
        if (limit == null) {
            return;
        }
        Bucket bucket = getLimitBucket(key, limit, refreshInterval);
        //执行preFilter时
        if (requestTime == null) {
            calcAndSetRemainingBucket(1L, rate, bucket, false);
        }
        //执行POSTFilter时
        else {
            calcAndSetRemainingBucket(bucket, rate, false);
        }
    }

    @Override
    protected void calcRemainingQuota(final Long quota, final Long refreshInterval, final Long requestTime,
                                      final String key, final Rate rate) {
        if (quota == null) {
            return;
        }
        Bucket bucket = getQuotaBucket(key, quota, refreshInterval);
        if (requestTime != null) {
            calcAndSetRemainingBucket(requestTime, rate, bucket, true);
        } else {
            calcAndSetRemainingBucket(bucket, rate, true);
        }
    }
}
