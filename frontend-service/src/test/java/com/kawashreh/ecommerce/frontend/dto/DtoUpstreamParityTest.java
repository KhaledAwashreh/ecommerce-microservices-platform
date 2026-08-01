package com.kawashreh.ecommerce.frontend.dto;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for GH #34: several frontend-service DTOs declared fields with no
 * counterpart on the corresponding upstream service's DTO, so those fields always
 * deserialized to null/false from a real gateway response - silent data loss the UI
 * rendered as blanks instead of failing.
 * <p>
 * Each expected field set below is a snapshot of the upstream service's actual DTO
 * (checked directly, since frontend-service has no compile-time dependency on the
 * other services' DTO classes to assert against). A frontend DTO field set that isn't
 * a subset of its upstream counterpart is exactly the class of drift GH #34 describes.
 */
class DtoUpstreamParityTest {

    private static Set<String> fieldNames(Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());
    }

    @Test
    void orderItemDto_hasNoFieldsBeyondOrderServiceCounterpart() {
        // order-service/.../application/dto/OrderItemDto.java
        Set<String> upstream = Set.of("id", "productSku", "quantity", "unitPrice",
                "createdAt", "updatedAt", "createdBy", "updatedBy");

        assertThat(fieldNames(OrderItemDto.class)).isSubsetOf(upstream);
    }

    @Test
    void orderDto_hasNoFieldsBeyondOrderServiceCounterpart() {
        // order-service/.../application/dto/OrderDto.java
        Set<String> upstream = Set.of("id", "storeId", "seller", "buyer", "shippingAddressId",
                "status", "selectedItems", "discountsApplied", "createdAt", "updatedAt",
                "createdBy", "updatedBy");

        assertThat(fieldNames(OrderDto.class)).isSubsetOf(upstream);
    }

    @Test
    void productDto_hasNoFieldsBeyondProductServiceCounterpart() {
        // product-service/.../application/dto/ProductDto.java
        Set<String> upstream = Set.of("id", "ownerId", "name", "description", "categories",
                "createdAt", "updatedAt", "thumbnailUrl");

        assertThat(fieldNames(ProductDto.class)).isSubsetOf(upstream);
    }

    @Test
    void paymentRequestDto_hasNoFieldsBeyondPaymentServiceCounterpart() {
        // payment-service/.../application/dto/PaymentRequestDto.java
        Set<String> upstream = Set.of("orderId", "buyerId", "amount", "paymentMethod");

        assertThat(fieldNames(PaymentRequestDto.class)).isSubsetOf(upstream);
    }

    @Test
    void paymentResponseDto_hasNoFieldsBeyondPaymentServiceCounterpart() {
        // payment-service/.../application/dto/PaymentResponseDto.java
        Set<String> upstream = Set.of("id", "orderId", "buyerId", "amount", "paymentMethod",
                "status", "transactionId", "paymentGateway", "createdAt", "updatedAt");

        assertThat(fieldNames(PaymentResponseDto.class)).isSubsetOf(upstream);
    }

    @Test
    void userDto_hasNoFieldsBeyondUserServiceCounterpart() {
        // user-service/.../application/dto/UserDto.java
        Set<String> upstream = Set.of("id", "name", "username", "email", "birthdate",
                "phone", "gender");

        assertThat(fieldNames(UserDto.class)).isSubsetOf(upstream);
    }
}
