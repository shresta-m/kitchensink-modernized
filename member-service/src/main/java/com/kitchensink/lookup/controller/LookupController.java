package com.kitchensink.lookup.controller;

import com.kitchensink.lookup.dto.LookupOption;
import com.kitchensink.member.model.MemberStatus;
import com.kitchensink.member.model.Role;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Fixed value lists for dropdowns, so the UI never hardcodes the enums. Admins and viewers may read them. */
@RestController
@RequestMapping("/api/v1/lookups")
public class LookupController implements LookupControllerInterface {

    @Override
    @GetMapping("/roles")
    public List<LookupOption> roles() {
        return Arrays.stream(Role.values())
                .map(r -> LookupOption.builder().value(r.name()).label(r.label()).build())
                .toList();
    }

    @Override
    @GetMapping("/statuses")
    public List<LookupOption> statuses() {
        return Arrays.stream(MemberStatus.values())
                .map(s -> LookupOption.builder().value(s.name()).label(s.label()).build())
                .toList();
    }
}
