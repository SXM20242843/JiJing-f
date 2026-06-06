<template>
  <view class="page">
    <view class="toolbar">
      <view class="status-wrap">
        <view class="dot" :class="{ online: connected }"></view>
        <text class="status-text">{{ connected ? '已连接' : '未连接' }}</text>
      </view>

      <view class="btn-row">
        <button class="ctl-btn" size="mini" type="primary" @click="playTestMp3">
          播放测试 mp3
        </button>
        <button class="ctl-btn" size="mini" @click="stopAudio">
          停止播放
        </button>
        <button class="ctl-btn" size="mini" type="warn" @click="connectBackend">
          连接后端
        </button>
      </view>
    </view>

    <!-- H5 调试：用 iframe，方便父页面直接 postMessage 控制 -->
    <!-- #ifdef H5 -->
    <iframe
      ref="live2dFrame"
      class="live2d-frame"
      src="/static/live2d/index.html"
    ></iframe>
    <!-- #endif -->

    <!-- App 端先保留 web-view，后面打包时再单独适配控制 -->
    <!-- #ifdef APP-PLUS -->
    <web-view src="/static/live2d/index.html"></web-view>
    <!-- #endif -->
  </view>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'

const live2dFrame = ref(null)
const connected = ref(false)

function postToLive2D(message) {
  // H5 iframe 调试使用
  // #ifdef H5
  const frame = live2dFrame.value
  if (frame && frame.contentWindow) {
    frame.contentWindow.postMessage(
      {
        source: 'uni-live2d-control',
        ...message
      },
      window.location.origin
    )
  }
  // #endif
}

function playTestMp3() {
  postToLive2D({
    type: 'play-test-mp3'
  })
}

function stopAudio() {
  postToLive2D({
    type: 'stop-audio'
  })
}

function connectBackend() {
  postToLive2D({
    type: 'connect-backend',
    url: 'ws://127.0.0.1:8080/ws/lipsync'
  })
}

function handleMessage(event) {
  if (event.origin !== window.location.origin) return

  const data = event.data || {}
  if (data.source !== 'live2d-status') return

  if (data.type === 'socket-status') {
    connected.value = !!data.connected
  }
}

onMounted(() => {
  // #ifdef H5
  window.addEventListener('message', handleMessage)
  // #endif
})

onBeforeUnmount(() => {
  // #ifdef H5
  window.removeEventListener('message', handleMessage)
  // #endif
})
</script>

<style>
.page {
  width: 100%;
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f6f7fb;
}

.toolbar {
  padding: 20rpx 24rpx;
  background: #ffffff;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.06);
  z-index: 2;
}

.status-wrap {
  display: flex;
  align-items: center;
  margin-bottom: 16rpx;
}

.dot {
  width: 18rpx;
  height: 18rpx;
  border-radius: 50%;
  background: #bfbfbf;
  margin-right: 12rpx;
}

.dot.online {
  background: #18c964;
}

.status-text {
  font-size: 28rpx;
  color: #333;
}

.btn-row {
  display: flex;
  gap: 16rpx;
  flex-wrap: wrap;
}

.ctl-btn {
  min-width: 180rpx;
}

.live2d-frame {
  width: 100%;
  flex: 1;
  border: none;
  background: #000;
}
</style>