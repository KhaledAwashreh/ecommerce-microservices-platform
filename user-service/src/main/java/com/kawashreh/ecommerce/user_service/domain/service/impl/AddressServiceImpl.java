package com.kawashreh.ecommerce.user_service.domain.service.impl;


import com.kawashreh.ecommerce.user_service.dataAccess.repository.AddressRepository;
import com.kawashreh.ecommerce.user_service.domain.model.Address;
import com.kawashreh.ecommerce.user_service.domain.service.AddressService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class AddressServiceImpl implements AddressService {

    private final AddressRepository repository;

    public AddressServiceImpl(AddressRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(Address address) {
        repository.save(address);
    }

    @Override
    public List<Address> getAll() {
        return repository.findAll();
    }

    @Override
    public Address find(UUID id) {
        return repository.findById(id)
                .orElse(null);
    }

    @Override
    public void delete(UUID id) {
        repository.deleteById(id);
    }

}
