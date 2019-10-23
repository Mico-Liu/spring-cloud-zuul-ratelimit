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

import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.properties.RateLimitProperties.Policy.MatchType;
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.properties.RateLimitType;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.convert.converter.Converter;

/**
 * 字符串转成MatchType类型。转换器
 *
 * @author Liel Chayoun
 */
public final class StringToMatchTypeConverter implements Converter<String, MatchType> {

    private static final String DELIMITER = "=";

    /**
     * 转换器
     *
     * @param type 类型值，不允许为空。 比如ORIGIN、USER、URL、ROLE、HTTP_METHOD、URL_PATTERN。可以小写
     * @return MatchType类型
     */
    @Override
    public MatchType convert(@NotNull String type) {
        //type含有等号
        if (type.contains(DELIMITER)) {
            String[] matchType = type.split(DELIMITER);
            //type转成大写，并且转成RateLimitType相应的枚举类型
            //matchType[1]为匹配规则，即匹配上的需要限流
            return new MatchType(RateLimitType.valueOf(matchType[0].toUpperCase()), matchType[1]);
        }
        //matcher为空，则所有都会有限流
        return new MatchType(RateLimitType.valueOf(type.toUpperCase()), null);
    }
}
