## HyperOJ Code Sandbox

一个基于 Spring Boot 的在线判题代码沙箱服务，支持通过 HTTP 接口提交代码，在受控环境中编译/运行并返回输出与判题信息。项目集成 Docker Java SDK，并预留 RabbitMQ 相关配置，适合在 OJ/评测系统中作为独立沙箱服务使用。

### 技术栈
- Spring Boot 2.7.x
- Java 8
- Docker Java SDK (`com.github.docker-java`)
- Lombok / Hutool
- 可选：RabbitMQ (spring-boot-starter-amqp)

---

### 目录结构
```
src/
  main/
    java/com/hkex/hyperojcodesandbox/
      HyperOjCodeSandboxApplication.java
      controller/MainController.java
      model/{ExecuteCodeRequest, ExecuteCodeResponse, ExecuteMessage, JudgeInfo}.java
      docker/DockerDemo.java
      utils/{MQUtils, ProcessUtils}.java
    resources/
      application.yml
      code/
      static/
      profile.json
```

---

### 快速开始

#### 环境要求
- Java 8 (JDK 1.8)
- Maven 3.6+
- 可选：Docker 环境（用于容器化运行与隔离）
- 可选：RabbitMQ（如需消息队列）

#### 本地运行
```bash
mvn -v
mvn clean package -DskipTests
mvn spring-boot:run
# 或
java -jar target/HyperOJ-code-sandbox-0.0.1-SNAPSHOT.jar
```
默认端口：8090（见 `src/main/resources/application.yml`）。

健康检查：
```bash
curl -s http://localhost:8090/health
# 返回: OK
```

---

### 配置说明
主要配置位于 `src/main/resources/application.yml`：

```yaml
server:
  port: 8090

spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: developer
    password: devpassword
    virtual-host: /dev
```

- 如无需 MQ，可保留默认不使用；如启用，请确保 RabbitMQ 可用。
- 其他运行时资源（如 `resources/code` 模板目录、`profile.json`）可按需扩展。

---

### 鉴权
服务对执行接口采用简单请求头鉴权：

- 请求头名：`Auth`
- 约定密钥：`itsmygo`

生产环境务必改为安全的密钥或替换为更可靠的鉴权机制（例如签名校验、OAuth、网关校验等）。

---

### API 文档

#### 1) 健康检查
- 方法：GET
- 路径：`/health`
- 响应：`OK`

示例：
```bash
curl -s http://localhost:8090/health
```

#### 2) 执行代码
- 方法：POST
- 路径：`/executeCode`
- 鉴权：请求头 `Auth: itsmygo`
- 请求体：`application/json`

Request Body（`ExecuteCodeRequest`）：
```json
{
  "inputList": ["1 2", "3 4"],
  "code": "public class Main { public static void main(String[] args){ /* ... */ } }",
  "language": "java"
}
```

Response Body（`ExecuteCodeResponse`）：
```json
{
  "outputList": ["3", "7"],
  "message": "", // 接口信息（超时/宕机/错误等）
  "status": 0,     // 业务状态码（由服务定义）
  "judgeInfo": {
    "message": "Accepted",
    "time": 25,     // ms
    "memory": 10240 // kb
  }
}
```

当鉴权失败时，HTTP 状态为 403。

---

### 开发说明
- 应用入口：`HyperOjCodeSandboxApplication`
- 控制器：`controller/MainController` 暴露了 `/health` 与 `/executeCode`
- 代码执行：`JavaDockerCodeSandBoxTemplateImpl`、`JavaNativeCodeSandBoxImpl` 与 `JavaCodeSandBoxTemplate` 负责不同执行策略
- 工具类：`utils/ProcessUtils` 等

> 说明：`pom.xml` 中包含 `docker-java` 与 `docker-java-transport-httpclient5` 依赖，若启用 Docker 沙箱，请确保宿主机 Docker 守护进程已启动并具备相应权限。

---

### 常见问题
- 403 未授权：确认请求头 `Auth` 是否等于预设密钥，或修改服务端密钥。
- 代码运行超时/异常：查看 Response 的 `message` 与 `judgeInfo.message`，同时检查服务日志。
- Docker 相关错误：检查 Docker 服务是否运行、用户权限以及镜像/网络设置。

---

### 许可证
本项目采用 Apache License 2.0 开源协议。详见仓库根目录 `LICENSE` 文件。


