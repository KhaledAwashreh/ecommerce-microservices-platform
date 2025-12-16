package com.kawashreh.ecommerce.order_service.domain.model;


import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Data
@Builder
@Component
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class Order {

    @Id
    private UUID id;

}
