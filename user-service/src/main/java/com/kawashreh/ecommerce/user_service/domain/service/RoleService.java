package com.kawashreh.ecommerce.user_service.domain.service;

import com.kawashreh.ecommerce.user_service.domain.model.Role;

import java.util.List;
import java.util.UUID;

public interface RoleService {
    Role save(Role role);
    List<Role> getAll();
    Role find(UUID id);
    Role findByName(String name);
    void delete(UUID id);
}
