package ch.jacem.for_keycloak.email_otp_authenticator.helpers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.models.RealmModel;
import org.keycloak.models.SingleUseObjectProvider;
import org.keycloak.models.UserModel;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.sessions.RootAuthenticationSessionModel;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("IssuanceLimiter")
class IssuanceLimiterTest {

    @Mock
    private SingleUseObjectProvider store;

    @Mock
    private RealmModel realm;

    @Mock
    private UserModel user;

    // Keys currently held in the fake store; putIfAbsent succeeds only for unseen keys
    private final Set<String> claimed = new HashSet<>();

    @BeforeEach
    void setUp() {
        lenient().when(realm.getId()).thenReturn("realm-1");
        lenient().when(user.getId()).thenReturn("user-1");
        lenient().when(store.putIfAbsent(anyString(), anyLong())).thenAnswer(i -> claimed.add(i.getArgument(0)));
        lenient().when(store.remove(anyString())).thenAnswer(i -> {
            claimed.remove(i.<String>getArgument(0));
            return null;
        });
    }

    @Test
    @DisplayName("allows up to the limit, then refuses")
    void allowsUpToLimit() {
        for (int i = 0; i < 5; i++) {
            assertNotNull(IssuanceLimiter.tryAcquire(store, realm, user, 5, 900), "code " + (i + 1) + " should be allowed");
        }

        assertNull(IssuanceLimiter.tryAcquire(store, realm, user, 5, 900));
    }

    @Test
    @DisplayName("allows again once a slot has expired")
    void allowsAfterSlotExpires() {
        for (int i = 0; i < 3; i++) {
            IssuanceLimiter.tryAcquire(store, realm, user, 3, 900);
        }
        assertNull(IssuanceLimiter.tryAcquire(store, realm, user, 3, 900));

        // The store expires the oldest slot at the end of its window
        claimed.remove("email-otp-issuance:realm-1:user-1:0");

        assertNotNull(IssuanceLimiter.tryAcquire(store, realm, user, 3, 900));
        assertNull(IssuanceLimiter.tryAcquire(store, realm, user, 3, 900));
    }

    @Test
    @DisplayName("slots expire after the window, scoped to realm and user")
    void slotKeysAndLifespan() {
        assertEquals("email-otp-issuance:realm-1:user-1:0", IssuanceLimiter.tryAcquire(store, realm, user, 2, 600));
        // The second request finds slot 0 taken and claims slot 1
        assertEquals("email-otp-issuance:realm-1:user-1:1", IssuanceLimiter.tryAcquire(store, realm, user, 2, 600));

        verify(store, times(2)).putIfAbsent("email-otp-issuance:realm-1:user-1:0", 600L);
        verify(store).putIfAbsent("email-otp-issuance:realm-1:user-1:1", 600L);
    }

    @Test
    @DisplayName("a released slot can be claimed again")
    void releaseFreesSlot() {
        String slot = IssuanceLimiter.tryAcquire(store, realm, user, 1, 900);
        assertNull(IssuanceLimiter.tryAcquire(store, realm, user, 1, 900));

        IssuanceLimiter.release(store, slot);

        assertEquals(slot, IssuanceLimiter.tryAcquire(store, realm, user, 1, 900));
    }

    @Test
    @DisplayName("a failed release is logged, not thrown")
    void releaseSwallowsStoreErrors() {
        when(store.remove(anyString())).thenThrow(new RuntimeException("cache down"));

        assertDoesNotThrow(() -> IssuanceLimiter.release(store, "email-otp-issuance:realm-1:user-1:0"));
    }

    @Test
    @DisplayName("counts each user separately")
    void separateUsers() {
        UserModel otherUser = mock(UserModel.class);
        when(otherUser.getId()).thenReturn("user-2");

        assertNotNull(IssuanceLimiter.tryAcquire(store, realm, user, 1, 900));
        assertNull(IssuanceLimiter.tryAcquire(store, realm, user, 1, 900));

        assertNotNull(IssuanceLimiter.tryAcquire(store, realm, otherUser, 1, 900));
    }

    @Test
    @DisplayName("the limit is capped, bounding store calls per request")
    void limitIsCapped() {
        for (int i = 0; i < IssuanceLimiter.MAX_LIMIT; i++) {
            assertNotNull(IssuanceLimiter.tryAcquire(store, realm, user, 100_000, 900));
        }

        assertNull(IssuanceLimiter.tryAcquire(store, realm, user, 100_000, 900));
        verify(store, never()).putIfAbsent(eq("email-otp-issuance:realm-1:user-1:" + IssuanceLimiter.MAX_LIMIT), anyLong());
    }

    @Test
    @DisplayName("store errors fail closed")
    void storeErrorsFailClosed() {
        when(store.putIfAbsent(anyString(), anyLong())).thenThrow(new RuntimeException("cache down"));

        assertNull(IssuanceLimiter.tryAcquire(store, realm, user, 5, 900));
    }

    @Test
    @DisplayName("a limit or window of 0 or less is disabled")
    void isEnabled() {
        assertTrue(IssuanceLimiter.isEnabled(5, 900));
        assertFalse(IssuanceLimiter.isEnabled(0, 900));
        assertFalse(IssuanceLimiter.isEnabled(-1, 900));
        assertFalse(IssuanceLimiter.isEnabled(5, 0));
        assertFalse(IssuanceLimiter.isEnabled(5, -1));
    }

    @Test
    @DisplayName("acquiring with the limit disabled is a programming error")
    void acquireWhenDisabledThrows() {
        assertThrows(IllegalArgumentException.class, () -> IssuanceLimiter.tryAcquire(store, realm, user, 0, 900));
        verifyNoInteractions(store);
    }

    @Test
    @DisplayName("only the first resend of a code in an authentication session is claimed")
    void resendClaimedOncePerCode() {
        AuthenticationSessionModel authSession = mock(AuthenticationSessionModel.class);
        RootAuthenticationSessionModel rootSession = mock(RootAuthenticationSessionModel.class);
        when(authSession.getParentSession()).thenReturn(rootSession);
        when(rootSession.getId()).thenReturn("root-1");
        when(authSession.getTabId()).thenReturn("tab-1");

        assertTrue(IssuanceLimiter.tryClaimResend(store, authSession, "1000", 60));
        assertFalse(IssuanceLimiter.tryClaimResend(store, authSession, "1000", 60));
        // A newer code can be resent again
        assertTrue(IssuanceLimiter.tryClaimResend(store, authSession, "1060", 60));

        verify(store, times(2)).putIfAbsent("email-otp-resend:root-1:tab-1:1000", 60L);
    }

    @Test
    @DisplayName("a released resend claim can be claimed again")
    void releasedResendCanBeClaimedAgain() {
        AuthenticationSessionModel authSession = mock(AuthenticationSessionModel.class);
        RootAuthenticationSessionModel rootSession = mock(RootAuthenticationSessionModel.class);
        when(authSession.getParentSession()).thenReturn(rootSession);
        when(rootSession.getId()).thenReturn("root-1");
        when(authSession.getTabId()).thenReturn("tab-1");

        assertTrue(IssuanceLimiter.tryClaimResend(store, authSession, "1000", 60));
        IssuanceLimiter.releaseResend(store, authSession, "1000");

        assertTrue(IssuanceLimiter.tryClaimResend(store, authSession, "1000", 60));
        verify(store).remove("email-otp-resend:root-1:tab-1:1000");
    }
}
