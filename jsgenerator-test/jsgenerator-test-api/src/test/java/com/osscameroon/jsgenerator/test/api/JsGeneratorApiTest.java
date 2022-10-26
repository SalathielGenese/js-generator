package com.osscameroon.jsgenerator.test.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.osscameroon.jsgenerator.api.JsGeneratorApi;
import com.osscameroon.jsgenerator.api.rest.ConvertController;
import com.osscameroon.jsgenerator.core.VariableDeclaration;
import org.hamcrest.CustomMatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Map;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringBootTest(webEnvironment = MOCK)
public class JsGeneratorApiTest {
    @Autowired
    private WebApplicationContext webApplicationContext;
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    public void beforeEach() {
        mockMvc = webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        objectMapper = JsonMapper.builder().build();
    }

    @Test
    public void actuatorPublicEndpoint() throws Exception {
        // GET /actuator            :: public
        mockMvc.perform(get("/actuator"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.spring-boot.actuator.v3+json"));
        // GET /actuator/health     :: public
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.spring-boot.actuator.v3+json"));
        // GET /actuator/metrics    :: secured
        mockMvc.perform(get("/actuator/beans"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/mappings"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "user", roles = "ACTUATOR")
    public void actuatorSecuredEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/beans"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.spring-boot.actuator.v3+json"));
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.spring-boot.actuator.v3+json"));
        mockMvc.perform(get("/actuator/mappings"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.spring-boot.actuator.v3+json"));
    }

    @ParameterizedTest
    @EnumSource(VariableDeclaration.class)
    public void convertInlineContent(
            final VariableDeclaration variableDeclaration) throws Exception {
        final var keyword = keyword(variableDeclaration);
        final var extension = randomUUID().toString();
        final var prefix = randomUUID().toString();
        final var content = randomUUID().toString();

        mockMvc.perform(post(ConvertController.MAPPING)
                        .header(CONTENT_TYPE, APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "inlineContents", List.of("<div contenteditable>%s</div>".formatted(content)),
                                "inlinePattern", "%s.{{ index }}{{ extension }}".formatted(prefix),
                                "variableDeclaration", variableDeclaration,
                                "extension", ".%s".formatted(extension)
                        ))))
                .andExpectAll(
                        status().isOk(),
                        header().string(CONTENT_TYPE, APPLICATION_JSON.toString()),
                        jsonPath("$").isArray(),
                        jsonPath("$.length()").value(1),
                        jsonPath("$.[0].filename").value("%s.0.%s".formatted(prefix, extension)),
                        jsonPath("$.[0].content").value(new Match(new String[]{
                                "%s targetElement_000 = document.querySelector(`:root > body`);".formatted(keyword),
                                "%s div_000 = document.createElement('div');".formatted(keyword),
                                "div_000.setAttribute(`contenteditable`, `true`);",
                                "%s text_000 = document.createTextNode(`%s`);".formatted(keyword, content),
                                "div_000.appendChild(text_000);",
                                "targetElement_000.appendChild(div_000);",
                        }))
                );
    }

    private static String keyword(final VariableDeclaration variableDeclaration) {
        return variableDeclaration.name().toLowerCase();
    }

    public static class Application extends JsGeneratorApi {
    }

    private static final class Match extends CustomMatcher<String> {
        private final String[] lines;

        public Match(final String[] lines) {
            super("jsjenerator-matcher");
            this.lines = lines;
        }

        @Override
        public boolean matches(Object actual) {
            assertThat(((String) actual)
                    .lines()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty())
                    .toArray(String[]::new)
            ).containsExactly(lines);
            return true;
        }
    }
}