package com.kitchensink.security.throttle;

/**
 * The brute-force policy for the auth endpoints, in one place. Two limiters:
 * <ul>
 *   <li><b>account</b>: failed logins per submitted email (whether or not it exists, so the lockout reveals nothing),
 *       failed current-password checks per member, and wrong admin codes per client address (the code is a shared secret
 *       that guards the ADMIN role, so guessing it gets the same strict budget);</li>
 *   <li><b>ip</b>: failed logins per client address, higher limit because offices and proxies share addresses.</li>
 * </ul>
 */
public class AuthThrottle {

    private static final int MAX_KEY = 320;      // an email is at most 254 characters; never keep an attacker-sized key

    private final AttemptLimiter byAccount;
    private final AttemptLimiter byIp;

    public AuthThrottle(AttemptLimiter byAccount, AttemptLimiter byIp) {
        this.byAccount = byAccount;
        this.byIp = byIp;
    }

    public void beforeLogin(String normalizedEmail, String ip) {
        byAccount.checkAllowed(loginKey(normalizedEmail));
        byIp.checkAllowed(ipKey(ip));
    }

    public void loginFailed(String normalizedEmail, String ip) {
        byAccount.recordFailure(loginKey(normalizedEmail));
        byIp.recordFailure(ipKey(ip));
    }

    /** Clears the account budget only: one good login must not wipe the address's failures. */
    public void loginSucceeded(String normalizedEmail) {
        byAccount.recordSuccess(loginKey(normalizedEmail));
    }

    public void beforePasswordChange(String memberId) {
        byAccount.checkAllowed(passwordKey(memberId));
    }

    public void passwordChangeFailed(String memberId) {
        byAccount.recordFailure(passwordKey(memberId));
    }

    public void passwordChangeSucceeded(String memberId) {
        byAccount.recordSuccess(passwordKey(memberId));
    }

    public void beforeAdminCode(String ip) {
        byAccount.checkAllowed(adminCodeKey(ip));
    }

    public void adminCodeFailed(String ip) {
        byAccount.recordFailure(adminCodeKey(ip));
    }

    private static String loginKey(String email) {
        return "login:" + cap(email);
    }

    private static String passwordKey(String memberId) {
        return "password:" + cap(memberId);
    }

    private static String adminCodeKey(String ip) {
        return "admin-code:" + cap(ip);
    }

    private static String ipKey(String ip) {
        return "ip:" + cap(ip);
    }

    private static String cap(String value) {
        String safe = String.valueOf(value);
        return safe.substring(0, Math.min(safe.length(), MAX_KEY));
    }
}
