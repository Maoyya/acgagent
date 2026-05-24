package com.darkness.agent.service.impl;

import com.darkness.common.entity.AgentDO;
import com.darkness.common.entity.ConversationDO;
import com.darkness.common.entity.MessageDO;
import com.darkness.common.exception.BizException;
import com.darkness.common.mapper.AgentMapper;
import com.darkness.common.mapper.ConversationMapper;
import com.darkness.common.mapper.MessageMapper;
import com.darkness.common.model.ConversationVO;
import com.darkness.common.model.MessageVO;
import com.darkness.common.result.ResultCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 对话服务单元测试，覆盖会话 CRUD 和 IDOR 所有权校验逻辑。
 * 核心验证点：不存在的会话抛 NOT_FOUND，非所有者访问抛 FORBIDDEN。
 * sendMessage 的 SSE 异步流不做单元测试（涉及线程池和 WebClient，适合集成测试）。
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceImplTest {

    @Mock
    private ConversationMapper conversationMapper;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private AgentMapper agentMapper;

    @Mock
    private com.darkness.agent.client.AgentClient agentClient;

    @InjectMocks
    private ChatServiceImpl chatService;

    // ==================== createConversation ====================

    @Test
    void createConversation_success() {
        when(conversationMapper.insert(any(ConversationDO.class))).thenAnswer(invocation -> {
            ConversationDO conv = invocation.getArgument(0);
            conv.setId(1L);
            return 1;
        });

        ConversationVO result = chatService.createConversation(1L, 10L, "Test Chat");

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
    }

    // ==================== listConversations ====================

    @Test
    void listConversations_returnsUserConversations() {
        ConversationDO c1 = new ConversationDO();
        c1.setId(1L);
        c1.setUserId(1L);
        c1.setTitle("Chat 1");
        when(conversationMapper.selectList(any())).thenReturn(List.of(c1));

        List<ConversationVO> result = chatService.listConversations(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Chat 1");
    }

    // ==================== getMessages ====================

    @Test
    void getMessages_success() {
        ConversationDO conv = new ConversationDO();
        conv.setId(1L);
        conv.setUserId(1L);
        when(conversationMapper.selectById(1L)).thenReturn(conv);

        MessageDO msg = new MessageDO();
        msg.setId(1L);
        msg.setConversationId(1L);
        msg.setContent("Hello");
        when(messageMapper.selectList(any())).thenReturn(List.of(msg));

        List<MessageVO> result = chatService.getMessages(1L, 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getContent()).isEqualTo("Hello");
    }

    @Test
    void getMessages_conversationNotFound_throws404() {
        when(conversationMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> chatService.getMessages(999L, 1L))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(ResultCode.NOT_FOUND.getCode());
    }

    @Test
    void getMessages_forbidden_whenNotOwner() {
        ConversationDO conv = new ConversationDO();
        conv.setId(1L);
        conv.setUserId(1L);
        when(conversationMapper.selectById(1L)).thenReturn(conv);

        assertThatThrownBy(() -> chatService.getMessages(1L, 999L))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(ResultCode.FORBIDDEN.getCode());
    }

    // ==================== deleteConversation ====================

    @Test
    void deleteConversation_success() {
        ConversationDO conv = new ConversationDO();
        conv.setId(1L);
        conv.setUserId(1L);
        when(conversationMapper.selectById(1L)).thenReturn(conv);
        when(conversationMapper.deleteById(1L)).thenReturn(1);

        chatService.deleteConversation(1L, 1L);

        verify(conversationMapper).deleteById(1L);
    }

    @Test
    void deleteConversation_notFound_throws404() {
        when(conversationMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> chatService.deleteConversation(999L, 1L))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(ResultCode.NOT_FOUND.getCode());
    }

    @Test
    void deleteConversation_forbidden_whenNotOwner() {
        ConversationDO conv = new ConversationDO();
        conv.setId(1L);
        conv.setUserId(1L);
        when(conversationMapper.selectById(1L)).thenReturn(conv);

        assertThatThrownBy(() -> chatService.deleteConversation(1L, 999L))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(ResultCode.FORBIDDEN.getCode());
    }
}
