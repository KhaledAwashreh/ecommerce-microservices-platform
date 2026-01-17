package com.kawashreh.ecommerce.user_service.application.dto;


import lombok.Data;
import lombok.NonNull;

@Data
public class UserLoginDto {

    @NonNull
    private String username;

    @NonNull
    private String password;
}
