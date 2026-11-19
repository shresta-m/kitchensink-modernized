package com.kitchensink.member.repository;

import com.kitchensink.member.model.MemberSearchCriteria;
import com.kitchensink.member.model.MemberStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

/**
 * Builds the Mongo query for the members list. Search is a case-insensitive prefix match: the stored fields
 * (nameLower, email, phoneNumber) are already normalized, so an anchored regex can use their indexes.
 */
@Component
public class MemberQueryBuilder {

    private static final String PHONE_LIKE = "[\\d\\s+\\-]+";

    public Query build(MemberSearchCriteria criteria) {
        List<Criteria> filters = new ArrayList<>();
        searchClause(criteria.search()).ifPresent(filters::add);
        if (criteria.role() != null) {
            filters.add(Criteria.where("role").is(criteria.role()));
        }
        if (criteria.status() != null) {
            filters.add(statusClause(criteria.status()));
        }
        Query query = new Query();
        if (!filters.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(filters.toArray(new Criteria[0])));
        }
        return query;
    }

    /**
     * {@code pendingApproval} is matched with {@code ne(true)} rather than {@code is(false)} because members stored
     * before approval existed have no such field, and {@code is(false)} would not match a missing field.
     */
    private static Criteria statusClause(MemberStatus status) {
        return switch (status) {
            case PENDING_APPROVAL -> Criteria.where("pendingApproval").is(true);
            case TEMP_PASSWORD -> new Criteria().andOperator(
                    Criteria.where("pendingApproval").ne(true), Criteria.where("mustChangePassword").is(true));
            case ACTIVE -> new Criteria().andOperator(
                    Criteria.where("pendingApproval").ne(true), Criteria.where("mustChangePassword").is(false));
        };
    }

    private Optional<Criteria> searchClause(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String term = raw.trim().toLowerCase(Locale.ROOT);
        List<Criteria> anyOf = new ArrayList<>();
        anyOf.add(Criteria.where("nameLower").regex(prefix(term)));
        anyOf.add(Criteria.where("email").regex(prefix(term)));
        if (term.matches(PHONE_LIKE)) {                       // only look at phones when the text looks like one
            String digits = term.replaceAll("\\D", "");
            if (!digits.isEmpty()) {
                anyOf.add(Criteria.where("phoneNumber").regex(prefix(digits)));
            }
        }
        return Optional.of(new Criteria().orOperator(anyOf.toArray(new Criteria[0])));
    }

    private static String prefix(String literal) {
        // Escape regex metacharacters so user input is always matched literally.
        return "^" + literal.replaceAll("[\\\\^$.|?*+()\\[\\]{}]", "\\\\$0");
    }
}
