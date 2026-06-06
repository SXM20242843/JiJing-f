import { API_BASE } from './api'
import { getToken, getVisitorId, getSessionId, clearLogin } from './auth'

function normalizeUrl(url) {
  if (!url) return ''

  if (url.startsWith('http://') || url.startsWith('https://')) {
    return url
  }

  if (url.startsWith('/')) {
    return `${API_BASE}${url}`
  }

  return `${API_BASE}/${url}`
}

function getMessage(data, fallback = '请求失败') {
  if (!data) return fallback
  return data.message || data.msg || data.error || fallback
}

function buildAuthorizationHeader(token = '') {
  const text = String(token || '').trim()

  if (!text) {
    return ''
  }

  return /^Bearer\s+/i.test(text) ? text : `Bearer ${text}`
}

function request(options = {}) {
  const {
    url,
    method = 'GET',
    data = {},
    header = {},
    loading = false,
    loadingText = '加载中...',
    needAuth = false,
    showErrorToast = true
  } = options

  const token = getToken()

  if (needAuth && !token) {
    if (showErrorToast) {
      uni.showToast({
        title: '请先登录',
        icon: 'none'
      })
    }

    return Promise.reject(new Error('未登录'))
  }

  if (loading) {
    uni.showLoading({
      title: loadingText,
      mask: true
    })
  }

  const finalHeader = {
    'Content-Type': 'application/json',
    'X-Visitor-Id': getVisitorId(),
    'X-Session-Id': getSessionId(),
    ...header
  }

  if (token) {
    finalHeader.Authorization = buildAuthorizationHeader(token)
  }

  return new Promise((resolve, reject) => {
    uni.request({
      url: normalizeUrl(url),
      method,
      data,
      header: finalHeader,
      success: res => {
        const statusCode = res.statusCode
        const body = res.data || {}

        if (statusCode === 401) {
          clearLogin()

          if (showErrorToast) {
            uni.showToast({
              title: '登录已过期，请重新登录',
              icon: 'none'
            })
          }

          reject(new Error('登录已过期'))
          return
        }

        if (statusCode < 200 || statusCode >= 300) {
          const message = getMessage(body, `请求失败：${statusCode}`)

          if (showErrorToast) {
            uni.showToast({
              title: message,
              icon: 'none'
            })
          }

          reject(new Error(message))
          return
        }

        /**
         * 兼容两种后端返回：
         * 1. { code: 200, data: xxx, message: '成功' }
         * 2. 直接返回 xxx
         */
        if (
          Object.prototype.hasOwnProperty.call(body, 'code') &&
          body.code !== 0 &&
          body.code !== 200
        ) {
          const message = getMessage(body)

          if (showErrorToast) {
            uni.showToast({
              title: message,
              icon: 'none'
            })
          }

          reject(new Error(message))
          return
        }

        resolve(body)
      },
      fail: err => {
        if (showErrorToast) {
          uni.showToast({
            title: '网络连接失败',
            icon: 'none'
          })
        }

        reject(err)
      },
      complete: () => {
        if (loading) {
          uni.hideLoading()
        }
      }
    })
  })
}

export default request
