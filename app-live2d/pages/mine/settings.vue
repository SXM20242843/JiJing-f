<template>
  <view class="page">
    <view class="section-card">
      <view class="section-title">语音播报</view>

      <view class="setting-item">
        <view class="setting-left">
          <view class="setting-name">开启语音播报</view>
          <view class="setting-desc">AI 回答后是否启用语音播放</view>
        </view>
        <switch
          :checked="settings.enableVoice"
          color="#2f80ed"
          @change="onSwitchChange('enableVoice', $event)"
        />
      </view>

      <view class="setting-item">
        <view class="setting-left">
          <view class="setting-name">自动播放讲解</view>
          <view class="setting-desc">进入讲解内容后自动播放语音</view>
        </view>
        <switch
          :checked="settings.autoPlayVoice"
          color="#2f80ed"
          @change="onSwitchChange('autoPlayVoice', $event)"
        />
      </view>
    </view>

    <view class="section-card">
      <view class="section-title">界面体验</view>

      <view class="setting-item">
        <view class="setting-left">
          <view class="setting-name">显示咨询记录</view>
          <view class="setting-desc">保留并展示历史 AI 咨询记录</view>
        </view>
        <switch
          :checked="settings.showConsultHistory"
          color="#2f80ed"
          @change="onSwitchChange('showConsultHistory', $event)"
        />
      </view>

      <view class="setting-item">
        <view class="setting-left">
          <view class="setting-name">显示首页公告</view>
          <view class="setting-desc">首页是否展示公告与活动模块</view>
        </view>
        <switch
          :checked="settings.showHomeNotice"
          color="#2f80ed"
          @change="onSwitchChange('showHomeNotice', $event)"
        />
      </view>
    </view>

    <view class="section-card">
      <view class="section-title">数据管理</view>

      <view class="action-item" @click="clearFavorites">
        <view class="action-left">
          <view class="action-name">清空收藏景点</view>
          <view class="action-desc">删除本地保存的收藏内容</view>
        </view>
        <view class="action-arrow">›</view>
      </view>

      <view class="action-item" @click="clearConsults">
        <view class="action-left">
          <view class="action-name">清空咨询记录</view>
          <view class="action-desc">删除本地保存的 AI 提问记录</view>
        </view>
        <view class="action-arrow">›</view>
      </view>

      <view class="action-item" @click="resetSettings">
        <view class="action-left">
          <view class="action-name">恢复默认设置</view>
          <view class="action-desc">将系统设置恢复到初始状态</view>
        </view>
        <view class="action-arrow">›</view>
      </view>
    </view>

    <view class="section-card">
      <view class="section-title">系统信息</view>

      <view class="info-item">
        <text class="info-label">当前版本</text>
        <text class="info-value">v1.0.0</text>
      </view>

      <view class="info-item">
        <text class="info-label">系统名称</text>
        <text class="info-value">景区 AI 数字人导览系统</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { reactive } from 'vue'
import { onShow } from '@dcloudio/uni-app'

const SETTINGS_KEY = 'appSettings'
const FAVORITE_KEY = 'favoriteScenics'
const CONSULT_KEY = 'recentConsults'

const defaultSettings = {
  enableVoice: true,
  autoPlayVoice: true,
  showConsultHistory: true,
  showHomeNotice: true
}

const settings = reactive({
  ...defaultSettings
})

function loadSettings() {
  const cache = uni.getStorageSync(SETTINGS_KEY)

  if (!cache) {
    Object.assign(settings, defaultSettings)
    return
  }

  try {
    const parsed = typeof cache === 'string' ? JSON.parse(cache) : cache
    Object.assign(settings, defaultSettings, parsed || {})
  } catch (e) {
    Object.assign(settings, defaultSettings)
  }
}

function saveSettings() {
  uni.setStorageSync(SETTINGS_KEY, JSON.stringify(settings))
}

function onSwitchChange(key, event) {
  settings[key] = event.detail.value
  saveSettings()

  uni.showToast({
    title: '设置已保存',
    icon: 'none'
  })
}

function clearFavorites() {
  uni.showModal({
    title: '提示',
    content: '确定清空全部收藏景点吗？',
    success: (res) => {
      if (!res.confirm) return

      uni.removeStorageSync(FAVORITE_KEY)
      uni.showToast({
        title: '已清空收藏',
        icon: 'none'
      })
    }
  })
}

function clearConsults() {
  uni.showModal({
    title: '提示',
    content: '确定清空全部咨询记录吗？',
    success: (res) => {
      if (!res.confirm) return

      uni.removeStorageSync(CONSULT_KEY)
      uni.showToast({
        title: '已清空咨询记录',
        icon: 'none'
      })
    }
  })
}

function resetSettings() {
  uni.showModal({
    title: '提示',
    content: '确定恢复默认设置吗？',
    success: (res) => {
      if (!res.confirm) return

      Object.assign(settings, defaultSettings)
      saveSettings()

      uni.showToast({
        title: '已恢复默认设置',
        icon: 'none'
      })
    }
  })
}

onShow(() => {
  loadSettings()
})
</script>

<style>
.page {
  min-height: 100vh;
  background: linear-gradient(180deg, #f5f7fb 0%, #eef4ff 100%);
  padding: 24rpx;
  box-sizing: border-box;
}

.section-card {
  background: #ffffff;
  border-radius: 28rpx;
  box-shadow: 0 12rpx 28rpx rgba(15, 23, 42, 0.06);
  padding: 24rpx;
  margin-bottom: 24rpx;
}

.section-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #1f2937;
  margin-bottom: 18rpx;
}

.setting-item,
.action-item,
.info-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24rpx 0;
  border-bottom: 1rpx solid #eef2f7;
}

.setting-item:last-child,
.action-item:last-child,
.info-item:last-child {
  border-bottom: none;
}

.setting-left,
.action-left {
  flex: 1;
  padding-right: 20rpx;
}

.setting-name,
.action-name {
  font-size: 28rpx;
  font-weight: 600;
  color: #1f2937;
}

.setting-desc,
.action-desc {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #6b7280;
  line-height: 1.6;
}

.action-arrow {
  color: #b0b7c3;
  font-size: 34rpx;
  margin-left: 12rpx;
}

.info-label {
  font-size: 26rpx;
  color: #6b7280;
}

.info-value {
  font-size: 26rpx;
  color: #1f2937;
  font-weight: 600;
  text-align: right;
  margin-left: 20rpx;
}
</style>