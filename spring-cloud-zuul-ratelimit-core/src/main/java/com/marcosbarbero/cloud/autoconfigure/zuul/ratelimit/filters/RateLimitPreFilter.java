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

import static com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.support.RateLimitConstants.*;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.springframework.cloud.netflix.zuul.filters.support.FilterConstants.PRE_TYPE;

import com.google.common.collect.Maps;
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.Rate;
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.RateLimitKeyGenerator;
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.RateLimitUtils;
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.RateLimiter;
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.properties.RateLimitProperties;
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.support.RateLimitExceededException;
import com.netflix.zuul.context.RequestContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.cloud.netflix.zuul.filters.Route;
import org.springframework.cloud.netflix.zuul.filters.RouteLocator;
import org.springframework.http.HttpStatus;
import org.springframework.web.util.UrlPathHelper;

import java.util.Map;

/**
 * @author Marcos Barbero
 * @author Michal Šváb
 * @author Liel Chayoun
 */
public class RateLimitPreFilter extends AbstractRateLimitFilter {

    private final RateLimitProperties properties;
    private final RateLimiter rateLimiter;
    private final RateLimitKeyGenerator rateLimitKeyGenerator;

    public RateLimitPreFilter(final RateLimitProperties properties, final RouteLocator routeLocator,
                              final UrlPathHelper urlPathHelper, final RateLimiter rateLimiter,
                              final RateLimitKeyGenerator rateLimitKeyGenerator, final RateLimitUtils rateLimitUtils) {
        super(properties, routeLocator, urlPathHelper, rateLimitUtils);
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.rateLimitKeyGenerator = rateLimitKeyGenerator;
    }

    @Override
    public String filterType() {
        return PRE_TYPE;
    }

    @Override
    public int filterOrder() {
        return properties.getPreFilterOrder();
    }

    @Override
    public Object run() {
        final RequestContext ctx = RequestContext.getCurrentContext();
        final HttpServletResponse response = ctx.getResponse();
        //获取请求
        final HttpServletRequest request = ctx.getRequest();
        //根据请求获取Route
        final Route route = route(request);

        //获取配置的规则
        policy(route, request).forEach(policy -> {
            Map<String, String> responseHeaders = Maps.newHashMap();

            //计数的key的生成
            final String key = rateLimitKeyGenerator.key(request, route, policy);
            //计数
            final Rate rate = rateLimiter.consume(policy, key, null);
            final String httpHeaderKey = key.replaceAll("[^A-Za-z0-9-.]", "_").replaceAll("__", "_");

            //获取配置的单位时间窗口内的请求数限制
            final Long limit = policy.getLimit();
            //根据rate获取剩余的请求次数
            final Long remaining = rate.getRemaining();
            if (limit != null) {
                responseHeaders.put(HEADER_LIMIT + httpHeaderKey, String.valueOf(limit));
                responseHeaders.put(HEADER_REMAINING + httpHeaderKey, String.valueOf(Math.max(remaining, 0)));
            }

            //获取配置的单位时间窗口内的请求时长
            final Long quota = policy.getQuota();
            final Long remainingQuota = rate.getRemainingQuota();
            if (quota != null) {
                //设置rate limit 请求的开始时间
                request.setAttribute(REQUEST_START_TIME, System.currentTimeMillis());
                responseHeaders.put(HEADER_QUOTA + httpHeaderKey, String.valueOf(quota));
                responseHeaders.put(HEADER_REMAINING_QUOTA + httpHeaderKey,
                    String.valueOf(MILLISECONDS.toSeconds(Math.max(remainingQuota, 0))));
            }

            responseHeaders.put(HEADER_RESET + httpHeaderKey, String.valueOf(rate.getReset()));

            if (properties.isAddResponseHeaders()) {
                for (Map.Entry<String, String> headersEntry : responseHeaders.entrySet()) {
                    response.setHeader(headersEntry.getKey(), headersEntry.getValue());
                }
            }

            /**
             * 单位时间窗口内的请求次数或者一段时间内的请求时长超过设定值就给429
             */
            if ((limit != null && remaining < 0) || (quota != null && remainingQuota < 0)) {
                //http status  statusCode=429
                ctx.setResponseStatusCode(HttpStatus.TOO_MANY_REQUESTS.value());
                ctx.put(RATE_LIMIT_EXCEEDED, "true");
                ctx.setSendZuulResponse(false);
                //抛出达到限流的异常
                throw new RateLimitExceededException();
            }
        });

        return null;
    }
}
