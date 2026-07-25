# DataStoria 中文开发文档

本文档为开发者提供 DataStoria 项目的完整开发指南。

---

## 📋 目录

- [项目概述](#项目概述)
- [环境准备](#环境准备)
- [项目结构](#项目结构)
- [安装与启动](#安装与启动)
- [开发工作流](#开发工作流)
- [代码规范](#代码规范)
- [提交规则](#提交规则)
- [常见问题](#常见问题)

---

## 项目概述

DataStoria 是基于 **Next.js 16 + React 19** 的现代化 ClickHouse 管理控制台，提供 AI 辅助的查询生成、数据可视化、性能分析和集群诊断。

### 核心特性

- **AI 功能**：自然语言查询生成、智能优化、可视化生成、多模型支持
- **查询体验**：高级 SQL 编辑器、错误诊断、执行计划可视化
- **集群管理**：多集群支持、实时监控、模式探索
- **安全隐私**：100% 本地执行、BYOK（自带 API Key）

---

## 环境准备

### 必备软件

| 软件    | 版本要求 | 用途         |
| ------- | -------- | ------------ |
| Node.js | v22+     | 运行时环境   |
| pnpm    | 任意版本 | 外部依赖管理 |
| Git     | 任意版本 | 版本控制     |

### 安装 Node.js 和 pnpm

```bash
# 安装 Node.js (推荐使用 nvm)
nvm install 22
nvm use 22

# 安装 pnpm
npm install -g pnpm
```

---

## 项目结构

```
datastoria/
├── src/                      # 源代码
│   ├── app/                  # Next.js App Routes 和 API Handlers
│   ├── components/            # React 组件
│   │   ├── ai/             # AI 相关组件
│   │   ├── chat/           # 聊天界面
│   │   ├── query-tab/      # 查询面板
│   │   ├── cluster-tab/    # 集群监控
│   │   └── ui/            # UI 通用组件
│   └── lib/                   # 核心库
│       ├── ai/             # AI 引擎 (Agent/Skill/Tool/Model)
│       ├── clickhouse/      # ClickHouse 客户端
│       ├── connection/      # 连接管理
│       └── storage/        # 数据持久化
├── external/                  # 外部依赖 (git 子模块)
│   ├── number-flow/       # 数字动画库 (pnpm workspace)
│   ├── vizlayer/          # 可视化引擎 (pnpm workspace)
│   ├── cmdk/              # 命令面板 (pnpm workspace)
│   └── clickhouse/        # Agent Skills (submodule)
├── docs/                     # VitePress 文档
├── resources/skills/         # 运行时技能文件 (从 external 同步)
└── AGENTS.md                # AI Agent 开发指南

```

### 核心目录说明

- **`src/lib/ai/`**: AI 引擎核心，包含 Agent、Skill、Tool、Model 管理
- **`external/`**: 嵌入的第三方依赖，**不要**当作普通应用代码修改
- **`worktrees/`**: 本地并行工作树，不要主动修改

---

## 安装与启动

### 方式 1：全新安装 (推荐)

```bash
# 1. 克隆仓库（带子模块）
git clone --recurse-submodules https://github.com/FrankChen021/datastoria.git
cd datastoria

# 2. 安装主项目依赖
npm install

# 3. 构建外部依赖
npm run build:deps

# 4. 启动开发服务器
npm run dev
```

### 方式 2：本地项目重装

```bash
# 1. 清理旧依赖
rm -rf node_modules package-lock.json

# 2. 重新安装
npm install

# 3. 构建外部依赖
npm run build:deps

# 4. 启动
npm run dev
```

### 方式 3：Submodule 问题修复

```bash
# 重新初始化子模块
git submodule update --init --recursive --remote

# 继续安装
npm install && npm run build:deps
```

### `build:deps` 脚本说明

此脚本按顺序构建所有外部依赖：

1. **sync:skills** → 同步技能文件到 `resources/skills/`
2. **build:number-flow-core** → 构建 `number-flow` 核心包
3. **build:number-flow-react** → 构建 `number-flow` React 包
4. **build:vizlayer-core** → 构建 `vizlayer` 核心包 (需要 pnpm)
5. **build:vizlayer-react** → 构建 `vizlayer` React 包 (需要 pnpm)
6. **build:cmdk** → 构建 `cmdk` 命令面板

---

## 开发工作流

### 1. 创建功能分支

**必须**使用 git worktree 进行开发：

```bash
# 创建新的 worktree
git worktree add ../datastoria-feature-xyz origin/master

# 进入 worktree
cd ../datastoria-feature-xyz

# 创建并切换分支
git checkout -b feature/xyz
```

### 2. 代码开发

- 遵循现有代码风格（查看相邻文件）
- 专注于单一职责
- 避免过度重构

### 3. 验证变更

```bash
# 类型检查
npm run typecheck

# 代码质量检查
npm run lint

# 运行测试
npm run test

# 构建检查
npm run build
```

### 4. 提交变更

```bash
# 添加变更
git add .

# 提交（提交前请手动完成格式化与验证）
git commit -m "feat: add new feature"
```

**注意**：`npm run lint` 和 `npm run typecheck` 不会自动触发，需在提交前手动运行。

---

## 代码规范

### TypeScript & React

- **命名约定**：
  - 组件文件：`PascalCase.tsx` (如 `ChatInput.tsx`)
  - 工具函数：`camelCase.ts` (如 `formatDate.ts`)
  - 常量：`UPPER_SNAKE_CASE.ts` (如 `API_ENDPOINTS.ts`)

- **导入顺序**：

  ```typescript
  // 1. 外部库

  // 3. 内部组件
  import { Button } from "@/components/ui/button";
  // 2. 内部类型
  import type { ChatMessage } from "@/lib/ai/chat-types";
  import type { NextPage } from "next";
  import { useState } from "react";
  ```

- **组件结构**：

  ```tsx
  export const ComponentName = React.forwardRef<HTMLDivElement, Props>(({ prop1, prop2 }, ref) => {
    // hooks
    const [state, setState] = useState();

    // effects
    useEffect(() => {
      // ...
    }, []);

    // handlers
    const handleClick = () => {
      // ...
    };

    // render
    return <div ref={ref}>...</div>;
  });
  ComponentName.displayName = "ComponentName";
  ```

### CSS 样式

- 使用 Tailwind CSS
- 避免内联样式，优先使用 `className`
- 按用途排序：`flex items-center gap-2 px-4 py-2`

### 错误处理

```typescript
// 组件中的异步错误
try {
  await someAction();
} catch (error) {
  showToast({
    title: "操作失败",
    description: error instanceof Error ? error.message : "未知错误",
    variant: "destructive",
  });
}

// Server 端错误
export async function POST(req: NextRequest) {
  try {
    // ...
  } catch (error) {
    console.error("[API] Error:", error);
    return NextResponse.json({ error: "Internal error" }, { status: 500 });
  }
}
```

---

## 提交规则

### 提交前检查清单

在提交代码前，必须完成以下检查：

- [ ] 变更符合本地代码风格和模式
- [ ] 导入、类型和调用点保持一致
- [ ] 错误和空状状态仍逻辑正确
- [ ] 运行了相关验证（typecheck、lint、test）
- [ ] 如果验证未运行，明确说明原因

### 提交信息格式

```
<scope>: <subject>

<body>
```

**作用域 (scope)**：

- `feat`: 新功能
- `fix`: 缺陷修复
- `docs`: 文档更新
- `refactor`: 代码重构
- `style`: 样式调整
- `test`: 测试相关
- `chore`: 构建/工具链

**示例**：

```
feat(ai): add support for Nebius provider

Implement model loading and streaming for Nebius API.
Includes validation tests for API key format.

Co-authored-by: GenX Code <genx@example.com>
```

### Git 工作流

#### 1. 开发分支

```bash
# 在 worktree 中开发
cd ../datastoria-feature-xyz

# 创建功能分支
git checkout -b feature/xyz

# 进行开发...
```

#### 2. 构建验证

```bash
# 在 push 前必须构建
npm run build
```

#### 3. 拉取最新主分支

```bash
git fetch origin master
```

#### 4. 列出变更文件（必须）

在创建 PR 前，必须先展示所有变更文件并等待用户确认：

```bash
git diff origin/master --stat
```

#### 5. 创建 Pull Request

获得用户明确批准后，创建 PR：

```bash
gh pr create --title "feat: add xyz feature" --body "..."
```

### 自动化 Hooks

**注意**：当前项目未配置 `pre-commit` hook。以下 npm 生命周期脚本仅在运行对应命令时触发：

- `npm run build` 前会通过 `prebuild` 运行 `build:deps`
- `npm run dev` 前会通过 `predev` 运行 `sync:skills`

**需要手动执行**：

- ❌ `npm run lint` (代码质量检查)
- ❌ `npm run typecheck` (类型检查)

---

## 常见问题

### Q1: 安装时报错 "dependency not found"

**原因**：外部依赖未正确构建。

**解决**：

```bash
# 1. 检查子模块状态
git submodule status

# 2. 确保外部依赖有 dist 目录
ls external/number-flow/packages/number-flow/dist/
ls external/vizlayer/packages/core/dist/

# 3. 重新构建
npm run build:deps
```

### Q2: 外部依赖链接失败

**现象**：`node_modules` 中没有符号链接到 external 包。

**解决**：

```bash
# 1. 清理并重装
rm -rf node_modules package-lock.json
npm install

# 2. 重新构建
npm run build:deps

# 3. 验证链接
ls -la node_modules/@vizlayer/
ls -la node_modules/@number-flow/
```

### Q3: npm install 非常慢或失败

**可能原因**：

- 网络问题
- npm registry 配置错误
- `.npmrc` 中的 `force=true` 导致

**解决**：

```bash
# 1. 检查 npm 配置
npm config list

# 2. 临时切换到官方 registry
npm config set registry https://registry.npmjs.org/

# 或使用淘宝镜像（国内）
npm config set registry https://registry.npmmirror.com/

# 3. 清理并重试
rm -rf node_modules package-lock.json
npm install --registry=https://registry.npmmirror.com/
```

### Q4: TypeScript 类型错误

**现象**：`pnpm` 构建的外部依赖在主项目中类型不匹配。

**解决**：

```bash
# 1. 清理类型缓存
rm -rf .next

# 2. 重新构建类型
npm run build:deps

# 3. 运行类型检查
npm run typecheck
```

### Q5: 开发服务器启动失败

**常见原因**：

- 端口 3000 被占用
- `.env` 文件配置错误
- 外部依赖未构建

**排查步骤**：

```bash
# 1. 检查端口占用
lsof -i :3000

# 2. 验证 .env 格式
cat .env

# 3. 检查构建状态
ls external/*/packages/*/dist/

# 4. 查看详细错误
npm run dev 2>&1
```

### Q6: Git worktree 问题

**现象**：worktree 文件夹残留或冲突。

**解决**：

```bash
# 1. 列出所有 worktrees
git worktree list

# 2. 删除指定 worktree
git worktree remove ../datastoria-feature-xyz

# 3. 或删除所有 worktrees (谨慎!)
# git worktree prune
```

---

## 调试技巧

### 开发模式调试

```bash
# 启动开发服务器
npm run dev

# 查看浏览器控制台
# 1. 打开 Chrome DevTools (F12)
# 2. 查看 Console 标签页的错误和日志
# 3. 查看 Network 标签页的 API 请求
```

### Server 端点调试

```bash
# 查看日志输出
npm run dev

# 添加调试日志
console.log("[DEBUG] Processing request:", req);
```

### AI 模型调用调试

在 `.env` 中设置调试模式：

```env
# 启用 LLM 调试日志
LOG_LEVEL=debug
```

---

## 相关文档

- [AGENTS.md](AGENTS.md) - AI Agent 开发指南
- [README.md](README.md) - 项目概述和用户指南
- [docs/dev/llm-provider-api-key.md](docs/dev/llm-provider-api-key.md) - API Key 配置
- [docs/dev/authentication.md](docs/dev/authentication.md) - 认证设置

---

## 反馈与贡献

如有问题或建议，请：

- 提交 Issue: https://github.com/FrankChen021/datastoria/issues
- 提交 PR: https://github.com/FrankChen021/datastoria/pulls
