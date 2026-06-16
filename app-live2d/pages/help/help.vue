<template>
  <view class="page">
    <view class="hero">
      <view class="hero-bg-circle hero-bg-circle-1"></view>
      <view class="hero-bg-circle hero-bg-circle-2"></view>

      <view class="hero-content">
        <view class="hero-badge">JIJING SMART GUIDE</view>
        <view class="hero-title">即境三阶段智慧导览流程</view>
        <view class="hero-subtitle">
          覆盖游览前规划、现场 AI 数字人导览、游览后报告生成，形成完整智慧文旅闭环。
        </view>
      </view>
    </view>

    <view class="section-header">
      <view class="section-title">三阶段闭环</view>
      <view class="section-more">完整体验链路</view>
    </view>

    <view class="stage-list">
      <view class="stage-card card stage-before">
        <view class="stage-index">01</view>
        <view class="stage-main">
          <view class="stage-tag">游览前</view>
          <view class="stage-title">行前规划</view>
          <view class="stage-desc">
            未到达景区时，可通过首页、景区列表和 AI 助手查询景区信息、了解景点内容、咨询路线建议。
          </view>
        </view>
      </view>

      <view class="stage-card card stage-onsite">
        <view class="stage-index">02</view>
        <view class="stage-main">
          <view class="stage-tag">现场中</view>
          <view class="stage-title">AI 数字人现场导览</view>
          <view class="stage-desc">
            到达景区后，可结合 GPS/NFC 或演示进入景区开启现场导览，体验数字人讲解、语音问答和路线推荐。
          </view>
        </view>
      </view>

      <view class="stage-card card stage-report">
        <view class="stage-index">03</view>
        <view class="stage-main">
          <view class="stage-tag">游览后</view>
          <view class="stage-title">自动生成游玩报告</view>
          <view class="stage-desc">
            结束导览后，系统会汇总景点停留、AI 对话、路线记录、消费记录等信息，生成本次游玩报告。
          </view>
        </view>
      </view>
    </view>

    <view class="section-header">
      <view class="section-title">核心能力</view>
      <view class="section-more">智慧文旅服务</view>
    </view>

    <view class="ability-grid">
      <view class="ability-card card">
        <view class="ability-icon">🧭</view>
        <view class="ability-title">行前规划</view>
        <view class="ability-desc">支持景区查询、景点了解、路线咨询和出行建议。</view>
      </view>

      <view class="ability-card card">
        <view class="ability-icon">🤖</view>
        <view class="ability-title">数字人讲解</view>
        <view class="ability-desc">进入现场导览后，由 AI 数字人提供沉浸式讲解。</view>
      </view>

      <view class="ability-card card">
        <view class="ability-icon">🎙️</view>
        <view class="ability-title">语音问答</view>
        <view class="ability-desc">支持文字与语音提问，获取景区讲解和游客服务回答。</view>
      </view>

      <view class="ability-card card">
        <view class="ability-icon">🗺️</view>
        <view class="ability-title">路线推荐</view>
        <view class="ability-desc">明确提出路线需求后，系统生成推荐路线和节点讲解。</view>
      </view>

      <view class="ability-card card">
        <view class="ability-icon">📍</view>
        <view class="ability-title">位置识别</view>
        <view class="ability-desc">到达景区后可结合 GPS/NFC 识别当前位置并触发现场导览。</view>
      </view>

      <view class="ability-card card">
        <view class="ability-icon">📊</view>
        <view class="ability-title">游玩报告</view>
        <view class="ability-desc">结束导览后，自动沉淀本次游玩记录和报告内容。</view>
      </view>
    </view>

    <view class="section-header">
      <view class="section-title">推荐使用流程</view>
      <view class="section-more">6步完整体验</view>
    </view>

    <view class="process-card card">
      <view class="process-item">
        <view class="process-index">01</view>
        <view class="process-main">
          <view class="process-title">游览前查询景区</view>
          <view class="process-desc">
            在首页、景区列表或 AI 助手中查看热门景区、景点详情和基础服务信息。
          </view>
        </view>
      </view>

      <view class="process-item">
        <view class="process-index">02</view>
        <view class="process-main">
          <view class="process-title">进行行前规划</view>
          <view class="process-desc">
            可向 AI 助手提问，例如“介绍灵山胜境”或“帮我规划灵山胜境路线”。
          </view>
        </view>
      </view>

      <view class="process-item">
        <view class="process-index">03</view>
        <view class="process-main">
          <view class="process-title">到达景区并开启导览</view>
          <view class="process-desc">
            APP 识别进入景区后，可提交本次出行信息并开启 AI 数字人现场导览。
          </view>
        </view>
      </view>

      <view class="process-item">
        <view class="process-index">04</view>
        <view class="process-main">
          <view class="process-title">现场景点讲解与问答</view>
          <view class="process-desc">
            到达景点后可获取景点讲解，也可以通过文字或语音向数字人继续提问。
          </view>
        </view>
      </view>

      <view class="process-item">
        <view class="process-index">05</view>
        <view class="process-main">
          <view class="process-title">按需生成路线推荐</view>
          <view class="process-desc">
            需要路线时，主动提出“推荐路线”或“规划路线”，系统才会生成路线卡片。
          </view>
        </view>
      </view>

      <view class="process-item">
        <view class="process-index">06</view>
        <view class="process-main">
          <view class="process-title">结束导览查看报告</view>
          <view class="process-desc">
            点击结束导览后，系统会生成游玩报告，可查看景点停留、AI 对话、路线和消费记录。
          </view>
        </view>
      </view>
    </view>

    <view class="section-header">
      <view class="section-title">功能说明</view>
      <view class="section-more">常见问题</view>
    </view>

    <view class="help-card card">
      <view class="help-item">
        <view class="help-icon">💬</view>
        <view class="help-main">
          <view class="help-title">AI 助手和现场导览有什么区别？</view>
          <view class="help-desc">
            AI 助手主要用于游览前咨询和规划；现场导览需要进入景区后开启，会创建本次导览并记录游玩过程。
          </view>
        </view>
      </view>

      <view class="help-item">
        <view class="help-icon">📍</view>
        <view class="help-main">
          <view class="help-title">如何开启现场导览？</view>
          <view class="help-desc">
            到达景区后，首页会提示开启智能导览；演示时也可以点击首页“演示进入景区”体验完整流程。
          </view>
        </view>
      </view>

      <view class="help-item">
        <view class="help-icon">🗺️</view>
        <view class="help-main">
          <view class="help-title">为什么没有自动弹出路线？</view>
          <view class="help-desc">
            普通景点讲解不会自动生成路线。只有明确提出“推荐路线”或“规划路线”时，才会生成路线建议。
          </view>
        </view>
      </view>

      <view class="help-item">
        <view class="help-icon">🎙️</view>
        <view class="help-main">
          <view class="help-title">语音问答失败怎么办？</view>
          <view class="help-desc">
            请检查麦克风权限、网络连接和语音服务状态；也可以切换为文字输入继续提问。
          </view>
        </view>
      </view>

      <view class="help-item">
        <view class="help-icon">📊</view>
        <view class="help-main">
          <view class="help-title">在哪里查看游玩报告？</view>
          <view class="help-desc">
            结束导览后会自动进入报告页，也可以在首页的报告卡片或个人中心入口查看历史记录。
          </view>
        </view>
      </view>
    </view>

    <view class="tips-card card">
      <view class="tips-title">演示建议</view>
      <view class="tips-desc">
        比赛演示时建议按“游览前问 AI → 演示进入景区 → 开启现场导览 → 推荐路线 → 结束导览查看报告”的顺序展示，更容易体现完整闭环。
      </view>
    </view>

    <view class="action-card card">
      <view class="action-header">
        <view class="action-icon">即</view>
        <view class="action-info">
          <view class="action-title">开始使用即境导览</view>
          <view class="action-desc">先做行前规划，或回到首页体验现场导览闭环。</view>
        </view>
      </view>

      <view class="action-buttons">
        <view class="action-btn primary" @click="goGuide">打开 AI 助手</view>
        <view class="action-btn" @click="goScenicList">查看景区列表</view>
        <view class="action-btn ghost" @click="goHome">回到首页</view>
      </view>
    </view>
  </view>
</template>

<script setup>
function goGuide() {
  uni.switchTab({
    url: '/pages/guide/guide'
  })
}

function goScenicList() {
  uni.switchTab({
    url: '/pages/scenic/scenic'
  })
}

function goHome() {
  uni.switchTab({
    url: '/pages/index/index'
  })
}
</script>

<style>
.page {
  min-height: 100vh;
  background: linear-gradient(180deg, #f5f7fb 0%, #eef4ff 100%);
  padding: 24rpx;
  box-sizing: border-box;
}

.card {
  background: #ffffff;
  border-radius: 28rpx;
  box-shadow: 0 12rpx 28rpx rgba(15, 23, 42, 0.06);
}

.hero {
  position: relative;
  overflow: hidden;
  background: linear-gradient(135deg, #2f80ed 0%, #56ccf2 100%);
  border-radius: 32rpx;
  padding: 34rpx 30rpx;
  margin-bottom: 28rpx;
  color: #ffffff;
}

.hero-bg-circle {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.12);
}

.hero-bg-circle-1 {
  width: 240rpx;
  height: 240rpx;
  right: -50rpx;
  top: -40rpx;
}

.hero-bg-circle-2 {
  width: 170rpx;
  height: 170rpx;
  right: 120rpx;
  bottom: -70rpx;
}

.hero-content {
  position: relative;
  z-index: 1;
}

.hero-badge {
  display: inline-flex;
  padding: 8rpx 18rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.18);
  font-size: 20rpx;
  letter-spacing: 1rpx;
  margin-bottom: 18rpx;
}

.hero-title {
  font-size: 40rpx;
  font-weight: 700;
  line-height: 1.45;
}

.hero-subtitle {
  margin-top: 14rpx;
  font-size: 25rpx;
  line-height: 1.7;
  opacity: 0.96;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 10rpx 0 18rpx;
}

.section-title {
  font-size: 31rpx;
  font-weight: 700;
  color: #1f2937;
}

.section-more {
  font-size: 23rpx;
  color: #9ca3af;
}

.stage-list {
  margin-bottom: 28rpx;
}

.stage-card {
  display: flex;
  padding: 26rpx 24rpx;
  margin-bottom: 18rpx;
  border: 1rpx solid rgba(47, 128, 237, 0.08);
}

.stage-card:last-child {
  margin-bottom: 0;
}

.stage-index {
  width: 72rpx;
  height: 72rpx;
  border-radius: 24rpx;
  background: #eff6ff;
  color: #2f80ed;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26rpx;
  font-weight: 800;
  margin-right: 22rpx;
  flex-shrink: 0;
}

.stage-main {
  flex: 1;
}

.stage-tag {
  display: inline-flex;
  padding: 5rpx 14rpx;
  border-radius: 999rpx;
  background: #eff6ff;
  color: #2f80ed;
  font-size: 21rpx;
  margin-bottom: 8rpx;
}

.stage-title {
  font-size: 30rpx;
  font-weight: 800;
  color: #1f2937;
  line-height: 1.45;
}

.stage-desc {
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #6b7280;
  line-height: 1.7;
}

.stage-onsite .stage-index,
.stage-onsite .stage-tag {
  background: #ecfdf5;
  color: #16a34a;
}

.stage-report .stage-index,
.stage-report .stage-tag {
  background: #fff7ed;
  color: #ea580c;
}

.ability-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 18rpx;
  margin-bottom: 28rpx;
}

.ability-card {
  padding: 24rpx 20rpx;
}

.ability-icon {
  font-size: 40rpx;
  margin-bottom: 14rpx;
}

.ability-title {
  font-size: 28rpx;
  font-weight: 700;
  color: #1f2937;
}

.ability-desc {
  margin-top: 8rpx;
  font-size: 23rpx;
  color: #6b7280;
  line-height: 1.6;
}

.process-card {
  padding: 8rpx 26rpx;
  margin-bottom: 28rpx;
}

.process-item {
  display: flex;
  padding: 28rpx 0;
  border-bottom: 1rpx solid #eef0f4;
}

.process-item:last-child {
  border-bottom: none;
}

.process-index {
  width: 64rpx;
  font-size: 28rpx;
  font-weight: 700;
  color: #2f80ed;
  flex-shrink: 0;
}

.process-main {
  flex: 1;
}

.process-title {
  font-size: 29rpx;
  font-weight: 700;
  color: #1f2937;
}

.process-desc {
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #6b7280;
  line-height: 1.7;
}

.help-card {
  padding: 8rpx 26rpx;
  margin-bottom: 24rpx;
}

.help-item {
  display: flex;
  padding: 28rpx 0;
  border-bottom: 1rpx solid #eef0f4;
}

.help-item:last-child {
  border-bottom: none;
}

.help-icon {
  width: 58rpx;
  height: 58rpx;
  border-radius: 18rpx;
  background: #eff6ff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  margin-right: 20rpx;
  flex-shrink: 0;
}

.help-main {
  flex: 1;
}

.help-title {
  font-size: 29rpx;
  font-weight: 700;
  color: #1f2937;
  line-height: 1.5;
}

.help-desc {
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #6b7280;
  line-height: 1.7;
}

.tips-card {
  padding: 26rpx 24rpx;
  margin-bottom: 24rpx;
  background: #fffaf0;
}

.tips-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #92400e;
}

.tips-desc {
  margin-top: 10rpx;
  font-size: 24rpx;
  color: #b45309;
  line-height: 1.7;
}

.action-card {
  padding: 26rpx 24rpx;
  margin-bottom: 30rpx;
}

.action-header {
  display: flex;
  align-items: center;
}

.action-icon {
  width: 82rpx;
  height: 82rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, #18b368 0%, #4cd7a3 100%);
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  font-weight: 700;
  margin-right: 20rpx;
  flex-shrink: 0;
}

.action-info {
  flex: 1;
}

.action-title {
  font-size: 29rpx;
  font-weight: 700;
  color: #1f2937;
  line-height: 1.5;
}

.action-desc {
  margin-top: 6rpx;
  font-size: 23rpx;
  color: #6b7280;
  line-height: 1.5;
}

.action-buttons {
  display: flex;
  gap: 14rpx;
  margin-top: 24rpx;
}

.action-btn {
  flex: 1;
  height: 72rpx;
  border-radius: 999rpx;
  background: #f3f4f6;
  color: #4b5563;
  font-size: 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.action-btn.primary {
  background: #2f80ed;
  color: #ffffff;
}

.action-btn.ghost {
  background: #ecfdf5;
  color: #16a34a;
}
</style>
