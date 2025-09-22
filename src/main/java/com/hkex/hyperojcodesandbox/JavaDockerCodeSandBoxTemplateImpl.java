package com.hkex.hyperojcodesandbox;

import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.ArrayUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.hkex.hyperojcodesandbox.model.ExecuteCodeRequest;
import com.hkex.hyperojcodesandbox.model.ExecuteCodeResponse;
import com.hkex.hyperojcodesandbox.model.ExecuteMessage;
import com.hkex.hyperojcodesandbox.old.JavaOldDockerCodeSandBoxImpl;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * java沙箱Docker实现
 */
@Component
public class JavaDockerCodeSandBoxTemplateImpl extends JavaCodeSandBoxTemplate {
    public static final boolean IMAGE_EXIST = true;
    private static final long TIME_OUT = 5000L;

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
     * 运行代码
     * @param inputList
     * @param userCodeFile
     * @return
     */
    @Override
    public List<ExecuteMessage> runCode(List<String> inputList, File userCodeFile) {
        //创建容器，把文件赋值到容器内
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();

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
            ResultCallback<Statistics> statisticsResultCallback = new ResultCallback<Statistics>() {

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
            };
            statsCmd.exec(statisticsResultCallback);

            try {
                stopWatch.start();
                dockerClient
                        .execStartCmd(execId)
                        .exec(execStartResultCallback)
                        .awaitCompletion(TIME_OUT, TimeUnit.MILLISECONDS);
                stopWatch.stop();
                time = stopWatch.getLastTaskTimeMillis();
                MaxTime = Math.max(time, MaxTime);
            } catch (InterruptedException e ) {
                System.out.println("执行异常");
                throw new RuntimeException(e);
            }finally {
                statsCmd.close();
                //停止容器
                dockerClient.stopContainerCmd(containerId).exec();
                dockerClient.removeContainerCmd(containerId).withForce(true).exec();
            }
            executeMessage.setErrorMessage(errorMessage[0]);
            executeMessage.setMessage(message[0]);
            executeMessage.setTime(time);
            executeMessage.setMemory(maxMemory[0]);
            executeMessageList.add(executeMessage);

        }

        //整理输出结果
//        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
//        List<String> outputList = new ArrayList<>();
//        for (ExecuteMessage executeMessage : executeMessageList) {
//            String errorMessage = executeMessage.getErrorMessage();
//            if (StrUtil.isNotBlank(errorMessage)) {
//                executeCodeResponse.setMessage(errorMessage);
//                //代码有错误
//                executeCodeResponse.setStatus(3);
//                break;
//            }
//            outputList.add(executeMessage.getMessage());
//        }
//
//        if(outputList.size() == executeMessageList.size()){
//            executeCodeResponse.setStatus(1);
//        }

        return executeMessageList;
    }
}
