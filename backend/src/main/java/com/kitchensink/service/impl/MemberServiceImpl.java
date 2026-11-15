package com.kitchensink.service.impl;



import com.kitchensink.dto.common.PageResult;
import com.kitchensink.dto.member.BulkDeleteResponse;
import com.kitchensink.dto.member.MemberRequest;
import com.kitchensink.dto.member.MemberSearchCriteria;
import com.kitchensink.dto.member.PageQuery;
import com.kitchensink.dto.member.UpdateMemberRequest;
import com.kitchensink.exception.domain.MemberNotFoundException;
import com.kitchensink.exception.domain.VersionConflictException;
import com.kitchensink.model.Member;
import com.kitchensink.service.MemberService;
import com.kitchensink.service.port.MemberActivityMetrics;
import com.kitchensink.service.port.MemberStore;
import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MemberServiceImpl implements MemberService {

    private static final Logger log = LoggerFactory.getLogger(MemberServiceImpl.class);

    private final MemberStore members;
    private final MemberActivityMetrics metrics;
    private final Clock clock;

    public MemberServiceImpl(MemberStore members, MemberActivityMetrics metrics, Clock clock) {
        this.members = members;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Override
    public Member create(MemberRequest request) {
        Member member = Member.register(request.name(), request.email(), request.phoneNumber(), clock.instant());
        member = members.insert(member);
        metrics.memberCreated();
        log.info("Member created id={}", member.getId());
        return member;
    }

    @Override
    public Member get(String id) {
        return members.findById(id).orElseThrow(() -> new MemberNotFoundException(id));
    }

    @Override
    public PageResult<Member> search(String text, PageQuery page) {
        return members.search(MemberSearchCriteria.of(text), page);
    }

    @Override
    public Member update(String id, UpdateMemberRequest request) {
        Member member = get(id);
        if (!Objects.equals(member.getVersion(), request.version())) {
            throw new VersionConflictException();
        }
        member.apply(request.name(), request.email(), request.phoneNumber(), clock.instant());
        member = members.save(member);
        metrics.memberUpdated();
        log.info("Member updated id={} version={}", member.getId(), member.getVersion());
        return member;
    }

    @Override
    public void delete(String id) {
        if (!members.existsById(id)) {
            throw new MemberNotFoundException(id);
        }
        members.deleteById(id);
        metrics.membersDeleted(1);
        log.info("Member deleted id={}", id);
    }

    @Override
    public BulkDeleteResponse bulkDelete(List<String> ids) {
        Set<String> requested = new LinkedHashSet<>(ids);
        Set<String> existing = members.findAllById(requested).stream()
                .map(Member::getId)
                .collect(Collectors.toSet());
        members.deleteAllById(existing);
        List<String> notFound = requested.stream().filter(id -> !existing.contains(id)).toList();
        metrics.membersDeleted(existing.size());
        log.info("Members bulk-deleted requested={} deleted={}", requested.size(), existing.size());
        return new BulkDeleteResponse(requested.size(), existing.size(), notFound);
    }
}
