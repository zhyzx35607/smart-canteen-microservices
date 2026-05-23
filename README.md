# Smart Canteen — 智能食堂点餐与取餐微服务系统

[![Java](https://img.shields.io/badge/Java-17-blue)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2023.0.1-brightgreen)](https://spring.io/projects/spring-cloud)
[![SCA](https://img.shields.io/badge/Spring%20Cloud%20Alibaba-2023.0.1.0-brightgreen)](https://sca.aliyun.com/)
[![License](https://img.shields.io/badge/License-MIT-lightgrey)](LICENSE)

面向校园/园区食堂场景的微服务系统，覆盖「用户在线点餐 → 商家接单备餐 → 用户凭取餐码核销 → 食堂大屏实时显示队列」的完整业务流程。

---

## 目录

- [项目特性](#项目特性)
- [技术栈](#技术栈)
- [系统架构](#系统架构)
- [仓库结构](#仓库结构)
- [快速开始 — 本地开发](#快速开始--本地开发)
- [使用 IntelliJ IDEA 运行和测试](#使用-intellij-idea-运行和测试)
- [K3S 集群部署](#k3s-集群部署)
- [API 速查](#api-速查)
- [测试](#测试)
- [安全配置](#安全配置)
- [文档索引](#文档索引)

---

## 项目特性

- **5 个独立微服务**：用户、菜品菜单、订单、取餐排队、网关，按领域边界拆分，各自独立数据库与进程
- **Spring Cloud 全栈**：Nacos 注册与配置中心、Spring Cloud Gateway 路由、OpenFeign 服务调用、Sentinel 限流、Spring Cloud Stream + RocketMQ 延时消息
- **核心能力**：
  - JWT 双 Token 鉴权（Access 15min / Refresh 7d），Redis 黑名单实现 Token 实时吊销
  - Redis Lua 脚本原子库存扣减，Redis ↔ MySQL 双写持久化
  - 订单状态机（PLACED → ACCEPTED → PREPARING → WAITING_PICKUP → PICKED_UP），RocketMQ 延时 30 分钟超时自动取消
  - Redis List FIFO 取餐队列，STOMP WebSocket 实时推送大屏
  - 网关层：JWT 校验与黑名单、IP/用户级 Redis 计数限流、Sentinel 路由级限流、WebSocket 代理
- **工程实践**：幂等键、TraceId 全链路追踪、统一响应体与异常处理、服务间内部 Token 认证、MyBatis-Plus 软删除
- **K3S 生产部署**：Kustomize 资源清单，Secret/ConfigMap 分离敏感配置，健康检查与 WebSocket 会话亲和性

---

## 技术栈

| 分类 | 组件 | 版本 |
|------|------|------|
| 语言 / 框架 | Java + Spring Boot | 17 / 3.2.5 |
| 微服务 | Spring Cloud + Spring Cloud Alibaba | 2023.0.1 / 2023.0.1.0 |
| 注册 / 配置中心 | Nacos | 2.3.2 |
| API 网关 | Spring Cloud Gateway (WebFlux) | 4.x |
| 服务调用 | OpenFeign + Spring Cloud LoadBalancer | - |
| 限流熔断 | Sentinel | 1.8.x |
| 消息队列 | RocketMQ (延时消息) | 5.1.4 |
| 持久化 | MySQL + MyBatis-Plus | 8.0 / 3.5.5 |
| 缓存 | Redis | 7 |
| 认证 | JJWT (io.jsonwebtoken) | 0.12.5 |
| 部署 | K3S + Docker + Kustomize | - |

---

## 系统架构

```
                        HTTP / WebSocket
┌──────────────────────────────┬──────────────────────────────┐
│  客户端：H5 / 小程序 / 商家端 / 食堂大屏                      │
└──────────────────────────────┴──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│  Gateway Service  :8080  (K3S NodePort 30080)               │
│   路由发现(Nacos)  JWT鉴权+黑名单  Sentinel+Redis限流  WS代理  │
└──┬───────────┬───────────┬───────────┬─────────────────────┘
   │           │           │           │
   ▼           ▼           ▼           ▼
┌────────┐ ┌────────┐ ┌────────┐ ┌────────────┐
│ User   │ │ Menu   │ │ Order  │ │ Pickup     │
│ :8081  │ │ :8082  │ │ :8083  │ │ :8084 (+WS)│
└───┬────┘ └───┬────┘ └───┬────┘ └─────┬──────┘
    │         │ ◄────────┤ Feign      │
    │         │ 扣库存/   │            │
    │         │ 回滚/    ├───────────►│ 入队
    │         │ 查商户   │            │
    │         │          │ ◄──────────┤ 标记已取餐
    │         │          │            │
┌───┴─────────┴──────────┴────────────┴──────┐
│  Nacos  │  MySQL(3 DB)  │  Redis  │ RocketMQ  │
└──────────────────────────────────────────────┘
```

详细架构图、状态机流转、数据库 ER 图见 [`docs/架构设计文档.md`](docs/架构设计文档.md)。

---

## 仓库结构

```
.
├── README.md
├── 开发计划.md
├── docs/                              # 设计文档
│   ├── 架构设计文档.md
│   ├── K3S部署方案说明.md
│   ├── 需求规格说明书.md
│   ├── 概要设计说明书.md
│   ├── 详细设计说明书.md
│   ├── 测试用例.md
│   └── 测试报告.md
└── canteen-backend/
    ├── pom.xml                        # 父 POM（依赖与版本管理）
    ├── canteen-common/                # 公共模块：Result、JWT、TraceId、InternalToken、RedissonConfig
    ├── canteen-gateway/               # 网关：路由、JWT鉴权、限流、WebSocket代理 (8080)
    ├── canteen-user/                  # 用户服务：注册、登录、Token管理 (8081)
    ├── canteen-menu/                  # 菜品菜单服务：菜品CRUD、每日菜单、库存管理 (8082)
    ├── canteen-order/                 # 订单服务：下单、状态机、超时取消 (8083)
    ├── canteen-pickup/                # 取餐排队服务：FIFO队列、叫号、核销、WebSocket推送 (8084)
    ├── deploy/
    │   ├── infra/                     # 基础设施 K8s 清单 (MySQL/Redis/Nacos/RocketMQ)
    │   └── apps/                      # 应用 K8s 清单 (含 secrets.yaml 和 configmap)
    └── scripts/
        ├── docker-compose.yaml        # 本地开发基础设施
        ├── init-db.sql                # 数据库初始化 DDL + 测试数据
        ├── common.yaml                # Nacos 共享配置模板
        ├── broker.conf                # RocketMQ Broker 配置
        └── e2e.sh                     # 端到端冒烟测试脚本
```

---

## 快速开始 — 本地开发

### 前置条件

- JDK 17+（推荐 Eclipse Temurin）
- Maven 3.8+
- Docker Desktop 或 Docker Engine + Compose
- 建议 16 GB+ 内存（基础设施 + 5 个 Java 服务，至少留 8 GB 给应用）

### 1. 启动基础设施

```bash
cd canteen-backend
docker compose -f scripts/docker-compose.yaml up -d
```

等待所有容器就绪（约 30 秒），验证：

```bash
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

应看到 5 个容器：canteen-nacos、canteen-mysql、canteen-redis、canteen-rocketmq-namesrv、canteen-rocketmq-broker。

> Docker Compose 使用非标准端口以避免本地端口冲突：
> - Nacos：`18848`（控制台） / `19848`（gRPC）
> - MySQL：`13306`
> - Redis：`16379`
> - RocketMQ NameServer：`19876`
> - RocketMQ Broker：`20911`
>
> 各服务的 `application.yaml` 中默认值已对应上述端口，无需额外配置。

基础设施手动验证：

```bash
# Nacos（账号密码均为 nacos）
curl -s http://localhost:18848/nacos/v1/console/health/readiness

# MySQL（root / root123），确认三个数据库已创建
docker exec canteen-mysql mysql -uroot -proot123 -e "SHOW DATABASES;"

# Redis
docker exec canteen-redis redis-cli -p 6379 PING

# RocketMQ NameServer
docker logs canteen-rocketmq-namesrv --tail 5
```

### 2. Nacos 配置

浏览器打开 http://localhost:18848/nacos，登录（`nacos` / `nacos`）。

进入 **配置管理 → 配置列表**，点击 **+** 新建配置：

| 字段 | 值 |
|------|-----|
| Data ID | `common.yaml` |
| Group | `DEFAULT_GROUP` |
| 配置格式 | `YAML` |

配置内容（完整版，参考 `scripts/common.yaml`）：

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:16379}

mybatis-plus:
  mapper-locations: classpath*:/mapper/**/*.xml
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
  global-config:
    db-config:
      logic-delete-field: deletedAt
      logic-delete-value: NOW()
      logic-not-delete-value: "NULL"

jwt:
  secret: ${JWT_SECRET:dev-only-jwt-secret-please-override-in-nacos-or-env-256bits}
  access-token-expiration: 900000
  refresh-token-expiration: 604800000

internal:
  token: ${INTERNAL_TOKEN:dev-only-internal-token-please-override}

rocketmq:
  name-server: ${NAMESRV_ADDR:localhost:19876}
  producer:
    group: canteen-producer-group
    send-message-timeout: 3000
    retry-times-when-send-failed: 2

logging:
  pattern:
    console: "%d{ISO8601} %-5level [%X{traceId}] %logger - %msg%n"
  level:
    root: INFO
    com.smartcanteen: DEBUG

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  metrics:
    tags:
      application: ${spring.application.name}
```

> 各服务 `application.yaml` 已内置 `dev-only-*` 兜底默认值，本地开发跳过此步骤也能运行。但正式部署时务必通过 Nacos 或环境变量覆盖 JWT secret 和 internal token。

### 3. 编译

```bash
cd canteen-backend
mvn clean package -DskipTests
```

### 4. 启动服务

严格按顺序启动——先 4 个业务服务，最后网关（网关启动时从 Nacos 发现下游）：

```bash
# 终端 1：用户服务 (8081)
java -jar canteen-user/target/canteen-user-1.0.0.jar

# 终端 2：菜品菜单服务 (8082)
java -jar canteen-menu/target/canteen-menu-1.0.0.jar

# 终端 3：订单服务 (8083)
java -jar canteen-order/target/canteen-order-1.0.0.jar

# 终端 4：取餐排队服务 (8084)
java -jar canteen-pickup/target/canteen-pickup-1.0.0.jar

# 终端 5：网关 (8080) — 最后
java -jar canteen-gateway/target/canteen-gateway-1.0.0.jar
```

每个服务看到 `Started XxxApplication in ...` 即启动成功。在 Nacos 控制台 **服务管理 → 服务列表** 中确认 5 个服务全部在线。

### 5. 运行 E2E 测试

```bash
bash scripts/e2e.sh http://localhost:8080
```

覆盖完整的注册 → 登录 → 查菜品 → 发布菜单 → 下单 → 接单 → 制作 → 入队 → 大屏 → 叫号 → 核销流程。

---

## 使用 IntelliJ IDEA 运行和测试

### 1. 导入项目

打开 IDEA → **Open** → 选择 `canteen-backend/pom.xml` → **Open as Project**。等待右下角 "Indexing..." 完成（首次约 3-5 分钟）。

配置检查：
- **File → Project Structure → Project**：SDK 选 JDK 17，Language level 选 17
- **File → Settings → Build, Execution, Deployment → Compiler → Annotation Processors**：勾选 **Enable annotation processing**（Lombok 依赖）
- **File → Settings → Plugins**：确认已安装 **Lombok** 插件

### 2. 创建 Run Configuration

依次打开以下 5 个 Application 类，点类名左侧绿色 ▶，IDEA 自动创建 Run Config：

```
canteen-user/src/main/java/com/canteen/user/UserApplication.java
canteen-menu/src/main/java/com/canteen/menu/MenuApplication.java
canteen-order/src/main/java/com/canteen/order/OrderApplication.java
canteen-pickup/src/main/java/com/canteen/pickup/PickupApplication.java
canteen-gateway/src/main/java/com/canteen/gateway/GatewayApplication.java
```

> 默认环境变量无需额外配置——所有服务 `application.yaml` 已有本地开发默认值。如需自定义，在 **Run → Edit Configurations → Environment variables** 中设置。

### 3. 运行测试

- 单个类：打开测试类，点类名左侧 ▶ → **Run**
- 单个模块：右键模块目录 → **Run 'All Tests'**
- 全部测试：右键 `canteen-backend` → **Run 'Tests in canteen-backend'**

或使用 Maven 命令行：

```bash
cd canteen-backend
mvn test                               # 全部测试
mvn test -pl canteen-order             # 单模块
mvn test -pl canteen-order -Dtest=OrderStateMachineTest                      # 单类
mvn test -pl canteen-order -Dtest=OrderStateMachineTest#testAcceptTransition # 单方法
```

单元测试全部使用 Mockito，不需要数据库或 Redis。集成测试需要 Docker（Testcontainers），无 Docker 时会自动跳过。

### 4. 调试接口

推荐使用 IDEA 内置 HTTP Client。新建 `scratches/canteen.http`：

```http
### 注册
POST http://localhost:8080/api/user/auth/register
Content-Type: application/json

{"phone":"13900001111","studentNo":"2024999","password":"test123456","nickname":"测试"}

### 登录
POST http://localhost:8080/api/user/auth/login
Content-Type: application/json

{"phone":"13900001111","loginType":"password","password":"test123456"}

### 查菜品
GET http://localhost:8080/api/menu/dishes?onShelf=1
Authorization: Bearer {{token}}
```

### 5. 常见问题

| 现象 | 原因 / 解决 |
|------|------|
| 编译报 `cannot find symbol`（Lombok） | 未启用注解处理或未安装 Lombok 插件 |
| 网关返回 503 | 下游服务还未注册到 Nacos，等待 30 秒后重试 |
| WebSocket 连不上 | 应通过网关 `ws://localhost:8080/ws/screen/{counterId}`，不要直连 8084 |
| `mvn test` 集成测试被跳过 | 未安装 Docker 或 Docker 不可达——单元测试仍可正常运行 |
| MySQL 连接被拒绝 | 确认 Docker Compose 已启动，端口为 `13306` 而非默认 3306 |
| RocketMQ 相关功能不可用 | `sendDefaultImpl call timeout` 表示 Broker 端口映射问题；不影响下单流程（已做异常容错） |

---

## K3S 集群部署

完整步骤详见 [`docs/K3S部署方案说明.md`](docs/K3S部署方案说明.md)。概要命令：

```bash
# 1. 替换 deploy/apps/secrets.yaml 中的所有 REPLACE_ME_* 占位符
#    推荐用 openssl rand -base64 48 生成 JWT secret
#    推荐用 openssl rand -hex 32 生成 internal token

# 2. 构建并推送镜像
cd canteen-backend
mvn clean package -DskipTests
for svc in gateway user menu order pickup; do
  docker build -t your-registry/canteen/${svc}-service:1.0.0 ./canteen-${svc}
  docker push your-registry/canteen/${svc}-service:1.0.0
done

# 3. 部署基础设施
kubectl apply -k deploy/infra/

# 4. 在 Nacos 中创建 common.yaml 共享配置（同上文）

# 5. 部署应用
kubectl apply -k deploy/apps/

# 6. 验证
kubectl get pods -n canteen
bash scripts/e2e.sh http://<NODE_IP>:30080
```

---

## API 速查

所有接口均通过网关 `http://localhost:8080` 访问（K3S 中为 `http://<node>:30080`）。除登录/注册/WebSocket/大屏队列外，均需 `Authorization: Bearer <token>`。

### 用户服务

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/user/auth/register` | 注册（手机号+学工号+密码） |
| POST | `/api/user/auth/login` | 登录（password 或 sms） |
| POST | `/api/user/auth/refresh` | 刷新 Token |
| POST | `/api/user/auth/logout` | 登出（Token 加入黑名单） |
| GET | `/api/user/users/me` | 查询个人信息 |
| PUT | `/api/user/users/me` | 修改个人信息 |

### 菜品菜单服务

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/menu/dishes` | 菜品列表（支持 merchantId/onShelf 过滤，分页） |
| GET | `/api/menu/dishes/{id}` | 菜品详情 |
| POST | `/api/menu/dishes` | 新增菜品 |
| PUT | `/api/menu/dishes/{id}` | 修改菜品 |
| PUT | `/api/menu/dishes/{id}/on-shelf` | 上下架切换 |
| POST | `/api/menu/daily` | 发布今日菜单（设置库存） |
| GET | `/api/menu/daily` | 查询今日菜单 |
| GET | `/api/menu/daily?date=YYYY-MM-DD` | 查询指定日期菜单 |

### 订单服务

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/order/orders` | 下单（Header 带 `Idempotency-Key` 防重复） |
| GET | `/api/order/orders` | 我的订单列表（分页） |
| GET | `/api/order/orders/{id}` | 订单详情 |
| PUT | `/api/order/orders/{id}/accept` | 商家接单（PLACED → ACCEPTED） |
| PUT | `/api/order/orders/{id}/start` | 开始制作（ACCEPTED → PREPARING） |
| PUT | `/api/order/orders/{id}/ready` | 制作完成，生成取餐码并入队（PREPARING → WAITING_PICKUP） |
| PUT | `/api/order/orders/{id}/cancel?reason=...` | 取消订单（库存回滚） |

### 取餐排队服务

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/pickup/queues/{counterId}` | 大屏查询（当前叫号+等待队列+历史） |
| POST | `/api/pickup/queues/{counterId}/call` | 叫号（弹出队首，WebSocket 推送） |
| POST | `/api/pickup/pickups/verify` | 核销取餐码（标记 PICKED_UP） |
| WS | `ws://localhost:8080/ws/screen/{counterId}` | WebSocket 实时推送（STOMP over SockJS） |

### 测试数据

`init-db.sql` 在 Docker Compose 初次启动时自动执行，写入：

| 类型 | 数据 |
|------|------|
| 用户 | 手机号 `13800000001`，学工号 `2024001`，密码 `password123` |
| 商户 | 川味窗口 (C01)、粤式窗口 (C02)、面食窗口 (C03) |
| 菜品 | 麻婆豆腐 (1200分)、回锅肉 (1800分)、水煮鱼 (2500分)、白切鸡 (2200分)、煲仔饭 (1500分)、牛肉面 (1600分)、炸酱面 (1200分) |

---

## 测试

### 测试概况

| 类型 | 范围 | 命令 |
|------|------|------|
| 单元测试 | 55 个用例，覆盖 Service/Controller/Filter/状态机/消息监听器 | `mvn test` |
| 集成测试 | 18 个用例（H2 内存库 + Testcontainers Redis） | `mvn test -Dtest="*IntegrationTest"` |
| E2E 冒烟 | 完整业务流程，需全部服务运行 | `bash scripts/e2e.sh http://localhost:8080` |

### 测试文件明细

| 模块 | 测试类 | 用例数 | 类型 | 覆盖内容 |
|------|--------|--------|------|----------|
| common | JwtTokenProviderTest | 4 | 单元 | Token生成/验证/过期 |
| gateway | JwtAuthGlobalFilterTest | 3 | 单元 | 白名单放行/缺Token/无效Token |
| gateway | RateLimitFilterTest | 2 | 单元 | IP计数限流/登录IP限流 |
| user | AuthServiceTest | 6 | 单元 | 注册/登录/登出/刷新 |
| user | UserControllersTest | 3 | 单元 | 控制器接口 |
| menu | DishServiceTest | 7 | 单元 | 菜品CRUD/上下架/onShelf过滤 |
| menu | StockServiceTest | 7 | 单元 | 库存扣减/回滚/数值容错 |
| menu | DishControllerTest | 2 | 单元 | 控制器接口 |
| menu | MenuServiceIntegrationTest | 8 | 集成 | 库存扣减/不足/回滚/批量原子/低库存预警 |
| order | OrderStateMachineTest | 8 | 单元 | 状态机全部路径覆盖 |
| order | OrderTimeoutListenerTest | 3 | 单元 | RocketMQ超时消费/无效ID/异常容错 |
| order | OrderControllerTest | 5 | 单元 | 下单/接单/取消/查询 |
| order | OrderServiceIntegrationTest | 6 | 集成 | 下单/状态流转/取消/幂等/非法操作/详情 |
| pickup | PickupQueueServiceTest | 5 | 单元 | 入队/叫号/核销/异常 |
| pickup | PickupControllerTest | 2 | 单元 | 大屏/叫号接口 |
| pickup | PickupQueueServiceIntegrationTest | 4 | 集成 | 入队→叫号→核销/空队/无效码/多窗口 |

> 单元测试全部基于 Mockito，无需任何外部依赖。集成测试使用 Testcontainers 启动 Redis 容器，需要本地 Docker 环境；如 Docker 不可用，集成测试会自动跳过。

详细结果见 [`docs/测试报告.md`](docs/测试报告.md)。

---

## 安全配置

仓库中所有密码和密钥均为开发环境占位值，不可用于生产。

| 配置项 | 用途 | 生产环境来源 |
|--------|------|------|
| `jwt.secret` | JWT HS256 签名密钥（≥256 bit） | 环境变量 `JWT_SECRET` 或 Nacos `common.yaml` |
| `internal.token` | 服务间 Feign 调用的 `X-Internal-Token` | 环境变量 `INTERNAL_TOKEN` 或 Nacos `common.yaml` |
| 数据库密码 | MySQL root 密码 | K3S 中由 `deploy/apps/secrets.yaml` 提供 |

部署前务必完成以下安全配置：

1. 替换 `deploy/apps/secrets.yaml` 中所有 `REPLACE_ME_*` 占位符
2. 在 Nacos 中显式设置 `jwt.secret` 和 `internal.token` 为强随机值

生成强随机值：

```bash
openssl rand -base64 48 | tr -d '\n'   # JWT secret (256+ bit)
openssl rand -hex 32                    # internal token
```

---

## 文档索引

| 文档 | 路径 |
|------|------|
| 架构设计文档 | [`docs/架构设计文档.md`](docs/架构设计文档.md) |
| K3S 部署方案 | [`docs/K3S部署方案说明.md`](docs/K3S部署方案说明.md) |
| 需求规格说明书 | [`docs/需求规格说明书.md`](docs/需求规格说明书.md) |
| 概要设计说明书 | [`docs/概要设计说明书.md`](docs/概要设计说明书.md) |
| 详细设计说明书 | [`docs/详细设计说明书.md`](docs/详细设计说明书.md) |
| 测试用例 | [`docs/测试用例.md`](docs/测试用例.md) |
| 测试报告 | [`docs/测试报告.md`](docs/测试报告.md) |
