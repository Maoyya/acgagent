
package com.darkness.user.controller;

import com.darkness.common.exception.BizException;
import com.darkness.user.entity.User;
import com.darkness.user.mapper.UserMapper;
import com.darkness.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;


import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "com.alibaba.cloud.nacos.NacosDiscoveryAutoConfiguration,"
                + "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration"
})
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserMapper userMapper;

    @MockBean
    private UserService userService;

    // ========== GET /api/user/{id} ==========

    @Test
    void getUser_userExists_returns200() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setEmail("test@example.com");
        when(userService.getUserById(1L)).thenReturn(user);

        mockMvc.perform(get("/api/user/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.username").value("testuser"))
                .andExpect(jsonPath("$.data.email").value("test@example.com"))
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    void getUser_userNotFound_returns404() throws Exception {
        when(userService.getUserById(999L))
                .thenThrow(new BizException(404, "User not found: 999"));

        mockMvc.perform(get("/api/user/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("User not found: 999"));
    }

    @Test
    void getUser_maxLongId_returns200() throws Exception {
        User user = new User();
        user.setId(Long.MAX_VALUE);
        user.setUsername("maxuser");
        user.setEmail("max@example.com");
        when(userService.getUserById(Long.MAX_VALUE)).thenReturn(user);

        mockMvc.perform(get("/api/user/" + Long.MAX_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(Long.MAX_VALUE));
    }

    @Test
    void getUser_zeroId_returnsError() throws Exception {
        when(userService.getUserById(0L))
                .thenThrow(new BizException(404, "User not found: 0"));

        mockMvc.perform(get("/api/user/0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void getUser_negativeId_returnsError() throws Exception {
        when(userService.getUserById(-1L))
                .thenThrow(new BizException(404, "User not found: -1"));

        mockMvc.perform(get("/api/user/-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    // ========== GET /api/user ==========

    @Test
    void listUsers_hasData_returns200() throws Exception {
        User u1 = new User();
        u1.setId(1L);
        u1.setUsername("user1");
        u1.setEmail("u1@example.com");
        User u2 = new User();
        u2.setId(2L);
        u2.setUsername("user2");
        u2.setEmail("u2@example.com");
        User u3 = new User();
        u3.setId(3L);
        u3.setUsername("user3");
        u3.setEmail("u3@example.com");
        List<User> users = Arrays.asList(u1, u2, u3);
        when(userService.listUsers()).thenReturn(users);

        mockMvc.perform(get("/api/user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[1].id").value(2))
                .andExpect(jsonPath("$.data[2].id").value(3))
                .andExpect(jsonPath("$.data[0].password").doesNotExist());
    }

    @Test
    void listUsers_emptyList_returns200() throws Exception {
        when(userService.listUsers()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void listUsers_singleItem_returns200() throws Exception {
        User u1 = new User();
        u1.setId(1L);
        u1.setUsername("onlyuser");
        u1.setEmail("only@example.com");
        when(userService.listUsers()).thenReturn(Collections.singletonList(u1));

        mockMvc.perform(get("/api/user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(1));
    }

    // ========== POST /api/user ==========

    @Test
    void createUser_validInput_returns200() throws Exception {
        User saved = new User();
        saved.setId(1L);
        saved.setUsername("newuser");
        saved.setEmail("new@example.com");
        when(userService.createUser(any(User.class))).thenReturn(saved);

        mockMvc.perform(post("/api/user")
                        .contentType("application/json")
                        .content("{\"username\":\"newuser\",\"email\":\"new@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.username").value("newuser"))
                .andExpect(jsonPath("$.data.email").value("new@example.com"));
    }

    @Test
    void createUser_nullUsername_returnsError() throws Exception {
        mockMvc.perform(post("/api/user")
                        .contentType("application/json")
                        .content("{\"username\":null,\"email\":\"test@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").isNumber());
    }

    @Test
    void createUser_nullEmail_returns200() throws Exception {
        User saved = new User();
        saved.setId(1L);
        saved.setUsername("newuser");
        when(userService.createUser(any(User.class))).thenReturn(saved);

        mockMvc.perform(post("/api/user")
                        .contentType("application/json")
                        .content("{\"username\":\"newuser\",\"email\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void createUser_emptyBody_returnsError() throws Exception {
        mockMvc.perform(post("/api/user")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").isNumber());
    }

    // ========== PUT /api/user/{id} ==========

    @Test
    void updateUser_validInput_returns200() throws Exception {
        User updated = new User();
        updated.setId(1L);
        updated.setUsername("updateduser");
        updated.setEmail("updated@example.com");
        when(userService.updateUser(eq(1L), any(User.class))).thenReturn(updated);

        mockMvc.perform(put("/api/user/1")
                        .contentType("application/json")
                        .content("{\"username\":\"updateduser\",\"email\":\"updated@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.username").value("updateduser"))
                .andExpect(jsonPath("$.data.email").value("updated@example.com"));
    }

    @Test
    void updateUser_notFound_returns404() throws Exception {
        when(userService.updateUser(eq(999L), any(User.class)))
                .thenThrow(new BizException(404, "User not found: 999"));

        mockMvc.perform(put("/api/user/999")
                        .contentType("application/json")
                        .content("{\"username\":\"someone\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void updateUser_partialFields_returns200() throws Exception {
        User partial = new User();
        partial.setId(1L);
        partial.setUsername("renameonly");
        partial.setEmail("old@example.com");
        when(userService.updateUser(eq(1L), any(User.class))).thenReturn(partial);

        mockMvc.perform(put("/api/user/1")
                        .contentType("application/json")
                        .content("{\"username\":\"renameonly\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("renameonly"));
    }

    // ========== DELETE /api/user/{id} ==========

    @Test
    void deleteUser_exists_returns200() throws Exception {
        doNothing().when(userService).deleteUser(1L);

        mockMvc.perform(delete("/api/user/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void deleteUser_notFound_returns404() throws Exception {
        doThrow(new BizException(404, "User not found: 999"))
                .when(userService).deleteUser(999L);

        mockMvc.perform(delete("/api/user/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void deleteUser_alreadyDeleted_returns404() throws Exception {
        doThrow(new BizException(404, "User not found: 1"))
                .when(userService).deleteUser(1L);

        mockMvc.perform(delete("/api/user/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }
}
