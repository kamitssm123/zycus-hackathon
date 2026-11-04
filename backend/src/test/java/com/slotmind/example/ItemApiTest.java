package com.slotmind.example;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotmind.example.api.ItemRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The contract test every new endpoint should mirror: happy path, validation failure, not-found.
 * See CLAUDE.md — "write tests with every endpoint".
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ItemApiTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  private String json(Object o) throws Exception {
    return objectMapper.writeValueAsString(o);
  }

  @Test
  void createsThenListsThenDeletesItem() throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/items")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(new ItemRequest("Ship the demo", "end to end", false))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Ship the demo"))
            .andExpect(jsonPath("$.done").value(false))
            .andReturn()
            .getResponse()
            .getContentAsString();

    String id = objectMapper.readTree(body).get("id").asText();

    mockMvc
        .perform(get("/api/items").param("search", "Ship"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value(id))
        .andExpect(jsonPath("$.total").value(1));

    mockMvc.perform(delete("/api/items/" + id)).andExpect(status().isNoContent());
  }

  @Test
  void rejectsBlankNameWithFieldLevelDetail() throws Exception {
    mockMvc
        .perform(
            post("/api/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new ItemRequest("  ", null, null))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.details[0].field").value("name"));
  }

  @Test
  void returnsStructuredNotFound() throws Exception {
    mockMvc
        .perform(get("/api/items/" + UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.requestId").exists());
  }
}
