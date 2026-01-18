package com.kawashreh.ecommerce.user_service.application.dto;

import lombok.*;
import lombok.experimental.SuperBuilder;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class UserRegisterDto extends UserDto {


    @NonNull
    private String RawPassword;

}
