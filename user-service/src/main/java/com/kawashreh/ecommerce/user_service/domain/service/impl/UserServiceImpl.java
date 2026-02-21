package com.kawashreh.ecommerce.user_service.domain.service.impl;

import com.kawashreh.ecommerce.user_service.constants.CacheConstants;
import com.kawashreh.ecommerce.user_service.dataAccess.mapper.AccountMapper;
import com.kawashreh.ecommerce.user_service.dataAccess.mapper.UserMapper;
import com.kawashreh.ecommerce.user_service.dataAccess.entity.AccountEntity;
import com.kawashreh.ecommerce.user_service.dataAccess.entity.UserEntity;
import com.kawashreh.ecommerce.user_service.dataAccess.repository.AccountRepository;
import com.kawashreh.ecommerce.user_service.dataAccess.repository.UserRepository;
import com.kawashreh.ecommerce.user_service.domain.model.Account;
import com.kawashreh.ecommerce.user_service.domain.model.User;
import com.kawashreh.ecommerce.user_service.domain.service.UserService;
import com.kawashreh.ecommerce.user_service.infrastructure.security.PasswordHasher;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository repository;
    private final AccountRepository accountRepository;
    private final PasswordHasher passwordHasher;

    public UserServiceImpl(UserRepository repository, AccountRepository accountRepository, PasswordHasher passwordHasher) {
        this.repository = repository;
        this.accountRepository = accountRepository;
        this.passwordHasher = passwordHasher;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User create(User user, String hashedPassword) {

        UserEntity ue = UserMapper.toEntity(user);

        AccountEntity ae = new AccountEntity();
        ae.setUser(ue);        // owning side
        ae.setHashedPassword(hashedPassword);
        ue.setAccount(ae);     // inverse side

        repository.save(ue);

        return UserMapper.toDomain(repository
                .findByUsername(user.getUsername())
                .orElse(null));
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
                        .collect(Collectors.toMap(
                                ae -> ae.getUser().getId(),   // entity → entity (safe)
                                ae -> {
                                    User user = UserMapper.toDomain(ae.getUser());
                                    Account account = AccountMapper.toDomain(ae);
                                    account.setUser(user);
                                    return account;
                                }
                        ));


        users.forEach(u ->
                u.setAccount(accounts.get(u.getId()))
        );

        return users;
    }


    @Cacheable(value = CacheConstants.USERS_BY_ID, key = "#id")
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

    @Cacheable(value = CacheConstants.USER_BY_USERNAME, key = "#username")
    @Override
    public User findByUsername(String username) {
        return repository.findByUsernameWithAccount(username)
                .map(UserMapper::toDomain)
                .orElse(null);
    }

    @Caching(evict = {
            @CacheEvict(value = CacheConstants.USERS_BY_ID, key = "#id"),
            @CacheEvict(value = CacheConstants.USERS_BY_EMAIL, key = "#username")
    })
    @Override
    public void delete(UUID id) {
        User user = find(id);
        if (user == null) {
            return;
        }

        String username = user.getUsername();

        repository.deleteById(id);
    }

    @Override
    public User Login(String username, String password) {
        User user = findByUsername(username);
        Account account = findAccountByUserId(user.getId());
        user.setAccount(account);

        if (user == null) {
            return null;
        }
        return user.checkPassword(password, passwordHasher) ? user : null;
    }

    @Override
    public User changePassword(String username, String oldPassword, String newPassword) throws Exception {
        User user = findByUsername(username);
        if (user == null) {
            return null;
        }

        if (!user.checkPassword(oldPassword, passwordHasher)) {
            return null;
        }

        user.changePassword(newPassword, passwordHasher);
        return user;
    }

    private Account findAccountByUserId(UUID id) {
        return accountRepository.findByUserId(id)
                .map(AccountMapper::toDomain)
                .orElse(null);
    }
}
