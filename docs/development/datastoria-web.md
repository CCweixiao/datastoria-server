# datastoria-web 开发与调试

`datastoria-web` 是 Next.js 16 + React 19 前端，也是根 Maven reactor 中的独立构建模块。
日常开发推荐使用 npm，以获得最快的热更新；Maven 入口用于统一构建、CI、发布以及验证前端模块
可以被完整复现。

## 1. 环境准备

需要 JDK 17、Node.js 22、npm、pnpm 和 Git submodule：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
java -version
node --version
npm --version
pnpm --version
git submodule update --init --recursive
```

如果没有 pnpm：

```bash
npm install --global pnpm
```

首次安装或 `package-lock.json` 变化后，在仓库根目录执行：

```bash
cd datastoria-web
npm ci --force
```

当前依赖树包含 React 19 与部分尚未声明 React 19 peer range 的组件，因此安装命令保留
`--force`。不要删除 `package-lock.json` 后重新生成一套未评审的依赖版本。

## 2. Git Submodule 与 external 依赖

`datastoria-web/external/` 下的目录不是构建产物，而是前端直接引用的 Git Submodule：

| 目录 | npm 本地依赖 | 用途 |
|---|---|---|
| `external/number-flow` | `number-flow`、`@number-flow/react` | Dashboard 等界面的数字变化动画 |
| `external/vizlayer` | `@vizlayer/core`、`@vizlayer/react` | AI 可视化配置、核心渲染能力和 React 图表组件 |
| `external/cmdk` | `cmdk` | 命令菜单、搜索候选、键盘导航和 `/` 指令交互 |

这些依赖在 `package.json` 中通过 `file:./external/...` 引用。主仓库只记录子仓库地址和固定
commit，不保存其完整源码。因此首次 clone、新建 worktree，或切换到更新过 Submodule commit
的分支后，需要执行：

```bash
git submodule update --init --recursive
```

也可以在首次 clone 时一次完成：

```bash
git clone --recurse-submodules \
  https://github.com/CCweixiao/datastoria-server.git
```

检查当前状态：

```bash
git submodule status --recursive
```

commit 前的状态字符含义如下：

| 字符 | 含义 | 处理建议 |
|---|---|---|
| 空格 | 已初始化，且 commit 与主仓库记录一致 | 无需处理 |
| `-` | 尚未初始化 | 执行 `git submodule update --init --recursive` |
| `+` | 当前 checkout 与主仓库记录的 commit 不一致 | 确认是否在开发 Submodule；否则执行更新命令恢复 |
| `U` | Submodule 存在合并冲突 | 先解决冲突，不要继续安装或构建 |

初始化成功后，日常启动不需要重复执行该命令。不要删除 `external/`，也不要未经兼容性验证就把
本地依赖替换成 npm 公共版本；`number-flow` 和 `cmdk` 当前使用项目指定的定制分支/commit。

开发方式按修改范围选择：

- 修改 DataStoria 页面，同时需要 external 热更新：使用 `npm run dev`，它会启动全部 watcher。
- 只修改 DataStoria 自身前端：先执行 `npm run build:deps`，随后使用 `npm run dev:next`，减少
  常驻进程和日志。
- 修改某个 Submodule：在对应仓库中创建分支并提交，再由主仓库更新 Submodule commit；不要把
  Submodule 内的未提交修改混入主仓库提交。

## 3. 启动 Java 后端

前端不保存连接、模型凭据、会话或 Agent Run，开发时必须连接 Spring Boot：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export DATASTORIA_MASTER_KEY="$(openssl rand -base64 32)"

./mvnw -pl datastoria-boot -am package -DskipTests
SPRING_PROFILES_ACTIVE=dev \
  java -jar datastoria-boot/target/datastoria-boot-0.0.1-SNAPSHOT.jar
```

确认后端可用：

```bash
curl -fsS http://127.0.0.1:8080/actuator/health
```

日常断点调试可以直接在 IDE 中运行
`io.github.ccweixiao.datastoria.DatastoriaServerApplication`，JDK 选择 17，Active Profile
设置为 `dev`，并配置 `DATASTORIA_MASTER_KEY`。

## 4. 推荐方式：npm 启动前端

另开终端：

```bash
cd datastoria-web

NEXT_PUBLIC_DATASTORIA_SESSION_BACKEND=java \
NEXT_PUBLIC_DATASTORIA_JAVA_API_BASE_URL=http://127.0.0.1:8080 \
NEXT_PUBLIC_DATASTORIA_DEV_USER_EMAIL=dev@example.com \
npm run dev
```

访问 `http://localhost:3000`。`npm run dev` 会同时启动：

- Next.js 开发服务器；
- number-flow core/react watcher；
- vizlayer core/react watcher；
- cmdk watcher。

如果不修改 `external/` 中的本地依赖，可以先构建一次依赖，然后只启动 Next.js：

```bash
npm run build:deps

NEXT_PUBLIC_DATASTORIA_SESSION_BACKEND=java \
NEXT_PUBLIC_DATASTORIA_JAVA_API_BASE_URL=http://127.0.0.1:8080 \
NEXT_PUBLIC_DATASTORIA_DEV_USER_EMAIL=dev@example.com \
npm run dev:next
```

修改端口：

```bash
PORT=3001 npm run dev
```

`NEXT_PUBLIC_*` 会进入浏览器构建结果。修改这些变量后必须重启 Next.js，不能依赖热更新。
API Key、数据库密码和模型 Secret 不得放进 `NEXT_PUBLIC_*`。

## 5. Maven 启动方式

从仓库根目录执行：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./mvnw -pl datastoria-web -Pweb-dev generate-resources
```

`web-dev` Profile 会依次执行 `npm ci --force` 和 `npm run dev`，默认连接
`http://127.0.0.1:8080`。覆盖后端地址或前端端口：

```bash
PORT=3001 \
./mvnw -pl datastoria-web \
  -Pweb-dev \
  -Ddatastoria.web.dev-api-base-url=http://127.0.0.1:18080 \
  generate-resources
```

该命令是持续运行的开发进程，使用 `Ctrl+C` 会同时结束 Maven、Next.js 和各依赖 watcher。
因为每次都会执行 `npm ci`，日常开发仍推荐 npm 方式；Maven方式更适合验证全新环境能否复现。

## 6. Maven 构建和制品

只构建前端：

```bash
./mvnw -pl datastoria-web package
```

指定生产构建使用的 Java API 地址：

```bash
./mvnw -pl datastoria-web package \
  -Ddatastoria.web.api-base-url=https://api.example.com
```

默认使用 `/backend`，适用于项目统一安装包中的同源代理。前端模块制品位于：

```text
datastoria-web/target/datastoria-web-0.0.1-SNAPSHOT-standalone.tar.gz
```

构建完整 reactor 但临时跳过前端：

```bash
./mvnw package -Ddatastoria.web.skip=true
```

该参数仅用于后端快速迭代，不能作为发布验证结果。

## 7. 浏览器与 IDE 调试

浏览器开发者工具重点检查：

1. Network 中 API 请求是否指向 `127.0.0.1:8080`，状态码和响应体是否符合预期；
2. Agent SSE 请求的响应类型、事件顺序和 `Last-Event-ID`；
3. 请求体中是否只有供应商、模型或连接 ID，不能包含 API Key；
4. Console 中的 hydration、React state update 和动态导入异常；
5. Application 中是否残留旧版本 localStorage/sessionStorage 配置。

前端断点可直接在 Chrome/Edge Sources 中打开 `.tsx` 源文件。使用 VS Code 时，可创建
Chrome 类型的 Launch Configuration，URL 指向 `http://localhost:3000`，`webRoot` 指向
`${workspaceFolder}/datastoria-web`。Next.js server component 或代理代码的日志在运行
`npm run dev` 的终端中查看，浏览器控制台只显示客户端日志。

## 8. 修改后的最小验证

在 `datastoria-web` 目录执行：

```bash
npm run format:check
npm run lint
npm run typecheck
npm test -- --run
npm run build
```

常用定向测试：

```bash
npm test -- --run src/lib/connection/connection.test.ts
npm test -- --run src/components/settings/models/model-manager.test.ts
```

涉及 Java API 路径或前后端契约时，还应在仓库根目录执行：

```bash
./mvnw -pl datastoria-boot -am test
```

发布前使用统一入口验证：

```bash
bin/build-package.sh
```

## 9. 常见问题

### `concurrently: command not found`

依赖没有完整安装：

```bash
cd datastoria-web
npm ci --force
```

### `pnpm: command not found`

```bash
npm install --global pnpm
```

### `.next` 缓存或模块路径异常

停止开发服务器后执行：

```bash
rm -rf datastoria-web/.next
cd datastoria-web
npm run typecheck
npm run dev
```

不要在 Next.js 仍运行时删除 `.next`。

### 页面能打开，但 API 请求失败

依次确认：

```bash
curl -fsS http://127.0.0.1:8080/actuator/health
```

- `NEXT_PUBLIC_DATASTORIA_JAVA_API_BASE_URL` 是否与后端端口一致；
- 修改环境变量后是否重启了 Next.js；
- 浏览器 Network 中是否出现 401、403、404 或 CORS 错误；
- 本地身份是否为 `dev@example.com`；
- 后端是否使用 `dev` Profile。

### `CLIENT_SECRET_NOT_ALLOWED`

浏览器正在发送旧版 `secret` 字段。清除旧页面状态，并在“系统设置 → 模型”通过服务端接口
保存 API Key；不要把 Secret 放入前端请求或公开环境变量。

### Maven 提示 JDK 版本不匹配

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -version
```
