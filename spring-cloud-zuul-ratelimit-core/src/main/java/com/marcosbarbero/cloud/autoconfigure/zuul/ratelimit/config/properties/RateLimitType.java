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

import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.RateLimitUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.net.util.SubnetUtils;
import org.springframework.cloud.netflix.zuul.filters.Route;
import org.springframework.util.AntPathMatcher;

import javax.servlet.http.HttpServletRequest;
import java.util.Optional;

public enum RateLimitType {
    /**
     * Rate limit policy considering the user's origin.
     * <p>
     * 针对请用户的IP地址限流
     */
    ORIGIN {
        @Override
        public boolean apply(HttpServletRequest request, Route route, RateLimitUtils rateLimitUtils, String matcher) {
            if (matcher.contains("/")) {
                //判断matcher是否在网段内
                SubnetUtils subnetUtils = new SubnetUtils(matcher);
                return subnetUtils.getInfo().isInRange(rateLimitUtils.getRemoteAddress(request));
            }
            //判断matcher是否等于 RateLimitUtils.getRemoteAddress(request)
            return matcher.equals(rateLimitUtils.getRemoteAddress(request));
        }

        @Override
        public String key(HttpServletRequest request, Route route, RateLimitUtils rateLimitUtils, String matcher) {
            return rateLimitUtils.getRemoteAddress(request);
        }
    },

    /**
     * 针对请求的用户认证进行限流
     * <p>
     * Rate limit policy considering the authenticated user.
     */
    USER {
        @Override
        public boolean apply(HttpServletRequest request, Route route, RateLimitUtils rateLimitUtils, String matcher) {
            return matcher.equals(rateLimitUtils.getUser(request));
        }

        @Override
        public String key(HttpServletRequest request, Route route, RateLimitUtils rateLimitUtils, String matcher) {
            return rateLimitUtils.getUser(request);
        }
    },

    /**
     * 使用请求的路径限流
     *
     * Rate limit policy considering the request path to the downstream service.
     */
    URL {
        @Override
        public boolean apply(HttpServletRequest request, Route route, RateLimitUtils rateLimitUtils, String matcher) {
            return route == null || route.getPath().startsWith(matcher);
        }

        @Override
        public String key(HttpServletRequest request, Route route, RateLimitUtils rateLimitUtils, String matcher) {
            return Optional.ofNullable(route).map(Route::getPath).orElse(StringUtils.EMPTY);
        }
    },

    /**
     * Rate limit policy considering the authenticated user's role.
     */
    ROLE {
        @Override
        public boolean apply(HttpServletRequest request, Route route, RateLimitUtils rateLimitUtils, String matcher) {
            return rateLimitUtils.getUserRoles().contains(matcher.toUpperCase());
        }

        @Override
        public String key(HttpServletRequest request, Route route, RateLimitUtils rateLimitUtils, String matcher) {
            return matcher;
        }

        @Override
        public boolean isValid(String matcher) {
            return StringUtils.isNotEmpty(matcher);
        }
    },

    /**
     * @deprecated See {@link #HTTP_METHOD}
     */
    @Deprecated
    HTTPMETHOD {
        @Override
        public boolean apply(HttpServletRequest request, Route route, RateLimitUtils rateLimitUtils, String matcher) {
            return HTTP_METHOD.apply(request, route, rateLimitUtils, matcher);
        }

        @Override
        public String key(HttpServletRequest request, Route route, RateLimitUtils rateLimitUtils, String matcher) {
            return HTTP_METHOD.key(request, route, rateLimitUtils, matcher);
        }
    },

    /**
     * Rate limit policy considering the HTTP request method.
     */
    HTTP_METHOD {
        @Override
        public boolean apply(HttpServletRequest request, Route route, RateLimitUtils rateLimitUtils, String matcher) {
            return request.getMethod().equalsIgnoreCase(matcher);
        }

        @Override
        public String key(HttpServletRequest request, Route route, RateLimitUtils rateLimitUtils, String matcher) {
            return StringUtils.isEmpty(matcher) ? request.getMethod() : "http-method";
        }
    },

    /**
     * Rate limit policy considering an URL Pattern
     */
    URL_PATTERN {
        @Override
        public boolean apply(HttpServletRequest request, Route route, RateLimitUtils rateLimitUtils, String matcher) {
            return new AntPathMatcher().match(matcher.toLowerCase(), request.getRequestURI().toLowerCase());
        }

        @Override
        public String key(HttpServletRequest request, Route route, RateLimitUtils rateLimitUtils, String matcher) {
            return matcher;
        }

        @Override
        public boolean isValid(String matcher) {
            return StringUtils.isNotEmpty(matcher);
        }
    };

    public abstract boolean apply(HttpServletRequest request, Route route,
                                  RateLimitUtils rateLimitUtils, String matcher);

    public abstract String key(HttpServletRequest request, Route route,
                               RateLimitUtils rateLimitUtils, String matcher);

    /**
     * Helper method to validate specific cases per type.
     *
     * @param matcher The type matcher
     * @return The default behavior will always return true.
     */
    public boolean isValid(String matcher) {
        return true;
    }
}
