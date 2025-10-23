package com.hkex.hyperojcodesandbox.CodeSandBoxes;

import cn.hutool.core.util.StrUtil;
import com.hkex.hyperojcodesandbox.model.ExecuteCodeResponse;
import com.hkex.hyperojcodesandbox.model.ExecuteMessage;
import com.hkex.hyperojcodesandbox.model.JudgeInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

import static com.hkex.hyperojcodesandbox.constant.CodeSandBoxConstant.*;

/**
 * java沙箱Docker实现
 */
@Component
@Slf4j
public class JavaDockerCodeSandBox extends AbstractDockerCodeSandBox {

    @PostConstruct
    public void init() {
        super.init(); // 调用父类初始化方法
    }

    @Override
    protected String getCodeFileName() {
        return GLOBAL_JAVA_CLASS_NAME;
    }

    @Override
    protected String getImage() {
        return JAVA_IMAGE;
    }

    @Override
    protected void compileInContainer(String containerId) {
        try {
            // 编译Java代码
            String[] compileCmd = {"javac", "-source", "8", "-target", "8", "-encoding", "utf-8", "/code/Main.java"};
            executeCommand(containerId, compileCmd);

            
            log.info("代码编译成功，容器ID: {}", containerId);
        } catch (Exception e) {
            log.error("编译过程中发生异常，容器ID: {}", containerId, e);
            throw e; // 重新抛出异常，让父类处理容器清理
        }
    }

    @Override
    protected List<ExecuteMessage> executeInputs(String containerId, List<String> inputList) {
        List<ExecuteMessage> results = new ArrayList<>();
        try {
            if(inputList == null || inputList.isEmpty()){
                String[] cmd = {"java", "-cp", "/code", "Main"};
                log.info("输入为空，容器ID: {}",containerId);
                ExecuteMessage message = super.executeCommandWithInput(containerId, cmd, "");
                if (message.getExitValue() != 0) {
                    log.warn("输入执行失败，容器ID: {}, 错误信息: {}", containerId, message.getErrorMessage());
                }

                results.add(message);
            }
            else {
                for (int i = 0; i < inputList.size(); i++) {
                    String input = inputList.get(i);
                    log.debug("执行第{}个输入，容器ID: {}, 输入内容: {}", i + 1, containerId, input);

                    // 使用标准输入而不是命令行参数
                    String[] cmd = {"java", "-cp", "/code", "Main"};
                    ExecuteMessage message = super.executeCommandWithInput(containerId, cmd, input);

                    if (message.getExitValue() != 0) {
                        log.warn("第{}个输入执行失败，容器ID: {}, 错误信息: {}", i + 1, containerId, message.getErrorMessage());
                    }

                    results.add(message);
                }
            }
            log.info("所有输入执行完成，容器ID: {}", containerId);
        } catch (Exception e) {
            log.error("执行输入过程中发生异常，容器ID: {}", containerId, e);
            // 如果发生异常，创建一个错误消息
            ExecuteMessage errorMessage = new ExecuteMessage();
            errorMessage.setErrorMessage("执行过程中发生异常: " + e.getMessage());
            errorMessage.setExitValue(1);
            results.add(errorMessage);
        }
        return results;
    }

    @Override
    protected ExecuteCodeResponse getResponse(List<ExecuteMessage> executeMessageList) {
        //整理输出结果
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        List<String> outputList = new ArrayList<>();

        long maxTime = 0;
        long maxMemory = 0;
        for (ExecuteMessage executeMessage : executeMessageList) {
            if (StrUtil.isNotBlank(executeMessage.getErrorMessage())) {
                executeCodeResponse.setMessage(executeMessage.getErrorMessage());
                log.warn("输出信息有错误，错误信息: {}", executeMessage.getErrorMessage());
                //代码有错误
                executeCodeResponse.setStatus(3);
                break;
            }
            outputList.add(executeMessage.getMessage());

            //整理内存时间用量
            if(executeMessage.getTime() != null){
                maxTime = Math.max(executeMessage.getTime(), maxTime);
            }
            if(executeMessage.getMemory() != null){
                maxMemory = Math.max(executeMessage.getMemory(), maxMemory);
            }
        }

        //判断是否运行成功
        if(outputList.size() == executeMessageList.size()){
            executeCodeResponse.setStatus(1);
        }

        //封装判题信息
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setTime(maxTime);
        judgeInfo.setMemory(maxMemory);
        executeCodeResponse.setOutputList(outputList);
        executeCodeResponse.setJudgeInfo(judgeInfo);

        return executeCodeResponse;
    }

}
