package com.kitchensink.model;



import com.kitchensink.validation.MemberRules;
import java.time.Instant;
import java.util.Locale;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** Member entity stored in the {@code members} collection. Fields are normalized on the way in. */
@Document("members")
public class Member {

    @Id
    private String id;

    private String name;

    /** Lowercased copy of name: lets prefix search and sorting use an index and ignore case. */
    @Indexed
    private String nameLower;

    @Indexed(unique = true)
    private String email;

    @Indexed
    private String phoneNumber;

    @Indexed
    private Instant createdAt;

    private Instant updatedAt;

    @Version
    private Long version;

    protected Member() {}

    /** Creates a not-yet-persisted member (normalizes name, email and phone). */
    public static Member register(String name, String email, String phoneNumber, Instant now) {
        Member m = new Member();
        m.createdAt = now;
        m.apply(name, email, phoneNumber, now);
        return m;
    }

    /** Replaces the editable fields. */
    public void apply(String name, String email, String phoneNumber, Instant now) {
        this.name = MemberRules.normalizeName(name);
        this.nameLower = this.name.toLowerCase(Locale.ROOT);
        this.email = MemberRules.normalizeEmail(email);
        this.phoneNumber = MemberRules.normalizePhone(phoneNumber);
        this.updatedAt = now;
    }

    public void initVersion() {
        if (version == null) {
            version = 0L;
        }
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getNameLower() { return nameLower; }
    public String getEmail() { return email; }
    public String getPhoneNumber() { return phoneNumber; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
