package com.hkex.hyperojcodesandbox.controller;

import com.hkex.hyperojcodesandbox.JavaDockerCodeSandBoxTemplateImpl;
import com.hkex.hyperojcodesandbox.model.ExecuteCodeRequest;
import com.hkex.hyperojcodesandbox.model.ExecuteCodeResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RestController("/")
public class MainController {
    //鉴权请求头&密钥
    private static final String AUTH_REQUEST_HEADER = "Auth";

    private static final String AUTH_REQUEST_SECRET = "itsmygo";

    @Resource
    private JavaDockerCodeSandBoxTemplateImpl javaCodeSandBox;

    @GetMapping("/health")
    public String health() {
        return "OK";
    }

    /**
     * 执行代码接口
     * @param executeCodeRequest
     * @return
     */
    @PostMapping("/executeCode")
    ExecuteCodeResponse executeCode(@RequestBody ExecuteCodeRequest executeCodeRequest, HttpServletRequest request,
                                    HttpServletResponse response) {
        String authHeader = request.getHeader(AUTH_REQUEST_HEADER);
        if(!authHeader.equals(AUTH_REQUEST_SECRET)){
            response.setStatus(403);
            return null;
        }
        if(executeCodeRequest == null){
            throw new RuntimeException("参数为空");
        }
        return javaCodeSandBox.executeCode(executeCodeRequest);
    }
}
