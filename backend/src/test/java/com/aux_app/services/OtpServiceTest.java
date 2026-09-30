package com.aux_app.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.aux_app.entity.OtpCodeEntity;
import com.aux_app.error.AuxException;
import com.aux_app.repository.OtpCodeRepository;

class OtpServiceTest {

    private final OtpCodeRepository repo = mock(OtpCodeRepository.class);
    private final EmailService email = mock(EmailService.class);
    private final OtpService otps = new OtpService(repo, email);

    // Sends a code and wires the repo to return what was saved; returns the raw code from the email
    private String sendAndStore(String pendingEmail) {
        when(repo.findById("u1")).thenReturn(Optional.empty());
        otps.send("u1", "a@b.com", pendingEmail);
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(email).sendOtpCode(eq("a@b.com"), code.capture());
        ArgumentCaptor<OtpCodeEntity> saved = ArgumentCaptor.forClass(OtpCodeEntity.class);
        verify(repo).save(saved.capture());
        when(repo.findById("u1")).thenReturn(Optional.of(saved.getValue()));
        return code.getValue();
    }

    @Test
    void rightCodeReturnsPendingEmailAndBurns() {
        String code = sendAndStore("new@b.com");
        assertEquals("new@b.com", otps.consume("u1", code));
        verify(repo).delete(any());
    }

    @Test
    void locksAfterMaxWrongAttempts() {
        String code = sendAndStore(null);
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < OtpService.MAX_ATTEMPTS; i++) {
            assertThrows(AuxException.class, () -> otps.consume("u1", wrong));
        }
        // Right code no longer works once attempts are used up
        assertThrows(AuxException.class, () -> otps.consume("u1", code));
    }

    @Test
    void takenNoticeStoresCodeButNeverEmailsIt() {
        when(repo.findById("u1")).thenReturn(Optional.empty());
        otps.sendTakenNotice("u1", "taken@b.com");
        verify(email).sendEmailTakenNotice("taken@b.com");
        verify(email, never()).sendOtpCode(any(), any());
        // Stored, so a quick retry hits the same cooldown a real send would
        verify(repo).save(any());
    }

    @Test
    void resendBlockedDuringCooldown() {
        sendAndStore(null);
        assertThrows(AuxException.class, () -> otps.send("u1", "a@b.com", null));
    }
}
