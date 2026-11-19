package com.kitchensink;

import com.kitchensink.auth.dto.RegisterRequest;
import com.kitchensink.auth.dto.RegisterCommand;
import com.kitchensink.common.config.AppProperties;
import com.kitchensink.common.exception.GlobalExceptionHandler;
import com.kitchensink.member.dto.CreateMemberRequest;
import com.kitchensink.member.model.Member;
import com.kitchensink.member.model.MemberDetails;
import com.kitchensink.member.model.Role;
import com.kitchensink.member.service.CreateMemberCommand;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/** Shared fixtures. A valid Indian mobile number from the libphonenumber examples is used throughout. */
public final class TestData {

    public static final Instant NOW = Instant.parse("2026-11-17T08:00:00Z");
    public static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    public static final String PHONE = "8123456789";
    public static final String IP = "10.0.0.1";
    public static final String SECRET = "test-secret-test-secret-test-secret-1234";

    public static AppProperties props(String adminCode) {
        return new AppProperties(adminCode, new AppProperties.JwtSettings(SECRET, Duration.ofMinutes(30)),
                List.of("http://localhost:5173"));
    }

    private TestData() {}

    public static MemberDetails details(String email) {
        return new MemberDetails("Asha Rao", email, "+91", PHONE);
    }

    public static Member member(String id, Role role, String email) {
        Member m = Member.register(details(email), role, "hash", false, NOW);
        ReflectionTestUtils.setField(m, "id", id);
        return m;
    }

    public static CreateMemberRequest createRequest(Role role) {
        CreateMemberRequest r = new CreateMemberRequest();
        r.setName("Asha Rao");
        r.setEmail("asha@example.com");
        r.setCountryCode("+91");
        r.setPhone(PHONE);
        r.setRole(role);
        return r;
    }

    public static CreateMemberCommand createCommand(Role role) {
        return new CreateMemberCommand(details("asha@example.com"), role);
    }

    public static RegisterCommand registerCommand(String adminCode) {
        return new RegisterCommand(details("asha@example.com"), "Secret@123", adminCode, IP);
    }

    public static RegisterRequest registerRequest(String adminCode) {
        RegisterRequest r = new RegisterRequest();
        r.setName("Asha Rao");
        r.setEmail("asha@example.com");
        r.setCountryCode("+91");
        r.setPhone(PHONE);
        r.setPassword("Secret@123");
        r.setConfirmPassword("Secret@123");
        r.setAdminCode(adminCode);
        return r;
    }

    /** MockMvc around one controller with the real exception handler and Bean Validation. */
    public static MockMvc mockMvc(Object controller) {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }
}
