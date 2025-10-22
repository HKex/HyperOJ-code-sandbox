package com.hkex.hyperojcodesandbox.CodeSandBoxes;

import cn.hutool.core.io.FileUtil;
import com.hkex.hyperojcodesandbox.model.ExecuteCodeResponse;
import com.hkex.hyperojcodesandbox.model.JudgeInfo;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.UUID;

import static com.hkex.hyperojcodesandbox.constant.CodeSandBoxConstant.GLOBAL_CODE_FILE_PATH;

public abstract class AbstractCodeSandBox implements CodeSandBox {

    protected abstract String getCodeFileName();

    /**
     * 保存代码
     * @param code 用户代码
     * @return 保存代码的文件
     */
    public File saveCode(String code) {
        String userDir = System.getProperty("user.dir");
        String globalCodeFilePath = userDir + File.separator + GLOBAL_CODE_FILE_PATH;
        //判断全局代码目录是否存在
        if (!FileUtil.exist(globalCodeFilePath)) {
            FileUtil.mkdir(globalCodeFilePath);
        }

        //不同用户代码隔离
        String userCodeParentPath = globalCodeFilePath + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParentPath + File.separator + getCodeFileName();
        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);
        if (userCodeFile == null || !userCodeFile.exists()) {
            throw new RuntimeException("代码文件创建失败：" + userCodePath);
        }

        return userCodeFile;
    }

    /**
     * 错误处理
     * @param e 错误
     * @return 错误信息
     */
    protected ExecuteCodeResponse getErrorResponse(Throwable e) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();

        // 表示代码沙箱错误
        executeCodeResponse.setStatus(2);
        executeCodeResponse.setOutputList(new ArrayList<>());
        executeCodeResponse.setMessage(e.getMessage());
        executeCodeResponse.setJudgeInfo(new JudgeInfo());
        return executeCodeResponse;
    }
}
