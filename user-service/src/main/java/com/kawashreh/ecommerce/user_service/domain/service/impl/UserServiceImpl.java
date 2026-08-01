package com.kawashreh.ecommerce.user_service.domain.service.impl;

import com.kawashreh.ecommerce.common.exceptions.DuplicateEntityException;
import com.kawashreh.ecommerce.common.exceptions.ForbiddenException;
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
import com.kawashreh.ecommerce.user_service.domain.service.dto.UserCreateRequest;
import com.kawashreh.ecommerce.user_service.domain.service.dto.UserResponse;
import com.kawashreh.ecommerce.user_service.domain.service.dto.UserSearchRequest;
import com.kawashreh.ecommerce.user_service.domain.service.dto.UserUpdateRequest;
import com.kawashreh.ecommerce.user_service.infrastructure.security.JwtService;
import com.kawashreh.ecommerce.user_service.infrastructure.security.PasswordHasher;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository repository;
    private final AccountRepository accountRepository;
    private final PasswordHasher passwordHasher;
    private final JwtService jwtService;

    public UserServiceImpl(UserRepository repository, AccountRepository accountRepository,
                            PasswordHasher passwordHasher, JwtService jwtService) {
        this.repository = repository;
        this.accountRepository = accountRepository;
        this.passwordHasher = passwordHasher;
        this.jwtService = jwtService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserResponse create(UserCreateRequest request) {
        if (repository.existsByUsername(request.getUsername())) {
            throw new DuplicateEntityException("Username is taken");
        }

        if (repository.existsByEmail(request.getEmail())) {
            throw new DuplicateEntityException("User with this email already exists");
        }

        User user = User.builder()
                .name(request.getName())
                .username(request.getUsername())
                .email(request.getEmail())
                .birthdate(request.getBirthdate())
                .phone(request.getPhone())
                .gender(request.getGender())
                .build();

        UserEntity ue = UserMapper.toEntity(user);

        AccountEntity ae = new AccountEntity();
        ae.setUser(ue);
        ae.setHashedPassword(passwordHasher.encode(request.getRawPassword()));
        ue.setAccount(ae);

        repository.save(ue);

        return toResponse(UserMapper.toDomain(repository
                .findByUsername(user.getUsername())
                .orElse(null)));
    }

    @Override
    public List<UserResponse> getAll() {
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
                                ae -> ae.getUser().getId(),
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

        return users.stream()
                .map(this::toResponse)
                .toList();
    }

    @Cacheable(value = CacheConstants.USERS_BY_ID, key = "#id")
    @Override
    public UserResponse find(UUID id) {
        return repository.findById(id)
                .map(UserMapper::toDomain)
                .map(this::toResponse)
                .orElse(null);
    }

    @Override
    public UserResponse findByEmail(String email) {
        return repository.findByEmail(email)
                .map(UserMapper::toDomain)
                .map(this::toResponse)
                .orElse(null);
    }

    @Cacheable(value = CacheConstants.USER_BY_USERNAME, key = "#username")
    @Override
    public UserResponse findByUsername(String username) {
        return repository.findByUsernameWithAccount(username)
                .map(UserMapper::toDomain)
                .map(this::toResponse)
                .orElse(null);
    }

    @Caching(evict = {
            @CacheEvict(value = CacheConstants.USERS_BY_ID, key = "#id"),
            @CacheEvict(value = CacheConstants.USER_BY_USERNAME, allEntries = true)
    })
    @Override
    public void delete(UUID id, UUID requestingUserId) {
        UserResponse user = find(id);
        if (user == null) {
            return;
        }

        // TODO (investigate SpEL): Replace manual ownership check with
        //   @PreAuthorize SpEL once security context is available at service layer.
        if (!requestingUserId.equals(id)) {
            throw new ForbiddenException("You can only delete your own account");
        }

        repository.deleteById(id);
    }

    @Override
    public String login(String username, String password) {
        User user = repository.findByUsernameWithAccount(username)
                .map(UserMapper::toDomain)
                .orElse(null);

        if (user == null) {
            return null;
        }

        Account account = findAccountByUserId(user.getId());
        if (account == null) {
            return null;
        }

        user.setAccount(account);

        if (!user.checkPassword(password, passwordHasher)) {
            return null;
        }

        // GH #33: reject archived (banned/deleted) accounts even with a correct password.
        // Scoped to `archived` specifically rather than the full Account.canLogin()
        // (activated && !archived && emailVerified): nothing in this codebase ever sets
        // `activated`/`emailVerified` true (registration has no activation or email-
        // verification step), so enforcing the full predicate here would reject every
        // account, including ones just registered - a much larger behavior change than
        // this issue describes. Archived is the one flag with a real, working lifecycle
        // (see GH #32's AccountMapper fix), so it's the one enforced here.
        if (account.isArchived()) {
            return null;
        }

        String role = user.getRole() != null ? user.getRole().getName() : null;
        return jwtService.generateToken(user.getId(), user.getUsername(), role);
    }

    @Override
    public UserResponse update(UUID id, UserUpdateRequest request) {
        // TODO (investigate SpEL): Replace manual ownership check with
        //   @PreAuthorize SpEL once security context is available at service layer.
        if (!request.getRequestingUserId().equals(id)) {
            throw new ForbiddenException("You can only edit your own profile");
        }

        UserEntity entity = repository.findById(id).orElse(null);
        if (entity == null) {
            return null;
        }

        if (request.getName() != null) entity.setName(request.getName());
        if (request.getEmail() != null) entity.setEmail(request.getEmail());
        if (request.getPhone() != null) entity.setPhone(request.getPhone());
        if (request.getBirthdate() != null) entity.setBirthdate(request.getBirthdate());
        if (request.getGender() != null) entity.setGender(request.getGender());
        entity.setUpdatedAt(Instant.now());

        repository.save(entity);
        return find(id);
    }

    @Override
    public List<UserResponse> search(UserSearchRequest request) {
        if (request.getQuery() == null || request.getQuery().isBlank()) {
            return getAll();
        }

        String q = request.getQuery().toLowerCase();
        return getAll().stream()
                .filter(u -> (u.getName() != null && u.getName().toLowerCase().contains(q))
                        || (u.getUsername() != null && u.getUsername().toLowerCase().contains(q))
                        || (u.getEmail() != null && u.getEmail().toLowerCase().contains(q)))
                .toList();
    }

    @Override
    public UserResponse changePassword(String username, String oldPassword, String newPassword) {
        User user = repository.findByUsernameWithAccount(username)
                .map(UserMapper::toDomain)
                .orElse(null);

        if (user == null) {
            return null;
        }

        Account account = findAccountByUserId(user.getId());
        if (account == null) {
            return null;
        }

        user.setAccount(account);

        if (!user.checkPassword(oldPassword, passwordHasher)) {
            return null;
        }

        try {
            user.changePassword(newPassword, passwordHasher);
        } catch (Exception e) {
            return null;
        }

        UserEntity entity = repository.findByUsername(username).orElse(null);
        if (entity != null && entity.getAccount() != null) {
            entity.getAccount().setHashedPassword(account.getHashedPassword());
            repository.save(entity);
        }

        return find(user.getId());
    }

    private Account findAccountByUserId(UUID id) {
        return accountRepository.findByUserId(id)
                .map(AccountMapper::toDomain)
                .orElse(null);
    }

    private UserResponse toResponse(User user) {
        if (user == null) return null;

        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .username(user.getUsername())
                .email(user.getEmail())
                .birthdate(user.getBirthdate())
                .phone(user.getPhone())
                .gender(user.getGender())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
