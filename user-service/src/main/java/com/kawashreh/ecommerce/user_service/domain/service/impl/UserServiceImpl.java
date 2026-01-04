package com.kawashreh.ecommerce.user_service.domain.service.impl;

import com.kawashreh.ecommerce.user_service.dataAccess.repository.AccountRepository;
import com.kawashreh.ecommerce.user_service.dataAccess.repository.UserRepository;
import com.kawashreh.ecommerce.user_service.domain.model.Account;
import com.kawashreh.ecommerce.user_service.domain.model.User;
import com.kawashreh.ecommerce.user_service.domain.service.UserService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository repository;
    private final AccountRepository accountRepository;

    public UserServiceImpl(UserRepository repository, AccountRepository accountRepository) {
        this.repository = repository;
        this.accountRepository = accountRepository;
    }

    @Override
    public void create(User user) {

        Account account = new Account()
                .setUser(user);
        user.setAccount(account);

        repository.save(user);
        accountRepository.save(account);
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
