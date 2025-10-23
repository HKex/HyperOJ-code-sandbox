package com.hkex.hyperojcodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import com.hkex.hyperojcodesandbox.CodeSandBoxes.JavaDockerCodeSandBox;
import com.hkex.hyperojcodesandbox.CodeSandBoxes.JavaNativeCodeSandBox;
import com.hkex.hyperojcodesandbox.model.ExecuteCodeRequest;
import com.hkex.hyperojcodesandbox.model.ExecuteCodeResponse;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class JavaDockerCodeSandBoxIT {

    @Autowired
    private JavaDockerCodeSandBox codeSandBox;

    @Autowired
    private JavaNativeCodeSandBox nCodeSandBox;

    private String readTrueCodeFromFile(String filename) {
        String codePath = "code/SimpleExample/" + filename;
        return ResourceUtil.readUtf8Str(codePath);
    }

    private String readWrongCodeFromFile(String filename) {
        String codePath = "code/wrongExample/" + filename;
        return ResourceUtil.readUtf8Str(codePath);
    }

    // 测试正常代码
    @Test
    @Order(1)
    void testValidCode() {
        String code = readTrueCodeFromFile("Main.java");

        List<String> inputs = new ArrayList<>(Arrays.asList("1 2"));
        ExecuteCodeRequest request = ExecuteCodeRequest.builder()
                .code(code)
                .language("java")
                .inputList(inputs)
                .build();

        ExecuteCodeResponse response = nCodeSandBox.executeCode(request);


        assertEquals(1, response.getStatus()); // 成功
        assertEquals(1, response.getOutputList().size());

        System.out.println(response);
    }

//    // 测试编译错误
//    @Test
//    @Order(2)
//    void testCompileError() {
//        String code =
//
//        ExecuteCodeResponse response = codeSandBox.executeCode(code, List.of());
//
//        assertEquals(3, response.getStatus()); // 错误状态
//        assertTrue(response.getMessage().contains("cannot find symbol"));
//    }
//
//    // 测试运行时异常
//    @Test
//    @Order(3)
//    void testRuntimeError() {
//        String code = """
//            public class Main {
//                public static void main(String[] args) {
//                    System.out.println(1 / 0);
//                }
//            }
//            """;
//
//        ExecuteCodeResponse response = codeSandBox.executeCode(code, List.of());
//
//        assertEquals(3, response.getStatus());
//        assertTrue(response.getMessage().contains("ArithmeticException"));
//    }
//
//    // 测试超时（需确保你的 executeCommandWithInput 支持超时！）
//    @Test
//    @Order(4)
//    @Timeout(value = 10) // JUnit 超时兜底
//    void testInfiniteLoop() {
//        String code = """
//            public class Main {
//                public static void main(String[] args) {
//                    while (true) {
//                        // do nothing
//                    }
//                }
//            }
//            """;
//
//        ExecuteCodeResponse response = codeSandBox.executeCode(code, List.of());
//
//        // 期望：被中断，状态为错误，且包含超时信息
//        assertEquals(3, response.getStatus());
//        assertTrue(
//                response.getMessage().toLowerCase().contains("timeout") ||
//                        response.getMessage().contains("interrupted"),
//                "应包含超时提示"
//        );
//    }
//
//    // 可选：测试内存超限（较难精确控制，可跳过）
//    @Test
//    @Disabled("依赖宿主机内存，CI 环境可能不稳定")
//    void testMemoryOverflow() {
//        String code = """
//            public class Main {
//                public static void main(String[] args) {
//                    byte[][] arr = new byte[10000][];
//                    for (int i = 0; i < 10000; i++) {
//                        arr[i] = new byte[1024 * 1024]; // 1MB * 10000 = 10GB
//                    }
//                }
//            }
//            """;
//
//        ExecuteCodeResponse response = codeSandBox.executeCode(code, List.of());
//        assertEquals(3, response.getStatus());
//        // 可检查是否 OOMKilled（需底层支持）
//    }
//
//    // 清理：可选，Spring 会自动管理 Bean 生命周期
//    @AfterAll
//    static void cleanup() {
//        // 可手动清理残留容器（如果父类没做好）
//        // 但通常不需要，因为每个测试用唯一容器名
//    }
}
