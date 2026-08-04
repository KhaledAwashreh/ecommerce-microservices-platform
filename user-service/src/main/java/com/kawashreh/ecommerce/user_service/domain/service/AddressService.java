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

    // GH #64: previously getAll() took no argument and returned every address for every
    // user in the system, with no auth/ownership scoping at all. Now scoped to the
    // caller's own X-User-ID, same pattern as search()/update()/delete().
    List<AddressResponse> getAll(UUID requestingUserId);

    // Found during GH #64 review: findById() had no ownership scoping at all - any
    // authenticated user could read any other user's address (street/city/phone/etc.)
    // by supplying its UUID, exploitable live via the frontend's address-edit modal
    // (ProfileController#addressModal -> AddressServiceClient.getAddressById). Same
    // IDOR class as GH #59/#64, just not caught by either. Scoped like delete()/update():
    // throws ForbiddenException when the address exists but isn't the caller's.
    AddressResponse find(UUID id, UUID requestingUserId);

    void delete(UUID id, UUID requestingUserId);

    AddressResponse update(UUID id, AddressUpdateRequest request);

    List<AddressResponse> search(AddressSearchRequest request);
}
