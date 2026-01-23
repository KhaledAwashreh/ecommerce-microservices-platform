package com.kawashreh.ecommerce.api_gateway.Infrastructure.http.client;

import com.kawashreh.ecommerce.api_gateway.Infrastructure.http.dto.UserDto;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class ReactiveUserServiceClient {

    private final WebClient webClient;

    public ReactiveUserServiceClient(@LoadBalanced WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl("http://user-service").build();
    }

    public Mono<UserDto> retrieveByUsername(String username, String token) {
        return webClient.get()
                .uri("/api/v1/user?username={username}", username)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(UserDto.class);
    }

    public Mono<UserDto> retrieveByUsername(String username) {
        return webClient.get()
                .uri("/api/v1/user?username={username}", username)
                .retrieve()
                .bodyToMono(UserDto.class);
    }

    public Mono<UserDto> retrieveUserById(String userId) {
        return webClient.get()
                .uri("/api/v1/user/{userId}", userId)
                .retrieve()
                .bodyToMono(UserDto.class);
    }
}
