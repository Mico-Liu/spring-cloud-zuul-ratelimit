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

package com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.repository.springdata;

import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.Rate;
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.RateLimiter;
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.repository.AbstractRateLimiter;
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.repository.RateLimiterErrorHandler;

/**
 * Rate的CRUD操作
 *
 * JPA {@link RateLimiter} configuration.
 *
 * @author Marcos Barbero
 * @author Liel Chayoun
 * @since 2017-06-23
 */
public class JpaRateLimiter extends AbstractRateLimiter {

    private final RateLimiterRepository repository;

    public JpaRateLimiter(final RateLimiterErrorHandler rateLimiterErrorHandler,
                          final RateLimiterRepository repository) {
        super(rateLimiterErrorHandler);
        this.repository = repository;
    }

    /**
     * 根据计算出来的key值获取Rate
     * @param key
     * @return
     */
    @Override
    protected Rate getRate(String key) {
        //根据主键查询，key为主键。查不到就返回空
        return this.repository.findById(key).orElse(null);
    }

    /**
     * 更新或创建Rate
     * @param rate
     */
    @Override
    protected void saveRate(Rate rate) {
        this.repository.save(rate);
    }

}
