package com.hkex.hyperojcodesandbox.utils;

import com.hkex.hyperojcodesandbox.config.RabbitMQConfig;
import com.hkex.hyperojcodesandbox.model.ExecuteCodeResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * MQ工具类
 */
@Component
@Slf4j
public class MQUtils {
    private final RabbitTemplate rabbitTemplate;

    public MQUtils(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 发送执行结果到结果队列
     * @param response 执行结果
     */
    public void sendExecuteResult(ExecuteCodeResponse response) {
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.CODE_EXECUTE_RESULT_QUEUE, response);
            log.info("成功发送代码执行请求，请求内容：{}", response);
        }catch (Exception e){
            log.error("发送消息失败，请求内容：{}，错误信息：{}",
                    response, e.getMessage(), e);
        }
    }
}
