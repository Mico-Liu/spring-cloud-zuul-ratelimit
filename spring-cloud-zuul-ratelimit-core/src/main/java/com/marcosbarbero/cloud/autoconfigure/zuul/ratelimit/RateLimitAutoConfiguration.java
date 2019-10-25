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

package com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit;

import com.ecwid.consul.v1.ConsulClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hazelcast.core.IMap;
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.RateLimitKeyGenerator;
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.RateLimitUtils;
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.RateLimiter;
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.properties.RateLimitProperties;
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.repository.ConsulRateLimiter;
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.repository.DefaultRateLimiterErrorHandler;
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.repository.RateLimiterErrorHandler;
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.repository.RedisRateLimiter;
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.repository.bucket4j.Bucket4jHazelcastRateLimiter;
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.repository.bucket4j.Bucket4jIgniteRateLimiter;
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.repository.bucket4j.Bucket4jInfinispanRateLimiter;
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.repository.bucket4j.Bucket4jJCacheRateLimiter;
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.repository.springdata.JpaRateLimiter;
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.repository.springdata.RateLimiterRepository;
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.filters.RateLimitPostFilter;
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.filters.RateLimitPreFilter;
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.support.DefaultRateLimitKeyGenerator;
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.support.DefaultRateLimitUtils;
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.support.SecuredRateLimitUtils;
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.support.StringToMatchTypeConverter;
import com.netflix.zuul.ZuulFilter;
import io.github.bucket4j.grid.GridBucketState;
import io.github.bucket4j.grid.hazelcast.Hazelcast;
import io.github.bucket4j.grid.ignite.Ignite;
import io.github.bucket4j.grid.infinispan.Infinispan;
import io.github.bucket4j.grid.jcache.JCache;
import org.apache.ignite.IgniteCache;
import org.infinispan.functional.FunctionalMap.ReadWriteMap;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.consul.ConditionalOnConsulEnabled;
import org.springframework.cloud.netflix.zuul.filters.RouteLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.util.UrlPathHelper;

import javax.cache.Cache;

import static com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.properties.RateLimitProperties.PREFIX;

/**
 * RateLimit的自动配置初始化
 * <p>
 * <p>
 * 1、注意EnableConfigurationProperties与ConfigurationProperties的区别
 * <p>
 * 2、如果属性配置了zuul.ratelimit.enabled=true，就开启自动化配置
 *
 * @author Marcos Barbero
 * @author Liel Chayoun
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
@ConditionalOnProperty(prefix = PREFIX, name = "enabled", havingValue = "true")
public class RateLimitAutoConfiguration {

    /**
     * Spring 的工具类
     */
    private static final UrlPathHelper URL_PATH_HELPER = new UrlPathHelper();

    /**
     * 字符串转MatchType处理类
     *
     * @return
     */
    @Bean
    @ConfigurationPropertiesBinding
    public StringToMatchTypeConverter stringToMatchTypeConverter() {
        return new StringToMatchTypeConverter();
    }

    /**
     * 实例化错误处理器，没有找到自定义的RateLimiterErrorHandlerBean，就使用默认的处理器
     *
     * @return
     */
    @Bean
    @ConditionalOnMissingBean(RateLimiterErrorHandler.class)
    public RateLimiterErrorHandler rateLimiterErrorHandler() {
        return new DefaultRateLimiterErrorHandler();
    }

    /**
     * 初始化限流执行前过滤器
     *
     * @param rateLimiter
     * @param rateLimitProperties
     * @param routeLocator
     * @param rateLimitKeyGenerator
     * @param rateLimitUtils
     * @return
     */
    @Bean
    public ZuulFilter rateLimiterPreFilter(final RateLimiter rateLimiter, final RateLimitProperties rateLimitProperties,
                                           final RouteLocator routeLocator, final RateLimitKeyGenerator rateLimitKeyGenerator,
                                           final RateLimitUtils rateLimitUtils) {
        return new RateLimitPreFilter(rateLimitProperties, routeLocator, URL_PATH_HELPER, rateLimiter,
                rateLimitKeyGenerator, rateLimitUtils);
    }

    /**
     * 初始化限流执行后的过滤器
     *
     * @param rateLimiter
     * @param rateLimitProperties
     * @param routeLocator
     * @param rateLimitKeyGenerator
     * @param rateLimitUtils
     * @return
     */
    @Bean
    public ZuulFilter rateLimiterPostFilter(final RateLimiter rateLimiter, final RateLimitProperties rateLimitProperties,
                                            final RouteLocator routeLocator, final RateLimitKeyGenerator rateLimitKeyGenerator,
                                            final RateLimitUtils rateLimitUtils) {
        return new RateLimitPostFilter(rateLimitProperties, routeLocator, URL_PATH_HELPER, rateLimiter,
                rateLimitKeyGenerator, rateLimitUtils);
    }

    /**
     * 实例化RateLimitKeyGenerator，如果用户没有自定义RateLimitKeyGenerator实力，则实例化默认实现
     *
     * @param properties
     * @param rateLimitUtils
     * @return
     */
    @Bean
    @ConditionalOnMissingBean(RateLimitKeyGenerator.class)
    public RateLimitKeyGenerator ratelimitKeyGenerator(final RateLimitProperties properties,
                                                       final RateLimitUtils rateLimitUtils) {
        return new DefaultRateLimitKeyGenerator(properties, rateLimitUtils);
    }

    @Configuration
    @ConditionalOnMissingBean(RateLimitUtils.class)
    public static class RateLimitUtilsConfiguration {

        @Bean
        @ConditionalOnClass(name = "org.springframework.security.core.Authentication")
        public RateLimitUtils securedRateLimitUtils(final RateLimitProperties rateLimitProperties) {
            return new SecuredRateLimitUtils(rateLimitProperties);
        }

        @Bean
        @ConditionalOnMissingClass("org.springframework.security.core.Authentication")
        public RateLimitUtils rateLimitUtils(final RateLimitProperties rateLimitProperties) {
            return new DefaultRateLimitUtils(rateLimitProperties);
        }
    }

    /**
     * 1、在类路径下存在RedisTemplate类
     * 2、在上下文找不到RateLimiter对象
     * 3、zuul.ratelimit.repository配置REDIS
     * 满足以上所有条件时，才会初始化此配置
     */
    @Configuration
    @ConditionalOnClass(RedisTemplate.class)
    @ConditionalOnMissingBean(RateLimiter.class)
    @ConditionalOnProperty(prefix = PREFIX, name = "repository", havingValue = "REDIS")
    public static class RedisConfiguration {

        /**
         * 实例化StringRedisTemplate对象，并且bean的名称是rateLimiterRedisTemplate
         *
         * @param connectionFactory
         * @return
         */
        @Bean("rateLimiterRedisTemplate")
        public StringRedisTemplate redisTemplate(final RedisConnectionFactory connectionFactory) {
            return new StringRedisTemplate(connectionFactory);
        }

        /**
         * 实例化RateLimiter对象，使用RedisRateLimiter的实现类创建
         *
         * @param rateLimiterErrorHandler rateLimiterErrorHandler对象
         * @param redisTemplate           ateLimiterRedisTemplate对象
         * @return 返回RateLimiter对象
         */
        @Bean
        public RateLimiter redisRateLimiter(final RateLimiterErrorHandler rateLimiterErrorHandler,
                                            @Qualifier("rateLimiterRedisTemplate") final RedisTemplate redisTemplate) {
            return new RedisRateLimiter(rateLimiterErrorHandler, redisTemplate);
        }
    }

    /**
     * 1、开启consul功能，即ConditionalOnConsulEnabled注解
     * 2、在上下文找不到RateLimiter对象
     * 3、zuul.ratelimit.repository配置CONSUL
     * 满足以上所有条件时，才会初始化此配置
     */
    @Configuration
    @ConditionalOnConsulEnabled
    @ConditionalOnMissingBean(RateLimiter.class)
    @ConditionalOnProperty(prefix = PREFIX, name = "repository", havingValue = "CONSUL")
    public static class ConsulConfiguration {

        /**
         * 例化RateLimiter对象，使用ConsulRateLimiter的实现类创建
         *
         * @param rateLimiterErrorHandler rateLimiterErrorHandler对象
         * @param consulClient
         * @param objectMapper
         * @return 返回RateLimiter对象
         */
        @Bean
        public RateLimiter consultRateLimiter(final RateLimiterErrorHandler rateLimiterErrorHandler,
                                              final ConsulClient consulClient, final ObjectMapper objectMapper) {
            return new ConsulRateLimiter(rateLimiterErrorHandler, consulClient, objectMapper);
        }

    }

    /**
     * 1、开类路径下游JCache和Cache类。JCache是bucket4j的基础类。Cache是jdk自带的cache-api类
     * 2、在上下文找不到RateLimiter对象
     * 3、zuul.ratelimit.repository配置BUCKET4J_JCACHE
     * 满足以上所有条件时，才会初始化此配置
     */
    @Configuration
    @ConditionalOnMissingBean(RateLimiter.class)
    @ConditionalOnClass({JCache.class, Cache.class})
    @ConditionalOnProperty(prefix = PREFIX, name = "repository", havingValue = "BUCKET4J_JCACHE")
    public static class Bucket4jJCacheConfiguration {

        /**
         * 例化RateLimiter对象，使用Bucket4jJCacheRateLimiter的实现类创建
         *
         * @param cache
         * @return
         */
        @Bean
        public RateLimiter jCache4jHazelcastRateLimiter(@Qualifier("RateLimit") final Cache<String, GridBucketState> cache) {
            return new Bucket4jJCacheRateLimiter(cache);
        }
    }

    /**
     * 1、在类路径下能找到Hazelcast和IMap类。Hazelcast是bucket4j-hazelcast的基础类。IMap是hazelcast-core自带中的类
     * 2、在上下文找不到RateLimiter对象
     * 3、zuul.ratelimit.repository配置BUCKET4J_HAZELCAST
     * 满足以上所有条件时，才会初始化此配置
     *
     * Hazelcast是内存数据平台，并提供了分布式计算。有嵌入部署和独立部署两种方式
     */
    @Configuration
    @ConditionalOnMissingBean(RateLimiter.class)
    @ConditionalOnClass({Hazelcast.class, IMap.class})
    @ConditionalOnProperty(prefix = PREFIX, name = "repository", havingValue = "BUCKET4J_HAZELCAST")
    public static class Bucket4jHazelcastConfiguration {

        /**
         * 化RateLimiter对象，使用Bucket4jHazelcastRateLimiter的实现类创建
         *
         * @param rateLimit
         * @return
         */
        @Bean
        public RateLimiter bucket4jHazelcastRateLimiter(@Qualifier("RateLimit") final IMap<String, GridBucketState> rateLimit) {
            return new Bucket4jHazelcastRateLimiter(rateLimit);
        }
    }

    /**
     * 1、在类路径下能找到Ignite和IgniteCache类。Ignite是bucket4j-ignite的基础类。IgniteCache是ignite-core自带中的类
     * 2、在上下文找不到RateLimiter对象
     * 3、zuul.ratelimit.repository配置BUCKET4J_HAZELCAST
     * 满足以上所有条件时，才会初始化此配置
     * <p>
     * Ignite是一个内存为中心的数据平台。嵌入部署
     */
    @Configuration
    @ConditionalOnMissingBean(RateLimiter.class)
    @ConditionalOnClass({Ignite.class, IgniteCache.class})
    @ConditionalOnProperty(prefix = PREFIX, name = "repository", havingValue = "BUCKET4J_IGNITE")
    public static class Bucket4jIgniteConfiguration {

        @Bean
        public RateLimiter bucket4jIgniteRateLimiter(@Qualifier("RateLimit") final IgniteCache<String, GridBucketState> cache) {
            return new Bucket4jIgniteRateLimiter(cache);
        }
    }

    /**
     * 1、在类路径下能找到Infinispan和ReadWriteMap类。Infinispan是bucket4j-infinispan的基础类。ReadWriteMap是nfinispan-core自带中的类
     * 2、在上下文找不到RateLimiter对象
     * 3、zuul.ratelimit.repository配置BUCKET4J_INFINISPAN
     * 满足以上所有条件时，才会初始化此配置
     * <p>
     * Ignite是一个分布式键值服务，提供嵌入部署和独立部署两种方式
     */
    @Configuration
    @ConditionalOnMissingBean(RateLimiter.class)
    @ConditionalOnClass({Infinispan.class, ReadWriteMap.class})
    @ConditionalOnProperty(prefix = PREFIX, name = "repository", havingValue = "BUCKET4J_INFINISPAN")
    public static class Bucket4jInfinispanConfiguration {

        @Bean
        public RateLimiter bucket4jInfinispanRateLimiter(@Qualifier("RateLimit") final ReadWriteMap<String, GridBucketState> readWriteMap) {
            return new Bucket4jInfinispanRateLimiter(readWriteMap);
        }
    }

    /**
     * 1、开启entity扫描
     * 2、在上下文找不到RateLimiter对象
     * 3、zuul.ratelimit.repository配置JPA
     * 4、扫描com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.repository.springdata包下的所有JapanRepository
     * 满足以上所有条件时，才会初始化此配置
     *
     */
    @EntityScan
    @Configuration
    @EnableJpaRepositories(basePackages = "com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.repository.springdata")
    @ConditionalOnMissingBean(RateLimiter.class)
    @ConditionalOnProperty(prefix = PREFIX, name = "repository", havingValue = "JPA")
    public static class SpringDataConfiguration {

        @Bean
        public RateLimiter springDataRateLimiter(final RateLimiterErrorHandler rateLimiterErrorHandler,
                                                 final RateLimiterRepository rateLimiterRepository) {
            return new JpaRateLimiter(rateLimiterErrorHandler, rateLimiterRepository);
        }

    }

}
