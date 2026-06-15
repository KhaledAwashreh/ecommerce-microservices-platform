package com.kawashreh.ecommerce.user_service.domain.service;

import com.kawashreh.ecommerce.user_service.domain.service.dto.AddressCreateRequest;
import com.kawashreh.ecommerce.user_service.domain.service.dto.AddressResponse;
import com.kawashreh.ecommerce.user_service.domain.service.dto.AddressSearchRequest;
import com.kawashreh.ecommerce.user_service.domain.service.dto.AddressUpdateRequest;

import java.util.List;
import java.util.UUID;

// TODO (investigate SpEL): Add @PreAuthorize/@PostAuthorize SpEL expressions for
//   ownership checks (e.g., #id == authentication.principal.id) once security
//   context is wired into the service layer, replacing manual checks.
public interface AddressService {
    AddressResponse create(AddressCreateRequest request);

    List<AddressResponse> getAll();

    AddressResponse find(UUID id);

    void delete(UUID id, UUID requestingUserId);

    AddressResponse update(UUID id, AddressUpdateRequest request);

    List<AddressResponse> search(AddressSearchRequest request);
}
