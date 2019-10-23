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

package com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.properties;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.RateLimitUtils;
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.properties.validators.Policies;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.netflix.zuul.filters.Route;
import org.springframework.validation.annotation.Validated;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.springframework.cloud.netflix.zuul.filters.support.FilterConstants.FORM_BODY_WRAPPER_FILTER_ORDER;
import static org.springframework.cloud.netflix.zuul.filters.support.FilterConstants.SEND_RESPONSE_FILTER_ORDER;

/**
 * 限流的配置属性
 *
 * @author Marcos Barbero
 * @author Liel Chayoun
 */
@Validated
@RefreshScope
@ConfigurationProperties(RateLimitProperties.PREFIX)
public class RateLimitProperties {

    /**
     *

     zuul:
     ratelimit:
     ## Key的前缀
     key-prefix: your-prefix
     ## 是否看起限流
     enabled: true
     ## 数据存储方式，有那些数据存储方式？
     repository: REDIS
     behind-proxy: true
     add-response-headers: true
     ## 默认的限流策略
     default-policy-list:
     ## 限流的时间范围60秒
     -refresh-interval: 60
     ## 限流的时间范围内的请求次数
     limit: 10
     ## 限流的时间范围内的请求时间
     quota: 1000
     ## 特定的限流策略
     policy-list:
     ## Zuul中注册的服务的ServiceId
     myServiceId:
     limit: 10
     quota: 1000
     refresh-interval: 60
     ## 限流策略，有那些现流策略？
     type:
     - URL
     *
     */


    /**
     * 限流配置的前缀
     */
    public static final String PREFIX = "zuul.ratelimit";

    /**
     * 全局限流策略，可单独细化到服务粒度
     */
    @Valid
    @NotNull
    @Policies
    @NestedConfigurationProperty
    private List<Policy> defaultPolicyList = Lists.newArrayList();

    /**
     * 自定义的限流配置策略。 key为服务的服务名，value 为具体服务的限流策略
     */
    @Valid
    @NotNull
    @Policies
    @NestedConfigurationProperty
    private Map<String, List<Policy>> policyList = Maps.newHashMap();

    /**
     * 表示代理之后
     */
    private boolean behindProxy;

    /**
     * 限流开关是否开启，默认不开启
     */
    private boolean enabled;

    /**
     * 限流的信息是否设置到header，返回给客户端。默认开启
     */
    private boolean addResponseHeaders = true;

    /**
     * 按粒度拆分的临时变量key前缀.  如果未设置spring.application.name。就使用默认值rate-limit-application
     */
    @NotNull
    @Value("${spring.application.name:rate-limit-application}")
    private String keyPrefix;

    /**
     * 限流持久化枚举类型。key存储类型，默认是IN_MEMORY本地内存
     */
    @NotNull
    private RateLimitRepository repository;

    private int postFilterOrder = SEND_RESPONSE_FILTER_ORDER - 10;

    private int preFilterOrder = FORM_BODY_WRAPPER_FILTER_ORDER;

    /**
     * 根据key获取限流策略列表，获取不到就使用默认配置策略
     *
     * @param key
     * @return
     */
    public List<Policy> getPolicies(String key) {
        return policyList.getOrDefault(key, defaultPolicyList);
    }

    /**
     * 返回默认配置策略
     *
     * @return
     */
    public List<Policy> getDefaultPolicyList() {
        return defaultPolicyList;
    }

    //设置默认的配置策略
    public void setDefaultPolicyList(List<Policy> defaultPolicyList) {
        this.defaultPolicyList = defaultPolicyList;
    }

    /**
     * 获取所有配置策略
     *
     * @return
     */
    public Map<String, List<Policy>> getPolicyList() {
        return policyList;
    }

    /**
     * 设置所有的配置策略
     *
     * @param policyList
     */
    public void setPolicyList(Map<String, List<Policy>> policyList) {
        this.policyList = policyList;
    }

    public boolean isBehindProxy() {
        return behindProxy;
    }

    public void setBehindProxy(boolean behindProxy) {
        this.behindProxy = behindProxy;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAddResponseHeaders() {
        return addResponseHeaders;
    }

    public void setAddResponseHeaders(boolean addResponseHeaders) {
        this.addResponseHeaders = addResponseHeaders;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public RateLimitRepository getRepository() {
        return repository;
    }

    public void setRepository(RateLimitRepository repository) {
        this.repository = repository;
    }

    public int getPostFilterOrder() {
        return postFilterOrder;
    }

    public void setPostFilterOrder(int postFilterOrder) {
        this.postFilterOrder = postFilterOrder;
    }

    public int getPreFilterOrder() {
        return preFilterOrder;
    }

    public void setPreFilterOrder(int preFilterOrder) {
        this.preFilterOrder = preFilterOrder;
    }

    public static class Policy {

        /**
         * 单位时间窗口。默认是1秒
         */
        @NotNull
        private Long refreshInterval = MINUTES.toSeconds(1L);

        /**
         * 一个单位时间窗口的请求数量
         */
        private Long limit;

        /**
         * 一个单位时间窗口的请求时间限制
         */
        private Long quota;

        @NotNull
        private boolean breakOnMatch;

        @Valid
        @NotNull
        @NestedConfigurationProperty
        private List<MatchType> type = Lists.newArrayList();

        public Long getRefreshInterval() {
            return refreshInterval;
        }

        public void setRefreshInterval(Long refreshInterval) {
            this.refreshInterval = refreshInterval;
        }

        public Long getLimit() {
            return limit;
        }

        public void setLimit(Long limit) {
            this.limit = limit;
        }

        public Long getQuota() {
            return quota;
        }

        public void setQuota(Long quota) {
            this.quota = quota;
        }

        public boolean isBreakOnMatch() {
            return breakOnMatch;
        }

        public void setBreakOnMatch(boolean breakOnMatch) {
            this.breakOnMatch = breakOnMatch;
        }

        public List<MatchType> getType() {
            return type;
        }

        public void setType(List<MatchType> type) {
            this.type = type;
        }

        /**
         * 内部类
         */
        public static class MatchType {

            /**
             * 限流类型
             */
            @Valid
            @NotNull
            private RateLimitType type;

            /**
             * 匹配值。根据匹配规则，匹配上才会限流
             */
            private String matcher;

            public MatchType(@Valid @NotNull RateLimitType type, String matcher) {
                this.type = type;
                this.matcher = matcher;
            }

            public boolean apply(HttpServletRequest request, Route route, RateLimitUtils rateLimitUtils) {
                return StringUtils.isEmpty(matcher) || type.apply(request, route, rateLimitUtils, matcher);
            }

            public String key(HttpServletRequest request, Route route, RateLimitUtils rateLimitUtils) {
                return type.key(request, route, rateLimitUtils, matcher) +
                        (StringUtils.isEmpty(matcher) ? StringUtils.EMPTY : (":" + matcher));
            }

            public RateLimitType getType() {
                return type;
            }

            public void setType(RateLimitType type) {
                this.type = type;
            }

            public String getMatcher() {
                return matcher;
            }

            public void setMatcher(String matcher) {
                this.matcher = matcher;
            }
        }
    }
}