package com.hkex.hyperojcodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.hkex.hyperojcodesandbox.model.ExecuteCodeRequest;
import com.hkex.hyperojcodesandbox.model.ExecuteCodeResponse;
import com.hkex.hyperojcodesandbox.model.ExecuteMessage;
import com.hkex.hyperojcodesandbox.model.JudgeInfo;
import com.hkex.hyperojcodesandbox.utils.ProcessUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
public abstract class JavaCodeSandBoxTemplate implements CodeSandBox{

    private static final String GLOBAL_CODE_FILE_PATH = "tmpCode";

    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    private static final long TIME_OUT = 5000L;


    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        List<String> inputList = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();

        File file = saveCode(code);

        ExecuteMessage compMessage = compCode(file);
        System.out.println(compMessage);

        List<ExecuteMessage> executeMessageList = runCode(inputList, file);

        ExecuteCodeResponse response = getResponse(executeMessageList);

        boolean b = clearCodeFile(file);
        if(!b){
            log.error("代码清理异常, userCodeFilePath:",file.getAbsolutePath());
        }

        return response;
    }

    /**
     * 保存代码
     * @param code
     * @return
     */
    public File saveCode(String code){
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

        return userCodeFile;
    }

    /**
     * 编译代码
     * @param userCodeFile
     * @return
     */
    public ExecuteMessage compCode(File userCodeFile){
        //编译代码
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
            //return getErrorResponse(e)
            throw new RuntimeException(e);
        }
    }

    /**
     * 运行代码
     * @param inputList
     * @param userCodeFile
     * @return
     */
    public List<ExecuteMessage> runCode(List<String> inputList, File userCodeFile){
        //执行代码（小心使用Scanner的程序）

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

    /**
     * 整理输出结果
     * @param executeMessageList
     * @return
     */
    public ExecuteCodeResponse getResponse(List<ExecuteMessage> executeMessageList){
        //整理输出结果
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        long maxTime = 0;
        List<String> outputList = new ArrayList<>();
        for (ExecuteMessage executeMessage : executeMessageList) {
            String errorMessage = executeMessage.getErrorMessage();
            if (StrUtil.isNotBlank(errorMessage)) {
                executeCodeResponse.setMessage(errorMessage);
                //代码有错误
                executeCodeResponse.setStatus(3);
                break;
            }
            if(executeMessage.getTime() != null){
                maxTime = Math.max(executeMessage.getTime(), maxTime);
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
        return executeCodeResponse;
    }

    /**
     * 清理代码
     * @param userCodeFile
     * @return
     */
    public boolean clearCodeFile(File userCodeFile){
        //文件清理
        if (userCodeFile.getParentFile() != null) {
            boolean del = FileUtil.del(userCodeFile.getParentFile().getAbsolutePath());
            System.out.println("删除" + (del ? "成功" : "失败"));
            return del;
        }
        return true;
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
