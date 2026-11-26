package com.campusflow.modules.user.repository;

import com.campusflow.modules.user.entity.Role;
import com.campusflow.modules.user.enums.RoleCode;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface RoleRepository extends Repository<Role, Long> {

    Optional<Role> findByCode(RoleCode code);
}
