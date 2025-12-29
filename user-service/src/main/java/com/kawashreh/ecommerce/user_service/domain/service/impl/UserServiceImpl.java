package com.kawashreh.ecommerce.user_service.domain.service.impl;

import com.kawashreh.ecommerce.user_service.dataAccess.UserRepository;
import com.kawashreh.ecommerce.user_service.domain.model.User;
import com.kawashreh.ecommerce.user_service.domain.service.UserService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository repository;

    public UserServiceImpl(UserRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(User user) {
        repository.save(user);
    }

    @Override
    public List<User> getAll() {
        return repository.findAll();
    }

    @Override
    public User find(UUID id) {
        return repository.findById(id)
                .orElse(null);
    }

    @Override
    public User findByEmail(String email) {
        return repository.findByEmail(email)
                .orElse(null);
    }

    @Override
    public User findByUsername(String username) {
        return repository.findByUsername(username)
                .orElse(null);
    }

    @Override
    public void delete(UUID id) {
         repository.deleteById(id);
    }
}
