package com.darkness.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PythonAgentProperties 绑定测试：验证 python-agent.* 前缀的配置正确注入并带有默认值。
 * <p>
 * 测试切片通过 InnerConfig 显式启用 @EnableAutoConfiguration（从而注册
 * ConfigurationPropertiesBindingPostProcessor），并显式声明 @EnableConfigurationProperties
 * 绑定目标，避免加载完整应用上下文（数据源/Nacos/Dubbo）。生产环境由 AgentApplication 的
 * @SpringBootApplication 自动开启该后处理器，故生产类仅需 @Component + @ConfigurationProperties。
 */
@SpringBootTest(classes = PythonAgentPropertiesTest.InnerConfig.class)
@TestPropertySource(properties = {
        "python-agent.base-url=http://127.0.0.1:8100",
        "python-agent.api-key=test-key",
        "python-agent.connect-timeout=3000"
})
class PythonAgentPropertiesTest {

    @EnableAutoConfiguration
    @Configuration
    @EnableConfigurationProperties(PythonAgentProperties.class)
    static class InnerConfig {
    }

    @Autowired
    private PythonAgentProperties props;

    @Test
    void bindsProperties_andAppliesDefaults() {
        assertThat(props.getBaseUrl()).isEqualTo("http://127.0.0.1:8100");
        assertThat(props.getApiKey()).isEqualTo("test-key");
        assertThat(props.getConnectTimeout()).isEqualTo(3000);
        // 未显式设置时使用默认值
        assertThat(props.getReadTimeout()).isEqualTo(10000);
        assertThat(props.getStreamReadTimeout()).isEqualTo(300000);
    }
}
