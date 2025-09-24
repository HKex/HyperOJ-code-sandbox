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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
public abstract class JavaCodeSandBoxTemplate implements CodeSandBox{

    /**
     * 全局代码存储根目录（可通过setter修改）
     */
    private static final String GLOBAL_CODE_FILE_PATH = "tmpCode";

    /**
     * Java主类文件名
     */
    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    /**
     * 执行超时时间（毫秒）
     */
    private static final long TIME_OUT = 5000L;


    /**
     * 执行代码
     * @param executeCodeRequest 执行代码请求
     * @return 执行结果
     */
    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        List<String> inputList = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();

        File file = null;

        try {
            file = saveCode(code);
            log.info("代码保存成功，路径：{}", file.getAbsolutePath());

            ExecuteMessage compMessage = compCode(file);
            log.info("编译完成，结果：{}",compMessage.toString());

            if(compMessage.getExitValue() != 0){
                throw new RuntimeException("编译失败：" + compMessage.getErrorMessage());
            }

            //运行代码
            List<ExecuteMessage> executeMessageList = runCode(inputList, file);

            //整理输出结果
            ExecuteCodeResponse response = getResponse(executeMessageList);

            return response;
        } catch (Exception e) {
            return getErrorResponse(e);
        }finally {
            if (file != null ) {
                boolean b = clearCodeFile(file);
                if (!b) {
                    log.warn("临时文件清理失败，路径：{}",  file.getParentFile().getAbsolutePath());
                }
            }
        }
    }

    /**
     * 保存代码
     * @param code 用户代码
     * @return 保存代码的文件
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
        if (userCodeFile == null || !userCodeFile.exists()) {
            throw new RuntimeException("代码文件创建失败：" + userCodePath);
        }

        return userCodeFile;
    }

    /**
     * 编译代码
     * @param userCodeFile 用户代码文件
     * @return 编译结果
     */
    public ExecuteMessage compCode(File userCodeFile){
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
            //return getErrorResponse(e)
            throw new RuntimeException(e);
        }
    }

    /**
     * 运行代码
     * @param inputList 输入
     * @param userCodeFile 用户代码文件
     * @return 运行结果
     */
    public abstract List<ExecuteMessage> runCode(List<String> inputList, File userCodeFile);
        //执行代码（小心使用Scanner的程序）

//        List<ExecuteMessage> executeMessageList = new ArrayList<>();
//        for (String input : inputList) {
//            String runCmd = String.format("java -Xmx256m -Dfile.encoding=UTF-8 -cp %s Main %s", userCodeFile.getParentFile().getAbsolutePath(), input);
//            try {
//                Process runProcess = Runtime.getRuntime().exec(runCmd);
//                ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(runProcess, "运行",TIME_OUT);
//                executeMessageList.add(executeMessage);
//                System.out.println(executeMessage);
//            } catch (IOException e) {
//                throw new RuntimeException("运行异常",e);
//            }
//        }
//        return executeMessageList;


    /**
     * 整理输出结果
     * @param executeMessageList 运行结果
     * @return 整理后的结果
     */
    public ExecuteCodeResponse getResponse(List<ExecuteMessage> executeMessageList){
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

    /**
     * 清理代码
     * @param userCodeFile 代码文件
     * @return 是否清理成功
     */
    public boolean clearCodeFile(File userCodeFile){
        //文件清理
        if (userCodeFile.getParentFile() != null) {
            boolean del = FileUtil.del(userCodeFile.getParentFile().getAbsolutePath());
            log.info("删除" + (del ? "成功" : "失败"));
            return del;
        }
        return true;
    }

    /**
     * 错误处理
     * @param e 错误
     * @return 错误信息
     */
    private ExecuteCodeResponse getErrorResponse(Throwable e) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();

        // 表示代码沙箱错误
        executeCodeResponse.setStatus(2);
        executeCodeResponse.setOutputList(new ArrayList<>());
        executeCodeResponse.setMessage(e.getMessage());
        executeCodeResponse.setJudgeInfo(new JudgeInfo());
        return executeCodeResponse;
    }

}
