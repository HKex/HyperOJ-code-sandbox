package com.hkex.hyperojcodesandbox.CodeSandBoxes;

import com.hkex.hyperojcodesandbox.model.ExecuteCodeRequest;
import com.hkex.hyperojcodesandbox.model.ExecuteCodeResponse;
import com.hkex.hyperojcodesandbox.model.ExecuteMessage;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.List;

/**
 * 封装本地执行代码的通用逻辑的抽象代码沙箱
 */
@Slf4j
public abstract class AbstractNativeCodeSandBox extends AbstractCodeSandBox implements CodeSandBox {
    @Override
    public final ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {

        File file = saveCode(executeCodeRequest.getCode());

        try {
            log.info("代码保存成功，路径：{}", file.getAbsolutePath());

            ExecuteMessage compMessage = compCode(file);
            log.info("编译完成，结果：{}",compMessage.toString());

            if(compMessage.getExitValue() != 0){
                throw new RuntimeException("编译失败：" + compMessage.getErrorMessage());
            }

            //运行代码
            List<ExecuteMessage> executeMessageList = runCode(executeCodeRequest.getInputList(), file);

            //整理输出结果
            return getResponse(executeMessageList);
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
     * 编译代码
     * @param userCodeFile 用户代码文件
     * @return 编译结果
     */
    protected abstract ExecuteMessage compCode(File userCodeFile);

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
    protected abstract ExecuteCodeResponse getResponse(List<ExecuteMessage> executeMessageList);

    /**
     * 清理代码
     * @param userCodeFile 代码文件
     * @return 是否清理成功
     */
    protected abstract boolean clearCodeFile(File userCodeFile);
}
