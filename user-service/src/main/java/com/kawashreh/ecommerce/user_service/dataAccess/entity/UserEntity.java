package com.kawashreh.ecommerce.user_service.dataAccess.entity;

import com.kawashreh.ecommerce.user_service.domain.enums.Gender;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "\"user\"")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "username", unique = true, nullable = false)
    private String username;

    @Column(name = "email", unique = true, nullable = false)
    private String email;

    @Column(name = "birthdate", nullable = false)
    private Date birthdate;

    @Column(name = "phone", nullable = false)
    private String phone;

    @Column(name = "gender")
    private Gender gender;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private Instant updatedAt;

    @OneToOne(mappedBy = "user", fetch = FetchType.LAZY)
    private AccountEntity account;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<AddressEntity> addresses = new ArrayList<>();

}
