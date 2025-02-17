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

package com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.support;

import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.RateLimitUtils;
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.properties.RateLimitProperties;

import javax.servlet.http.HttpServletRequest;
import java.util.Set;

import static org.springframework.cloud.netflix.zuul.filters.support.FilterConstants.X_FORWARDED_FOR_HEADER;

/**
 * @author Liel Chayoun
 */
public class DefaultRateLimitUtils implements RateLimitUtils {

    private static final String ANONYMOUS_USER = "anonymous";
    private static final String X_FORWARDED_FOR_HEADER_DELIMITER = ",";

    private final RateLimitProperties properties;

    public DefaultRateLimitUtils(RateLimitProperties properties) {
        this.properties = properties;
    }

    @Override
    public String getUser(final HttpServletRequest request) {
        //获取存储起来的用户唯一识别码，为空，就使用默认值
        return request.getRemoteUser() != null ? request.getRemoteUser() : ANONYMOUS_USER;
    }

    /**
     * 获取远程IP地址，真实地址
     * @param request The {@link HttpServletRequest}
     * @return
     */
    @Override
    public String getRemoteAddress(final HttpServletRequest request) {
        String xForwardedFor = request.getHeader(X_FORWARDED_FOR_HEADER);
        if (properties.isBehindProxy() && xForwardedFor != null) {
            //多个ip地址，使用逗号隔开
            return xForwardedFor.split(X_FORWARDED_FOR_HEADER_DELIMITER)[0].trim();
        }
        return request.getRemoteAddr();
    }

    @Override
    public Set<String> getUserRoles() {
        throw new UnsupportedOperationException("Not supported");
    }
}
