<template>
  <view class="page">
    <view class="top-card">
      <view class="title">创建账号</view>
      <view class="subtitle">注册后可保存收藏、咨询记录和个性化导览偏好</view>
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
        <text class="label">昵称</text>
        <input
          v-model="form.nickname"
          class="input"
          placeholder="请输入昵称"
          placeholder-class="placeholder"
        />
      </view>

      <view class="form-item">
        <text class="label">密码</text>
        <input
          v-model="form.password"
          class="input"
          type="password"
          placeholder="请输入密码，至少 6 位"
          placeholder-class="placeholder"
        />
      </view>

      <view class="form-item">
        <text class="label">确认密码</text>
        <input
          v-model="form.confirmPassword"
          class="input"
          type="password"
          placeholder="请再次输入密码"
          placeholder-class="placeholder"
        />
      </view>

      <button class="register-btn" :loading="loading" @click="handleRegister">
        注册
      </button>

      <view class="login-row">
        <text class="tip">已有账号？</text>
        <text class="login-link" @click="goLogin">去登录</text>
      </view>
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
  nickname: '',
  password: '',
  confirmPassword: ''
})

function validateForm() {
  if (!form.account.trim()) {
    uni.showToast({
      title: '请输入账号',
      icon: 'none'
    })
    return false
  }

  if (!form.nickname.trim()) {
    uni.showToast({
      title: '请输入昵称',
      icon: 'none'
    })
    return false
  }

  if (!form.password) {
    uni.showToast({
      title: '请输入密码',
      icon: 'none'
    })
    return false
  }

  if (form.password.length < 6) {
    uni.showToast({
      title: '密码至少 6 位',
      icon: 'none'
    })
    return false
  }

  if (form.password !== form.confirmPassword) {
    uni.showToast({
      title: '两次密码不一致',
      icon: 'none'
    })
    return false
  }

  return true
}

function getRegisterPayload() {
  const account = form.account.trim()

  return {
    account,
    username: account,
    phone: account,
    nickname: form.nickname.trim(),
    password: form.password,
    visitor_id: getVisitorId(),
    session_id: getSessionId()
  }
}

function parseRegisterResult(res) {
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

async function handlePostRegister(registerData) {
  const shouldGoProfile = await showProfileOnboarding({
    source: 'register',
    userInfo: registerData.userInfo
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

  uni.switchTab({
    url: '/pages/mine/mine'
  })
}

async function handleRegister() {
  if (!validateForm()) return

  loading.value = true

  try {
    const res = await request({
      url: '/api/user/register',
      method: 'POST',
      data: getRegisterPayload(),
      loading: true,
      loadingText: '注册中...'
    })

    const registerData = parseRegisterResult(res)

    /**
     * 如果后端注册后直接返回 token，就自动登录。
     * 如果后端只返回注册成功，不返回 token，就跳转登录页。
     */
    if (registerData.token) {
      saveLogin(registerData)

      uni.showToast({
        title: '注册成功',
        icon: 'success'
      })

      setTimeout(() => {
        handlePostRegister(registerData)
      }, 500)
    } else {
      uni.showModal({
        title: '注册成功',
        content: '请使用刚才的账号登录',
        showCancel: false,
        confirmText: '去登录',
        success: () => {
          uni.redirectTo({
            url: '/pages/login/login'
          })
        }
      })
    }
  } catch (err) {
    console.error('注册失败：', err)
  } finally {
    loading.value = false
  }
}

function goLogin() {
  uni.redirectTo({
    url: '/pages/login/login'
  })
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: linear-gradient(180deg, #eef7ff 0%, #f7f9fc 42%, #f7f9fc 100%);
  padding: 56rpx 32rpx;
  box-sizing: border-box;
}

.top-card {
  padding: 28rpx 16rpx 40rpx;
}

.title {
  font-size: 46rpx;
  font-weight: 800;
  color: #1f2937;
}

.subtitle {
  margin-top: 16rpx;
  color: #6b7280;
  font-size: 26rpx;
  line-height: 1.6;
}

.form-card {
  background: #ffffff;
  border-radius: 32rpx;
  padding: 36rpx 32rpx 40rpx;
  box-shadow: 0 18rpx 48rpx rgba(31, 41, 55, 0.08);
}

.form-item {
  margin-bottom: 26rpx;
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

.register-btn {
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

.register-btn::after {
  border: none;
}

.login-row {
  margin-top: 30rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.tip {
  color: #6b7280;
  font-size: 26rpx;
}

.login-link {
  margin-left: 8rpx;
  color: #2f80ed;
  font-size: 26rpx;
  font-weight: 700;
}
</style>
