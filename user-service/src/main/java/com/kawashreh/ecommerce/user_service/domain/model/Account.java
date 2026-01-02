package com.kawashreh.ecommerce.user_service.domain.model;

import com.kawashreh.ecommerce.user_service.domain.enums.AccountStatus;
import com.kawashreh.ecommerce.user_service.domain.enums.AccountType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class Account {

    private UUID id;

    private User user;

    @Builder.Default
    private boolean archived = false;

    @Builder.Default
    private boolean activated = false;

    private Instant createdAt;

    private Instant updatedAt;

    private AccountStatus accountStatus;

    private AccountType accountType;

    private boolean emailVerified = false;

    private boolean phoneVerified = false;

    @Builder.Default
    private Locale locale = Locale.ENGLISH;

    @Builder.Default
    private TimeZone timeZone = TimeZone.getDefault();
}
