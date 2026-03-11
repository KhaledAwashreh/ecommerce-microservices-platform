package com.kawashreh.ecommerce.frontend.service;

import com.kawashreh.ecommerce.frontend.dto.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class ApiService {

    @Autowired
    private WebClient webClient;

    public Mono<String> login(UserLoginDto loginDto) {
        return webClient.post()
                .uri("/api/v1/user/login")
                .bodyValue(loginDto)
                .retrieve()
                .bodyToMono(String.class);
    }

    public Mono<UserDto> register(UserRegisterDto registerDto) {
        return webClient.post()
                .uri("/api/v1/user/register")
                .bodyValue(registerDto)
                .retrieve()
                .bodyToMono(UserDto.class);
    }

    public Mono<UserDto> getUserByUsername(String username, String token) {
        return webClient.get()
                .uri("/api/v1/user?username=" + username)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(UserDto.class);
    }

    public Mono<UserDto> getUserById(String userId, String token) {
        return webClient.get()
                .uri("/api/v1/user/" + userId)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(UserDto.class);
    }

    public Mono<List<ProductDto>> getAllProducts(String token) {
        return webClient.get()
                .uri("/api/v1/product")
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<List<ProductDto>>() {});
    }

    public Mono<ProductDto> getProductById(String productId, String token) {
        return webClient.get()
                .uri("/api/v1/product/" + productId)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(ProductDto.class);
    }

    public Mono<List<CategoryDto>> getAllCategories(String token) {
        return webClient.get()
                .uri("/api/v1/categories")
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<List<CategoryDto>>() {});
    }

    public Mono<InventoryDto> getInventoryByVariation(String variationId, String token) {
        return webClient.get()
                .uri("/api/v1/inventory/product-variation/" + variationId)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(InventoryDto.class);
    }

    public Mono<Boolean> checkInventoryAvailability(String variationId, int quantity, String token) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/inventory/product-variation/" + variationId + "/availability")
                        .queryParam("quantity", quantity)
                        .build())
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(Boolean.class);
    }

    public Mono<List<OrderDto>> getOrdersByBuyer(String buyerId, String token) {
        return webClient.get()
                .uri("/api/v1/orders/buyer/" + buyerId)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<List<OrderDto>>() {});
    }

    public Mono<OrderDto> createOrder(OrderDto order, String token) {
        return webClient.post()
                .uri("/api/v1/orders")
                .header("Authorization", "Bearer " + token)
                .bodyValue(order)
                .retrieve()
                .bodyToMono(OrderDto.class);
    }

    public Mono<PaymentResponseDto> processPayment(PaymentRequestDto request, String token) {
        return webClient.post()
                .uri("/api/v1/payment/process")
                .header("Authorization", "Bearer " + token)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(PaymentResponseDto.class);
    }

    public Mono<List<AddressDto>> getAddresses(String token) {
        return webClient.get()
                .uri("/api/v1/Address")
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<List<AddressDto>>() {});
    }

    public Mono<AddressDto> createAddress(AddressDto address, String token) {
        return webClient.post()
                .uri("/api/v1/Address")
                .header("Authorization", "Bearer " + token)
                .bodyValue(address)
                .retrieve()
                .bodyToMono(AddressDto.class);
    }

    public Mono<Void> deleteAddress(String addressId, String token) {
        return webClient.delete()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/Address")
                        .queryParam("id", addressId)
                        .build())
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(Void.class);
    }
}
