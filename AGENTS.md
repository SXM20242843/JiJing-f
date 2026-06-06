# 即境 AI 数字人导览 APP｜比赛收口规则

这是“即境 AI 数字人导览 APP”软件杯比赛收口仓库，包含三端：

1. app-live2d：uni-app 游客端前端
2. android-live2d：Android 原生 Live2D 导览页
3. scenic-ai-backend：SpringBoot 小后端

当前阶段是比赛收口阶段，不允许大重构，不允许改无关功能，不允许新增复杂架构。

唯一目标：严格按下面 A/B 流程跑通并稳定现场导览演示闭环。

---

## 一、总原则

1. 首页只负责定位、状态展示、弹窗入口，不允许普通浏览时创建 visitId。
2. 只有用户确认开启现场导览，并提交本次出行信息后，才允许调用 `/api/visit/start`。
3. `openNativeLive2D` 只负责打开 Android 原生 Live2D 页面，不允许在内部偷偷创建 visitId。
4. 原生 Live2D 页面启动后只播放欢迎语，不允许自动推荐路线。
5. 路线推荐只能由用户主动点击“推荐路线”或输入路线问题触发。
6. 现场导览结束必须按顺序执行：当前景点 `spot_leave` → `/api/visit/end`。
7. 禁止 `anonymous`、`visitor_`、`android-live2d-*` 创建 visitId 或写入核心游览数据。
8. 不允许删除或破坏现有 Live2D 数字人模型配置。
9. 不允许改数据库表结构，除非明确说明必须新增字段。
10. 不允许把普通 AI 助手、景区讲解、景点讲解入口改成现场导览入口。
11. 所有修改必须保持最小改动，优先保证能编译、能运行、能演示。
12. 每次修改完成后必须列出：改动文件、改动点、验证步骤。

---

## 二、A. 用户未到达景区流程

目标：用户未进入景区时，首页正常展示，不创建 visitId，不误弹现场导览。

完整流程如下：

1. 打开首页。
2. 展示即境首页。
3. 展示 GPS 能力说明。
4. 获取真实定位。
5. 判断未进入景区。
6. 显示：当前定位状态：当前未进入景区。
7. 查询是否有进行中导览。
8. 没有进行中导览，不显示继续导览。
9. 查询是否有已完成报告。
10. 如果有报告，显示报告卡片。
11. 普通首页继续展示搜索、快捷服务、热门推荐、公告。
12. 用户可以浏览景区、问 AI、查看收藏。
13. 这些操作都不创建 visitId。
14. 用户点击“演示进入景区”。
15. 前端模拟命中灵山胜境。
16. 弹出：欢迎来到灵山胜境，是否开启智能导览？
17. 进入到达景区流程。

### A 流程硬性要求

- 未进入景区时，不允许自动调用 `/api/visit/start`。
- 未进入景区时，如果没有进行中导览，不允许显示“继续导览”。
- 普通首页、热门推荐、景区列表、AI 助手、收藏、报告卡片都不能创建 visitId。
- “演示进入景区”只负责模拟命中景区并弹提示，不允许直接进入原生页面。
- 只有用户点击“开启智能导览”后，才能进入 B 流程。

---

## 三、B. 用户到达景区流程

目标：用户进入景区后，确认开启现场导览，创建 visitId，进入原生 Live2D，完成路线、景点、报告闭环。

完整流程如下：

1. 打开首页。
2. 展示 GPS 能力说明。
3. 获取真实定位。
4. 判断已进入灵山胜境。
5. 显示：当前定位状态：已进入灵山胜境。
6. 查询是否有进行中导览。
7. 如果没有进行中导览，弹出到达提醒。
8. 用户点击开启智能导览。
9. 弹出本次出行信息表单。
10. 用户填写人数、类型、偏好、游玩时长。
11. 前端调用 `/api/visit/start`。
12. 后端创建 `tourist_visit_session`。
13. 后端返回 visitId。
14. 前端调用 `openNativeLive2D`。
15. 打开原生 Live2D 现场导览页。
16. 原生页接收 `visitId / areaId / scenicName / mode`。
17. 显示现场导览模式。
18. 播放欢迎语。
19. 不自动推荐路线。
20. 用户模拟到达灵山大佛。
21. 原生调用 `/api/visit/spot/enter`。
22. 后端记录景点进入。
23. 原生向 AI 请求当前位置讲解或路线推荐。
24. AI 返回讲解、音频、嘴型、路线。
25. 小后端保存聊天记录。
26. 小后端保存路线计划和节点。
27. 原生展示路线地图卡片。
28. 记录 `map_card_show`。
29. 用户点击开始导航。
30. 记录 `navigation_start`。
31. 用户点击路线节点。
32. 记录 `route_spot_click`。
33. 用户模拟到达下一个节点。
34. 记录新的 `spot_enter`。
35. 离开上一个景点时记录 `spot_leave`。
36. AI 讲解当前景点。
37. 用户点击结束导览。
38. 原生弹出继续导览 / 结束并查看报告。
39. 用户确认结束。
40. 原生先调用当前景点 `spot_leave`。
41. 再调用 `/api/visit/end`。
42. 后端更新 visit 状态。
43. 后端生成游玩报告。
44. 后端更新用户画像。
45. 后端推荐相似景区。
46. 后端记录结束事件。
47. 原生返回首页或报告页。
48. 首页显示本次导览已完成 / 查看报告。
49. 首页继续 GPS 检测。
50. 点击演示进入景区后，可以再次弹现场导览提醒。

---

## 四、三端职责划分

### 1. app-live2d 前端职责

前端只负责：

- 首页展示。
- GPS 状态展示。
- 演示进入景区。
- 到达提醒弹窗。
- 本次出行信息表单。
- 调用 `/api/visit/start`。
- 拿到 visitId 后调用 `openNativeLive2D`。
- 查询 `/api/app/visit/status` 或 `/api/visit/status`。
- 展示进行中导览。
- 展示已完成报告卡片。

前端禁止：

- 普通浏览时创建 visitId。
- AI 助手普通入口创建 visitId。
- 景区讲解、景点讲解创建 visitId。
- 收藏、查看地图、查看报告创建 visitId。
- `openNativeLive2D` 内部自动创建 visitId。
- App.vue onShow 反复弹现场导览提示。

---

### 2. android-live2d 原生职责

原生只负责：

- 接收前端传入的 `visitId / areaId / scenicName / mode / userId / token`。
- 显示现场导览模式。
- 播放欢迎语。
- 用户主动提问。
- 用户主动推荐路线。
- 展示 AI 返回的路线地图卡片。
- 上报 `map_card_show`。
- 上报 `navigation_start`。
- 上报 `route_spot_click`。
- 模拟到达景点。
- 调用 `/api/visit/spot/enter`。
- 调用 `/api/visit/spot/leave`。
- 调用 `/api/visit/end`。
- 结束成功后返回首页或报告页。

原生禁止：

- 启动后自动推荐路线。
- 没有 visitId 时写入景点进入、离开、结束导览。
- 结束导览时跳过当前景点 `spot_leave`。
- 用户没有主动操作时自动弹路线卡片。
- 使用 `android-live2d-*` 当真实 userId。

---

### 3. scenic-ai-backend 小后端职责

后端负责：

- `/api/visit/start` 创建或复用 `tourist_visit_session`。
- `/api/visit/spot/enter` 写入 `tourist_spot_visit_record`。
- `/api/visit/spot/leave` 更新景点离开和停留时间。
- `/api/visit/end` 更新游览状态为已完成。
- 自动关闭未离开的景点。
- 记录 `visit_start / spot_enter / spot_leave / visit_end` 行为事件。
- `/api/guide/chat` 保存 `chat_session / chat_message`。
- AI 返回路线时保存 `tourist_route_plan / tourist_route_plan_node`。
- `/api/app/behavior/event` 保存 `map_card_show / navigation_start / route_spot_click`。
- `/api/app/visit/status` 返回进行中导览和已完成报告。
- `/api/app/visit/report/detail` 返回游玩报告详情。
- 游览结束后支持报告展示、画像更新、相似景区推荐。

后端禁止：

- 接受临时 userId 创建 visit。
- 没有 visitId 时写核心游览数据。
- 普通 AI 聊天误创建 visitId。
- 路线推荐失败影响普通问答。
- 行为事件失败影响主流程结束。

---

## 五、优先修改顺序

### 第一阶段：只收口前端首页入口

只允许优先修改：

- `app-live2d/pages/index/index.vue`
- `app-live2d/App.vue`
- `app-live2d/utils/openNativeLive2D.js`
- `app-live2d/common/onsite-guide.js`
- `app-live2d/common/location-utils.js`
- `app-live2d/common/scenic-geofence.js`

目标：

1. 首页正确展示定位状态。
2. 未进入景区不显示继续导览。
3. 已完成报告卡片可以显示。
4. 普通浏览不创建 visitId。
5. 演示进入景区只弹提醒。
6. 点击开启导览后弹本次出行信息。
7. 提交出行信息后才调用 `/api/visit/start`。
8. 拿到 visitId 后才打开原生 Live2D。
9. `openNativeLive2D` 不再内部创建 visitId。

---

### 第二阶段：只收口 Android 原生 MainActivity

只允许优先修改：

- `android-live2d/.../MainActivity.java`

必要时才修改：

- `LAppDefine.java`
- `LAppLive2DManager.java`

目标：

1. 原生启动只播放欢迎语。
2. 不自动推荐路线。
3. 用户主动点推荐路线后才请求 AI。
4. 路线卡片显示后记录 `map_card_show`。
5. 点击开始导航记录 `navigation_start`。
6. 点击路线节点记录 `route_spot_click`。
7. 模拟到达下一站时，先 `spot_leave` 上一站，再 `spot_enter` 下一站。
8. 结束导览时，先 `spot_leave` 当前景点，再 `/api/visit/end`。
9. 结束成功后返回首页或报告页。

---

### 第三阶段：只收口小后端主链路

只允许优先修改：

- `VisitController.java`
- `AppVisitController.java`
- `VisitService.java`
- `TouristVisitSessionMapper.java`
- `TouristSpotVisitRecordMapper.java`
- `GuideController.java`
- `GuideChatService.java`
- `BehaviorEventService.java`
- `BehaviorEventMapper.java`
- 路线入库相关 Service / Mapper

目标：

1. `/api/visit/start` 稳定创建或复用 visit。
2. `/api/visit/spot/enter` 稳定记录景点进入。
3. `/api/visit/spot/leave` 稳定记录景点离开。
4. `/api/visit/end` 稳定结束导览。
5. 结束时自动关闭未离开的景点。
6. `chat_session / chat_message` 绑定 visitId。
7. AI 返回 route 时保存路线计划和节点。
8. 行为事件正常写入。
9. 报告接口能返回本次导览数据。

---

## 六、验证流程

每次修改后必须按下面流程验证：

### 前端验证

1. 打开首页。
2. 未进入景区时显示“当前未进入景区”。
3. 不显示继续导览。
4. 普通浏览景区、AI 助手、收藏不创建 visitId。
5. 点击“演示进入景区”。
6. 弹出“欢迎来到灵山胜境，是否开启智能导览？”。
7. 点击开启智能导览。
8. 弹出本次出行信息表单。
9. 提交后才调用 `/api/visit/start`。
10. 返回 visitId 后打开原生页面。

### 原生验证

1. 原生页显示现场导览模式。
2. 只播放欢迎语。
3. 不自动推荐路线。
4. 点击推荐路线后才请求 AI。
5. AI 返回路线后显示路线卡片。
6. 数据库写入 `map_card_show`。
7. 点击开始导航后写入 `navigation_start`。
8. 点击路线节点后写入 `route_spot_click`。
9. 模拟到达灵山大佛后写入 `spot_enter`。
10. 到达下一个节点前先写入上一个 `spot_leave`。
11. 当前景点讲解正常。
12. 点击结束导览。
13. 弹出继续导览 / 结束并查看报告。
14. 确认结束后先写 `spot_leave`，再写 `/api/visit/end`。
15. 成功返回首页或报告页。

### 后端验证

检查数据库：

- `tourist_visit_session` 有记录。
- `tourist_visit_session.visit_status = COMPLETED`。
- `tourist_spot_visit_record` 有进入和离开时间。
- `chat_session` 有会话。
- `chat_message` 有用户问题和 AI 回复。
- `tourist_route_plan` 有路线。
- `tourist_route_plan_node` 有路线节点。
- `tourist_behavior_event` 有：
  - `visit_start`
  - `spot_enter`
  - `spot_leave`
  - `map_card_show`
  - `navigation_start`
  - `route_spot_click`
  - `visit_end`
- 报告接口能查到本次 visitId。

---

## 七、禁止事项

1. 禁止一次性大改三端。
2. 禁止删除已有功能。
3. 禁止为了修一个 bug 改动大量无关文件。
4. 禁止普通 AI 助手创建 visitId。
5. 禁止现场导览启动自动推荐路线。
6. 禁止无 visitId 写入景点记录。
7. 禁止无真实 userId 写入核心数据。
8. 禁止破坏 Live2D 数字人模型加载。
9. 禁止修改数据库表结构。
10. 禁止改动和本流程无关的管理端、大屏、图片、支付等功能。

---

## 八、当前比赛演示优先级

最高优先级：

1. 首页现场导览入口稳定。
2. visitId 创建时机正确。
3. 原生页面不自动推荐路线。
4. 路线卡片能展示。
5. 景点进入/离开能入库。
6. 结束导览能成功。
7. 报告页能看到本次数据。

低优先级，比赛前不要扩展：

1. 多景区真实围栏。
2. 完整地图实景导航。
3. 复杂用户画像算法。
4. 完整消费分析。
5. 多数字人动态换装。
6. 管理端复杂配置。
7. 大屏联动。
8. 非现场模式复杂优化。

---

## 九、CodeX 执行要求

CodeX 每轮任务必须遵守：

1. 先阅读本文件。
2. 先说明准备修改哪些文件。
3. 只改本轮目标相关文件。
4. 修改后列出完整 diff 摘要。
5. 给出手动验证步骤。
6. 遇到不确定逻辑时，优先保持现有可运行逻辑。
7. 不允许擅自重构整个项目。
8. 不允许引入新依赖。
9. 不允许改包名、路径、数据库结构。
10. 不允许为了“看起来更干净”删除已有兼容代码。

最终目标不是代码最优雅，而是比赛演示闭环稳定。