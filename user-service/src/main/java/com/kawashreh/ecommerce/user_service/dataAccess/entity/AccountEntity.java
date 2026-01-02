package com.kawashreh.ecommerce.user_service.dataAccess.entity;

import com.kawashreh.ecommerce.user_service.domain.enums.AccountStatus;
import com.kawashreh.ecommerce.user_service.domain.enums.AccountType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class AccountEntity {

    @Id
    @GeneratedValue( strategy = GenerationType.UUID )
    private UUID id;

    @OneToOne
    @JoinColumn()
    private UserEntity user;

    @Column
    @Builder.Default
    private boolean archived = false;

    @Column
    @Builder.Default
    private boolean activated = false;

    @Column
    @CreationTimestamp
    private Instant createdAt;

    @Column
    @UpdateTimestamp
    private Instant updatedAt;

    @Column
    private AccountStatus accountStatus;

    @Column
    private AccountType accountType;

    @Column
    private boolean emailVerified = false;

    @Column
    private boolean phoneVerified = false;

    @Column
    @Builder.Default
    private Locale locale = Locale.ENGLISH;

    @Column
    @Builder.Default
    private TimeZone timeZone = TimeZone.getDefault();
}
