package com.kitchensink.lookup.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kitchensink.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

class LookupControllerTest {

    private final MockMvc mvc = TestData.mockMvc(new LookupController());

    @Test
    void returnsEveryRoleWithItsLabel() throws Exception {
        mvc.perform(get("/api/v1/lookups/roles")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].value").value("ADMIN"))
                .andExpect(jsonPath("$[0].label").value("Admin"))
                .andExpect(jsonPath("$[1].value").value("VIEWER"))
                .andExpect(jsonPath("$[1].label").value("Viewer"));
    }

    @Test
    void returnsEveryStatusWithItsLabel() throws Exception {
        mvc.perform(get("/api/v1/lookups/statuses")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].value").value("ACTIVE"))
                .andExpect(jsonPath("$[0].label").value("Active"))
                .andExpect(jsonPath("$[1].value").value("TEMP_PASSWORD"))
                .andExpect(jsonPath("$[1].label").value("Temp password"));
    }
}
