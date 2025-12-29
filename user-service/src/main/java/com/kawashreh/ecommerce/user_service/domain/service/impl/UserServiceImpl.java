package com.kawashreh.ecommerce.user_service.domain.service;

import com.kawashreh.ecommerce.user_service.dataAccess.UserRepository;
import com.kawashreh.ecommerce.user_service.domain.model.User;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository repository;

    public UserService(UserRepository repository) {
        this.repository = repository;
    }


    public void save(User user) {

        repository.save(user);
    }

    public List<User> getAll() {
        return repository.findAll();
    }

    public User find(UUID id) {
        return repository.findById(id)
                .orElse(null);
    }
    public User findByEmail(String email) {
        return repository.findByEmail(email)
                .orElse(null);
    }

    public User findByUsername(String username) {
        return repository.findByUsername(username)
                .orElse(null);
    }

}
