# Bill Assistant 开发约定

## 项目目标

Bill Assistant 是个人账单管理 Web 应用。代码按前后端分离组织：React 前端负责展示和交互，Spring Boot 后端负责业务规则和 API。

## 仓库结构

- `frontend/`：React + TypeScript + Vite。页面、组件和浏览器端 API 调用都放在这里。
- `backend/`：Java 17 + Spring Boot + MyBatis + Maven。按 `controller`、`dto`、`service`、`dao`、`model` 分层；`dao` 中的 Mapper 负责数据库访问。
- `README.md`：运行和项目概览。
- 本地存储：MySQL 8.4（`127.0.0.1:3306`）与 Redis（`127.0.0.1:6379`）。连接配置见 `backend/.env.example`；真实凭据仅放在已忽略的 `backend/.env` 和 `.local/` 中。
- AI 私密配置放在已忽略的 `backend/.ai-secrets.env`（可从 `backend/.ai-secrets.env.example` 创建）；该文件及其中的 `AI_API_KEY`、`AI_BASE_URL` 禁止读取、打印、记录、提交或展示给用户。需要修改时只说明变量名和配置位置，不要读取其值。

不要在仓库根目录新增 Java 或 Node 应用代码；新功能应放入对应模块。

## 常用命令

```bash
# 前端
cd frontend
npm install
npm run dev
npm run build
npm run typecheck

# 后端
cd backend
mvn spring-boot:run
mvn test
mvn package
# 可选：连接真实本地存储
mvn -Dstorage.integration=true test
# 验证 MySQL 持久化及 Redis 会话在应用重启后仍可读取（需要两项服务）
mvn -Dmysql.integration=true -Dtest=MySqlPersistenceTests test
# 验证真实 Redis 的刷新会话、TTL、原子轮换、重放撤销和注销
mvn -Dredis.integration=true -Dtest=RedisSessionIntegrationTests test
```

如果团队后续引入 Maven Wrapper，可将上面的 `mvn` 替换为 `./mvnw`。

## 后端约定

- 使用 Java 17 语法和 Spring Boot 约定的自动配置。
- Controller 只处理 HTTP 输入输出，业务逻辑放在 Service 中。
- DAO 层使用 MyBatis；通过 `#{...}` 参数绑定编写 SQL，禁止拼接用户输入。写操作在 Service 层使用事务；ID 由 MySQL 自增生成。
- MyBatis 使用 `Mapper.java` + `Mapper.xml`：接口放在 `dao` 包，SQL 与结果映射放在 `backend/src/main/resources/mapper` 下的同名 XML，禁止在 Java 接口中使用 SQL 注解。XML 的 `namespace` 对应接口全限定名，语句 `id` 对应接口方法名；新增映射时保留所需的自增 ID 回填配置。
- 请求 DTO 与数据库实体分开；输入金额精度和字符串长度必须与表结构一致。
- 数据库结构由 `backend/src/main/resources/db/migration` 中的 Flyway SQL 管理；已执行迁移不可修改，结构变更需新增递增版本脚本。禁止启动时删表或覆盖用户数据。
- 对外 API 使用 `/api` 前缀；返回 JSON；新增接口时同步更新 README 或 API 文档。
- 金额使用 `BigDecimal`，日期使用 `java.time` 类型，禁止使用 `double` 表示金额。
- 输入 DTO 做校验，错误响应保持一致且不要泄露内部堆栈。
- 默认配置适合本地开发，生产环境的密钥和连接信息必须通过环境变量注入。
- AI 模型密钥和私有模型服务地址只能通过 `backend/.ai-secrets.env` 或外部环境变量注入；任何自动化代理不得读取该文件内容，也不得把其值写入日志、补丁、测试输出或文档。
- Capacitor App 使用 Bearer Token 跨源访问 `/api` 时，只允许 `APP_CORS_ALLOWED_ORIGINS` 配置的来源；不得对 App 跨源请求开启 Cookie 凭据。
- MySQL 负责账单主数据，Redis 用于缓存和短期状态；新增缓存时使用 `bill-assistant:` 键前缀并设置适当过期时间。
- 普通测试使用 `test` profile、H2、相同的 Flyway 迁移及测试专用内存 SessionStore，数据测试使用事务回滚。真实 MySQL/Redis 测试必须显式启用并清理其自身测试数据，不得使用 FLUSHDB 或删除其他用户的键。
- 当前账单已由 MyBatis 持久化到 MySQL，Redis 只负责刷新会话；不要主动加入账单缓存。
- 认证由 `AuthInterceptor` 本地验证 Access JWT（HS256），从 `sub` 提取 UID；普通鉴权禁止查询 Redis 或 MySQL。校验签名、算法、用途、签发方、受众和时间声明；签名密钥 `AUTH_JWT_SECRET` 必须为至少 32 个随机字节的 Base64，禁止提交、记录或使用生产默认密钥。
- 双 Token 使用 HttpOnly/SameSite=Lax Cookie：`BILL_ACCESS`（`/api`，默认 15 分钟）与 `BILL_REFRESH`（`/api/auth`，登录起绝对 30 天）。Redis Hash `bill-assistant:refresh:{sid}` 只保存 UID、当前刷新令牌的 SHA-256、绝对截止时间；禁止保存原始令牌或 Access JWT。刷新必须用 Lua 原子轮换，不延长绝对期限；已消费凭证使用同期限标记，5 秒内并发返回 409，窗口后重放撤销刷新会话，未知令牌不能撤销合法会话。
- 密码只存 BCrypt 哈希；Web 令牌仅通过 HttpOnly Cookie 交付，不得返回 JSON 或写日志。App `/api/app/auth/**` 可返回 Bearer Token，但原生客户端必须使用 Keychain/Keystore 等安全存储，不得写入明文本地存储或日志。登录与刷新检查账号是否启用；退出撤销当前刷新会话，Access JWT 自然到期（禁用账号同样最多延迟 15 分钟生效）。`/auth/me` 的资料查询可以访问 MySQL。
- MySQL 旧 `user_sessions` 表不再读写，也不作为 Redis 故障回退。Redis 故障返回 503，不能误判为 401 或清除 Cookie；有效 Access JWT 仍能鉴权。注册事务回滚时清理新建刷新会话。无效 Access Token 返回 `ACCESS_TOKEN_INVALID`，不要清除 Cookie，避免延迟响应覆盖新登录态。
- 当前 UID 通过 `UserContext.requireUid()` 从请求上下文读取，在 Controller 获取后显式传递给 Service/DAO。禁止信任请求体、参数或请求头中的 UID；所有用户数据 SQL 必须加用户条件。请求上下文不能跨异步任务使用。
- 除健康检查、注册、登录、刷新和退出外，`/api/**` 默认需要登录。所有写请求必须包含 `X-Requested-With: BillAssistant`；修改认证时应验证未登录、过期/注销令牌、用户隔离和请求上下文清理。

## 前端约定

- 使用 TypeScript，组件和业务类型保持明确，避免无理由使用 `any`。
- 前端按业务功能拆分：`src/app` 负责应用装配，`src/features/auth` 负责认证和用户资料，`src/features/bills` 负责账单，`src/shared/api` 负责公共请求与会话协调，`src/styles/global.css` 放公共样式。功能组件、接口、类型、专属样式就近存放，禁止公共请求层依赖业务功能模块。
- 业务接口分别放在对应功能的 `api.ts`，通过公共 `client.ts` 发送请求；底层传输位于 `http.ts`，Web 双 Token 刷新及跨标签页协调位于 `auth-session.ts`，App Bearer Token 刷新位于 `app-session.ts`，错误类型位于 `error.ts`。不要绕过统一的刷新重试逻辑。
- `/api` 请求使用相对路径，以便复用 Vite 开发代理和生产环境反向代理。
- 用户可见文案优先使用简洁中文；代码标识符使用英文。
- 登录态以 `/api/auth/me` 返回结果为准，由浏览器保存 HttpOnly Cookie；禁止使用 localStorage 存令牌或信任浏览器自报的用户 ID。仅在 `401 / ACCESS_TOKEN_INVALID` 时刷新并将原请求重试一次；共享刷新 Promise，并通过 Web Locks 串行同源标签页的刷新、登录与退出。刷新冲突短暂等待新 Cookie，503 不清除登录态；刷新最终返回 401 才回到登录入口并清理用户数据。通过 BroadcastChannel 同步其他标签页的账号界面；防止旧请求在退出或切换账号后刷新、重试或更新页面。

## 变更流程

1. 先确认改动属于 `frontend`、`backend` 还是两者。
2. 修改后运行受影响模块的构建或测试命令。
3. 新增 API 或环境变量时更新文档和 `.env.example`。
4. 不要提交构建产物、依赖目录、IDE 私有配置或真实密钥。
