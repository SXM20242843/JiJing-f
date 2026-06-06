<template>
  <view class="page">
    <view class="top-card">
      <view class="logo-wrap">
        <text class="logo-text">AI</text>
      </view>
      <view class="title">欢迎登录</view>
      <view class="subtitle">登录后可同步收藏、咨询记录和个性化导览数据</view>
    </view>

    <view class="form-card">
      <view class="form-item">
        <text class="label">账号</text>
        <input
          v-model="form.account"
          class="input"
          placeholder="请输入手机号或用户名"
          placeholder-class="placeholder"
        />
      </view>

      <view class="form-item">
        <text class="label">密码</text>
        <input
          v-model="form.password"
          class="input"
          type="password"
          placeholder="请输入密码"
          placeholder-class="placeholder"
        />
      </view>

      <button class="login-btn" :loading="loading" @click="handleLogin">
        登录
      </button>

      <view class="register-row">
        <text class="tip">还没有账号？</text>
        <text class="register-link" @click="goRegister">立即注册</text>
      </view>
    </view>

    <view class="bottom-tip">
      <text>登录后将用于游客行为分析、游玩偏好和数字人个性化导览</text>
    </view>
  </view>
</template>

<script setup>
import { reactive, ref } from 'vue'
import request from '@/utils/request'
import { saveLogin, getVisitorId, getSessionId } from '@/utils/auth'
import { showProfileOnboarding } from '@/utils/profileOnboarding'

const loading = ref(false)

const form = reactive({
  account: '',
  password: ''
})

function getLoginPayload() {
  const account = form.account.trim()

  return {
    account,
    username: account,
    phone: account,
    password: form.password,
    visitor_id: getVisitorId(),
    session_id: getSessionId()
  }
}

function parseLoginResult(res) {
  const data = res.data || res

  const token =
    data.token ||
    data.accessToken ||
    data.access_token ||
    data.data?.token ||
    data.data?.accessToken ||
    data.data?.access_token ||
    ''

  const userInfo =
    data.userInfo ||
    data.user ||
    data.currentUser ||
    data.data?.userInfo ||
    data.data?.user ||
    data.data?.currentUser ||
    {}

  return {
    token,
    userInfo: {
      ...userInfo,
      profileCompleted:
        userInfo.profileCompleted ??
        userInfo.profile_completed ??
        data.profileCompleted ??
        data.profile_completed ??
        data.hasProfile ??
        false,
      hasProfile:
        userInfo.hasProfile ??
        userInfo.has_profile ??
        data.hasProfile ??
        data.has_profile ??
        data.profileCompleted ??
        false,
      profile_status:
        userInfo.profile_status ||
        userInfo.profileStatus ||
        data.profile_status ||
        data.profileStatus ||
        ''
    }
  }
}

function goBackAfterLogin() {
  uni.navigateBack({
    fail: () => {
      uni.switchTab({
        url: '/pages/mine/mine'
      })
    }
  })
}

async function handlePostLogin(loginData) {
  const shouldGoProfile = await showProfileOnboarding({
    source: 'login',
    userInfo: loginData.userInfo
  })

  if (shouldGoProfile) {
    uni.redirectTo({
      url: '/pages/mine/profile?from=onboarding',
      fail: () => {
        uni.navigateTo({
          url: '/pages/mine/profile?from=onboarding'
        })
      }
    })
    return
  }

  goBackAfterLogin()
}

async function handleLogin() {
  if (!form.account.trim()) {
    uni.showToast({
      title: '请输入账号',
      icon: 'none'
    })
    return
  }

  if (!form.password) {
    uni.showToast({
      title: '请输入密码',
      icon: 'none'
    })
    return
  }

  loading.value = true

  try {
    const res = await request({
      url: '/api/user/login',
      method: 'POST',
      data: getLoginPayload(),
      loading: true,
      loadingText: '登录中...'
    })

    const loginData = parseLoginResult(res)

    if (!loginData.token) {
      uni.showToast({
        title: '后端未返回 token',
        icon: 'none'
      })
      return
    }

    saveLogin(loginData)

    uni.showToast({
      title: '登录成功',
      icon: 'success'
    })

    setTimeout(() => {
      handlePostLogin(loginData)
    }, 500)
  } catch (err) {
    console.error('登录失败：', err)
  } finally {
    loading.value = false
  }
}

function goRegister() {
  uni.navigateTo({
    url: '/pages/register/register'
  })
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: linear-gradient(180deg, #eef7ff 0%, #f7f9fc 42%, #f7f9fc 100%);
  padding: 48rpx 32rpx;
  box-sizing: border-box;
}

.top-card {
  padding: 44rpx 20rpx 36rpx;
  text-align: center;
}

.logo-wrap {
  width: 112rpx;
  height: 112rpx;
  border-radius: 36rpx;
  background: linear-gradient(135deg, #2f80ed, #56ccf2);
  margin: 0 auto 28rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 18rpx 40rpx rgba(47, 128, 237, 0.25);
}

.logo-text {
  color: #ffffff;
  font-size: 42rpx;
  font-weight: 800;
}

.title {
  font-size: 44rpx;
  font-weight: 800;
  color: #1f2937;
}

.subtitle {
  margin-top: 14rpx;
  font-size: 26rpx;
  color: #6b7280;
  line-height: 1.6;
}

.form-card {
  background: #ffffff;
  border-radius: 32rpx;
  padding: 36rpx 32rpx 40rpx;
  box-shadow: 0 18rpx 48rpx rgba(31, 41, 55, 0.08);
}

.form-item {
  margin-bottom: 28rpx;
}

.label {
  display: block;
  font-size: 26rpx;
  color: #374151;
  font-weight: 600;
  margin-bottom: 14rpx;
}

.input {
  height: 92rpx;
  border-radius: 24rpx;
  background: #f3f6fb;
  padding: 0 26rpx;
  font-size: 30rpx;
  color: #111827;
  box-sizing: border-box;
}

.placeholder {
  color: #a0a7b5;
}

.login-btn {
  margin-top: 18rpx;
  height: 92rpx;
  line-height: 92rpx;
  border-radius: 28rpx;
  background: linear-gradient(135deg, #2f80ed, #56ccf2);
  color: #ffffff;
  font-size: 32rpx;
  font-weight: 700;
  box-shadow: 0 18rpx 36rpx rgba(47, 128, 237, 0.22);
}

.login-btn::after {
  border: none;
}

.register-row {
  margin-top: 30rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.tip {
  color: #6b7280;
  font-size: 26rpx;
}

.register-link {
  margin-left: 8rpx;
  color: #2f80ed;
  font-size: 26rpx;
  font-weight: 700;
}

.bottom-tip {
  margin-top: 34rpx;
  padding: 0 24rpx;
  text-align: center;
  color: #8a94a6;
  font-size: 24rpx;
  line-height: 1.7;
}
</style>
