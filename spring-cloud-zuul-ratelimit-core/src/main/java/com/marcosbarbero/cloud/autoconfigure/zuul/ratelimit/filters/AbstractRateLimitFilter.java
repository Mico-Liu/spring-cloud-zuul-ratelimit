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

package com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.filters;

import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.RateLimitUtils;
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.properties.RateLimitProperties;
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.properties.RateLimitProperties.Policy;
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.properties.RateLimitProperties.Policy.MatchType;
import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import org.springframework.cloud.netflix.zuul.filters.Route;
import org.springframework.cloud.netflix.zuul.filters.RouteLocator;
import org.springframework.web.util.UrlPathHelper;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.stream.Collectors;

import static com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.support.RateLimitConstants.CURRENT_REQUEST_POLICY;
import static com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.support.RateLimitConstants.CURRENT_REQUEST_ROUTE;


/**
 * @author Marcos Barbero
 * @author Liel Chayoun
 */
abstract class AbstractRateLimitFilter extends ZuulFilter {

    /**
     * 限流的属性配置
     */
    private final RateLimitProperties properties;
    /**
     * 路由器
     */
    private final RouteLocator routeLocator;
    /**
     * url地址工具类
     */
    private final UrlPathHelper urlPathHelper;
    /**
     * 限流工具类
     */
    private final RateLimitUtils rateLimitUtils;

    /**
     * 是否已限流
     */
    private boolean alreadyLimited;

    AbstractRateLimitFilter(final RateLimitProperties properties, final RouteLocator routeLocator,
                            final UrlPathHelper urlPathHelper, final RateLimitUtils rateLimitUtils) {
        this.properties = properties;
        this.routeLocator = routeLocator;
        this.urlPathHelper = urlPathHelper;
        this.rateLimitUtils = rateLimitUtils;
    }

    @Override
    public boolean shouldFilter() {
        HttpServletRequest request = RequestContext.getCurrentContext().getRequest();
        //限流开关需打开
        //
        return properties.isEnabled() && !policy(route(request), request).isEmpty();
    }

    /**
     * 根据请求获取路由信息
     *
     * @param request
     * @return
     */
    Route route(HttpServletRequest request) {
        Route route = (Route) RequestContext.getCurrentContext().get(CURRENT_REQUEST_ROUTE);
        if (route != null) {
            return route;
        }

        //  urlPathHelper= org.springframework.web.util.UrlPathHelper
        //  routeLocator= org.springframework.cloud.netflix.zuul.filters.RouteLocator
        //  这两个实例是在RateLimitAutoConfiguration中注入的
        String requestURI = urlPathHelper.getPathWithinApplication(request);
        //获取Route
        route = routeLocator.getMatchingRoute(requestURI);

        //添加到zuul的上下文
        addObjectToCurrentRequestContext(CURRENT_REQUEST_ROUTE, route);

        return route;
    }

    /**
     * @param route   路由规则
     * @param request 当次请求
     * @return
     */
    @SuppressWarnings("unchecked")
    protected List<Policy> policy(Route route, HttpServletRequest request) {
        List<Policy> policies = (List<Policy>) RequestContext.getCurrentContext().get(CURRENT_REQUEST_POLICY);
        if (policies != null) {
            return policies;
        }

        String routeId = route != null ? route.getId() : null;
        alreadyLimited = false;
        //获取可使用的规则
        policies = properties.getPolicies(routeId).stream()
                .filter(policy -> applyPolicy(request, route, policy))
                .collect(Collectors.toList());

        //把规则设置到zuul的上下文
        addObjectToCurrentRequestContext(CURRENT_REQUEST_POLICY, policies);

        return policies;
    }

    /**
     * 把object对象添加到zuul上下文
     *
     * @param key
     * @param object 值
     */
    private void addObjectToCurrentRequestContext(String key, Object object) {
        if (object != null) {
            RequestContext.getCurrentContext().put(key, object);
        }
    }

    private boolean applyPolicy(HttpServletRequest request, Route route, Policy policy) {
        List<MatchType> types = policy.getType();
        boolean tmp = alreadyLimited;
        if (policy.isBreakOnMatch() && types.stream().allMatch(type -> type.apply(request, route, rateLimitUtils))) {
            alreadyLimited = true;
        }
        //判断请求是否需要限流
        return (types.isEmpty() || types.stream().allMatch(type -> type.apply(request, route, rateLimitUtils))) && !tmp;
    }
}
