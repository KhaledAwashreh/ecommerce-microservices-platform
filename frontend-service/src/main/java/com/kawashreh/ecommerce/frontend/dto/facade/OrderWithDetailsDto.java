package com.kawashreh.ecommerce.frontend.dto.facade;

import com.kawashreh.ecommerce.frontend.dto.OrderDto;
import com.kawashreh.ecommerce.frontend.dto.PaymentResponseDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderWithDetailsDto {
    private OrderDto order;
    private PaymentResponseDto payment;
}