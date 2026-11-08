package com.billassistant.agent.service;

import com.billassistant.agent.tool.BillAgentTools;
import com.billassistant.exception.ApiException;
import com.billassistant.service.BillService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AgentService {

    private final ChatClient.Builder chatClientBuilder;
    private final BillService billService;
    private final boolean enabled;

    public AgentService(ObjectProvider<ChatClient.Builder> chatClientBuilders, BillService billService,
                        @Value("${app.ai.enabled:false}") boolean enabled) {
        this.chatClientBuilder = chatClientBuilders.getIfAvailable();
        this.billService = billService;
        this.enabled = enabled;
    }

    public String chat(long uid, String message) {
        if (!enabled || chatClientBuilder == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_DISABLED", "AI 助手尚未配置，请先配置模型服务。");
        }
        try {
            String result = chatClientBuilder.defaultSystem("你是 Bill Assistant 的账单分析助手。只能使用提供的只读工具查询当前用户账单。不要猜测数据；无法确定时明确说明。使用简洁中文回答，不执行任何写操作。\n")
                    .build().prompt()
                    .user(message)
                    .tools(new BillAgentTools(billService, uid))
                    .call()
                    .content();
            return result == null || result.isBlank() ? "暂时无法生成回答，请稍后重试。" : result;
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_UNAVAILABLE", "AI 服务暂时不可用，请稍后重试。");
        }
    }
}
