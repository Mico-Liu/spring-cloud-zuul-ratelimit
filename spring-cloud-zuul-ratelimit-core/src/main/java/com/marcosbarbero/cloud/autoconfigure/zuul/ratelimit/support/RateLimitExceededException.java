package com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.support;

import com.netflix.zuul.exception.ZuulException;
import org.springframework.cloud.netflix.zuul.util.ZuulRuntimeException;
import org.springframework.http.HttpStatus;

/**
 * 达到限流条件时，抛出此异常
 *
 * @author Liel Chayoun
 */
public class RateLimitExceededException extends ZuulRuntimeException {

    public RateLimitExceededException() {
        super(new ZuulException(HttpStatus.TOO_MANY_REQUESTS.toString(), HttpStatus.TOO_MANY_REQUESTS.value(), null));
    }
}
