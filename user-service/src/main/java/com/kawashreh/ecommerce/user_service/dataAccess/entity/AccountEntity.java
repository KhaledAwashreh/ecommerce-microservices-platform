package com.kawashreh.ecommerce.user_service.dataAccess.entity;

import com.kawashreh.ecommerce.user_service.domain.enums.AccountStatus;
import com.kawashreh.ecommerce.user_service.domain.enums.AccountType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@Table(name = "Account")
public class AccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ToString.Exclude
    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "archived", nullable = false)
    @Builder.Default
    private boolean archived = false;

    @Column(name = "activated", nullable = false)
    @Builder.Default
    private boolean activated = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private Instant updatedAt;

    @Builder.Default
    @Column(name = "account_status", nullable = false)
    private AccountStatus accountStatus = AccountStatus.ACTIVE;

    @Builder.Default
    @ToString.Exclude
    @Column(name = "account_type", nullable = false)
    private AccountType accountType = AccountType.REGULAR;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "phone_verified", nullable = false)
    private boolean phoneVerified = false;

    @Column(name = "locale", nullable = false)
    @Builder.Default
    private Locale locale = Locale.ENGLISH;

    @Column(name = "time_zone", nullable = false)
    @Builder.Default
    private TimeZone timeZone = TimeZone.getDefault();
}