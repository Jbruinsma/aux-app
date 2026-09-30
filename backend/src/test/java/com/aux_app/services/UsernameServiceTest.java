package com.aux_app.services;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aux_app.entity.UserEntity;
import com.aux_app.repository.UserRepository;
import org.junit.jupiter.api.Test;

class UsernameServiceTest {

    private final UsernameService usernames = new UsernameService(mock(UserRepository.class));

    @Test
    void reservedUsernamesIgnoreCase() {
        assertTrue(usernames.isTaken("Admin"));
        assertTrue(usernames.isTaken("SETTINGS"));
        assertFalse(usernames.isTaken("admin2"));
    }

    @Test
    void caseOnlyChangeOfOwnNameAllowed() {
        UserRepository repo = mock(UserRepository.class);
        when(repo.existsByUsernameIgnoreCase("Moe")).thenReturn(true);
        UserEntity user = mock(UserEntity.class);
        when(user.getUsername()).thenReturn("moe");

        new UsernameService(repo).change(user, "Moe");

        verify(user).setUsername("Moe");
    }
}
