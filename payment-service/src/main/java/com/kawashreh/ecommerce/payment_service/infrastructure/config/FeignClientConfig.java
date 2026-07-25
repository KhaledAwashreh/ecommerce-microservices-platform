package com.kawashreh.ecommerce.payment_service.infrastructure.config;

import com.kawashreh.ecommerce.payment_service.infrastructure.http.client.OrderServiceErrorDecoder;
import feign.Logger;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignClientConfig {

    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    @Bean
    public ErrorDecoder orderServiceErrorDecoder() {
        return new OrderServiceErrorDecoder();
    }
}
