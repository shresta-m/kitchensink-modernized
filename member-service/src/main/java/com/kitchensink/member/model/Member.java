package com.kitchensink.member.model;

import com.kitchensink.member.validation.MemberRules;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Member aggregate stored in the {@code members} collection. Fields are normalized on the way in and only
 * changed through the domain methods below, so every state change has one meaning and one place.
 *
 * <p>Index to query map (add an index only after explain() shows a need):
 * <ul>
 *   <li>{@code email} unique: duplicate guard, email prefix search, sort by email</li>
 *   <li>{@code nameLower}: name prefix search, default sort by name</li>
 *   <li>{@code phoneNumber}: phone prefix search</li>
 * </ul>
 * Every write touches exactly one document, so MongoDB's single-document atomicity is enough: {@code @Version}
 * rejects stale writes, the unique index rejects duplicate emails, and no multi-document transaction is needed.
 * {@code role}, {@code createdAt} and {@code mustChangePassword} are deliberately not indexed: role and status have
 * two values each, so an index barely narrows a search, and sorting by createdAt is cheap at this size. Add one
 * only after explain() on real data shows a slow query.
 * Status has no field of its own: it is derived from {@code pendingApproval} and {@code mustChangePassword}.
 * {@code pendingApproval} is stored inverted (false = approved) on purpose: members stored before approval existed have
 * no such field and must read as approved, not locked out.
 */
@Document("members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

    @Id
    private String id;

    private String name;

    /** Lowercased copy of name: lets prefix search and sorting use an index and ignore case. */
    @Indexed
    private String nameLower;

    @Indexed(unique = true)
    private String email;

    /** Dial code such as "+91". */
    private String countryCode;

    /** Digits only, without the country code. */
    @Indexed
    private String phoneNumber;

    /** BCrypt hash. Never expose in a DTO. */
    private String passwordHash;

    private Role role;

    /** True from self-registration until an administrator approves the member. A pending member cannot sign in. */
    private boolean pendingApproval;

    /** True while the member holds an admin-issued temporary password. Drives the Active / Temp password status. */
    private boolean mustChangePassword;

    private Instant createdAt;

    private Instant updatedAt;

    /** Who created / last changed the member. Filled by Spring Data auditing from the AuditorAware bean. */
    @CreatedBy
    private String createdBy;

    @LastModifiedBy
    private String updatedBy;

    @Version
    private Long version;

    /**
     * Session generation. Every access token carries the value it was issued under; bumping it (sign-out, any password
     * change or reset) makes every earlier token invalid on its next use, with no token blacklist to store.
     */
    private int tokenVersion;

    /**
     * Self-registration: the member chose their own password. {@code pendingApproval} is true when the member still
     * needs an administrator's approval, false when the registration was already vouched for (a valid admin code).
     */
    public static Member register(MemberDetails details, Role role, String passwordHash, boolean pendingApproval,
                                  Instant now) {
        Member m = create(details, role, passwordHash, false, now);
        m.pendingApproval = pendingApproval;
        return m;
    }

    /** Admin created the member: the password is temporary and must be changed at first sign-in. */
    public static Member createByAdmin(MemberDetails details, Role role, String temporaryPasswordHash, Instant now) {
        return create(details, role, temporaryPasswordHash, true, now);
    }

    private static Member create(MemberDetails details, Role role, String passwordHash,
                                 boolean mustChangePassword, Instant now) {
        Member m = new Member();
        m.role = Objects.requireNonNull(role, "role must not be null");
        m.passwordHash = passwordHash;
        m.mustChangePassword = mustChangePassword;
        m.createdAt = now;
        m.updateProfile(details, now);
        return m;
    }

    /** Replaces name, email, country code and phone. Role and password are untouched. */
    public void updateProfile(MemberDetails details, Instant now) {
        this.name = MemberRules.normalizeName(details.name());
        this.nameLower = this.name.toLowerCase(Locale.ROOT);
        this.email = MemberRules.normalizeEmail(details.email());
        this.countryCode = MemberRules.normalizeCountryCode(details.countryCode());
        this.phoneNumber = MemberRules.normalizePhone(details.phoneNumber());
        this.updatedAt = now;
    }

    /** Changes the role. The caller must make sure this does not remove the last admin. */
    public void changeRole(Role newRole, Instant now) {
        this.role = Objects.requireNonNull(newRole, "role must not be null");
        this.updatedAt = now;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    /** Administrator accepted the registration: the member can sign in from now on. */
    public void approve(Instant now) {
        this.pendingApproval = false;
        this.updatedAt = now;
    }

    /** The one place that combines the flags into the status shown to users. A pending member shows as pending first. */
    public MemberStatus getStatus() {
        if (pendingApproval) {
            return MemberStatus.PENDING_APPROVAL;
        }
        return mustChangePassword ? MemberStatus.TEMP_PASSWORD : MemberStatus.ACTIVE;
    }

    /** Admin reset the password: member must choose a new one at next sign-in. */
    public void issueTemporaryPassword(String passwordHash, Instant now) {
        this.passwordHash = passwordHash;
        this.mustChangePassword = true;
        this.tokenVersion++;                  // whoever held the old password is signed out everywhere
        this.updatedAt = now;
    }

    /** Member chose their own password. */
    public void changePassword(String passwordHash, Instant now) {
        this.passwordHash = passwordHash;
        this.mustChangePassword = false;
        this.tokenVersion++;                  // a stolen token dies with the old password
        this.updatedAt = now;
    }

    /** Sign out everywhere: every token issued so far stops working. */
    public void revokeSessions(Instant now) {
        this.tokenVersion++;
        this.updatedAt = now;
    }
}
