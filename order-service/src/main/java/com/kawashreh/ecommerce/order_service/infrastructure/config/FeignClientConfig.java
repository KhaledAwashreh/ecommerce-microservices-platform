package com.kawashreh.ecommerce.order_service.infrastructure.config;

import com.kawashreh.ecommerce.order_service.infrastructure.http.client.ProductServiceErrorDecoder;
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
    public ErrorDecoder productServiceErrorDecoder() {
        return new ProductServiceErrorDecoder();
    }
}
