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

package com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config;

import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.properties.RateLimitProperties.Policy;

/**
 * @author Marcos Barbero
 * @author Liel Chayoun
 */
public interface RateLimiter {

    String QUOTA_SUFFIX = "-quota";

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
    Rate consume(Policy policy, String key, Long requestTime);
}
