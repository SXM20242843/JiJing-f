# 即境 uni-app 游客端 UI 优化 Skill

## 何时使用这个 Skill

当用户要求对"即境"APP 的 uni-app 游客端（`app-live2d/`）进行 **UI 层面优化** 时，使用本 Skill。包括但不限于：

- 调整页面布局、间距、颜色、字体、卡片样式
- 优化三阶段闭环的视觉引导和过渡体验
- 改进信息展示层级（如导览入口卡、报告摘要卡）
- 统一 rpx 单位、色板、圆角、阴影等设计 token
- 调整按钮位置、图标、提示文案等交互细节
- 优化空态、加载态、错误态的展示

**不属于 UI 优化**（不触发本 Skill）的场景：
- 改动业务逻辑（如 visitId 创建时机、API 调用顺序）
- 改动 Android 原生工程
- 改动后端接口
- 新增功能模块

---

## 项目背景

"即境"是一款面向景区游客的 AI 数字人导览 APP，包含三端：

| 端 | 路径 | 角色 |
|---|---|---|
| uni-app 游客端 | `app-live2d/` | 游客交互界面（本 Skill 的作用范围） |
| Android 原生 Live2D | `CubismSdkForJava-5-r.4.1/` | 数字人渲染、语音交互、现场导览 |
| SpringBoot 后端 | `scenic-ai-guid-backend1/` | 景区数据、导览会话、行为记录、报告生成 |

当前是**比赛收口阶段**，项目规则由 `AGENTS.md` 严格约束。所有修改必须保持最小改动，优先保证能编译、能运行、能演示。

---

## 三阶段闭环说明

这是项目的核心体验链路，**所有 UI 优化必须服务于这个闭环**：

```
游览前规划  →  现场数字人导览  →  游览后报告生成
(行前模式)     (现场模式)         (报告模式)
```

### 阶段一：游览前规划（行前模式）

- **入口**：首页（`pages/index/index`）的导览入口卡、AI 助手 tab（`pages/guide/guide`）、景区列表（`pages/scenic/scenic`）
- **核心行为**：搜索景区、查看景区/景点详情、咨询 AI 助手、收藏景点
- **UI 特征**：蓝色系（`#2f80ed`），标签显示"行前规划模式"
- **关键约束**：此阶段 **不允许创建 visitId**，不允许调用 `/api/visit/start`

### 阶段二：现场数字人导览（现场模式）

- **入口**：首页检测到进入景区（GPS/NFC/演示）后，弹出到达提醒 → 填写出行信息 → 提交 → 打开 Android 原生 Live2D 页面
- **核心行为**：AI 数字人讲解景点、语音问答、主动请求路线推荐
- **UI 特征**：绿色系（`#18b368`），标签显示"现场导览模式"，卡片边框变为绿色
- **关键约束**：必须先创建 visitId 才能进入原生页面；普通景点讲解不自动弹出路线卡片

### 阶段三：游览后报告生成（报告模式）

- **入口**：结束导览后自动跳转（`pages/visit/report`），首页报告卡片，个人中心"最近游玩报告"
- **核心行为**：查看景点停留明细、AI 对话统计、消费记录、满意度反馈、相似景区推荐
- **UI 特征**：蓝色 hero + 白色卡片，报告摘要网格
- **关键约束**：结束后首页应突出展示"查看报告"入口，引导用户进入闭环终点

---

## 页面结构总览

### tabBar 页面（4 个）
| 页面 | 路径 | navigationBarTitleText |
|---|---|---|
| 首页 | `pages/index/index` | 首页 |
| AI助手 | `pages/guide/guide` | 即境 AI 助手 |
| 景区 | `pages/scenic/scenic` | 景区 |
| 我的 | `pages/mine/mine` | 我的 |

### 二级页面（按功能分组）

**导览相关：**
- `pages/visit/start` — 现场导览启动
- `pages/visit/report` — 游玩报告

**景区相关：**
- `pages/park/detail` — 景区详情
- `pages/scenic/detail` — 景点详情

**个人中心：**
- `pages/mine/favorite` — 收藏景点
- `pages/mine/consult` — 咨询记录
- `pages/mine/profile` — 我的偏好
- `pages/mine/settings` — 系统设置

**其他：**
- `pages/help/help` — 使用帮助
- `pages/map/map` — 景区地图
- `pages/map/test` — 地图测试
- `pages/login/login` — 登录
- `pages/register/register` — 注册
- `pages/pay/simulate` — 模拟支付
- `pages/pay/records` — 消费记录
- `pages/consume/records` — 消费记录

### 关键公共模块
- `common/onsite-guide.js` — 现场导览状态管理
- `common/location-utils.js` — 位置工具
- `common/scenic-geofence.js` — 景区围栏
- `utils/openNativeLive2D.js` — 打开 Android 原生 Live2D 页面的桥接
- `utils/visit.js` — 导览生命周期（visitId、start/end、状态查询、出行信息）
- `utils/auth.js` — 登录认证
- `utils/api.js` — API 基础配置
- `utils/behavior.js` — 用户行为追踪
- `utils/favorite.js` — 收藏管理
- `utils/image.js` — 图片 URL 解析

---

## 允许修改范围

以下文件/内容**可以在 UI 优化时修改**：

| 文件 | 允许改什么 |
|---|---|
| `pages/index/index.vue` | `<style>` 块、布局结构、卡片样式、按钮位置、文案（不改逻辑） |
| `pages/guide/guide.vue` | `<style>` 块、快捷入口布局、示例文案样式、输入框样式 |
| `pages/help/help.vue` | `<style>` 块、流程展示布局、帮助卡片样式 |
| `pages/scenic/scenic.vue` | `<style>` 块、列表卡片布局、搜索框样式 |
| `pages/scenic/detail.vue` | `<style>` 块、信息展示布局、操作按钮样式 |
| `pages/park/detail.vue` | `<style>` 块、景点列表卡片、操作按钮样式 |
| `pages/visit/report.vue` | `<style>` 块、报告摘要布局、反馈表单样式 |
| `pages/mine/mine.vue` | `<style>` 块、菜单列表、个人信息卡片样式 |
| `pages/visit/start.vue` | `<style>` 块 |
| `pages/mine/favorite.vue` | `<style>` 块 |
| `pages/mine/consult.vue` | `<style>` 块 |
| `pages/mine/profile.vue` | `<style>` 块 |
| `pages/mine/settings.vue` | `<style>` 块 |
| `pages/map/map.vue` | `<style>` 块 |
| `pages/login/login.vue` | `<style>` 块 |

**基本原则：可以改 `<style>` 块、布局 HTML 结构（只要不删掉关键 `@click` 绑定和业务 `v-if`/`v-show`）、展示文案、视觉样式。不改变 `<script setup>` 中的业务逻辑。**

---

## 禁止修改范围（绝对红线）

以下内容**任何时候都不能在 UI 优化中修改**：

### 禁止改动的文件
1. **`CubismSdkForJava-5-r.4.1/` 下所有文件**（Android 原生 Live2D 工程）
2. **`MainActivity.java`** 及其所在目录的所有 `.java` 文件
3. **`scenic-ai-guid-backend1/` 下所有文件**（后端接口、数据库表结构）
4. **`AGENTS.md`**（项目比赛收口规则）

### 禁止改动的业务逻辑
5. **visitId 创建时机**：不允许在普通浏览、AI 助手对话、景区列表浏览时创建 visitId
6. **现场导览开始/结束主流程**：不允许改动 `startVisitGuide` → `openNativeLive2DGuide` → 原生 Live2D → spot_enter/leave → `/api/visit/end` 的完整链路
7. **SSE/TTS/ASR/口型同步主链路**：这些是 Android 原生负责的，uni-app 端不能触碰
8. **NFC 主流程**：原生 Live2D 里的 NFC 触发逻辑不能改
9. **后端接口路径和接口字段**：所有 `/api/` 接口的 URL 和请求/响应字段格式不能改
10. **数据库结构**：不允许新增/删除/修改数据库表和字段

### 禁止改动的交互逻辑
11. **不允许把"AI助手"和"现场导览"混成一个入口**：AI 助手 tab 是行前规划入口，现场导览入口在首页的导览入口卡
12. **普通景点讲解不允许自动弹出路线卡片**：只有明确提出"推荐路线"/"规划路线"意图时才生成路线卡片
13. **不允许删除或禁用"演示进入景区"功能**：这是比赛演示的关键入口
14. **不允许改变出行信息表单的字段**：人数、类型、偏好、游玩时长四个字段是后端要求的
15. **不允许把 `openNativeLive2D` 的调用时机提前**：必须先拿到 visitId 再打开原生页面

### 禁止破坏的 UI 状态机
16. **三阶段状态标签不能混**：
    - 行前模式 → 蓝色标签 "行前规划模式"
    - 现场模式 → 绿色标签 "现场导览模式"
    - 报告模式 → "游玩报告"（走报告流程）
17. **首页导览入口卡（`guide-entry-card`）的状态切换逻辑不能改**：
    - `hasRunningVisit` → 显示"现场导览进行中"，按钮"继续导览"
    - `hasReport` → 显示"本次导览已完成"，按钮"查看报告"
    - `isInsideScenic` → 显示"现场导览已就绪"，按钮"开启导览"
    - 默认 → 显示"即境 AI 导览助手"，按钮"立即进入"

---

## 页面优化优先级

根据比赛演示重要性和当前 UI 完整度排序：

### 优先级 1（最高 — 直接影响比赛演示闭环）

1. **首页导览入口卡**（`pages/index/index.vue` 的 `.guide-entry-card`）
   - 三阶段状态切换的视觉区分度
   - 现场模式下的绿色边框和背景渐变是否足够醒目
   - 报告完成后的卡片是否足够突出
   - **不建议大改**，当前逻辑已经很完善

2. **游玩报告页**（`pages/visit/report.vue`）
   - 报告 hero 区域的信息层次
   - 景点停留明细的可读性
   - 反馈表单的交互体验
   - **这是闭环亮点页，值得重点打磨**

3. **首页出行信息弹窗**（`pages/index/index.vue` 的 `.trip-info-dialog`）
   - Chip 选择器的视觉反馈
   - 按钮布局和文案

### 优先级 2（中 — 用户日常接触频率高）

4. **AI 助手页**（`pages/guide/guide.vue`）
   - 快捷入口网格布局
   - 匹配结果展示
   - 示例 chip 的视觉层次

5. **景区列表/详情页**（`pages/scenic/scenic.vue`、`pages/park/detail.vue`）
   - 卡片信息密度
   - 图片加载失败占位符
   - 操作按钮布局

6. **景点详情页**（`pages/scenic/detail.vue`）
   - 操作栏固定在底部的样式
   - 信息分区卡片

### 优先级 3（低 — 辅助功能页面）

7. **使用帮助页**（`pages/help/help.vue`）— 三阶段流程图展示
8. **个人中心**（`pages/mine/mine.vue`）— 菜单列表样式
9. **登录/注册页**（`pages/login/login.vue`、`pages/register/register.vue`）
10. **地图页**（`pages/map/map.vue`）

---

## UI 风格建议

基于当前代码提炼的设计模式（新增 UI 必须遵循）：

### 色板
```css
/* 主色 - 行前/通用 */
--color-primary: #2f80ed;
--color-primary-light: #eff6ff;
--color-primary-gradient: linear-gradient(135deg, #2f80ed 0%, #56ccf2 100%);

/* 强调色 - 现场/已完成 */
--color-onsite: #18b368;
--color-onsite-light: #ecfdf5;
--color-onsite-gradient: linear-gradient(135deg, #18b368 0%, #4cd7a3 100%);

/* 警告/报告 */
--color-orange: #ea580c;
--color-orange-light: #fff7ed;

/* 中性色 */
--color-bg: #f5f7fb;
--color-bg-end: #eef4ff;
--color-card: #ffffff;
--color-text-primary: #1f2937;
--color-text-secondary: #374151;
--color-text-tertiary: #6b7280;
--color-text-muted: #9ca3af;
--color-border: #eef0f4;
--color-divider: #f3f4f6;
```

### 圆角
- 页面卡片（`.card`）：`28rpx`
- Hero 区域：`32rpx`
- 按钮/Chip：`999rpx`（全圆角）
- 小图标卡片：`20-24rpx`

### 阴影
```css
box-shadow: 0 12rpx 28rpx rgba(15, 23, 42, 0.06);
```

### 间距
- 页面 padding：`24rpx`
- 卡片内边距：`24-30rpx`
- 卡片间距：`16-24rpx`
- 元素内间距：`12-18rpx`

### 字体
- 页面大标题：`38-46rpx`，`font-weight: 700-800`
- 区块标题：`30-32rpx`，`font-weight: 700`
- 卡片标题：`28-30rpx`，`font-weight: 700`
- 正文：`24-26rpx`
- 辅助文字：`22-24rpx`
- 小标签：`20-22rpx`

### 组件模式
- **卡片**：白色背景 + `28rpx` 圆角 + 统一阴影
- **Hero**：蓝/绿渐变 + 两个半透明圆背景装饰 + 白色文字
- **按钮**：全圆角 `999rpx`，主按钮渐变背景，次按钮 `#f3f4f6` 背景
- **Chip/Tag**：全圆角，主色浅背景 + 主色文字
- **列表项**：flex 布局，底部 `1rpx solid #eef2f7` 分割线

---

## 关键业务规则

这些规则决定了 UI 的展示逻辑，进行 UI 优化时 **必须保持这些展示条件不变**：

### 首页导览入口卡状态机
```
┌──────────────────────────────────────────────────────┐
│ hasRunningVisit  →  标签"现场导览模式" 绿色           │
│                     标题"现场导览进行中"              │
│                     按钮"继续导览" → 打开原生Live2D   │
├──────────────────────────────────────────────────────┤
│ hasReport        →  标签"游玩报告"                   │
│ (且无进行中导览)    标题"本次导览已完成"              │
│                     按钮"查看报告" → goVisitReport()  │
├──────────────────────────────────────────────────────┤
│ isInsideScenic   →  标签"现场导览模式" 绿色           │
│ (且无进行中/报告)   标题"现场导览已就绪"              │
│                     按钮"开启导览" → goVisitStart()   │
├──────────────────────────────────────────────────────┤
│ 默认(未进入景区)  →  标签"行前规划模式" 蓝色          │
│                     标题"即境 AI 导览助手"            │
│                     按钮"立即进入" → goGuide()        │
└──────────────────────────────────────────────────────┘
```

### visitId 创建规则
- **唯一入口**：用户提交出行信息表单（`submitTripInfoForm`）→ `startVisitGuide()` → `/api/visit/start`
- **禁止创建**：AI 助手对话、景区列表浏览、景区/景点详情查看、收藏操作、地图查看、报告查看

### 路线卡片弹出规则
- **触发条件**：用户明确输入/点击"推荐路线"/"规划路线"
- **禁止触发**：普通景点讲解、欢迎语、AI 自由对话

### 现场导览开启条件
- **GPS 真实进入**：首页定位检测到进入景区范围 + 用户确认弹窗
- **演示进入**：点击"演示进入景区" → 选择演示景区 → 模拟命中
- **禁止**：AI 助手页直接开启现场导览、景区详情页直接开启现场导览

---

## 每次修改前必须说明

1. **要修改哪个文件**（路径）
2. **修改理由**（与三阶段闭环的哪个体验点相关）
3. **涉及 `<style>` 还是 `<template>` 还是两者**
4. **是否会影响任何 `@click` 绑定或 `v-if`/`v-show` 条件**
5. **是否会影响 `computed` 属性的依赖**
6. **预期视觉效果变化**

---

## 每次修改后必须输出

1. **改动文件列表**（含路径）
2. **每个文件的具体改动点**（CSS 类名、样式属性、布局变化）
3. **改动前后对比说明**
4. **验证步骤**（手动验证清单）
5. **是否影响三阶段闭环的任何状态展示**

---

## 验证步骤

每次 UI 修改后，必须按以下顺序验证（模拟比赛演示路径）：

1. **首页默认态**：打开首页 → 确认显示"行前规划模式"标签 → 确认"当前未进入景区" → 确认不显示"继续导览"
2. **演示进入**：点击"演示进入景区" → 确认弹窗"欢迎来到灵山胜境，是否开启智能导览？" → 点击"开启智能导览" → 确认出行信息弹窗正常展示
3. **填写出行信息**：选择人数/类型/偏好/时长 → 点击"提交并开启导览" → 确认调用了 startVisitGuide
4. **AI 助手页**：切换到 AI 助手 tab → 输入"介绍灵山胜境" → 确认匹配景区 → 点击后进入原生页面（非现场模式）
5. **景区列表**：切换到景区 tab → 确认列表正常加载 → 点击景区进入详情 → 确认 AI 讲解按钮可用
6. **个人中心**：切换到我的 tab → 确认登录状态展示 → 确认菜单入口正常
7. **使用帮助**：从首页快捷服务进入帮助页 → 确认三阶段说明完整展示

---

## 其他重要约定

1. **不要为了"更好看"而删除已有的业务注释或逻辑代码**
2. **不要改变 uni-app 的 rpx 自适应机制**（所有尺寸保持 rpx）
3. **不要引入新的 npm 依赖或 CSS 框架**
4. **不要改变 `pages.json` 的 tabBar 配置和页面路径**
5. **颜色统一使用当前色板**，不要引入新的主色调
6. **所有图片 URL 处理保持 `resolveImageUrl` 工具函数的调用方式**
7. **文字截断保持 `formatShortDesc` + `-webkit-line-clamp` 的方式**
8. **弹窗/Modal 的 z-index 层级保持 `99`（mask）**

---

## 项目文件索引

本 Skill 基于以下真实项目文件生成：

| 文件 | 行数 | 核心内容 |
|---|---|---|
| `AGENTS.md` | 403 | 比赛收口规则、三端职责、A/B 流程、禁止事项 |
| `pages.json` | 151 | 页面注册、tabBar 配置 |
| `pages/index/index.vue` | 2674 | 首页：Hero、定位状态、导览入口卡、出行信息弹窗、热门推荐、快捷服务 |
| `pages/guide/guide.vue` | 1003 | AI 助手：意图识别、景区匹配、打开讲解/路线规划 |
| `pages/help/help.vue` | 633 | 使用帮助：三阶段说明、核心能力、推荐流程、常见问题 |
| `pages/scenic/scenic.vue` | 606 | 景区列表：热门/全部切换、搜索、AI 讲解入口 |
| `pages/scenic/detail.vue` | 982 | 景点详情：介绍、亮点、提示、收藏、路线推荐、AI 讲解 |
| `pages/park/detail.vue` | 939 | 景区详情：景点列表、单体景区、AI 讲解、地图入口 |
| `pages/visit/report.vue` | 1273 | 游玩报告：摘要、出行信息、景点停留、消费、反馈、推荐 |
| `pages/mine/mine.vue` | 962 | 个人中心：个人信息、菜单列表、登录状态 |
| `utils/openNativeLive2D.js` | 1106 | 原生桥接：Intent 构造、参数传递、数字人配置、状态锁定 |
| `common/onsite-guide.js` | — | 现场导览状态管理 |
| `common/location-utils.js` | — | 位置工具函数 |
| `common/scenic-geofence.js` | — | 景区地理围栏 |
