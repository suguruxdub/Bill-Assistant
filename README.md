# Bill Assistant

个人账单管理项目，采用前后端分离架构。Web 页面已可运行；App 共用 React 界面，并已有 Bearer Token API，但尚未生成 Android/iOS 原生工程。

- `frontend`：React、TypeScript、Vite、Capacitor
- `backend`：Java 17、Spring Boot、MyBatis、MySQL、Redis、Flyway、Maven

## 本地运行

需要 Java 17+、Maven 3.6.3+，以及 Node.js 22.12+（22.x）或 24+；建议使用 Node.js LTS。

### MySQL 与 Redis

本机已通过 Homebrew 安装 MySQL 8.4 和 Redis，并注册为当前用户登录后自动启动的服务。

```bash
# 安装（新机器）
brew install mysql@8.4 redis

# 启动 / 查看状态
brew services start mysql@8.4
brew services start redis
brew services list

# 停止
brew services stop mysql@8.4
brew services stop redis
```

| 服务 | 地址 | 项目配置 |
| --- | --- | --- |
| MySQL | `127.0.0.1:3306` | 数据库 `bill_assistant`，用户 `bill_assistant` |
| Redis | `127.0.0.1:6379` | 数据库 `0`，仅本机访问，默认无密码，已开启 AOF 持久化 |

项目账号仅有 `bill_assistant` 库的权限。随机生成的 MySQL 应用密码保存在 `backend/.env`；管理员凭据保存在 `.local/mysql-admin.cnf`。两个文件均已被 Git 忽略，并设为仅当前用户可读写，请勿提交或覆盖。

MySQL 8.4 的命令行工具未添加到全局 PATH，可使用完整路径连接：

```bash
# 项目账号：交互输入 backend/.env 中的 MYSQL_PASSWORD
/opt/homebrew/opt/mysql@8.4/bin/mysql -h 127.0.0.1 -u bill_assistant -p bill_assistant

# 管理员：从仓库根目录执行，读取本地凭据
/opt/homebrew/opt/mysql@8.4/bin/mysql --defaults-extra-file="$PWD/.local/mysql-admin.cnf"

# Redis 连通性检查
redis-cli ping
```

在新机器上，需要创建对应数据库和专用账号，再将 `backend/.env.example` 复制为 `backend/.env` 并填写密码、生成 JWT 签名密钥。Spring 从后端或仓库根目录启动时会自动加载该文件；也可通过同名环境变量覆盖连接配置。IDEA 的 MySQL 数据源可填写上表信息，并使用 `backend/.env` 中的密码。

### 后端

```bash
cd backend
mvn spring-boot:run
```

后端默认监听 `http://localhost:8080`，健康检查地址为 `GET /api/health`。

启动时 Flyway 会自动执行 `backend/src/main/resources/db/migration` 中尚未执行的 SQL，创建账单、用户和迁移历史表。MySQL 保存用户与账单，Redis 保存登录会话；后端重启后未过期的 Redis 会话仍可使用。MySQL 必须已启动，连接账号需要对应数据库的建表和读写权限；登录、注册和刷新登录态需要 Redis 可用，尚未过期的 Access Token 鉴权不访问 Redis。

### 前端

```bash
cd frontend
npm install
npm run dev
```

前端默认监听 `http://localhost:5173`。开发服务器会把 `/api` 请求代理到后端的 `8080` 端口。

未登录时显示登录入口，可以切换到注册页面。注册成功后自动登录并进入个人账单页面；点击「＋ 创建账单」，填写名称、金额、日期、分类和支付状态即可保存，列表与汇总会立即更新。右上角可以查看个人信息或退出。刷新页面会通过 `/api/auth/me` 恢复登录态，登录过期后返回登录页面。

可复制 `frontend/.env.example` 为 `frontend/.env.local` 调整 API 前缀；默认无需配置。生产部署时，需要由反向代理将同域名下的 `/api` 转发给后端，并托管 `frontend/dist` 静态文件。

### App 联调

启动后端、MySQL 和 Redis 后，可以通过 HTTP 客户端联调 App 认证接口：

```bash
# 注册，响应中的 tokens.accessToken 和 tokens.refreshToken 供 App 客户端保存
curl -i -X POST http://localhost:8080/api/app/auth/register \
  -H 'Content-Type: application/json' -H 'X-Requested-With: BillAssistant' \
  -d '{"username":"app_user","password":"test-password"}'

# 使用 Access Token 读取当前用户
curl -i http://localhost:8080/api/app/auth/me \
  -H 'Authorization: Bearer <accessToken>'

# 使用 Refresh Token 轮换令牌
curl -i -X POST http://localhost:8080/api/app/auth/refresh \
  -H 'Content-Type: application/json' -H 'X-Requested-With: BillAssistant' \
  -d '{"refreshToken":"<refreshToken>"}'
```

Capacitor 环境会共用 Web 界面，自动改用 `src/shared/api/app-session.ts` 中的 `AppApiClient` 发送 Bearer Token。它支持登录、注册、账单请求、刷新及退出。默认令牌只保存在内存中，进程退出后需要重新登录；发布 App 前，需要接入原生安全存储，并在界面启动前通过 `configureAppClient({ apiBaseUrl, tokenStore })` 注入基于 Keychain/Keystore 的 `AppTokenStore`。

当前仓库只有 `capacitor.config.ts`，尚无 `android/` 或 `ios/` 工程。首次创建原生工程时，先设置原生 App 专用的 `VITE_APP_API_BASE_URL` 并构建，再添加所需平台：

```bash
cd frontend
# Android 模拟器示例；iOS 模拟器改为 http://127.0.0.1:8080/api
VITE_APP_API_BASE_URL=http://10.0.2.2:8080/api npm run build
npm run cap:add:android   # 需要 Android SDK
# 或 npm run cap:add:ios  # 需要 macOS 和 Xcode
npm run cap:sync
```

真机联调需将 API 地址改为开发机的局域网地址，确保后端可从设备访问。以上 HTTP 地址仅用于本地开发；原生平台还需配置开发期明文流量权限，正式环境使用 HTTPS。当前已验证真实 MySQL/Redis 下的注册、Bearer 资料查询、刷新和账单查询；尚未执行模拟器或真机端到端测试。

## 在 IntelliJ IDEA 中导入

打开仓库根目录后，右键 `backend/pom.xml`，选择 **Add as Maven Project（添加为 Maven 项目）**。然后在 Maven 工具窗口执行 **Reload All Maven Projects（重新加载所有 Maven 项目）**，等待依赖下载和索引完成。Project SDK 选择本机安装的 JDK 17 或更高版本。

`backend/src/main/java` 和 `backend/src/test/java` 的源码目录由 Maven 自动识别，无需手动标记。如果只打开后端，也可以直接通过 `backend/pom.xml` 打开项目。前端在支持 JavaScript/TypeScript 的 IDEA 版本中启用相关插件，并在 `frontend` 目录安装 npm 依赖。

## 验证

```bash
# 在 backend 目录执行
mvn test
mvn package

# 可选：检查真实 MySQL 与 Redis 连通性；需要本地服务已启动
mvn -Dstorage.integration=true test

# 运行全部测试（含下面两组真实存储测试）；需要 MySQL 与 Redis
mvn -Dstorage.integration=true -Dmysql.integration=true -Dredis.integration=true test

# 验证 MySQL 中的账号、账单和 Redis 会话在应用上下文重启后仍可用
# 需要 MySQL 与 Redis；验证后仅删除本次测试创建的账号、会话与账单
mvn -Dmysql.integration=true -Dtest=MySqlPersistenceTests test

# H2 用户数据 + 真实 Redis：验证刷新令牌哈希、TTL、原子轮换、重放撤销与退出
mvn -Dredis.integration=true -Dtest=RedisSessionIntegrationTests test

# 在 frontend 目录执行
npm run typecheck
npm run build
```

## API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/health` | 服务健康检查 |
| POST | `/api/auth/register` | 注册并登录，返回当前用户信息并设置登录 Cookie |
| POST | `/api/auth/login` | 用户名及密码登录，返回当前用户信息并设置登录 Cookie |
| GET | `/api/auth/me` | 获取当前登录用户信息，需要登录 |
| POST | `/api/auth/refresh` | 使用 Refresh Cookie 轮换双 Token，成功返回 204，无需有效 Access Token |
| POST | `/api/auth/logout` | 撤销当前刷新会话并清除 Cookie，无需有效 Access Token |
| POST | `/api/app/auth/register` | App 注册，JSON 返回用户资料和 Bearer Token |
| POST | `/api/app/auth/login` | App 登录，JSON 返回用户资料和 Bearer Token |
| GET | `/api/app/auth/me` | 使用 App Bearer Token 获取当前用户信息 |
| POST | `/api/app/auth/refresh` | 使用 JSON Refresh Token 轮换 App 双 Token |
| POST | `/api/app/auth/logout` | 撤销 App 刷新会话 |
| GET | `/api/bills` | 当前用户的账单列表，按账单日期、ID 倒序，需要登录 |
| POST | `/api/bills` | 创建当前用户的账单，由 MySQL 自增生成 ID，需要登录 |

注册的必填字段为 `username`、`password`；可选字段为 `nickname`、`email`、`phone`、`gender`、`birthday`。用户名为 3～32 位字母、数字或下划线，且不能以下划线开头；统一转为小写，用户名和非空邮箱唯一。密码至少 8 个字符，最多 72 个 UTF-8 字节；邮箱和联系电话目前仅存储，不进行验证。性别取值为 `UNSPECIFIED`、`MALE`、`FEMALE` 或 `OTHER`。生日格式为 `YYYY-MM-DD`，不能晚于今天。

用户表包含 UID、用户名、密码哈希、昵称、邮箱、电话、头像 URL、性别、生日、启用状态和创建/更新时间（UTC）。头像字段预留，当前没有上传或修改入口。用户资料 API 不返回密码哈希；只有 App 登录、注册和刷新接口返回令牌。

登录请求示例：

```json
{
  "username": "alice",
  "password": "your-password"
}
```

所有写请求（包括注册、登录、刷新、退出和创建账单）需要请求头 `X-Requested-With: BillAssistant`；前端 API 模块会自动添加。Web 通过 Cookie 保持登录态，App 通过 Bearer Token 保持登录态，后端不接受客户端指定 UID。未登录或过期返回 `401`；缺少写请求来源标识返回 `403`；用户名或邮箱重复返回 `409`；参数错误返回 `400`；Redis 不可用返回 `503`。认证等业务错误返回 `{ "code": "错误码", "message": "说明" }`，参数校验错误至少包含 `message`。刷新并发冲突返回 `409 / REFRESH_CONFLICT`；访问凭证失效返回 `401 / ACCESS_TOKEN_INVALID`。

## 登录态与用户上下文

密码使用 BCrypt 哈希存储。Web 登录、注册成功后设置两个 `HttpOnly`、`SameSite=Lax` Cookie，浏览器自动保存。App 登录、注册成功后通过 JSON 返回 Bearer Token，原生端必须使用 Keychain/Keystore 等安全存储保存令牌。Web 令牌不写入 `localStorage`，也不通过 JSON 返回：

| Cookie | 内容 | 默认有效期 | 路径 |
| --- | --- | --- | --- |
| `BILL_ACCESS` | HS256 签名 JWT，包含 `sub`（UID）、`sid`、签发/过期时间、签发方、受众及用途 | 15 分钟 | `/api` |
| `BILL_REFRESH` | 256 位随机秘密加会话 ID | 从登录起 30 天，轮换不延长截止时间 | `/api/auth` |

`AuthInterceptor` 在 Controller 前本地验证 Access JWT 的签名、算法、签发方、受众、用途和时间等声明，并通过 `client_type` 区分 Web Cookie 与 App Bearer Token；两类令牌不能交叉使用。验证后从 `sub` 提取 UID 放入请求上下文，**鉴权不查询 Redis 或 MySQL**。业务入口通过 `UserContext.requireUid()` 读取 UID，并显式传给 Service 和 DAO：

```java
return billService.findAll(UserContext.requireUid());
```

上下文仅在当前 HTTP 请求内有效，请求结束会清理；后台任务或异步任务应显式传递 UID。账单读写 SQL 均按该 UID 限定。`/api/auth/me` 是用户资料查询接口，会访问 MySQL；登录与刷新时检查账号是否启用。历史无归属账单保留为 `user_id IS NULL`，不会出现在用户账单列表里。

Redis 仅保存刷新会话：`bill-assistant:refresh:{sid}` 是 Hash，保存 UID、客户端类型、当前 Refresh Token 的 SHA-256 和绝对过期时间，不保存原始令牌或 Access JWT。Lua 脚本原子轮换当前哈希，并用 `:used:<旧令牌哈希>` 记录已消费凭证；这些键在同一个绝对截止时间过期。旧 Refresh Token 在默认 5 秒并发容错窗口内再次使用返回 409，不覆盖 Cookie；超过窗口再重放会撤销该刷新会话。未知随机令牌不会撤销合法会话。

Web 前端仅在 `ACCESS_TOKEN_INVALID` 时刷新，然后将原请求重试一次；页面内共享刷新任务，支持 Web Locks 的浏览器还会在同源标签页之间串行刷新、登录与退出。不支持 Web Locks 时，409 分支会短暂等待其他请求交付的新 Cookie；支持 BroadcastChannel 时，登录与退出会同步其他标签页的用户界面。App 客户端也只在该错误码出现时共享刷新任务并重试一次。刷新时检查账号状态；Redis 故障返回 503，刷新失败时 Web 保留 Cookie、App 保留当前令牌。注册数据库事务回滚时清理已创建的刷新会话，清理失败的键按 TTL 过期。

退出撤销当前刷新会话；Web 同时清除两个 Cookie，App 清除本地令牌。其他客户端的会话继续有效。由于 Access JWT 本地验签，**已复制的 Access Token 在退出或禁用账号后最多仍可使用 15 分钟**；这些操作会阻止后续刷新。接近 30 天截止时间时，Access Token 的签发有效期也会缩短到该截止时间。

原 MySQL `user_sessions` 表保留历史结构，业务代码不再读写，也不作为 Redis 故障回退；已有 Flyway V1/V2 不修改，本次无需数据库迁移。旧 `BILL_SESSION` Cookie 不兼容，更新后需要重新登录，登录时会清除旧 Cookie。旧 Redis 会话键不读取，按原 TTL 自然过期。

必须配置 `AUTH_JWT_SECRET` 为至少 32 个随机字节的 Base64 编码，例如用 `openssl rand -base64 48` 生成，写入已忽略的 `backend/.env` 或环境变量。密钥应固定保存，重启及多实例使用同一密钥；更换密钥会使已有 Access JWT 失效。模板不含可用于部署的默认密钥，本机密钥已生成。

`AUTH_ACCESS_MAX_AGE_SECONDS` 默认 900，`AUTH_REFRESH_MAX_AGE_SECONDS` 默认 2592000，`AUTH_REFRESH_GRACE_SECONDS` 默认 5。HTTPS 部署时设置 `AUTH_COOKIE_SECURE=true`，并通过同域反向代理转发 `/api`；本地 HTTP 开发默认 `false`。配置模板见 `backend/.env.example`。

App 跨源来源由 `APP_CORS_ALLOWED_ORIGINS` 配置，多个来源用逗号分隔。生产环境应只填写实际 App WebView 来源。

## 账单输入

创建账单的 JSON 请求示例：

```json
{
  "title": "午餐",
  "amount": 25.50,
  "billingDate": "2026-11-07",
  "category": "餐饮",
  "paid": true
}
```

金额范围为 `0.01`～`999999999999.99`，最多两位小数。名称和分类不能为空白，分别最多 255 和 64 个字符；账单日期必填。创建请求不需要传 ID。日期格式为 `YYYY-MM-DD`；默认以人民币展示金额。后端端口可通过 `SERVER_PORT` 环境变量调整，同时需要修改前端开发代理的目标端口。

## 目录结构

```text
.
├── backend/       # Spring Boot 服务端和 Maven 配置
├── frontend/      # React 前端和 Vite 配置
├── AGENTS.md      # 面向后续开发代理的仓库约定
└── README.md
```

