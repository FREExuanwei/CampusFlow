package com.campusflow.modules.user.service;

import com.campusflow.common.api.ErrorCode;
import com.campusflow.common.api.PageResponse;
import com.campusflow.common.config.DatabaseWebModule;
import com.campusflow.common.exception.BusinessException;
import com.campusflow.common.security.JwtTokens;
import com.campusflow.modules.operations.service.OperationsService;
import com.campusflow.modules.operations.service.OperationsService.Action;
import com.campusflow.modules.user.dto.UserProfile;
import com.campusflow.modules.user.dto.UserRequests;
import com.campusflow.modules.user.entity.UserAccount;
import com.campusflow.modules.user.enums.RoleCode;
import com.campusflow.modules.user.enums.UserStatus;
import com.campusflow.modules.user.repository.RoleRepository;
import com.campusflow.modules.user.repository.UserAccountRepository;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@DatabaseWebModule
public class UserService {

    private final UserAccountRepository users;
    private final RoleRepository roles;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final JwtTokens tokens;
    private final OperationsService operations;
    private final String dummyHash;
    private final boolean mysql;

    public UserService(UserAccountRepository users, RoleRepository roles, JdbcTemplate jdbc,
                       PasswordEncoder passwords, JwtTokens tokens, OperationsService operations,
                       Environment environment) {
        this.users = users;
        this.roles = roles;
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.tokens = tokens;
        this.operations = operations;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
        this.mysql = environment.matchesProfiles(DatabaseWebModule.MYSQL_PROFILE);
    }

    @Transactional
    public UserProfile register(UserRequests.Register request) {
        String username = canonicalUsername(request.username());
        if (users.existsByUsername(username)) {
            throw new BusinessException(ErrorCode.USERNAME_CONFLICT);
        }
        String hash = passwords.encode(request.password());
        String displayName = request.displayName().strip();
        try {
            if (mysql) {
                // The separately provisioned routine only assigns STUDENT in this transaction.
                jdbc.update("CALL campusflow_register_student(?, ?, ?)", username, hash, displayName);
                return users.findByUsername(username).map(UserProfile::from).orElseThrow();
            }
            var student = roles.findByCode(RoleCode.STUDENT)
                    .orElseThrow(() -> new IllegalStateException("Missing STUDENT role"));
            return UserProfile.from(users.saveAndFlush(UserAccount.registerStudent(username, hash, displayName, student)));
        } catch (DataIntegrityViolationException failure) {
            if (isDuplicateUsername(failure)) {
                throw new BusinessException(ErrorCode.USERNAME_CONFLICT);
            }
            throw failure;
        }
    }

    @Transactional(readOnly = true)
    public JwtTokens.AccessToken login(UserRequests.Login request) {
        var account = users.findLoginAccountByUsername(canonicalUsername(request.username()));
        String hash = account.map(UserAccountRepository.LoginAccount::getPasswordHash).orElse(dummyHash);
        boolean matches = passwords.matches(request.password(), hash);
        if (account.isEmpty() || !matches || account.get().getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_FAILED);
        }
        return tokens.issue(account.get().getId(), account.get().getTokenVersion());
    }

    @Transactional(readOnly = true)
    public Optional<UserProfile> authenticatedProfile(long id, Object tokenVersion) {
        return users.findWithRolesById(id)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .filter(user -> tokenVersion == null ? user.getTokenVersion() == 0
                        : Long.toString(user.getTokenVersion()).equals(tokenVersion))
                .map(UserProfile::from);
    }

    @Transactional(readOnly = true)
    public UserProfile profile(long id) {
        return UserProfile.from(requireUser(id));
    }

    @Transactional
    public UserProfile rename(long id, String displayName) {
        var account = requireUser(id);
        account.rename(displayName.strip());
        return UserProfile.from(account);
    }

    @Transactional
    public UserProfile updateStatus(long id, long actorId, UserStatus status) {
        var account = users.findLockedById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (account.getStatus() != status) {
            account.changeStatus(status);
            operations.record(Action.USER_STATUS_CHANGED, actorId, id);
        }
        return UserProfile.from(account);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserProfile> search(UserStatus status, String username, int page, int size) {
        return PageResponse.from(users.search(status, username == null ? null : canonicalUsername(username),
                PageRequest.of(page, size, Sort.by("id"))).map(UserProfile::from));
    }

    private UserAccount requireUser(long id) {
        return users.findWithRolesById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private static String canonicalUsername(String username) {
        return username.toLowerCase(Locale.ROOT);
    }

    private static boolean isDuplicateUsername(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getMessage() != null) {
                String message = sql.getMessage().toLowerCase(Locale.ROOT);
                if (("23505".equals(sql.getSQLState()) && message.contains("uk_users_username_index_"))
                        || (sql.getErrorCode() == 1062
                        && (message.endsWith("for key 'users.uk_users_username'")
                            || message.endsWith("for key 'uk_users_username'")))) {
                    return true;
                }
            }
        }
        return false;
    }
}
