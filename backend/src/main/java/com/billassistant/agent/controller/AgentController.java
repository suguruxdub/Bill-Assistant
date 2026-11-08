package com.billassistant.agent.controller;

import com.billassistant.agent.dto.AgentChatRequest;
import com.billassistant.agent.dto.AgentChatResponse;
import com.billassistant.agent.service.AgentService;
import com.billassistant.auth.UserContext;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/chat")
    public AgentChatResponse chat(@Valid @RequestBody AgentChatRequest request) {
        return new AgentChatResponse(agentService.chat(UserContext.requireUid(), request.message()));
    }
}
