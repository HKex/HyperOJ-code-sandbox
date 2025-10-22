package com.hkex.hyperojcodesandbox.constant;

public class CodeSandBoxConstant {
    /**
     * 全局代码存储根目录（可通过setter修改）
     */
    public static final String GLOBAL_CODE_FILE_PATH = "tmpCode";

    /**
     * Java主类文件名
     */
    public static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    /**
     * 执行超时时间（毫秒）
     */
    public static final long TIME_OUT = 5000L;

    /**
     * 内存限制（字节）
     */
    public static final long MEMORY_LIMIT = 100 * 1000 * 1000L;

    /**
     * Java运行环境镜像名称
     */
    public static final String JAVA_IMAGE = "openjdk:8-jdk-alpine3.9";
}
