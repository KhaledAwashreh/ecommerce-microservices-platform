package com.kawashreh.ecommerce.user_service.domain.service.impl;

import com.kawashreh.ecommerce.user_service.dataAccess.mapper.AccountMapper;
import com.kawashreh.ecommerce.user_service.dataAccess.mapper.UserMapper;
import com.kawashreh.ecommerce.user_service.dataAccess.entity.AccountEntity;
import com.kawashreh.ecommerce.user_service.dataAccess.entity.UserEntity;
import com.kawashreh.ecommerce.user_service.dataAccess.repository.AccountRepository;
import com.kawashreh.ecommerce.user_service.dataAccess.repository.UserRepository;
import com.kawashreh.ecommerce.user_service.domain.model.Account;
import com.kawashreh.ecommerce.user_service.domain.model.User;
import com.kawashreh.ecommerce.user_service.domain.service.UserService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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

        UserEntity ue = UserMapper.toEntity(user);

        AccountEntity ae = new AccountEntity();
        ae.setUser(ue);        // owning side
        ue.setAccount(ae);     // inverse side

        repository.save(ue);
        accountRepository.save(ae);
    }

    @Override
    public List<User> getAll() {
        List<User> users = repository.findAll()
                .stream()
                .map(UserMapper::toDomain)
                .toList();

        List<UUID> userIds = users.stream()
                .map(User::getId)
                .toList();

        Map<UUID, Account> accounts =
                accountRepository.findByUserIdIn(userIds)
                        .stream()
                        .map(AccountMapper::toDomain)
                        .collect(Collectors.toMap(
                                a -> a.getUser().getId(), // key = userId
                                a -> a
                        ));

        users.forEach(u ->
                u.setAccount(accounts.get(u.getId()))
        );

        return users;
    }


    @Override
    public User find(UUID id) {
        return repository.findById(id)
                .map(UserMapper::toDomain)
                .orElse(null);
    }

    @Override
    public User findByEmail(String email) {
        return repository.findByEmail(email)
                .map(UserMapper::toDomain)
                .orElse(null);
    }

    @Override
    public User findByUsername(String username) {
        return repository.findByUsername(username)
                .map(UserMapper::toDomain)
                .orElse(null);
    }

    @Override
    public void delete(UUID id) {
        repository.deleteById(id);
    }
}
