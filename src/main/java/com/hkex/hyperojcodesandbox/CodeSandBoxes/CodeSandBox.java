package com.hkex.hyperojcodesandbox.CodeSandBoxes;


import com.hkex.hyperojcodesandbox.model.ExecuteCodeRequest;
import com.hkex.hyperojcodesandbox.model.ExecuteCodeResponse;

/**
 * 所有代码沙箱的接口
 */
public interface CodeSandBox {

    /**
     * 执行代码
     *
     * @param executeCodeRequest 代码执行请求
     * @return 代码执行结构
     */
    ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest);

}
