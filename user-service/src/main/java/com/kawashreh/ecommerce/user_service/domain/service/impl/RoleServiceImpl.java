package com.kawashreh.ecommerce.user_service.domain.service.impl;

import com.kawashreh.ecommerce.user_service.dataAccess.mapper.RoleMapper;
import com.kawashreh.ecommerce.user_service.dataAccess.repository.RoleRepository;
import com.kawashreh.ecommerce.user_service.domain.model.Role;
import com.kawashreh.ecommerce.user_service.domain.service.RoleService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RoleServiceImpl implements RoleService {

    private final RoleRepository repository;

    public RoleServiceImpl(RoleRepository repository) {
        this.repository = repository;
    }

    @Override
    public Role save(Role role) {
        return RoleMapper.toDomain(repository.save(RoleMapper.toEntity(role)));
    }

    @Override
    public List<Role> getAll() {
        return repository.findAll().stream()
                .map(RoleMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Role find(UUID id) {
        return repository.findById(id)
                .map(RoleMapper::toDomain)
                .orElse(null);
    }

    @Override
    public Role findByName(String name) {
        return repository.findByName(name)
                .map(RoleMapper::toDomain)
                .orElse(null);
    }

    @Override
    public void delete(UUID id) {
        repository.deleteById(id);
    }
}
