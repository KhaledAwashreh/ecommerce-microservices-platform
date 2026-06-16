package com.kawashreh.ecommerce.user_service.domain.service.impl;

import com.kawashreh.ecommerce.common.exceptions.NoSuchElementException;
import com.kawashreh.ecommerce.user_service.constants.CacheConstants;
import com.kawashreh.ecommerce.user_service.dataAccess.entity.AddressEntity;
import com.kawashreh.ecommerce.user_service.dataAccess.entity.UserEntity;
import com.kawashreh.ecommerce.user_service.dataAccess.mapper.AddressMapper;
import com.kawashreh.ecommerce.user_service.dataAccess.repository.AddressRepository;
import com.kawashreh.ecommerce.user_service.dataAccess.repository.UserRepository;
import com.kawashreh.ecommerce.user_service.domain.model.Address;
import com.kawashreh.ecommerce.user_service.domain.service.AddressService;
import com.kawashreh.ecommerce.user_service.domain.service.dto.AddressCreateRequest;
import com.kawashreh.ecommerce.user_service.domain.service.dto.AddressResponse;
import com.kawashreh.ecommerce.user_service.domain.service.dto.AddressSearchRequest;
import com.kawashreh.ecommerce.user_service.domain.service.dto.AddressUpdateRequest;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AddressServiceImpl implements AddressService {

    private final AddressRepository repository;
    private final UserRepository userRepository;

    public AddressServiceImpl(AddressRepository repository, UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    @Override
    public AddressResponse create(AddressCreateRequest request) {
        UserEntity userEntity = userRepository.findById(request.getUserId()).orElse(null);
        if (userEntity == null) {
            throw new NoSuchElementException("User not found");
        }

        Address address = Address.builder()
                .street(request.getStreet())
                .city(request.getCity())
                .state(request.getState())
                .postalCode(request.getPostalCode())
                .country(request.getCountry())
                .defaultAddress(request.isDefaultAddress())
                .phoneNumber(request.getPhoneNumber())
                .additionalInformation(request.getAdditionalInformation())
                .build();

        AddressEntity entity = AddressMapper.toEntity(address);
        entity.setUser(userEntity);
        repository.save(entity);

        return toResponse(entity);
    }

    @Override
    public List<AddressResponse> getAll() {
        return repository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Cacheable(value = CacheConstants.ADDRESS_BY_ID, key = "#id")
    @Override
    public AddressResponse find(UUID id) {
        return repository.findById(id)
                .map(this::toResponse)
                .orElse(null);
    }

    @CacheEvict(value = CacheConstants.ADDRESS_BY_ID, key = "#id")
    @Override
    public void delete(UUID id, UUID requestingUserId) {
        AddressEntity entity = repository.findById(id).orElse(null);
        if (entity == null) {
            return;
        }

        // TODO (investigate SpEL): Replace manual ownership check with
        //   @PreAuthorize SpEL once security context is available at service layer.
        if (!entity.getUser().getId().equals(requestingUserId)) {
            throw new NoSuchElementException("You can only delete your own addresses");
        }

        repository.deleteById(id);
    }

    @CacheEvict(value = CacheConstants.ADDRESS_BY_ID, key = "#id")
    @Override
    public AddressResponse update(UUID id, AddressUpdateRequest request) {
        // TODO (investigate SpEL): Replace manual ownership check with
        //   @PreAuthorize SpEL once security context is available at service layer.
        AddressEntity entity = repository.findById(id).orElse(null);
        if (entity == null) {
            return null;
        }

        if (!entity.getUser().getId().equals(request.getRequestingUserId())) {
            throw new NoSuchElementException("You can only edit your own addresses");
        }

        if (request.getStreet() != null) entity.setStreet(request.getStreet());
        if (request.getCity() != null) entity.setCity(request.getCity());
        if (request.getState() != null) entity.setState(request.getState());
        if (request.getPostalCode() != null) entity.setPostalCode(request.getPostalCode());
        if (request.getCountry() != null) entity.setCountry(request.getCountry());
        if (request.getPhoneNumber() != null) entity.setPhoneNumber(request.getPhoneNumber());
        if (request.getAdditionalInformation() != null) entity.setAdditionalInformation(request.getAdditionalInformation());
        entity.setDefaultAddress(request.isDefaultAddress());

        repository.save(entity);
        return toResponse(entity);
    }

    @Override
    public List<AddressResponse> search(AddressSearchRequest request) {
        List<AddressEntity> all = repository.findAll();

        if (request.getUserId() != null) {
            all = all.stream()
                    .filter(a -> a.getUser().getId().equals(request.getUserId()))
                    .collect(Collectors.toList());
        }

        if (request.getQuery() != null && !request.getQuery().isBlank()) {
            String q = request.getQuery().toLowerCase();
            all = all.stream()
                    .filter(a -> (a.getStreet() != null && a.getStreet().toLowerCase().contains(q))
                            || (a.getCity() != null && a.getCity().toLowerCase().contains(q))
                            || (a.getState() != null && a.getState().toLowerCase().contains(q))
                            || (a.getCountry() != null && a.getCountry().toLowerCase().contains(q))
                            || (a.getPostalCode() != null && a.getPostalCode().toLowerCase().contains(q)))
                    .collect(Collectors.toList());
        }

        return all.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private AddressResponse toResponse(AddressEntity entity) {
        if (entity == null) return null;

        return AddressResponse.builder()
                .id(entity.getId())
                .userId(entity.getUser() != null ? entity.getUser().getId() : null)
                .street(entity.getStreet())
                .city(entity.getCity())
                .state(entity.getState())
                .postalCode(entity.getPostalCode())
                .country(entity.getCountry())
                .defaultAddress(entity.isDefaultAddress())
                .phoneNumber(entity.getPhoneNumber())
                .additionalInformation(entity.getAdditionalInformation())
                .build();
    }
}
