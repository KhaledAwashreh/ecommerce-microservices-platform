package com.kawashreh.ecommerce.user_service.domain.service;

import com.kawashreh.ecommerce.user_service.domain.model.Address;

import java.util.List;
import java.util.UUID;

public interface AddressService {
    void save(Address address);

    List<Address> getAll();

    Address find(UUID id);

    void delete(UUID id);
}
