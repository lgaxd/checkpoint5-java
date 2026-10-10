package br.com.fiap.delivery.order.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import br.com.fiap.delivery.order.dish.Dish;
import br.com.fiap.delivery.order.dish.DishRepository;
import br.com.fiap.delivery.order.error.ExternalServiceException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

/** Uses a fake ChatModel so the real ChatClient assembles the prompt without calling any AI provider. */
class ChatServiceTests {

    ChatModel chatModel;
    DishRepository dishRepository;
    ChatService chatService;

    @BeforeEach
    void setUp() {
        chatModel = mock(ChatModel.class);
        dishRepository = mock(DishRepository.class);
        when(dishRepository.findAll()).thenReturn(List.of(
                new Dish("Vegan Bowl", "Quinoa and vegetables. Vegan", new BigDecimal("34.50"), 40),
                new Dish("House Burger", "Brioche bun", new BigDecimal("39.90"), 0)));
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Temos o Vegan Bowl.")))));
        chatService = new ChatService(ChatClient.builder(chatModel), dishRepository, 5, 2);
    }

    private Prompt capturePrompt() {
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        return captor.getValue();
    }

    @Test
    void returnsTheModelAnswer() {
        assertThat(chatService.ask("Tem opção vegana?")).isEqualTo("Temos o Vegan Bowl.");
    }

    @Test
    void sendsSystemMessageDefiningTheRestaurantAttendantRole() {
        chatService.ask("Tem opção vegana?");

        Message system = capturePrompt().getInstructions().stream()
                .filter(m -> m.getMessageType() == MessageType.SYSTEM).findFirst().orElseThrow();
        assertThat(system.getText())
                .contains("attendant at a delivery restaurant")
                .contains("Portuguese")
                .contains("short")
                .contains("politely decline");
    }

    @Test
    void includesCurrentMenuWithNamePriceAndStockInThePrompt() {
        chatService.ask("Tem prato vegetariano até R$ 40?");

        Message user = capturePrompt().getInstructions().stream()
                .filter(m -> m.getMessageType() == MessageType.USER).findFirst().orElseThrow();
        assertThat(user.getText())
                .contains("Vegan Bowl", "34.50", "stock: 40")
                .contains("House Burger", "39.90", "stock: 0")
                .contains("Tem prato vegetariano até R$ 40?");
    }

    @Test
    void menuIsReadFromTheDatabaseOnEveryQuestion() {
        chatService.ask("one");
        chatService.ask("two");

        verify(dishRepository, org.mockito.Mockito.times(2)).findAll();
    }

    @Test
    void bracesInTheQuestionDoNotBreakPromptRendering() {
        assertThat(chatService.ask("What about {this} and {{that}}?")).isNotBlank();
    }

    @Test
    void retriesOnceWhenTheModelIsTemporarilyOverloaded() {
        when(chatModel.call(any(Prompt.class)))
                .thenThrow(new IllegalStateException("503 high demand"))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Agora foi.")))));

        assertThat(chatService.ask("Oi")).isEqualTo("Agora foi.");
        verify(chatModel, org.mockito.Mockito.times(2)).call(any(Prompt.class));
    }

    @Test
    void aHangingModelCallTimesOutInsteadOfBlockingForever() {
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            Thread.sleep(10_000);
            return null;
        });
        ChatService fast = new ChatService(ChatClient.builder(chatModel), dishRepository, 1, 1);

        long start = System.nanoTime();
        assertThatThrownBy(() -> fast.ask("Oi")).isInstanceOf(ExternalServiceException.class);

        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(5_000);
    }

    @Test
    void modelFailureIsTranslatedToExternalServiceException() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new IllegalStateException("401 invalid api key"));

        assertThatThrownBy(() -> chatService.ask("Oi"))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessage("Assistant is currently unavailable");
    }
}
