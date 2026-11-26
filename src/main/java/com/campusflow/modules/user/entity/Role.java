package com.campusflow.modules.user.entity;

import com.campusflow.modules.user.enums.RoleCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "roles")
public class Role {

    @Id
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "role_code", nullable = false, length = 32)
    private RoleCode code;

    @Column(name = "role_name", nullable = false, length = 64)
    private String name;

    protected Role() {
    }

    public RoleCode getCode() {
        return code;
    }

    public String getName() {
        return name;
    }
}
