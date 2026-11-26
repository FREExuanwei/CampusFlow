package com.campusflow.common.security;

import java.security.Principal;

public record CampusFlowPrincipal(long id, String username) implements Principal {

    @Override
    public String getName() {
        return username;
    }
}
