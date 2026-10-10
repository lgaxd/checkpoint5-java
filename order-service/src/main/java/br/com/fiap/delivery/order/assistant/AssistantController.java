package br.com.fiap.delivery.order.assistant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AssistantController {

    public record AssistantRequest(@NotBlank(message = "is required") String question) {
    }

    public record AssistantResponse(String answer) {
    }

    private final ChatService chatService;

    public AssistantController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/assistant")
    public AssistantResponse ask(@Valid @RequestBody AssistantRequest request) {
        return new AssistantResponse(chatService.ask(request.question()));
    }
}
