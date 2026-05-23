# 智能食堂点餐与取餐微服务系统

[![Java](https://img.shields.io/badge/Java-17-blue)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2023.0.1-brightgreen)](https://spring.io/projects/spring-cloud)
[![SCA](https://img.shields.io/badge/Spring%20Cloud%20Alibaba-2023.0.1.0-brightgreen)](https://sca.aliyun.com/)
[![License](https://img.shields.io/badge/License-课程作业-lightgrey)](#license)

面向校园/园区食堂场景的微服务系统，覆盖「用户在线点餐 → 商家接单备餐 → 用户凭取餐码核销 → 食堂大屏实时显示队列」全流程，是分布式系统架构课程大作业的完整交付。

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
- [从零开始完整部署与测试](#从零开始完整部署与测试)
- [安全配置](#安全配置)
- [文档索引](#文档索引)
- [License](#license)

---

## 项目特性

- **5 个独立微服务**：用户、菜品菜单、订单、取餐排队、网关，按领域边界划分，各自独立 schema/进程
- **Spring Cloud 全家桶**：Nacos 注册+配置、Spring Cloud Gateway 路由、OpenFeign 服务调用、Sentinel 限流、Spring Cloud Stream + RocketMQ 延时消息
- **核心能力**：
  - JWT 双 Token（Access 15min / Refresh 7d）+ 带 jti 的 Redis 黑名单可吊销
  - Redis Lua 原子库存扣减、Redis ↔ MySQL 双写一致性
  - 订单 5 态状态机 + RocketMQ 延时 30 min 超时自动取消
  - Redis List FIFO 取餐队列 + STOMP WebSocket 大屏推送
  - 网关层：JWT 校验、黑名单、IP/用户级 Redis 限流、Sentinel 路由级限流、WebSocket 代理
- **生产级实践**：幂等键、TraceId 透传、统一响应/异常、内部 Token 隔离服务间调用、MyBatis-Plus 软删除
- **K3S 一键部署**：Kustomize 资源清单，Secret/ConfigMap 分离敏感配置，含健康检查与 WebSocket 会话粘连

---

## 技术栈

| 分类 | 组件 | 版本 |
|------|------|------|
| 语言 / 框架 | Java + Spring Boot | 17 / 3.2.5 |
| 微服务 | Spring Cloud + Spring Cloud Alibaba | 2023.0.1 / 2023.0.1.0 |
| 注册 / 配置中心 | Nacos | 2.3.2 |
| API 网关 | Spring Cloud Gateway (reactive) | 4.x |
| 服务调用 | OpenFeign | - |
| 限流熔断 | Sentinel | 1.8.x |
| 消息队列 | RocketMQ (delay 消息) | 5.1.4 |
| 持久化 | MySQL + MyBatis-Plus | 8.0 / 3.5.5 |
| 缓存 / 队列 | Redis | 7 |
| 认证 | JWT (JJWT) | 0.12.5 |
| 部署 | K3S + Docker + Kustomize | - |

---

## 系统架构

```
┌──────────────────────────────────────────────────────────────┐
│  客户端：H5 / 小程序 / 商家端 / 食堂大屏                       │
└──────────────────────────┬───────────────────────────────────┘
                           │ HTTP / WebSocket
┌──────────────────────────▼───────────────────────────────────┐
│  Gateway Service  :8080  (NodePort 30080)                    │
│   路由(Nacos)  JWT校验+黑名单  Sentinel+Redis限流  WS代理      │
└──┬───────────┬───────────┬───────────┬────────────────────────┘
   │           │           │           │
   ▼           ▼           ▼           ▼
┌────────┐ ┌────────┐ ┌────────┐ ┌────────────┐
│ User   │ │ Menu   │ │ Order  │ │ Pickup     │
│ :8081  │ │ :8082  │ │ :8083  │ │ :8084 (+WS)│
└───┬────┘ └───┬────┘ └───┬────┘ └─────┬──────┘
    │         │ ◄────────┤ Feign      │
    │         │ deduct/  │            │
    │         │ restore/ ├───────────►│ enqueue
    │         │ merchant │            │
    │         │          │ ◄──────────┤ markPickedUp
    │         │          │            │
┌───┴─────────┴──────────┴────────────┴──────┐
│  Nacos  │  MySQL(3 schema)  │ Redis │ RocketMQ │
└─────────────────────────────────────────────┘
```

详细架构图与状态机、数据库 ER 图见 [`docs/架构设计文档.md`](docs/架构设计文档.md)。

---

## 仓库结构

```
.
├── README.md                          # 本文件
├── 开发计划.md
├── docs/                              # 软件工程文档
│   ├── 架构设计文档.md
│   ├── K3S部署方案说明.md
│   ├── 需求规格说明书.md
│   ├── 概要设计说明书.md
│   ├── 详细设计说明书.md
│   ├── 测试用例.md
│   └── 测试报告.md
└── canteen-backend/
    ├── pom.xml                        # 父 POM
    ├── canteen-common/                # 公共模块：Result、JWT、TraceId、内部 Token
    ├── canteen-gateway/               # 网关：路由、JWT、限流、WS 代理
    ├── canteen-user/                  # 用户服务（8081）
    ├── canteen-menu/                  # 菜品菜单服务（8082）
    ├── canteen-order/                 # 订单服务（8083）
    ├── canteen-pickup/                # 取餐排队服务（8084）
    ├── deploy/
    │   ├── infra/                     # 基础设施 K8s 清单
    │   └── apps/                      # 应用 K8s 清单（含 secrets.yaml）
    └── scripts/
        ├── docker-compose.yaml        # 一键启动本地基础设施
        ├── init-db.sql                # 数据库初始化 + 测试数据
        └── e2e.sh                     # 端到端冒烟测试脚本
```

---

## 快速开始 — 本地开发

### 前置条件

- **JDK 17+**（推荐 Eclipse Temurin / Zulu）
- **Maven 3.8+**
- **Docker Desktop** 或 **Docker Engine + Compose**
- 16 GB+ 内存（基础设施 + 5 个 Java 进程，本机至少留 8 GB 给应用）

### Step 1：启动基础设施

```bash
cd canteen-backend
docker compose -f scripts/docker-compose.yaml up -d
```

启动后：
- Nacos：http://localhost:8848/nacos （账号 / 密码：`nacos` / `nacos`）
- MySQL：`localhost:3306`（root / root123），自动执行 `init-db.sql`
- Redis：`localhost:6379`
- RocketMQ NameServer：`localhost:9876`

> Nacos 完全就绪约需 30 秒，可 `docker logs canteen-nacos -f` 等到 "Nacos started successfully" 再继续。

### Step 2：在 Nacos 创建共享配置

**Data ID** `common.yaml`，**Group** `DEFAULT_GROUP`，**配置格式** YAML：

```yaml
jwt:
  secret: ${JWT_SECRET:dev-only-jwt-secret-please-override-in-nacos-or-env-256bits}
  access-token-expiration: 900000      # 15 min
  refresh-token-expiration: 604800000  # 7 day

internal:
  token: ${INTERNAL_TOKEN:dev-only-internal-token-please-override}

# 本地开发建议显式覆盖：
# jwt.secret: 你自己生成的 ≥ 256 bit 随机字符串
# internal.token: 你自己生成的随机字符串
```

> 也可以不创建该配置，让各服务直接走 `application.yaml` 里的开发占位值；但生产部署必须覆盖。

### Step 3：编译

```bash
cd canteen-backend
mvn clean package -DskipTests
```

### Step 4：按顺序启动 5 个服务

**必须先启动 4 个业务服务，最后启动网关**（网关从 Nacos 拉取已注册的下游）：

```bash
# 终端 1 — 用户
java -jar canteen-user/target/canteen-user-1.0.0.jar
# 终端 2 — 菜品菜单
java -jar canteen-menu/target/canteen-menu-1.0.0.jar
# 终端 3 — 订单
java -jar canteen-order/target/canteen-order-1.0.0.jar
# 终端 4 — 取餐排队
java -jar canteen-pickup/target/canteen-pickup-1.0.0.jar
# 终端 5 — 网关（最后）
java -jar canteen-gateway/target/canteen-gateway-1.0.0.jar
```

### Step 5：冒烟验证

```bash
bash canteen-backend/scripts/e2e.sh http://localhost:8080
```

预期输出包含注册、登录、查询菜品、下单、查看订单等步骤的 JSON 响应。

---

## 使用 IntelliJ IDEA 运行和测试

### 1. 导入项目

1. 启动 IDEA → **Open** → 选择 `canteen-backend/pom.xml` → **Open as Project**。
2. IDEA 自动识别为 Maven 多模块工程，等右下角 **"Indexing..."** 与 **"Importing"** 完成（首次约 3-5 分钟拉依赖）。
3. **File → Project Structure → Project**：
   - SDK 选 **JDK 17**（不能用 8/11/21）
   - Language level 选 **17 - Sealed types, always-strict floating-point semantics**
4. **File → Settings**（macOS: Preferences）→ **Build, Execution, Deployment → Build Tools → Maven**：
   - Maven home：用 IDEA 自带的 Bundled (Maven 3) 即可
   - User settings file：如果有 `~/.m2/settings.xml`（含阿里云镜像）勾选 Override 并指向它，加速拉包
5. **File → Settings → Build, Execution, Deployment → Compiler → Annotation Processors**：
   - 勾选 **Enable annotation processing**（Lombok / MapStruct 都依赖它）
6. 安装插件（如未安装）：**Settings → Plugins** → 搜 **Lombok** → Install → Restart。

### 2. 启动本地基础设施

在 IDEA 内置终端或外部 Shell 中：

```bash
cd canteen-backend
docker compose -f scripts/docker-compose.yaml up -d
```

等 Nacos 就绪，然后按上文 [Step 2](#step-2在-nacos-创建共享配置) 在 Nacos 控制台创建 `common.yaml`。

### 3. 创建 5 个 Spring Boot Run Configuration

> **快捷做法**：依次打开下面 5 个 `*Application.java`，IDEA 会在类名左侧出现绿色 ▶️。点一下 → **Run 'XxxApplication'**，IDEA 自动建好 Run Config。

```
canteen-backend/canteen-user/src/main/java/com/canteen/user/UserApplication.java
canteen-backend/canteen-menu/src/main/java/com/canteen/menu/MenuApplication.java
canteen-backend/canteen-order/src/main/java/com/canteen/order/OrderApplication.java
canteen-backend/canteen-pickup/src/main/java/com/canteen/pickup/PickupApplication.java
canteen-backend/canteen-gateway/src/main/java/com/canteen/gateway/GatewayApplication.java
```

**为每个 Run Config 加环境变量**（**Run → Edit Configurations → 选中 → Environment variables**）：

| 服务 | 必填环境变量 |
|------|------|
| 全部 5 个 | `JWT_SECRET=本地随机字符串(≥256bit)`, `INTERNAL_TOKEN=本地随机字符串` |
| user/menu/order | `MYSQL_HOST=localhost;MYSQL_PORT=3306;MYSQL_USER=root;MYSQL_PASSWORD=root123` |
| 全部 5 个 | `REDIS_HOST=localhost;REDIS_PORT=6379` |
| order/pickup | `ROCKETMQ_NAMESRV=localhost:9876` |
| 全部 5 个 | `NACOS_ADDR=localhost:8848` |

> **不配 JWT_SECRET / INTERNAL_TOKEN 也能跑**：仓库 `application.yaml` 中有 `dev-only-*` 兜底值。**正式部署务必覆盖**。

**启动顺序**：先 user → menu → order → pickup，最后 gateway。可以利用 IDEA 的 **Compound Run Config**（`Run → Edit Configurations → + → Compound`）一次性启动全部。

### 4. 跑测试

#### 跑某个测试类
打开测试类（例如 `OrderStateMachineTest.java`），点类名左侧的绿色 ▶️ → **Run 'OrderStateMachineTest'**。

#### 跑某个测试方法
点方法左侧 ▶️ → **Run 'methodName()'**。

#### 跑某个模块的所有测试
右键模块（如 `canteen-order`）→ **Run 'All Tests'**。

#### 跑全部模块测试
右键 `canteen-backend` 根模块 → **Run 'Tests in canteen-backend'**；或在终端：

```bash
cd canteen-backend
mvn test
# 或单模块
mvn test -pl canteen-order
# 或单类
mvn test -pl canteen-order -Dtest=OrderStateMachineTest
# 或单方法
mvn test -pl canteen-order -Dtest=OrderStateMachineTest#testAcceptTransition
```

**测试不需要基础设施**：所有 `*Test` 都使用 Mockito mock，不连真 MySQL/Redis，可放心在 IDE 中随时跑。

### 5. 调试接口

推荐 **HTTP Client**（IDEA 内置）。新建 `scratches/canteen.http`：

```http
### 注册
POST http://localhost:8080/api/user/auth/register
Content-Type: application/json

{"phone":"13900001111","studentNo":"2024999","password":"test123456","nickname":"E2E"}

### 登录
POST http://localhost:8080/api/user/auth/login
Content-Type: application/json

{"phone":"13900001111","loginType":"password","password":"test123456"}

### 查菜品（用上一步返回的 accessToken 替换 {{token}}）
GET http://localhost:8080/api/menu/dishes
Authorization: Bearer {{token}}
```

点每个请求左侧 ▶️ 即可。或用 Postman / curl，或直接用 `scripts/e2e.sh`。

### 6. 常见 IDEA 问题排查

| 现象 | 原因 / 解决 |
|------|------|
| 编译报 `cannot find symbol` Lombok 注解 | 没启用注解处理 / 没装 Lombok 插件，参见上面第 1 步第 5/6 项 |
| Run 时报 `Failed to bind to embedded RocketMQ` | 没起 docker-compose，或 9876 端口被占 |
| 报 `Could not resolve placeholder 'jwt.secret'` | application.yaml 里现在用 `${JWT_SECRET:dev-only-...}` 已带默认，应不会报；如自己删了默认值，请在 Run Config 加 `JWT_SECRET` 环境变量 |
| 服务起来但网关返回 503 | 下游服务还没注册到 Nacos，等几秒；或检查 Nacos 控制台「服务管理 → 服务列表」 |
| WebSocket 连不上 | 浏览器端要走 `ws://localhost:8080/ws/screen/{counterId}`，不是直接 `:8084` |

---

## K3S 集群部署

完整步骤详见 [`docs/K3S部署方案说明.md`](docs/K3S部署方案说明.md)。关键命令：

```bash
# 1. 替换 deploy/apps/secrets.yaml 中的 REPLACE_ME_* 为强随机值
#    建议: openssl rand -base64 48 / openssl rand -hex 32

# 2. 构建并推送镜像
cd canteen-backend
mvn clean package -DskipTests
for svc in gateway user menu order pickup; do
  docker build -t registry.local/canteen/${svc}-service:1.0.0 ./canteen-${svc}
  docker push registry.local/canteen/${svc}-service:1.0.0
done

# 3. 部署基础设施
kubectl apply -k deploy/infra/

# 4. 在 Nacos 控制台创建 common.yaml（同本地开发 Step 2）

# 5. 部署应用
kubectl apply -k deploy/apps/

# 6. 验证
kubectl get pods -n canteen
bash scripts/e2e.sh http://<NODE_IP>:30080
```

---

## API 速查

所有请求经网关 `http://localhost:8080`（或 K3S 中 `http://<node>:30080`）转发，除登录/注册/WS 外均需 `Authorization: Bearer <token>`。

### 用户服务

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/user/auth/register` | 注册 |
| POST | `/api/user/auth/login` | 登录（password / sms） |
| POST | `/api/user/auth/refresh` | 刷新 Token |
| POST | `/api/user/auth/logout` | 登出（Token 加入黑名单） |
| GET / PUT | `/api/user/users/me` | 查询 / 修改用户信息 |

### 菜品菜单服务

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/menu/dishes` | 菜品列表 |
| POST | `/api/menu/dishes` | 新增菜品 |
| PUT | `/api/menu/dishes/{id}` | 修改菜品 |
| PUT | `/api/menu/dishes/{id}/on-shelf` | 上下架切换 |
| POST | `/api/menu/daily` | 发布每日菜单 |
| GET | `/api/menu/daily?date=YYYY-MM-DD` | 查询每日菜单 |

### 订单服务

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/order/orders` | 下单（带 `Idempotency-Key` Header） |
| GET | `/api/order/orders/{id}` | 订单详情 |
| GET | `/api/order/orders` | 我的订单列表 |
| PUT | `/api/order/orders/{id}/accept` | 商家接单 |
| PUT | `/api/order/orders/{id}/start` | 开始制作 |
| PUT | `/api/order/orders/{id}/ready` | 制作完成（生成取餐码并入队） |
| PUT | `/api/order/orders/{id}/cancel` | 取消订单 |

### 取餐排队服务

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/pickup/queues/{counterId}` | 大屏查询队列 |
| POST | `/api/pickup/queues/{counterId}/call` | 叫号 |
| POST | `/api/pickup/pickups/verify` | 核销取餐码 |
| WS | `/ws/screen/{counterId}` | WebSocket 实时推送 |

### 测试数据（init-db.sql 自动写入）

| 类型 | 数据 |
|------|------|
| 用户 | 手机号 `13800000001`，学工号 `2024001`，密码 `password123` |
| 商户 | 川味窗口(C01)、粤式窗口(C02)、面食窗口(C03) |
| 菜品 | 麻婆豆腐 / 回锅肉 / 水煮鱼 / 白切鸡 / 煲仔饭 / 牛肉面 / 炸酱面 |

---

## 测试

### 测试概览

| 类型 | 范围 | 命令 |
|------|------|------|
| 全部单元测试 | 15 个测试类、55 个用例，覆盖所有 Service / Controller / Filter / 状态机 / 消息监听器 | `mvn test` |
| 全部集成测试 | 3 个测试类、18 个用例（订单服务H2、取餐队列Testcontainers Redis、菜品库存Testcontainers Redis+H2） | `mvn test -Dtest="*IntegrationTest"` |
| 单模块测试 | 单个微服务 | `mvn test -pl canteen-order` |
| 单类 | 精确定位 | `mvn test -pl canteen-order -Dtest=OrderStateMachineTest` |
| 单方法 | 精确定位 | `mvn test -pl canteen-order -Dtest=OrderStateMachineTest#testNormalFlow` |
| 端到端冒烟 | 需先启动所有服务 | `bash canteen-backend/scripts/e2e.sh http://localhost:8080` |

### 测试文件明细

| 模块 | 测试类 | 用例数 | 类型 | 覆盖内容 |
|------|--------|--------|------|----------|
| common | JwtTokenProviderTest | 4 | 单元 | Token生成/验证/过期 |
| gateway | JwtAuthGlobalFilterTest | 3 | 单元 | 白名单/缺Token/无效Token |
| gateway | RateLimitFilterTest | 2 | 单元 | IP计数限流/登录IP限流 |
| user | AuthServiceTest | 6 | 单元 | 注册/登录/登出/刷新 |
| user | UserControllersTest | 3 | 单元 | 控制器接口 |
| menu | DishServiceTest | 6 | 单元 | 菜品CRUD/上下架/onShelf过滤 |
| menu | StockServiceTest | 6 | 单元 | 库存扣减/回滚/无效数值容错 |
| menu | DishControllerTest | 2 | 单元 | 控制器接口 |
| menu | MenuServiceIntegrationTest | 8 | 集成 | 库存扣减/不足/回滚/批量原子/低库存预警 |
| order | OrderStateMachineTest | 8 | 单元 | 状态机全部路径覆盖 |
| order | OrderTimeoutListenerTest | 3 | 单元 | RocketMQ超时消费/无效ID/异常容错 |
| order | OrderControllerTest | 5 | 单元 | 下单/接单/取消/查询 |
| order | OrderServiceIntegrationTest | 6 | 集成 | 下单/状态流转/取消/幂等/非法操作/详情 |
| pickup | PickupQueueServiceTest | 5 | 单元 | 入队/叫号/核销/异常 |
| pickup | PickupControllerTest | 2 | 单元 | 大屏/叫号接口 |
| pickup | PickupQueueServiceIntegrationTest | 4 | 集成 | 入队→叫号→核销/空队/无效码/多窗口 |

**注意：测试不需要外部基础设施。** 单元测试全部使用 Mockito mock，集成测试使用 H2 内存数据库 + Testcontainers Redis 容器，可直接在 IDE 或命令行运行。

测试结果详见 [`docs/测试报告.md`](docs/测试报告.md)。

---

## 从零开始完整部署与测试

以下是从空白环境到系统完全运行的详细步骤，适合首次接触项目的开发者。

### 环境要求检查

```bash
# 1. 检查 Java 版本（必须 17+）
java -version
# 期望输出：openjdk version "17.x" 或 "21.x"

# 2. 检查 Maven（3.8+）
mvn -version

# 3. 检查 Docker（基础设施依赖）
docker info
docker compose version

# 4. 检查端口占用（以下端口必须空闲）
# 8848(Nacos)  3306(MySQL)  6379(Redis)  9876(RocketMQ)
# 8080(Gateway) 8081(User)  8082(Menu)  8083(Order)  8084(Pickup)
```

### 第一阶段：启动基础设施

```bash
cd canteen-backend

# 启动 Nacos + MySQL + Redis + RocketMQ
docker compose -f scripts/docker-compose.yaml up -d

# 等待所有容器就绪（约 30-60 秒）
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

# 逐个验证基础设施
# Nacos（账号密码均为 nacos）
curl -s http://localhost:8848/nacos/v1/console/health/readiness
# MySQL（root/root123）
docker exec canteen-mysql mysql -uroot -proot123 -e "SHOW DATABASES;"
# Redis
docker exec canteen-redis redis-cli PING
# RocketMQ NameServer
curl -s http://localhost:9876/
```

### 第二阶段：配置 Nacos

1. 浏览器打开 http://localhost:8848/nacos ，登录（`nacos` / `nacos`）
2. 左侧菜单 → **配置管理** → **配置列表** → 点击 **＋** 新建配置
3. 填写以下内容：

| 字段 | 值 |
|------|-----|
| Data ID | `common.yaml` |
| Group | `DEFAULT_GROUP` |
| 配置格式 | `YAML` |
| 配置内容 | 见下方 |

```yaml
jwt:
  secret: ${JWT_SECRET:dev-only-jwt-secret-please-override-in-nacos-or-env-256bits}
  access-token-expiration: 900000
  refresh-token-expiration: 604800000

internal:
  token: ${INTERNAL_TOKEN:dev-only-internal-token-please-override}
```

4. 点击 **发布**

> 也可以跳过此步骤，各服务的 `application.yaml` 已内置上述默认值。Nacos 共享配置主要用于 K3S 生产部署时统一管理。

### 第三阶段：编译项目

```bash
cd canteen-backend

# 编译全部模块（跳过测试，加快速度）
mvn clean package -DskipTests

# 验证编译产物
ls -lh canteen-*/target/*.jar
```

### 第四阶段：启动微服务

**严格按以下顺序启动：先业务服务，最后网关。**

```bash
# === 终端 1：用户服务 (8081) ===
java -jar canteen-user/target/canteen-user-1.0.0.jar

# === 终端 2：菜单服务 (8082) ===
java -jar canteen-menu/target/canteen-menu-1.0.0.jar

# === 终端 3：订单服务 (8083) ===
java -jar canteen-order/target/canteen-order-1.0.0.jar

# === 终端 4：取餐服务 (8084) ===
java -jar canteen-pickup/target/canteen-pickup-1.0.0.jar

# === 终端 5：网关服务 (8080) — 最后启动 ===
java -jar canteen-gateway/target/canteen-gateway-1.0.0.jar
```

**等待所有服务注册到 Nacos：**

浏览器打开 http://localhost:8848/nacos → **服务管理** → **服务列表**，确认 5 个服务全部在线：

| 服务名 | 实例数 | 端口 |
|--------|--------|------|
| user-service | 1 | 8081 |
| menu-service | 1 | 8082 |
| order-service | 1 | 8083 |
| pickup-service | 1 | 8084 |
| gateway-service | 1 | 8080 |

### 第五阶段：执行完整测试

#### 5.1 运行全部单元测试 + 集成测试

```bash
cd canteen-backend

# 全部测试（单元 + 集成）
mvn test

# 预期输出：Tests run: 73, Failures: 0, Errors: 0, Skipped: 0
```

#### 5.2 逐模块运行测试

```bash
# 用户服务（注册/登录/Token 刷新/登出）
mvn test -pl canteen-user
# 预期：Tests run: 9

# 菜单服务（菜品CRUD/库存扣减/回滚/上下架/onShelf过滤）
mvn test -pl canteen-menu
# 预期：Tests run: 22（含 8 个集成测试）

# 订单服务（状态机/下单/接单/取消/幂等/RocketMQ监听器）
mvn test -pl canteen-order
# 预期：Tests run: 22（含 6 个集成测试）

# 取餐服务（入队/叫号/核销/WebSocket广播）
mvn test -pl canteen-pickup
# 预期：Tests run: 11（含 4 个集成测试）

# 网关（JWT校验/限流）
mvn test -pl canteen-gateway
# 预期：Tests run: 5
```

#### 5.3 运行端到端冒烟测试

```bash
# 确保所有服务已启动，然后执行
bash canteen-backend/scripts/e2e.sh http://localhost:8080

# 成功输出示例：
# [PASS] 注册
# [PASS] 登录
# [PASS] 查询菜品
# [PASS] 下单
# [PASS] 查询订单
# ...
```

#### 5.4 手动 API 验证

使用 curl 验证核心流程：

```bash
# 1. 注册
curl -s -X POST http://localhost:8080/api/user/auth/register \
  -H "Content-Type: application/json" \
  -d '{"phone":"13900001111","studentNo":"2024999","password":"test123456","nickname":"测试用户"}'

# 2. 登录（保存返回的 accessToken）
TOKEN=$(curl -s -X POST http://localhost:8080/api/user/auth/login \
  -H "Content-Type: application/json" \
  -d '{"phone":"13900001111","loginType":"password","password":"test123456"}' \
  | jq -r '.data.accessToken')

# 3. 查询菜品
curl -s http://localhost:8080/api/menu/dishes?onShelf=1 \
  -H "Authorization: Bearer $TOKEN" | jq

# 4. 下单
curl -s -X POST http://localhost:8080/api/order/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"merchantId":1,"items":[{"dishId":1,"quantity":2}]}' | jq
```

### 常见问题排查

| 问题 | 原因 | 解决 |
|------|------|------|
| `docker compose` 启动失败 | Docker Desktop 未运行 | 启动 Docker Desktop，等待引擎就绪 |
| 端口冲突 | 端口被其他进程占用 | `netstat -ano \| findstr <端口>` 查找占用进程 |
| 网关返回 503 | 下游服务未注册到 Nacos | 等待 30 秒，检查 Nacos 服务列表 |
| `mvn test` 失败 | 依赖未安装 | 先运行 `mvn install -DskipTests` |
| 集成测试失败 | Docker 未运行（Testcontainers 需要） | 启动 Docker Desktop |
| WebSocket 连不上 | 直接连了 pickup 端口 | 应通过网关 `ws://localhost:8080/ws/screen/{counterId}` |
| 编译报 Lombok 错误 | 未启用注解处理器 | IDEA: Settings → Build → Compiler → Annotation Processors → 勾选 Enable |
| 测试控制台中文乱码 | 终端编码问题 | Windows 终端执行 `chcp 65001` 切换到 UTF-8 |

---

## 安全配置

仓库中**不保留任何可用于生产的 secret**，关键敏感配置如下：

| 配置项 | 用途 | 来源 |
|--------|------|------|
| `jwt.secret` | JWT 签名密钥（HS256，≥256 bit） | 优先环境变量 `JWT_SECRET`；其次 Nacos `common.yaml`；本地有 `dev-only-*` 兜底 |
| `internal.token` | 服务间 Feign 调用 `X-Internal-Token` 校验 | 优先环境变量 `INTERNAL_TOKEN`；其次 Nacos `common.yaml`；本地有 `dev-only-*` 兜底 |
| 数据库密码 | MySQL root 密码 | K8s 中由 `deploy/apps/secrets.yaml` 的 `db-secret` 提供 |

部署前**必须**：
- 替换 `deploy/apps/secrets.yaml` 中所有 `REPLACE_ME_*` 占位符
- 在 Nacos 中显式设置 `jwt.secret` / `internal.token` 为强随机值

推荐生成方式：

```bash
openssl rand -base64 48 | tr -d '\n'   # jwt-secret
openssl rand -hex 32                    # internal-token
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

---

## License

课程作业项目，仅供学习参考。
