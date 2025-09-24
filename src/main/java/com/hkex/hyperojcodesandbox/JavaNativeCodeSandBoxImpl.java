package com.hkex.hyperojcodesandbox;

import com.hkex.hyperojcodesandbox.model.ExecuteCodeRequest;
import com.hkex.hyperojcodesandbox.model.ExecuteCodeResponse;
import com.hkex.hyperojcodesandbox.model.ExecuteMessage;
import com.hkex.hyperojcodesandbox.utils.ProcessUtils;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * java本地实现
 */
@Component
public class JavaNativeCodeSandBoxImpl extends JavaCodeSandBoxTemplate {
    /**
     * 执行超时时间（毫秒）
     */
    private static final long TIME_OUT = 5000L;

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
}
