package com.hkex.hyperojcodesandbox;

import cn.hutool.core.io.resource.ResourceUtil;
import com.hkex.hyperojcodesandbox.model.ExecuteCodeRequest;
import com.hkex.hyperojcodesandbox.model.ExecuteCodeResponse;
import com.hkex.hyperojcodesandbox.old.JavaOldDockerCodeSandBoxImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@SpringBootTest
class HyperOjCodeSandboxApplicationTests {

    @Resource
    private JavaDockerCodeSandBoxTemplateImpl codeSandBox;



    @Test
    void contextLoads() {
    }

    @Test
    void testSandBox(){
        Class<? extends CodeSandBox> sandBoxClass = codeSandBox.getClass();
        System.out.println(sandBoxClass.getName());
    }

    @Test
    void testCode() {
        ExecuteCodeRequest executeCodeRequest = ExecuteCodeRequest.builder()
                .inputList(Arrays.asList("1 2", "3 4"))
                .code("")
                .language("java")
                .build();
        String code = ResourceUtil.readStr("code/SimpleExample/Main.java", StandardCharsets.UTF_8);
//        String code = ResourceUtil.readStr("code/wrongCode/Memory/Main.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        ExecuteCodeResponse executeCodeResponse = codeSandBox.executeCode(executeCodeRequest);
        System.out.println(executeCodeResponse);
    }

}
