package com.hkex.hyperojcodesandbox;

import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.ArrayUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.hkex.hyperojcodesandbox.model.ExecuteMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import javax.annotation.PostConstruct;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * java沙箱Docker实现
 */
@Component
@Slf4j
public class JavaDockerCodeSandBoxTemplateImpl extends JavaCodeSandBoxTemplate {
    private static final long TIME_OUT = 5000L;

    private static final long MEMORY_LIMIT = 100 * 1000 * 1000L;

    private DockerClient dockerClient;

    private static final String IMAGE = "openjdk:8-jdk-alpine3.9";

    private String seccompProfile;

    /**
     * 初始化Docker客户端和安全配置
     */
    @PostConstruct
    public void init() {
        // 初始化Docker客户端（单例复用）
        this.dockerClient = DockerClientBuilder.getInstance().build();
        // 加载并校验安全配置
        this.seccompProfile = ResourceUtil.readUtf8Str("profile.json");
        if (this.seccompProfile.isEmpty()) {
            throw new RuntimeException("安全配置文件profile.json为空");
        }
        log.info("Docker代码沙箱初始化完成，镜像:{}", IMAGE);
    }

    /**
     * 运行代码
     * @param inputList 输入
     * @param userCodeFile 用户代码文件
     * @return 执行结果
     */
    @Override
    public List<ExecuteMessage> runCode(List<String> inputList, File userCodeFile) {
        //创建容器，把文件赋值到容器内
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
        String containerId = null;

        try {
            pullImage();

            containerId = createContainer(userCodeParentPath);

            //启动容器执行代码

            dockerClient.startContainerCmd(containerId).exec();
            log.debug("容器{}启动成功", containerId);

            //开始编译执行代码
            return executeAllInputs(containerId, inputList);

        } catch (Exception e) {
            log.warn("代码执行异常", e);
            return null;
        }finally {
            if (containerId != null) {
                log.info("准备清理容器{}", containerId);
                cleanupContainer(containerId);
            }
        }
    }

    /**
     * 执行所有输入
     * @param containerId 容器id
     * @param inputList 输入
     * @return 执行结果
     */
    private List<ExecuteMessage> executeAllInputs(String containerId, List<String> inputList){
        //docker exec <Container>
        List<ExecuteMessage> resultList = new ArrayList<>(inputList.size());
        //监测内存
        //获取占用的内存
        try(StatsCmd statsCmd = dockerClient.statsCmd(containerId)){
            AtomicLong maxMemory = new AtomicLong(0);
            statsCmd.exec(new ResultCallback<Statistics>() {
                @Override
                public void onNext(Statistics stats) {
                    long memoryUsage = stats.getMemoryStats().getUsage();
                    System.out.println("内存占用：" + memoryUsage);
                    if (memoryUsage > maxMemory.get()) {
                        maxMemory.set(memoryUsage);
                    }
                }
                @Override
                public void close() throws IOException {}
                @Override
                public void onStart(Closeable closeable) {}
                @Override
                public void onError(Throwable throwable) {
                    log.warn("容器{}内存监控异常", containerId, throwable);
                }
                @Override
                public void onComplete() {}
            });
            for(String input : inputList){
                ExecuteMessage message = executeSingleInput(containerId, input, maxMemory);
                resultList.add(message);

            }
        }catch (Exception e){
            throw new RuntimeException("执行输入用例失败", e);
        }
        return resultList;
    }

    /**
     * 执行单个输入
     * @param containerId 容器id
     * @param input 输入
     * @param maxMemory 最大内存
     * @return 执行结果
     */
    private ExecuteMessage executeSingleInput(String containerId, String input, AtomicLong maxMemory) {
        ExecuteMessage message = new ExecuteMessage();
        StopWatch stopWatch = new StopWatch();
        String[] inputArgs = input.split(" ");
        String[] cmdArray = ArrayUtil.append(new String[]{"java","-cp","/code","Main"}, inputArgs);
        try{
            //创建执行命令
            ExecCreateCmdResponse cmdResponse = dockerClient.execCreateCmd(containerId)
                    .withCmd(cmdArray)
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .exec();

            String resId = cmdResponse.getId();
            if (resId == null || resId.isEmpty()) {
                throw new RuntimeException("创建执行命令失败，execId为空");
            }

            log.info("执行以下指令:" + cmdResponse);

            // 执行结果回调
            // 判断是否超时
            AtomicBoolean isTimeout = new AtomicBoolean(true);
            StringBuilder output = new StringBuilder();
            StringBuilder error = new StringBuilder();

            ExecStartResultCallback execCallback = new ExecStartResultCallback(){
                @Override
                public void onComplete() {
                    isTimeout.set(false); // 执行完成，未超时
                    super.onComplete();
                }

                @Override
                public void onNext(Frame frame) {
                    if (frame.getStreamType() == StreamType.STDERR) {
                        error.append(new String(frame.getPayload(), StandardCharsets.UTF_8));
                    } else {
                        output.append(new String(frame.getPayload(), StandardCharsets.UTF_8));
                    }
                    super.onNext(frame);
                }
            };

            stopWatch.start();
            dockerClient
                    .execStartCmd(resId)
                    .exec(execCallback)
                    .awaitCompletion(TIME_OUT, TimeUnit.MILLISECONDS);
            stopWatch.stop();

            // 填充结果
            message.setTime(stopWatch.getLastTaskTimeMillis());
            message.setMemory(maxMemory.get());
            message.setMessage(output.toString().trim());
            message.setErrorMessage(error.toString().trim());

            // 处理超时情况
            if (isTimeout.get()) {
                message.setErrorMessage("执行超时（超过" + TIME_OUT + "ms）");
                log.warn("容器{}执行超时", containerId);
            }


        } catch (InterruptedException e ) {
            message.setErrorMessage("执行被中断: " + e.getMessage());
            log.error("容器{}执行中断", containerId, e);
        }catch (Exception e) {
            message.setErrorMessage("执行失败: " + e.getMessage());
            log.error("容器{}执行命令异常", containerId, e);
        }

        return message;
    }

    /**
     * 创建容器
     * @param userCodeParentPath 用户代码的父目录
     * @return 容器ID
     */
    private String createContainer(String userCodeParentPath) {
        //创建容器
        try {
            HostConfig hostConfig = new HostConfig();
            //挂载
            hostConfig.setBinds(new Bind(userCodeParentPath, new Volume("/code")));
            //内存限制
            hostConfig.withMemory(MEMORY_LIMIT);
            //内存限制交换区
            hostConfig.withMemorySwap(0L);
            //创建安全策略
            hostConfig.withSecurityOpts(Arrays.asList("seccomp=" + seccompProfile));
            //cpu限制
            hostConfig.withCpuCount(1L);
            //启动容器
            CreateContainerCmd containerCmd = dockerClient.createContainerCmd(IMAGE)
                    .withHostConfig(hostConfig)
                    .withNetworkDisabled(true)
                    .withReadonlyRootfs(true)
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withTty(true);

            CreateContainerResponse containerResponse = containerCmd.exec();
            String containerId = containerResponse.getId();
            log.info("创建容器成功，ID:{}", containerId);
            return containerId;
        }catch (Exception e) {
            throw new RuntimeException("创建容器失败", e);
        }
    }

    /**
     * 拉取镜像
     */
    private void pullImage() {
        try{
            ListImagesCmd listImagesCmd = dockerClient.listImagesCmd().withImageNameFilter(IMAGE);
            if (!listImagesCmd.exec().isEmpty()) {
                log.info("镜像{}已存在，无需拉取", IMAGE);
                return;
            }
            //拉取镜像
            PullImageCmd pullImageCmd = dockerClient.pullImageCmd(IMAGE);
            PullImageResultCallback pullImageResultCallback = new PullImageResultCallback(){
                @Override
                public void onNext(PullResponseItem item) {
                    log.debug("镜像下载" + item.getStatus());
                    super.onNext(item);
                }
            };
            pullImageCmd
                    .exec(pullImageResultCallback)
                    .awaitCompletion();
            log.info("下载完成");
        }catch (InterruptedException e) {
            log.error("拉取镜像异常");
            throw new RuntimeException(e);
        }
    }

    /**
     * 清理容器
     * @param containerId 容器ID
     */
    private void cleanupContainer(String containerId) {
        try {
            // 停止容器
            dockerClient.stopContainerCmd(containerId)
                    .withTimeout(3) // 3秒超时
                    .exec();
            log.debug("容器{}已停止", containerId);
        } catch (NotFoundException e) {
            log.warn("容器{}不存在，无需停止", containerId);
        } catch (Exception e) {
            log.error("停止容器{}失败", containerId, e);
        }

        try {
            // 删除容器
            dockerClient.removeContainerCmd(containerId)
                    .withForce(true)
                    .exec();
            log.debug("容器{}已删除", containerId);
        } catch (NotFoundException e) {
            log.warn("容器{}不存在，无需删除", containerId);
        } catch (Exception e) {
            log.error("删除容器{}失败", containerId, e);
        }
    }

}
