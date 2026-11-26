package com.campusflow.modules.user.entity;

import com.campusflow.modules.user.enums.RoleCode;
import com.campusflow.modules.user.enums.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@DynamicUpdate
@Table(name = "users")
public class UserAccount {

    private static final Pattern USERNAME = Pattern.compile("[a-z0-9_]{3,32}");
    private static final Pattern BCRYPT_HASH =
            Pattern.compile("\\$2[aby]\\$(?:0[4-9]|[12]\\d|3[01])\\$[./A-Za-z0-9]{53}");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32, updatable = false)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 60, updatable = false)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private UserStatus status;

    @Column(name = "token_version", nullable = false)
    private long tokenVersion;

    @CreationTimestamp
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "datetime(6)")
    private Instant createdAt;

    @UpdateTimestamp
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "updated_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant updatedAt;

    @ManyToMany
    @BatchSize(size = 32)
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id", nullable = false),
            inverseJoinColumns = @JoinColumn(name = "role_id", nullable = false))
    private Set<Role> roles = new HashSet<>();

    protected UserAccount() {
    }

    private UserAccount(String username, String passwordHash, String displayName, Role studentRole) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = Objects.requireNonNull(displayName);
        this.status = UserStatus.ACTIVE;
        this.roles.add(studentRole);
    }

    public static UserAccount registerStudent(String username, String passwordHash, String displayName, Role role) {
        if (username == null || !USERNAME.matcher(username).matches()) {
            throw new IllegalArgumentException("用户名格式无效");
        }
        if (passwordHash == null || !BCRYPT_HASH.matcher(passwordHash).matches()) {
            throw new IllegalArgumentException("需要 BCrypt 密码哈希");
        }
        if (role == null || role.getCode() != RoleCode.STUDENT) {
            throw new IllegalArgumentException("新注册账号只能关联学生角色");
        }
        return new UserAccount(username, passwordHash, displayName, role);
    }

    public void rename(String displayName) {
        this.displayName = Objects.requireNonNull(displayName);
    }

    public void changeStatus(UserStatus status) {
        if (status == UserStatus.DISABLED && this.status != UserStatus.DISABLED) {
            tokenVersion = Math.incrementExact(tokenVersion);
        }
        this.status = Objects.requireNonNull(status);
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public UserStatus getStatus() {
        return status;
    }

    public long getTokenVersion() {
        return tokenVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Set<Role> getRoles() {
        return Set.copyOf(roles);
    }
}
