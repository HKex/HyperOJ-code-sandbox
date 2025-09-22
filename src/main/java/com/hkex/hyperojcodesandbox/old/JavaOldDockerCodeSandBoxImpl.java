package com.hkex.hyperojcodesandbox.old;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.hkex.hyperojcodesandbox.CodeSandBox;
import com.hkex.hyperojcodesandbox.model.ExecuteCodeRequest;
import com.hkex.hyperojcodesandbox.model.ExecuteCodeResponse;
import com.hkex.hyperojcodesandbox.model.ExecuteMessage;
import com.hkex.hyperojcodesandbox.model.JudgeInfo;
import com.hkex.hyperojcodesandbox.utils.ProcessUtils;
import org.springframework.util.StopWatch;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class JavaOldDockerCodeSandBoxImpl implements CodeSandBox {

    private static final String GLOBAL_CODE_FILE_PATH = "tmpCode";

    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    private static final long TIME_OUT = 5000L;

    public static final boolean IMAGE_EXIST = true;


    public static void main(String[] args) {
        CodeSandBox codeSandBox = new JavaOldDockerCodeSandBoxImpl();
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

    /**
     * 代码沙箱执行代码
     * @param executeCodeRequest
     * @return
     */
    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        List<String> inputList = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();

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

        String compileCmd = String.format("javac -source 1.8 -target 1.8 -encoding utf-8 %s", userCodeFile.getAbsolutePath());
        try {
            Process Compileprocess = Runtime.getRuntime().exec(compileCmd);
            ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(Compileprocess, "编译",TIME_OUT);
            System.out.println(executeMessage);
        } catch (Exception e) {
            return getErrorResponse(e);
        }

        //创建容器，把文件赋值到容器内
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();


        //拉取镜像
        String image = "openjdk:8-jdk-alpine3.9";
        if(!IMAGE_EXIST) {
            PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
            PullImageResultCallback pullImageResultCallback = new PullImageResultCallback(){
                @Override
                public void onNext(PullResponseItem item) {
                    System.out.println("镜像下载" + item.getStatus());
                    super.onNext(item);
                }
            };
            try {
                pullImageCmd
                        .exec(pullImageResultCallback)
                        .awaitCompletion();
            }catch (InterruptedException e) {
                System.out.println("拉取镜像异常");
                throw new RuntimeException(e);
            }
        }
        System.out.println("下载完成");

        //创建容器
        String profileString = ResourceUtil.readUtf8Str("profile.json");
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);
        HostConfig hostConfig = new HostConfig();
        hostConfig.setBinds(new Bind(userCodeParentPath,new Volume("/code")));
        hostConfig.withMemory(100 * 1000 * 1000L);
        hostConfig.withMemorySwap(0L);
        hostConfig.withSecurityOpts(Arrays.asList("seccomp=" + profileString));
        hostConfig.withCpuCount(1L);
        CreateContainerResponse containerResponse = containerCmd
                .withHostConfig(hostConfig)
                .withNetworkDisabled(true)
                .withReadonlyRootfs(true)
                .withAttachStdin(true)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withTty(true)
                .exec();

        System.out.println(containerResponse);
        String containerId = containerResponse.getId();

        //启动容器执行代码
        dockerClient.startContainerCmd(containerId).exec();

        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        Long MaxTime = 0L;
        //docker exec <Container>
        for(String input : inputList){
            StopWatch stopWatch = new StopWatch();
            String[] strings = input.split(" ");
            String[] cmdArray = ArrayUtil.append(new String[]{"java","-cp","/code","Main"},strings);
            ExecCreateCmdResponse cmdResponse = dockerClient.execCreateCmd(containerId)
                    .withCmd(cmdArray)
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .exec();
            System.out.println("执行以下指令:" + cmdResponse);



            ExecuteMessage executeMessage = new ExecuteMessage();
            // 判断是否超时
            final boolean[] timeout = {true};
            String execId = cmdResponse.getId();
            final String[] message = {null};
            final String[] errorMessage = {null};
            long time = 0;
            //TODO 防止ID空指针
            ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback(){
                @Override
                public void onComplete() {
                    // 如果执行完成，则表示没超时
                    timeout[0] = false;
                    super.onComplete();
                }

                @Override
                public void onNext(Frame item) {

                    StreamType itemStreamType = item.getStreamType();
                    if(StreamType.STDERR == itemStreamType){
                        errorMessage[0] = new String(item.getPayload());
                        System.out.println("错误结果:" + errorMessage[0]);
                    } else {
                        message[0] = new String(item.getPayload());
                        System.out.println("执行结果:" + message[0]);
                    }
                    super.onNext(item);
                }
            };
            final long[] maxMemory = {0L};

            // 获取占用的内存
            StatsCmd statsCmd = dockerClient.statsCmd(containerId);
            ResultCallback<Statistics> statisticsResultCallback = statsCmd.exec(new ResultCallback<Statistics>() {

                @Override
                public void onNext(Statistics statistics) {
                    System.out.println("内存占用：" + statistics.getMemoryStats().getUsage());
                    maxMemory[0] = Math.max(statistics.getMemoryStats().getUsage(), maxMemory[0]);
                }

                @Override
                public void close() throws IOException {

                }

                @Override
                public void onStart(Closeable closeable) {

                }

                @Override
                public void onError(Throwable throwable) {

                }

                @Override
                public void onComplete() {

                }
            });
            statsCmd.exec(statisticsResultCallback);

            try {
                stopWatch.start();
                dockerClient
                        .execStartCmd(execId)
                        .exec(execStartResultCallback)
                        .awaitCompletion(TIME_OUT, TimeUnit.MILLISECONDS);
                stopWatch.stop();
                statsCmd.close();
                time = stopWatch.getLastTaskTimeMillis();
                MaxTime = Math.max(time, MaxTime);
            } catch (InterruptedException e) {
                System.out.println("执行异常");
                throw new RuntimeException(e);
            }
            executeMessage.setErrorMessage(errorMessage[0]);
            executeMessage.setMessage(message[0]);
            executeMessage.setTime(time);
            executeMessage.setMemory(maxMemory[0]);
            executeMessageList.add(executeMessage);

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
        judgeInfo.setTime(MaxTime);
//        judgeInfo.setMemory();
        executeCodeResponse.setJudgeInfo(judgeInfo);

        //文件清理
        if (userCodeFile.getParentFile() != null) {
            boolean del = FileUtil.del(userCodeParentPath);
            System.out.println("删除" + (del ? "成功" : "失败"));
        }


        return new ExecuteCodeResponse();
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
