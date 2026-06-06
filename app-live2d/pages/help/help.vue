<template>
  <view class="page">
    <view class="hero">
      <view class="hero-bg-circle hero-bg-circle-1"></view>
      <view class="hero-bg-circle hero-bg-circle-2"></view>

      <view class="hero-content">
        <view class="hero-badge">AI DIGITAL GUIDE</view>
        <view class="hero-title">AI数字人智慧导览使用指南</view>
        <view class="hero-subtitle">
          面向景区游客提供智能讲解、语音问答、路线咨询与个性化导览服务
        </view>
      </view>
    </view>

    <view class="section-header">
      <view class="section-title">核心能力</view>
      <view class="section-more">智慧文旅服务</view>
    </view>

    <view class="ability-grid">
      <view class="ability-card card">
        <view class="ability-icon">🤖</view>
        <view class="ability-title">数字人讲解</view>
        <view class="ability-desc">通过 AI 数字人提供更自然的景区讲解体验</view>
      </view>

      <view class="ability-card card">
        <view class="ability-icon">🎙️</view>
        <view class="ability-title">语音问答</view>
        <view class="ability-desc">支持文字与语音提问，提升游客交互效率</view>
      </view>

      <view class="ability-card card">
        <view class="ability-icon">🧭</view>
        <view class="ability-title">智能导览</view>
        <view class="ability-desc">结合景区内容，为游客提供导览与咨询服务</view>
      </view>

      <view class="ability-card card">
        <view class="ability-icon">📍</view>
        <view class="ability-title">景点讲解</view>
        <view class="ability-desc">支持在景区或景点详情页发起专属讲解</view>
      </view>
    </view>

    <view class="section-header">
      <view class="section-title">推荐使用流程</view>
      <view class="section-more">4步快速体验</view>
    </view>

    <view class="process-card card">
      <view class="process-item">
        <view class="process-index">01</view>
        <view class="process-main">
          <view class="process-title">浏览景区内容</view>
          <view class="process-desc">
            在首页点击“景区导览”，查看全部景区或热门推荐景区。
          </view>
        </view>
      </view>

      <view class="process-item">
        <view class="process-index">02</view>
        <view class="process-main">
          <view class="process-title">进入景区或景点详情</view>
          <view class="process-desc">
            查看景区简介、景点数量、热度标签和相关说明内容。
          </view>
        </view>
      </view>

      <view class="process-item">
        <view class="process-index">03</view>
        <view class="process-main">
          <view class="process-title">发起 AI 数字人讲解</view>
          <view class="process-desc">
            点击“AI讲解”后，系统会携带当前景区信息进入 AI 导览助手。
          </view>
        </view>
      </view>

      <view class="process-item">
        <view class="process-index">04</view>
        <view class="process-main">
          <view class="process-title">文字或语音提问</view>
          <view class="process-desc">
            游客可以通过文字或语音向 AI 数字人提问，获取景区讲解与出行建议。
          </view>
        </view>
      </view>
    </view>

    <view class="section-header">
      <view class="section-title">功能说明</view>
      <view class="section-more">常用操作</view>
    </view>

    <view class="help-card card">
      <view class="help-item">
        <view class="help-icon">🤖</view>
        <view class="help-main">
          <view class="help-title">如何进入 AI 导览？</view>
          <view class="help-desc">
            可以在首页点击“AI助手”，也可以在景区详情或景点详情页中点击“AI讲解”，进入数字人导览页面。
          </view>
        </view>
      </view>

      <view class="help-item">
        <view class="help-icon">🎙️</view>
        <view class="help-main">
          <view class="help-title">如何语音提问？</view>
          <view class="help-desc">
            进入 AI 数字人导览页面后，点击语音按钮开始提问，系统会识别游客问题并生成讲解回答。
          </view>
        </view>
      </view>

      <view class="help-item">
        <view class="help-icon">⭐</view>
        <view class="help-main">
          <view class="help-title">如何收藏景区或景点？</view>
          <view class="help-desc">
            在景区或景点详情页点击收藏按钮，即可将当前内容加入收藏，方便后续快速查看。
          </view>
        </view>
      </view>

      <view class="help-item">
        <view class="help-icon">👤</view>
        <view class="help-main">
          <view class="help-title">如何查看个人记录？</view>
          <view class="help-desc">
            进入“个人中心”，可以查看我的收藏、浏览记录、AI 咨询记录等个人使用信息。
          </view>
        </view>
      </view>
    </view>

    <view class="tips-card card">
      <view class="tips-title">使用建议</view>
      <view class="tips-desc">
        推荐在景区详情页或景点详情页中发起 AI 数字人讲解，这样系统可以结合当前景区内容生成更准确、更贴近场景的回答。
      </view>
    </view>

    <view class="action-card card" @click="goGuide">
      <view class="action-left">
        <view class="action-icon">AI</view>
        <view class="action-info">
          <view class="action-title">立即体验 AI 数字人导览</view>
          <view class="action-desc">进入导览助手，开始智能讲解与语音问答</view>
        </view>
      </view>
      <view class="action-btn">进入</view>
    </view>
  </view>
</template>

<script setup>
function goGuide() {
  uni.switchTab({
    url: '/pages/guide/guide'
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
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 30rpx;
}

.action-left {
  display: flex;
  align-items: center;
  flex: 1;
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

.action-btn {
  flex-shrink: 0;
  margin-left: 16rpx;
  padding: 14rpx 24rpx;
  border-radius: 999rpx;
  background: #2f80ed;
  color: #ffffff;
  font-size: 24rpx;
}
</style>