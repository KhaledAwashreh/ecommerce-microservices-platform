package com.kawashreh.ecommerce.api_gateway.Infrastructure.http.client;

import com.kawashreh.ecommerce.api_gateway.Infrastructure.http.dto.UserDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class ReactiveUserServiceClient {

    private final WebClient webClient;

    public ReactiveUserServiceClient(
            WebClient.Builder webClientBuilder,
            @Value("${USER_SERVICE_URL:http://user-service:8080}") String userServiceUrl) {
        this.webClient = webClientBuilder.baseUrl(userServiceUrl).build();
    }

    public Mono<UserDto> retrieveByUsername(String username) {
        return webClient.get()
                .uri("/api/v1/user?username={username}", username)
                .retrieve()
                .bodyToMono(UserDto.class);
    }

    public Mono<UserDto> retrieveById(String userId) {
        return webClient.get()
                .uri("/api/v1/user/{userId}", userId)
                .retrieve()
                .bodyToMono(UserDto.class);
    }
}