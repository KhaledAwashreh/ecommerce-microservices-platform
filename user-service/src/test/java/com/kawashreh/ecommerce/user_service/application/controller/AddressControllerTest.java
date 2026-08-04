package com.kawashreh.ecommerce.user_service.application.controller;

import com.kawashreh.ecommerce.user_service.domain.service.AddressService;
import com.kawashreh.ecommerce.user_service.domain.service.dto.AddressSearchRequest;
import com.kawashreh.ecommerce.user_service.infrastructure.security.JwtAuthFilter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression test for GH issue #59: GET /search took an unauthenticated
 * {@code userId} query param and filtered by it directly, so any authenticated
 * caller could read any other user's addresses by passing their UUID. The
 * endpoint must scope to the caller's own identity (from X-User-ID, set by the
 * gateway after JWT validation), the same way edit/delete already do.
 *
 * Also covers GH issue #64: GET / (getAll()) had no scoping or auth check at
 * all and returned every address for every user in the system. Fixed the same
 * way GH #59 fixed /search - scope to the caller's own X-User-ID.
 */
@WebMvcTest(controllers = AddressController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class))
class AddressControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AddressService addressService;

    @Test
    void getAll_shouldScopeToCallingUserFromHeader_notReturnEveryUsersAddresses() throws Exception {
        UUID callingUser = UUID.randomUUID();

        given(addressService.getAll(callingUser)).willReturn(List.of());

        mockMvc.perform(get("/api/v1/address")
                        .header("X-User-ID", callingUser.toString()))
                .andExpect(status().isOk());

        verify(addressService).getAll(callingUser);
    }

    @Test
    void getAll_shouldNotSucceed_whenMissingUserIdHeader() throws Exception {
        // Mirrors search_shouldNotSucceed_whenMissingUserIdHeader: GH #44 (separate,
        // pre-existing bug) means the exact status code for a missing required header
        // isn't guaranteed to be 400 today. The security property under test is that a
        // request without the header cannot succeed and get every user's addresses back.
        mockMvc.perform(get("/api/v1/address"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(200));
    }

    @Test
    void search_shouldIgnoreUserIdParam_andScopeToCallingUserFromHeader() throws Exception {
        UUID callingUser = UUID.randomUUID();
        UUID victim = UUID.randomUUID();

        given(addressService.search(org.mockito.ArgumentMatchers.any()))
                .willReturn(List.of());

        mockMvc.perform(get("/api/v1/address/search")
                        .param("userId", victim.toString())
                        .header("X-User-ID", callingUser.toString()))
                .andExpect(status().isOk());

        ArgumentCaptor<AddressSearchRequest> captor = ArgumentCaptor.forClass(AddressSearchRequest.class);
        verify(addressService).search(captor.capture());

        assertThat(captor.getValue().getUserId())
                .as("search must be scoped to the authenticated caller, not the query param")
                .isEqualTo(callingUser);
    }

    @Test
    void search_shouldNotSucceed_whenMissingUserIdHeader() throws Exception {
        // GH #44 (separate, pre-existing bug): GlobalExceptionHandler's catch-all turns
        // MissingRequestHeaderException into a 500 instead of a 400 for every endpoint,
        // not just this one. Not fixed here - the security property under test is that
        // a request without the header cannot succeed and get addresses back, whatever
        // the exact status code is.
        mockMvc.perform(get("/api/v1/address/search").param("q", "main"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(200));
    }
}
