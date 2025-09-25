package com.hkex.hyperojcodesandbox.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    //TODO 加入死信队列

    // 代码执行请求队列名称
    public static final String CODE_EXECUTE_QUEUE = "code.execute.producer";
    public static final String CODE_EXECUTE_RESULT_QUEUE = "code.execute.consumer";

    /**
     * 代码执行队列
     * @return 代码执行队列
     */
    @Bean
    public Queue codeExecuteQueue() {
        //  durable:是否持久化, exclusive:是否独占, autoDelete:是否自动删除
        return QueueBuilder.durable(CODE_EXECUTE_QUEUE).build();
    }

    /**
     * 队列结果队列
     * @return 队列结果队列
     */
    @Bean
    public Queue codeExecuteResultQueue() {
        return QueueBuilder.durable(CODE_EXECUTE_RESULT_QUEUE).build();
    }

    /**
     * 消息转换器
     * @return 消息转换器
     */
    @Bean
    public MessageConverter messageConverter() {
        // 使用Jackson将消息转为JSON
        return new Jackson2JsonMessageConverter();
    }

    /**
     * 创建交换机
     * @return 创建交换机
     */
    @Bean
    public TopicExchange codeEventExchange(){
        return new TopicExchange("code.event.exchange");
    }

    /**
     * 绑定队列和交换机
     * @param codeExecuteQueue 队列
     * @param codeEventExchange 交换机
     * @return 绑定
     */
    @Bean
    public Binding codeEventBiding(Queue codeExecuteQueue, TopicExchange codeEventExchange){
        return BindingBuilder.bind(codeExecuteQueue).to(codeEventExchange).with("code.execute.#");
    }
}
