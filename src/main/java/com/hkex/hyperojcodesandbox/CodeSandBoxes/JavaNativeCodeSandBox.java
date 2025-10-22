package com.hkex.hyperojcodesandbox.CodeSandBoxes;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.hkex.hyperojcodesandbox.model.ExecuteCodeResponse;
import com.hkex.hyperojcodesandbox.model.ExecuteMessage;
import com.hkex.hyperojcodesandbox.model.JudgeInfo;
import com.hkex.hyperojcodesandbox.utils.ProcessUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.hkex.hyperojcodesandbox.constant.CodeSandBoxConstant.*;

/**
 * java本地实现
 */
@Component
@Slf4j
public class JavaNativeCodeSandBox extends AbstractNativeCodeSandBox implements CodeSandBox {
    /**
     * 执行超时时间（毫秒）
     */
    private static final long TIME_OUT = 5000L;

    @Override
    protected String getCodeFileName() {
        return GLOBAL_JAVA_CLASS_NAME;
    }

    @Override
    protected ExecuteMessage compCode(File userCodeFile) {
        //编译代码 javac [compileArgs] [文件路径]
        //也可以传入userCodePath(不建议)
        String compileCmd = String.format("javac -source 8 -target 8 -encoding utf-8 %s", userCodeFile.getAbsolutePath());
        try {
            Process Compileprocess = Runtime.getRuntime().exec(compileCmd);
            ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(Compileprocess, "编译",TIME_OUT);
            if(executeMessage.getExitValue()!=0){
                throw new RuntimeException("编译异常");
            }
            return executeMessage;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<ExecuteMessage> runCode(List<String> inputList, File userCodeFile) {
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        for (String input : inputList) {
            String runCmd = String.format("java -Xmx256m -Dfile.encoding=UTF-8 -cp %s Main %s", userCodeFile.getParentFile().getAbsolutePath(), input);
            try {
                Process runProcess = Runtime.getRuntime().exec(runCmd);
                ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(runProcess, "运行",TIME_OUT);
                executeMessageList.add(executeMessage);
                System.out.println(executeMessage);
            } catch (IOException e) {
                throw new RuntimeException("运行异常",e);
            }
        }
        return executeMessageList;
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

    @Override
    protected boolean clearCodeFile(File userCodeFile) {
        //文件清理
        if (userCodeFile.getParentFile() != null) {
            boolean del = FileUtil.del(userCodeFile.getParentFile().getAbsolutePath());
            log.info("删除" + (del ? "成功" : "失败"));
            return del;
        }
        return true;
    }
}
