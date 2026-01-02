package com.kawashreh.ecommerce.user_service.domain.model;

import com.kawashreh.ecommerce.user_service.domain.enums.Gender;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "\"user\"")
public class User {

    private UUID id;

    private String name;

    private String username;

    private String email;

    private Date birthdate;

    private String phone;

    private Gender gender;

    private Instant createdAt;

    private Instant updatedAt;

    private Account account;
}
