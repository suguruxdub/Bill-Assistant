package com.billassistant.agent;

import com.billassistant.agent.service.AgentService;
import com.billassistant.exception.ApiException;
import com.billassistant.service.BillService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentServiceTests {

    @Test
    void doesNotCallAProviderWhenAiIsDisabled() {
        ObjectProvider<ChatClient.Builder> builders = mock(ObjectProvider.class);
        when(builders.getIfAvailable()).thenReturn(null);

        AgentService service = new AgentService(builders, mock(BillService.class), false);

        ApiException exception = assertThrows(ApiException.class, () -> service.chat(42, "查询本月账单"));

        assertEquals("AI_DISABLED", exception.getCode());
    }
}
