package com.kawashreh.ecommerce.payment_service.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kawashreh.ecommerce.payment_service.domain.model.Payment;
import com.kawashreh.ecommerce.payment_service.domain.service.PaymentService;
import com.kawashreh.ecommerce.payment_service.infrastructure.security.JwtAuthFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression tests for GH #40: PaymentController had no {@code @Valid}, and
 * PaymentRequestDto carried no Bean Validation annotations at all, so a payment
 * request missing orderId/buyerId/paymentMethod reached PaymentService unchecked.
 */
// excludeFilters: web-layer slice test, unrelated to JwtAuthFilter (GH #17) - same
// rationale as the other WebMvcTest slices in this repo.
@WebMvcTest(controllers = PaymentController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class))
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PaymentService paymentService;

    private Map<String, Object> validRequest() {
        Map<String, Object> request = new HashMap<>();
        request.put("orderId", UUID.randomUUID().toString());
        request.put("buyerId", UUID.randomUUID().toString());
        request.put("amount", BigDecimal.TEN);
        request.put("paymentMethod", "CARD");
        return request;
    }

    @Test
    void processPayment_shouldRejectMissingOrderId_withoutCallingService() throws Exception {
        Map<String, Object> request = validRequest();
        request.remove("orderId");

        mockMvc.perform(post("/api/v1/payment/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(paymentService);
    }

    @Test
    void processPayment_shouldRejectMissingPaymentMethod_withoutCallingService() throws Exception {
        Map<String, Object> request = validRequest();
        request.put("paymentMethod", "");

        mockMvc.perform(post("/api/v1/payment/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(paymentService);
    }

    @Test
    void processPayment_shouldCallService_whenPayloadValid() throws Exception {
        Payment payment = Payment.builder()
                .id(UUID.randomUUID())
                .orderId(UUID.randomUUID())
                .buyerId(UUID.randomUUID())
                .amount(BigDecimal.TEN)
                .paymentMethod("CARD")
                .status(Payment.PaymentStatus.COMPLETED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        given(paymentService.processPayment(any(), any(), any())).willReturn(payment);

        mockMvc.perform(post("/api/v1/payment/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk());
    }
}
