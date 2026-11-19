package com.kitchensink.auth.service.impl;

import com.kitchensink.auth.exception.AccountPendingException;
import com.kitchensink.auth.exception.InvalidAdminCodeException;
import com.kitchensink.auth.exception.InvalidCredentialsException;
import com.kitchensink.auth.exception.InvalidPasswordException;
import com.kitchensink.auth.service.AuthService;
import com.kitchensink.auth.dto.ChangePasswordCommand;
import com.kitchensink.auth.dto.LoginCommand;
import com.kitchensink.auth.dto.LoginResult;
import com.kitchensink.auth.dto.RegisterCommand;
import com.kitchensink.common.config.AppConfig;
import com.kitchensink.common.config.AppProperties;
import com.kitchensink.member.exception.MemberNotFoundException;
import com.kitchensink.member.model.Member;
import com.kitchensink.member.model.Role;
import com.kitchensink.member.repository.MemberRepository;
import com.kitchensink.member.service.MemberCreator;
import com.kitchensink.member.validation.MemberRules;
import com.kitchensink.security.JwtTokenService;
import com.kitchensink.security.throttle.AuthThrottle;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /**
     * Checked when the email is unknown, so a miss takes as long as a wrong password (no account enumeration by timing).
     * It must be a BCrypt hash at {@link AppConfig#BCRYPT_STRENGTH}: a cheaper hash would make the miss measurably faster.
     */
    private static final String DUMMY_HASH = "$2a$12$.xmW4uGA99Zcq6Zcq9DufOilaFbEgkGeCHRr811TTNiBCNHWIhPFu";

    private final MemberCreator memberCreator;
    private final MemberRepository memberRepository;
    private final JwtTokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties appProperties;
    private final Clock clock;
    private final AuthThrottle throttle;

    @Override
    public Member register(RegisterCommand command) {
        Role role = resolveRole(command.adminCode(), command.clientIp());

        boolean pendingApproval = role != Role.ADMIN;          // a valid admin code vouches for the registrant, nobody else is
        Member member = Member.register(
                command.details(),
                role,
                passwordEncoder.encode(command.password()),
                pendingApproval,
                clock.instant());

        return memberCreator.create(member);
    }

    @Override
    public LoginResult login(LoginCommand command) {
        String email = MemberRules.normalizeEmail(command.email());
        throttle.beforeLogin(email, command.clientIp());              // locked out: no database read, no BCrypt
        Optional<Member> found = memberRepository.findByEmail(email);
        String hash = found.map(Member::getPasswordHash).orElse(DUMMY_HASH);
        boolean passwordMatches = passwordEncoder.matches(command.password(), hash);
        if (found.isEmpty() || !passwordMatches) {
            throttle.loginFailed(email, command.clientIp());          // counted for unknown emails too: no enumeration
            throw new InvalidCredentialsException();
        }
        Member member = found.get();
        throttle.loginSucceeded(email);
        if (member.isPendingApproval()) {                          // right password, but not approved: no token
            throw new AccountPendingException();
        }
        JwtTokenService.IssuedToken token = tokenService.issue(member);
        return new LoginResult(member, token.value(), token.expiresInSeconds());
    }

    @Override
    public void logout(String memberId) {
        Member member = findOrThrow(memberId);
        member.revokeSessions(clock.instant());
        memberRepository.save(member);
    }

    @Override
    public Member me(String memberId) {
        return findOrThrow(memberId);
    }

    @Override
    public void changePassword(String memberId, ChangePasswordCommand command) {
        throttle.beforePasswordChange(memberId);                      // a stolen token must not brute-force the password
        Member member = findOrThrow(memberId);
        if (!passwordEncoder.matches(command.currentPassword(), member.getPasswordHash())) {
            throttle.passwordChangeFailed(memberId);
            throw new InvalidPasswordException("currentPassword", "Current password is incorrect");
        }
        throttle.passwordChangeSucceeded(memberId);
        if (passwordEncoder.matches(command.newPassword(), member.getPasswordHash())) {
            throw new InvalidPasswordException("newPassword", "New password must be different from the current one");
        }
        member.changePassword(passwordEncoder.encode(command.newPassword()), clock.instant());
        memberRepository.save(member);
    }

    private Member findOrThrow(String memberId) {
        return memberRepository.findById(memberId).orElseThrow(() -> new MemberNotFoundException(memberId));
    }

    /** Missing, null, empty or blank code means VIEWER. A code must match the configured one, otherwise 400. */
    private Role resolveRole(String adminCode, String clientIp) {
        if (adminCode == null || adminCode.isBlank()) {
            return Role.VIEWER;
        }
        throttle.beforeAdminCode(clientIp);                           // the code is a shared secret: guessing is throttled
        String expected = appProperties.adminCode();
        if (expected == null || expected.isBlank() || !constantTimeEquals(expected, adminCode.trim())) {
            throttle.adminCodeFailed(clientIp);
            throw new InvalidAdminCodeException();
        }
        return Role.ADMIN;
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
