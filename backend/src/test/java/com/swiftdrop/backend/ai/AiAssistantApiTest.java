package com.swiftdrop.backend.ai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The AI endpoints' doors: guests may chat (limited), the tool list is public, running a tool needs a login.
 * No Gemini key in tests, so the assistant answers with its "not configured" text and never calls Google.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD) // fresh limiter counts per test
class AiAssistantApiTest {

    private static final String HELLO = """
            {"message": "How much to send 2 kg to Kandy?"}
            """;

    @Autowired
    MockMvc mockMvc;

    @Test
    void guestCanChatWithoutLoggingIn() throws Exception {
        mockMvc.perform(post("/api/public/ai/chat").contentType(MediaType.APPLICATION_JSON).content(HELLO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").exists());
    }

    @Test
    void guestIsStoppedAfterFiveMessagesInAMinute() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/public/ai/chat").contentType(MediaType.APPLICATION_JSON).content(HELLO))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/public/ai/chat").contentType(MediaType.APPLICATION_JSON).content(HELLO))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    @Test
    void tooLongMessageIsRefused() throws Exception {
        String longMessage = "{\"message\": \"" + "a".repeat(501) + "\"}";
        mockMvc.perform(post("/api/public/ai/chat").contentType(MediaType.APPLICATION_JSON).content(longMessage))
                .andExpect(status().isBadRequest());
    }

    @Test
    void toolListIsPublic() throws Exception {
        mockMvc.perform(get("/api/public/ai/mcp/tools")).andExpect(status().isOk());
    }

    @Test
    void runningAToolDirectlyNeedsALogin() throws Exception {
        mockMvc.perform(post("/api/ai/mcp/tools/call").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"listMyDeliveries\", \"arguments\": {}}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void oldOpenAddressesAreGone() throws Exception {
        mockMvc.perform(post("/api/v1/ai/chat").contentType(MediaType.APPLICATION_JSON).content(HELLO))
                .andExpect(status().isUnauthorized());
    }
}
