package com.aux_app.dto.users;

import io.swagger.v3.oas.annotations.media.Schema;

// Stored in users.role. Set by hand on the server for now (see V10__user_roles.sql)
@Schema(description = "What the account may use beyond the basics. USER is everyone; TESTER gets features still being tried out; ADMIN gets everything")
public enum UserRole {
    USER,
    TESTER,
    ADMIN;

    // Testers try out features before everyone gets them; admins can do anything a tester can
    public boolean isTester() {
        return this == TESTER || this == ADMIN;
    }
}
