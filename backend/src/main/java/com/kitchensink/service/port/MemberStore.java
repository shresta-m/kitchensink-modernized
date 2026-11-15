package com.kitchensink.service.port;



import com.kitchensink.dto.common.PageResult;
import com.kitchensink.dto.imports.BatchInsertResult;
import com.kitchensink.dto.member.MemberSearchCriteria;
import com.kitchensink.dto.member.PageQuery;
import com.kitchensink.dto.member.SortSpec;
import com.kitchensink.model.Member;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Persistence port for members, owned by the service layer (dependency inversion). Implementations translate
 * storage failures into domain exceptions, so callers never see Spring Data or driver types.
 *
 * @throws com.kitchensink.exception.domain.DuplicateEmailException on {@link #insert} / {@link #save} when the email is taken
 * @throws com.kitchensink.exception.domain.VersionConflictException on {@link #save} when the stored version moved on
 */
public interface MemberStore {

    Member insert(Member member);

    Member save(Member member);

    Optional<Member> findById(String id);

    boolean existsById(String id);

    List<Member> findAllById(Collection<String> ids);

    void deleteById(String id);

    void deleteAllById(Collection<String> ids);

    PageResult<Member> search(MemberSearchCriteria criteria, PageQuery page);

    /** Streams matching members to the consumer without loading them all into memory. */
    void forEachMatching(MemberSearchCriteria criteria, SortSpec sort, int limit, Consumer<Member> consumer);

    /** Unordered bulk insert; unique-email violations are reported per position, not thrown. */
    BatchInsertResult insertBatch(List<Member> members);
}
