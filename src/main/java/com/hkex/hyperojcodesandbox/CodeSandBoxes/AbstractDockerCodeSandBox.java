package com.hkex.hyperojcodesandbox.CodeSandBoxes;

import cn.hutool.core.io.resource.ResourceUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.hkex.hyperojcodesandbox.model.ExecuteCodeRequest;
import com.hkex.hyperojcodesandbox.model.ExecuteCodeResponse;
import com.hkex.hyperojcodesandbox.model.ExecuteMessage;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.PostConstruct;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.hkex.hyperojcodesandbox.constant.CodeSandBoxConstant.*;

/**
 * 抽象docker代码沙箱
 */
@Slf4j
public abstract class AbstractDockerCodeSandBox extends AbstractCodeSandBox implements CodeSandBox {

    protected DockerClient dockerClient;

    protected String seccompProfile;

    /**
     * 初始化Docker客户端和安全配置
     */
    @PostConstruct
    public void init() {
        dockerClient = DockerClientBuilder.getInstance().build();

        // 加载并校验安全配置
        this.seccompProfile = ResourceUtil.readUtf8Str("profile.json");
        if (this.seccompProfile.isEmpty()) {
            throw new RuntimeException("安全配置文件profile.json为空");
        }
        log.info("Docker代码沙箱初始化完成");
    }

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {

        File file = saveCode(executeCodeRequest.getCode());

        //创建容器，把文件赋值到容器内
        String userCodeParentPath = file.getParentFile().getAbsolutePath();
        String containerId = null;

        try {
            pullImage(getImage());

            containerId = createContainer(userCodeParentPath);

            //启动容器执行代码

            dockerClient.startContainerCmd(containerId).exec();
            log.debug("容器{}启动成功", containerId);

            List<String> inputList = executeCodeRequest.getInputList();

            //开始编译代码
            compileInContainer(containerId);

            //开始执行代码
            String[] cmd = {"java", "-cp", "/code", "Main"};
            List<ExecuteMessage> executeMessageList = executeInputs(containerId, inputList);

            //整理输出结果
            return getResponse(executeMessageList);

        } catch (Exception e) {
            return getErrorResponse(e);
        }finally {
            if (containerId != null) {
                log.info("准备清理容器{}", containerId);
                cleanupContainer(containerId);
            }
        }
    }

    /**
     * 拉取镜像
     */
    protected void pullImage(String image) {
        try{
            ListImagesCmd listImagesCmd = dockerClient.listImagesCmd().withImageNameFilter(image);
            if (!listImagesCmd.exec().isEmpty()) {
                log.info("镜像{}已存在，无需拉取", image);
                return;
            }
            //拉取镜像
            PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
            PullImageResultCallback pullImageResultCallback = new PullImageResultCallback(){
                @Override
                public void onNext(PullResponseItem item) {
                    log.debug("镜像下载：{}" , item.getStatus());
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
     * 创建容器
     * @param userCodeParentPath 用户代码的父目录
     * @return 容器ID
     */
    protected String createContainer(String userCodeParentPath) {
        //创建容器
        try {
            HostConfig hostConfig = new HostConfig();
            //挂载
            hostConfig.setBinds(new Bind(userCodeParentPath, new Volume("/code")));
            //内存限制
            hostConfig.withMemory(MEMORY_LIMIT);
            //内存限制交换区
            hostConfig.withMemorySwap(0L);
            //cpu限制
            hostConfig.withCpuCount(1L);
            //创建安全策略
            hostConfig.withSecurityOpts(Arrays.asList("seccomp=" + seccompProfile));
            hostConfig.withReadonlyRootfs(true);
            //启动容器
            CreateContainerCmd containerCmd = dockerClient.createContainerCmd(JAVA_IMAGE)
                    .withHostConfig(hostConfig)
                    .withNetworkDisabled(true)
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

    /**
     * 获取执行结果
     * @param executeMessageList 执行结果
     * @return 执行结果
     */
    protected abstract ExecuteCodeResponse getResponse(List<ExecuteMessage> executeMessageList);


    /**
     * 获取对应语言镜像
     * @return 镜像名称
     */
    protected abstract String getImage();

    /**
     * 编译代码
     * @param containerId 容器id
     */
    protected abstract void compileInContainer(String containerId);

    /**
     * 执行所有输入
     * @param containerId 容器id
     * @param inputList 输入
     * @return 执行结果
     */
    protected abstract List<ExecuteMessage> executeInputs(String containerId, List<String> inputList);

    /**
     * 执行docker命令
     * @param containerId 容器id
     * @param cmd 命令
     */
    protected void executeCommand(String containerId, String[] cmd) {
        // 创建执行命令
        ExecCreateCmdResponse execResponse = dockerClient.execCreateCmd(containerId)
                .withCmd(cmd)
                .withAttachStdin(true)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .exec();
        String execId = execResponse.getId();
        if (execId == null || execId.isEmpty()) {
            throw new RuntimeException("创建容器内执行命令失败");
        }
    }

    /**
     * 执行命令并传入输入
     * @param containerId 容器id
     * @param cmd 命令
     * @param input 输入内容
     * @return 执行结果
     */
    protected ExecuteMessage executeCommandWithInput(String containerId, String[] cmd, String input) {
        ExecuteMessage message = new ExecuteMessage();
        // 创建执行命令
        ExecCreateCmdResponse execResponse = dockerClient.execCreateCmd(containerId)
                .withCmd(cmd)
                .withAttachStdin(true)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .exec();
        String execId = execResponse.getId();
        if (execId == null || execId.isEmpty()) {
            message.setErrorMessage("创建容器内执行命令失败");
            return message;
        }

        // 执行命令并等待完成
        try {
            // 执行结果回调
            AtomicBoolean isTimeout = new AtomicBoolean(true);
            StringBuilder output = new StringBuilder();
            StringBuilder error = new StringBuilder();
            long startTime = System.currentTimeMillis();
            
            // 执行命令并处理输入输出
            ExecStartResultCallback execCallback = new ExecStartResultCallback() {
                @Override
                public void onComplete() {
                    isTimeout.set(false);
                    super.onComplete();
                }

                @Override
                public void onNext(Frame frame) {
                    if (frame.getStreamType() == StreamType.STDERR) {
                        error.append(new String(frame.getPayload()));
                    } else {
                        output.append(new String(frame.getPayload()));
                    }
                    super.onNext(frame);
                }
            };
            
            // 启动命令执行并处理输入
            if (input != null && !input.isEmpty()) {
                // 使用带输入的执行方式
                dockerClient.execStartCmd(execId)
                    .withStdIn(new java.io.ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)))
                    .exec(execCallback)
                    .awaitCompletion(TIME_OUT, TimeUnit.MILLISECONDS);
            } else {
                // 没有输入的情况
                dockerClient.execStartCmd(execId)
                    .exec(execCallback)
                    .awaitCompletion(TIME_OUT, TimeUnit.MILLISECONDS);
            }
            long endTime = System.currentTimeMillis();
            message.setExitValue(0);
            message.setTime(endTime - startTime);
            message.setMessage(output.toString().trim());
            message.setErrorMessage(error.toString().trim());

            // 获取容器内存使用情况
            try {
                long memoryUsage = getContainerMemoryUsage(containerId);
                message.setMemory(memoryUsage);
                log.debug("容器 {} 内存使用: {} KB", containerId, memoryUsage);
            } catch (Exception e) {
                log.warn("获取容器 {} 内存使用情况失败: {}", containerId, e.getMessage());
                message.setMemory(0L);
            }

            if (isTimeout.get()) {
                message.setErrorMessage("执行超时（超过 " + TIME_OUT + "ms）");
            }
        } catch (InterruptedException e) {
            message.setErrorMessage("命令执行被中断：" + e.getMessage());
            message.setExitValue(-1);
            log.error("容器 {} 命令执行中断", containerId, e);
        } catch (Exception e) {
            message.setErrorMessage("命令执行异常：" + e.getMessage());
            log.error("容器 {} 命令执行失败", containerId, e);
            message.setExitValue(-1);
        }

        return message;
    }

    /**
     * 获取容器内存使用情况
     * @param containerId 容器ID
     * @return 内存使用量（KB）
     */
    private long getContainerMemoryUsage(String containerId) {
        try {
            // 获取容器统计信息
            StatsCallback statsCallback = new StatsCallback();
            dockerClient.statsCmd(containerId).exec(statsCallback);
            
            // 等待一小段时间获取统计信息
            Thread.sleep(100);
            
            // 从统计信息中获取内存使用量
            Statistics stats = statsCallback.getStats();
            if (stats != null && stats.getMemoryStats() != null) {
                MemoryStatsConfig memoryStats = stats.getMemoryStats();
                if (memoryStats.getUsage() != null) {
                    // 转换为KB
                    return memoryStats.getUsage() / 1024;
                }
            }
            return 0L;
        } catch (Exception e) {
            log.warn("获取容器 {} 内存统计失败: {}", containerId, e.getMessage());
            return 0L;
        }
    }

    /**
     * 容器统计信息回调
     */
    private static class StatsCallback extends ResultCallback.Adapter<Statistics> {
        private Statistics stats;
        
        @Override
        public void onNext(Statistics stats) {
            this.stats = stats;
        }
        
        public Statistics getStats() {
            return stats;
        }
    }

}
