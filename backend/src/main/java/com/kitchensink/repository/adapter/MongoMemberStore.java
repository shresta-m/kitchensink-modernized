package com.kitchensink.repository.adapter;



import com.kitchensink.dto.common.PageResult;
import com.kitchensink.dto.imports.BatchInsertResult;
import com.kitchensink.dto.member.MemberSearchCriteria;
import com.kitchensink.dto.member.PageQuery;
import com.kitchensink.dto.member.SortSpec;
import com.kitchensink.exception.domain.DuplicateEmailException;
import com.kitchensink.exception.domain.VersionConflictException;
import com.kitchensink.model.Member;
import com.kitchensink.repository.MemberRepository;
import com.kitchensink.service.port.MemberStore;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

/**
 * Adapter between the service-owned {@link MemberStore} port and Spring Data MongoDB. The only place where
 * driver/Spring Data failures are caught and turned into domain exceptions.
 */
@Repository
public class MongoMemberStore implements MemberStore {

    private final MemberRepository repository;

    public MongoMemberStore(MemberRepository repository) {
        this.repository = repository;
    }

    @Override
    public Member insert(Member member) {
        try {
            return repository.insert(member);
        } catch (DuplicateKeyException e) {
            // The unique index is the arbiter, so two simultaneous requests cannot both win.
            throw new DuplicateEmailException(e);
        }
    }

    @Override
    public Member save(Member member) {
        try {
            return repository.save(member);
        } catch (DuplicateKeyException e) {
            throw new DuplicateEmailException(e);
        } catch (OptimisticLockingFailureException e) {
            throw new VersionConflictException(e);
        }
    }

    @Override
    public Optional<Member> findById(String id) {
        return repository.findById(id);
    }

    @Override
    public boolean existsById(String id) {
        return repository.existsById(id);
    }

    @Override
    public List<Member> findAllById(Collection<String> ids) {
        return repository.findAllById(ids);
    }

    @Override
    public void deleteById(String id) {
        repository.deleteById(id);
    }

    @Override
    public void deleteAllById(Collection<String> ids) {
        repository.deleteAllById(ids);
    }

    @Override
    public PageResult<Member> search(MemberSearchCriteria criteria, PageQuery page) {
        return repository.search(criteria, page);
    }

    @Override
    public void forEachMatching(MemberSearchCriteria criteria, SortSpec sort, int limit, Consumer<Member> consumer) {
        repository.forEachMatching(criteria, sort, limit, consumer);
    }

    @Override
    public BatchInsertResult insertBatch(List<Member> members) {
        return repository.insertBatch(members);
    }
}
