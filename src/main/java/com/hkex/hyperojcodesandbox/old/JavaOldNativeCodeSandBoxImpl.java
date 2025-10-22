package com.hkex.hyperojcodesandbox.old;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.dfa.WordTree;
import com.hkex.hyperojcodesandbox.CodeSandBoxes.CodeSandBox;
import com.hkex.hyperojcodesandbox.model.ExecuteCodeRequest;
import com.hkex.hyperojcodesandbox.model.ExecuteCodeResponse;
import com.hkex.hyperojcodesandbox.model.ExecuteMessage;
import com.hkex.hyperojcodesandbox.model.JudgeInfo;
import com.hkex.hyperojcodesandbox.utils.ProcessUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class JavaOldNativeCodeSandBoxImpl implements CodeSandBox {

    private static final String GLOBAL_CODE_FILE_PATH = "tmpCode";

    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    private static final long TIME_OUT = 5000L;

    private static final List<String> blacklist = Arrays.asList("File","exec","Runtime", "Process", "ProcessBuilder");

    private static final WordTree BAN_CODE;

    static {
        BAN_CODE = new WordTree();
        BAN_CODE.addWords(blacklist);
    }

    public static void main(String[] args) {
        CodeSandBox codeSandBox = new JavaOldNativeCodeSandBoxImpl();
        ExecuteCodeRequest executeCodeRequest = ExecuteCodeRequest.builder()
                .inputList(Arrays.asList("1 2", "3 4"))
                .code("")
                .language("java")
                .build();
//        String code = ResourceUtil.readStr("code/SimpleExample/Main.java", StandardCharsets.UTF_8);
        String code = ResourceUtil.readStr("code/wrongCode/Memory/Main.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        ExecuteCodeResponse executeCodeResponse = codeSandBox.executeCode(executeCodeRequest);
        System.out.println(executeCodeResponse);
    }

    /**
     * 代码沙箱执行代码
     * @param executeCodeRequest
     * @return
     */
    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        List<String> inputList = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();

        //先校验代码
//        FoundWord foundWord = BAN_CODE.matchWord(code);
//        if (foundWord != null) {
//            return getErrorResponse(new RuntimeException("代码中有非法字符"));
//        }

        //代码保存为文件
        String userDir = System.getProperty("user.dir");
        String globalCodeFilePath = userDir + File.separator + GLOBAL_CODE_FILE_PATH;
        //判断全局代码目录是否存在
        if (!FileUtil.exist(globalCodeFilePath)) {
            FileUtil.mkdir(globalCodeFilePath);
        }

        //不同用户代码隔离
        String userCodeParentPath = globalCodeFilePath + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);

        //编译代码
        //也可以传入userCodePath(不建议)

        String compileCmd = String.format("javac -encoding utf-8 %s", userCodeFile.getAbsolutePath());
        try {
            Process Compileprocess = Runtime.getRuntime().exec(compileCmd);
            ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(Compileprocess, "编译",TIME_OUT);
            System.out.println(executeMessage);
        } catch (Exception e) {
            return getErrorResponse(e);
        }

        //执行代码（小心使用Scanner的程序）

        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        long maxTime = 0;
        for (String input : inputList) {
            String runCmd = String.format("java -Xmx256m -Dfile.encoding=UTF-8 -cp %s Main %s", userCodeParentPath, input);
            try {
                Process runProcess = Runtime.getRuntime().exec(runCmd);
                ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(runProcess, "运行",TIME_OUT);
                executeMessageList.add(executeMessage);
                System.out.println(executeMessage);
                if(executeMessage.getTime() != null){
                    maxTime = Math.max(executeMessage.getTime(), maxTime);
                }
            } catch (IOException e) {
                return getErrorResponse(e);
            }
        }

        //整理输出结果
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        List<String> outputList = new ArrayList<>();
        for (ExecuteMessage executeMessage : executeMessageList) {
            String errorMessage = executeMessage.getErrorMessage();
            if (StrUtil.isNotBlank(errorMessage)) {
                executeCodeResponse.setMessage(errorMessage);
                //代码有错误
                executeCodeResponse.setStatus(3);
                break;
            }
            outputList.add(executeMessage.getMessage());
        }

        if(outputList.size() == executeMessageList.size()){
            executeCodeResponse.setStatus(1);
        }

        executeCodeResponse.setOutputList(outputList);
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setTime(maxTime);
//        judgeInfo.setMemory();
        executeCodeResponse.setJudgeInfo(judgeInfo);

        //文件清理
        if (userCodeFile.getParentFile() != null) {
            boolean del = FileUtil.del(userCodeParentPath);
            System.out.println("删除" + (del ? "成功" : "失败"));
        }

        return executeCodeResponse;
    }

    /**
     * 错误处理
     * @param e
     * @return
     */
    private ExecuteCodeResponse getErrorResponse(Throwable e) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setOutputList(new ArrayList<>());
        executeCodeResponse.setMessage(e.getMessage());
        // 表示代码沙箱错误
        executeCodeResponse.setStatus(2);
        executeCodeResponse.setJudgeInfo(new JudgeInfo());
        return executeCodeResponse;
    }

}
