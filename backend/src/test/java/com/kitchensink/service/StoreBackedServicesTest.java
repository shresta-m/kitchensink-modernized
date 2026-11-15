package com.kitchensink.service;




import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kitchensink.dto.common.PageResult;
import com.kitchensink.dto.member.MemberSearchCriteria;
import com.kitchensink.dto.member.PageQuery;
import com.kitchensink.dto.member.SortSpec;
import com.kitchensink.exception.domain.InvalidQueryException;
import com.kitchensink.model.Member;
import com.kitchensink.model.Role;
import com.kitchensink.model.User;
import com.kitchensink.service.export.MemberCsvWriter;
import com.kitchensink.service.impl.MemberExportServiceImpl;
import com.kitchensink.service.impl.MemberServiceImpl;
import com.kitchensink.service.impl.UserServiceImpl;
import com.kitchensink.service.port.MemberActivityMetrics;
import com.kitchensink.service.port.MemberStore;
import com.kitchensink.service.port.UserStore;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class StoreBackedServicesTest {

    private static final Instant NOW = Instant.parse("2026-11-14T10:00:00Z");

    private static Member member(String name) {
        return Member.register(name, name.toLowerCase() + "@example.com", "2125551212", NOW);
    }

    @Test
    void memberServiceSearchAndGetDelegateToTheStore() {
        MemberStore store = mock(MemberStore.class);
        PageQuery page = new PageQuery(0, 10, SortSpec.DEFAULT);
        PageResult<Member> result = new PageResult<>(List.of(member("Ann")), 0, 10, 1);
        when(store.search(MemberSearchCriteria.of("an"), page)).thenReturn(result);
        MemberServiceImpl service = new MemberServiceImpl(store, mock(MemberActivityMetrics.class), Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(service.search(" an ", page)).isSameAs(result);
    }

    @Test
    void memberServiceDeleteRemovesAnExistingMember() {
        MemberStore store = mock(MemberStore.class);
        MemberActivityMetrics metrics = mock(MemberActivityMetrics.class);
        when(store.existsById("1")).thenReturn(true);

        new MemberServiceImpl(store, metrics, Clock.fixed(NOW, ZoneOffset.UTC)).delete("1");

        verify(store).deleteById("1");
        verify(metrics).membersDeleted(1);
    }

    @Test
    void memberServiceGetReturnsTheMember() {
        MemberStore store = mock(MemberStore.class);
        Member ann = member("Ann");
        when(store.findById("1")).thenReturn(java.util.Optional.of(ann));

        assertThat(new MemberServiceImpl(store, mock(MemberActivityMetrics.class), Clock.systemUTC()).get("1")).isSameAs(ann);
    }

    @Test
    void userServiceListsThroughTheStore() {
        UserStore store = mock(UserStore.class);
        User jane = new User("jane", "h", Role.VIEWER, NOW);
        when(store.findAllOrderedByUsername()).thenReturn(List.of(jane));
        var service = new UserServiceImpl(store, mock(org.springframework.security.crypto.password.PasswordEncoder.class),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(service.list()).containsExactly(jane);
    }

    // ---- export -------------------------------------------------------------------------------

    @Test
    void exportWritesHeaderThenEveryStreamedMember() {
        MemberStore store = mock(MemberStore.class);
        doAnswer(inv -> {
            Consumer<Member> consumer = inv.getArgument(3);
            consumer.accept(member("Ann"));
            consumer.accept(member("Bob"));
            return null;
        }).when(store).forEachMatching(any(), any(), eq(MemberExportService.MAX_ROWS), any());
        StringWriter out = new StringWriter();

        new MemberExportServiceImpl(store, new MemberCsvWriter()).exportCsv("  ", SortSpec.DEFAULT, out);

        assertThat(out.toString().split("\r\n")).hasSize(3);
        assertThat(out.toString()).startsWith("name,email,phoneNumber,createdAt").contains("ann@example.com", "bob@example.com");
    }

    @Test
    void exportRejectsAnOverlongSearchTerm() {
        var service = new MemberExportServiceImpl(mock(MemberStore.class), new MemberCsvWriter());

        assertThatThrownBy(() -> service.exportCsv("x".repeat(101), SortSpec.DEFAULT, new StringWriter()))
                .isInstanceOf(InvalidQueryException.class);
    }

    @Test
    void exportTurnsAFlushFailureIntoAnUncheckedException() {
        Writer ioFlush = new Writer() {
            @Override public void write(char[] c, int o, int l) { }
            @Override public void flush() throws IOException { throw new IOException("closed"); }
            @Override public void close() { }
        };
        var service = new MemberExportServiceImpl(mock(MemberStore.class), new MemberCsvWriter());

        assertThatThrownBy(() -> service.exportCsv(null, SortSpec.DEFAULT, ioFlush)).isInstanceOf(UncheckedIOException.class);
    }
}
