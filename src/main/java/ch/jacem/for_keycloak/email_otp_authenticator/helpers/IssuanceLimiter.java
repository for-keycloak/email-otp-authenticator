package ch.jacem.for_keycloak.email_otp_authenticator.helpers;

import org.jboss.logging.Logger;
import org.keycloak.models.RealmModel;
import org.keycloak.models.SingleUseObjectProvider;
import org.keycloak.models.UserModel;
import org.keycloak.sessions.AuthenticationSessionModel;

/**
 * Caps how many OTP emails a user can be sent within a sliding time window,
 * across all authentication sessions, and deduplicates concurrent resends.
 *
 * Each emailed code claims one of {@code limit} slots in Keycloak's
 * single-use object store. A slot is claimed with an atomic putIfAbsent and
 * expires on its own {@code windowSeconds} after it was claimed, so at most
 * {@code limit} claims can succeed within any window. The store is shared by
 * all cluster nodes and needs no schema, and putIfAbsent is atomic across the
 * cluster, so concurrent requests on different nodes cannot exceed the limit.
 *
 * Store errors fail closed: a slot that cannot be claimed counts as taken.
 */
public final class IssuanceLimiter {

    static final String ISSUANCE_KEY_PREFIX = "email-otp-issuance:";
    static final String RESEND_KEY_PREFIX = "email-otp-resend:";

    /**
     * Upper bound on the limit: checking it costs up to one store call per slot.
     */
    public static final int MAX_LIMIT = 100;

    private static final Logger logger = Logger.getLogger(IssuanceLimiter.class);

    private IssuanceLimiter() {
        // Utility class
    }

    /**
     * Whether the limit is enabled; a limit or window of 0 or less disables it.
     */
    public static boolean isEnabled(int limit, int windowSeconds) {
        return limit > 0 && windowSeconds > 0;
    }

    /**
     * Try to claim an issuance slot for the user.
     *
     * @param store         Keycloak's single-use object store
     * @param realm         The user's realm
     * @param user          The user the code would be emailed to
     * @param limit         Maximum codes within the window, capped at {@link #MAX_LIMIT}
     * @param windowSeconds Window length in seconds
     * @return the claimed slot, to pass to {@link #release} if the code is not sent,
     *         or null if the limit has been reached
     * @throws IllegalArgumentException if the limit is not enabled
     */
    public static String tryAcquire(SingleUseObjectProvider store, RealmModel realm, UserModel user, int limit, int windowSeconds) {
        if (!isEnabled(limit, windowSeconds)) {
            throw new IllegalArgumentException("Issuance limit is not enabled");
        }

        String keyPrefix = ISSUANCE_KEY_PREFIX + realm.getId() + ":" + user.getId() + ":";
        int slots = Math.min(limit, MAX_LIMIT);
        for (int slot = 0; slot < slots; slot++) {
            String key = keyPrefix + slot;
            if (putIfAbsent(store, key, windowSeconds)) {
                return key;
            }
        }

        return null;
    }

    /**
     * Give back a slot whose code was never sent.
     */
    public static void release(SingleUseObjectProvider store, String slot) {
        try {
            store.remove(slot);
        } catch (RuntimeException e) {
            // It still expires with its lifespan
            logger.warnf(e, "Could not release single-use object %s", slot);
        }
    }

    /**
     * Claim the right to resend the code created at {@code otpCreatedAt} in this
     * authentication session. Only the first of several concurrent resends of the
     * same code succeeds.
     *
     * @param lifespanSeconds How long the claim is kept; at least the cooldown
     * @return true if this request may resend the code
     */
    public static boolean tryClaimResend(SingleUseObjectProvider store, AuthenticationSessionModel authSession, String otpCreatedAt, int lifespanSeconds) {
        return putIfAbsent(store, resendKey(authSession, otpCreatedAt), Math.max(1, lifespanSeconds));
    }

    /**
     * Give back a resend claim whose resend sent no code, so the next resend is tried again.
     */
    public static void releaseResend(SingleUseObjectProvider store, AuthenticationSessionModel authSession, String otpCreatedAt) {
        release(store, resendKey(authSession, otpCreatedAt));
    }

    private static String resendKey(AuthenticationSessionModel authSession, String otpCreatedAt) {
        return RESEND_KEY_PREFIX + authSession.getParentSession().getId() + ":" + authSession.getTabId() + ":" + otpCreatedAt;
    }

    private static boolean putIfAbsent(SingleUseObjectProvider store, String key, long lifespanSeconds) {
        try {
            return store.putIfAbsent(key, lifespanSeconds);
        } catch (RuntimeException e) {
            // Fail closed, like Keycloak's remote store which reports errors as "already present"
            logger.warnf(e, "Could not claim single-use object %s, treating it as taken", key);
            return false;
        }
    }
}
