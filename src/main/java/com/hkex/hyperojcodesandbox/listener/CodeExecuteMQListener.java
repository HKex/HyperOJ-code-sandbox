package com.hkex.hyperojcodesandbox.listener;

import com.hkex.hyperojcodesandbox.CodeSandBoxes.JavaDockerCodeSandBox;
import com.hkex.hyperojcodesandbox.config.RabbitMQConfig;
import com.hkex.hyperojcodesandbox.model.ExecuteCodeRequest;
import com.hkex.hyperojcodesandbox.model.ExecuteCodeResponse;
import com.hkex.hyperojcodesandbox.utils.MQUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
@Slf4j
public class CodeExecuteMQListener {
    @Resource
    private JavaDockerCodeSandBox codeSandBox;

    @Resource
    private MQUtils mqUtils;


    /**
     * 监听指定队列请求，执行请求后把结果发到队列中
     * @param request 代码执行请求
     */
    @RabbitListener(queues = RabbitMQConfig.CODE_EXECUTE_QUEUE)
    public void handleCodeExecuteRequest(ExecuteCodeRequest request) {
        if (request == null) {
            log.error("MQ传来的代码执行请求为空");
            return;
        }
        log.info("从MQ接收代码执行请求：{}", request);
        // 调用沙箱执行代码
        ExecuteCodeResponse response = codeSandBox.executeCode(request);
        //发送结果到队列
        mqUtils.sendExecuteResult(response);
    }

}
