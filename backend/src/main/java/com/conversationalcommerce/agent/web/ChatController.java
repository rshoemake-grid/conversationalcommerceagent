package com.conversationalcommerce.agent.web;

import com.conversationalcommerce.agent.orchestration.OrchestratorService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ChatController {

    private final OrchestratorService orchestratorService;

    public ChatController(OrchestratorService orchestratorService) {
        this.orchestratorService = orchestratorService;
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        var response = orchestratorService.process(
                request.mode(),
                request.message(),
                request.conversationId(),
                request.sessionId() != null ? request.sessionId() : "session-" + System.currentTimeMillis()
        );
        return ResponseEntity.ok(ChatResponse.from(response));
    }
}
