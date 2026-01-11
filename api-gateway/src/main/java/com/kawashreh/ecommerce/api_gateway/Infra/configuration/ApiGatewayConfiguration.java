package com.kawashreh.ecommerce.api_gateway.Infra.configuration;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApiGatewayConfiguration {

    @Bean
    public RouteLocator gatewayRouter(RouteLocatorBuilder builder) {
        return builder.routes()
                .route(
                p -> p.path("/get")
                        .filters(f -> f.addRequestHeader("MyHeader", "MyURI")
                                .addRequestParameter("Param", "Value"))
                        .uri("http://httpbin.org:80"))
                .route(p -> p.path("/api/v1/user/**")
                        .uri("lb://user-service"))
                .route(p -> p.path("/api/v1/product/**")
                        .uri("lb://product-service"))

                .build();
    }
}
