package com.kawashreh.ecommerce.user_service.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kawashreh.ecommerce.user_service.domain.service.UserService;
import com.kawashreh.ecommerce.user_service.infrastructure.security.JwtAuthFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression test for GH #44: a missing X-User-ID header on PUT /{userId} raises
 * MissingRequestHeaderException, which GlobalExceptionHandler's catch-all
 * @ExceptionHandler(Exception.class) was intercepting before Spring's own default
 * mapping (400) ever applied - turning a client error into a 500.
 *
 * <p>Also covers GH #40: neither /register nor PUT /{userId} had {@code @Valid},
 * so null/blank/malformed fields (e.g. a missing username, a malformed email) reached
 * UserService and the database unchecked.
 */
@WebMvcTest(controllers = UserController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class))
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @Test
    void update_shouldReturnBadRequest_whenXUserIdHeaderMissing() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/user/{userId}", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    private Map<String, Object> validRegisterRequest() {
        Map<String, Object> request = new HashMap<>();
        request.put("name", "Test User");
        request.put("username", "testuser");
        request.put("email", "test@example.com");
        request.put("birthdate", "2000-01-01T00:00:00.000+00:00");
        request.put("phone", "555-0100");
        request.put("rawPassword", "Password123!");
        return request;
    }

    @Test
    void register_shouldRejectMissingUsername_withoutCallingService() throws Exception {
        Map<String, Object> request = validRegisterRequest();
        request.remove("username");

        mockMvc.perform(post("/api/v1/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    @Test
    void register_shouldRejectMalformedEmail_withoutCallingService() throws Exception {
        Map<String, Object> request = validRegisterRequest();
        request.put("email", "not-an-email");

        mockMvc.perform(post("/api/v1/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    @Test
    void update_shouldRejectMalformedEmail_withoutCallingService() throws Exception {
        UUID userId = UUID.randomUUID();
        Map<String, Object> request = new HashMap<>();
        request.put("email", "not-an-email");

        mockMvc.perform(put("/api/v1/user/{userId}", userId)
                        .header("X-User-ID", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }
}
