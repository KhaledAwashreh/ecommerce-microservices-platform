package com.kawashreh.ecommerce.user_service.dataAccess.Mapper;

import com.kawashreh.ecommerce.user_service.dataAccess.entity.AddressEntity;
import com.kawashreh.ecommerce.user_service.domain.model.Address;

public final class AddressMapper extends DomainEntityMapper<AddressEntity, Address> {

    @Override
    public AddressEntity toEntity(Address a) {
        if(a == null) return null;
        return AddressEntity.builder()
                .DefaultAddress(a.isDefaultAddress())
                .id(a.getId())
                .state(a.getState())
                .city(a.getCity())
                .country(a.getCountry())
                .postalCode(a.getPostalCode())
                .additionalInformation(a.getAdditionalInformation())
                .phoneNumber(a.getPhoneNumber())
                .street(a.getStreet())
                .build();
    }

    @Override
    public Address toDomain(AddressEntity e) {
        if (e == null) return null;

        return new Address()
                .setId(e.getId())
                .setStreet(e.getStreet())
                .setCity(e.getCity())
                .setState(e.getState())
                .setPostalCode(e.getPostalCode())
                .setCountry(e.getCountry())
                .setPhoneNumber(e.getPhoneNumber())
                .setAdditionalInformation(e.getAdditionalInformation())
                .setDefaultAddress(e.isDefaultAddress());
    }

}
