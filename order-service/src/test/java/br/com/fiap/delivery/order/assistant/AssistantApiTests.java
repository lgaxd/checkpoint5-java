package br.com.fiap.delivery.order.assistant;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.fiap.delivery.order.error.ExternalServiceException;
import br.com.fiap.delivery.order.support.IntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class AssistantApiTests {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ChatService chatService;

    @Test
    void returnsAnswerField() throws Exception {
        when(chatService.ask("Tem opção vegana?")).thenReturn("Sim, temos o Vegan Bowl.");

        mockMvc.perform(post("/assistant").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"Tem opção vegana?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Sim, temos o Vegan Bowl."));
    }

    @Test
    void blankQuestionReturns400() throws Exception {
        mockMvc.perform(post("/assistant").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isString());
    }

    @Test
    void providerFailureReturns502WithErrorBody() throws Exception {
        when(chatService.ask("Oi")).thenThrow(new ExternalServiceException("Assistant is currently unavailable", null));

        mockMvc.perform(post("/assistant").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"Oi\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("Assistant is currently unavailable"));
    }
}
