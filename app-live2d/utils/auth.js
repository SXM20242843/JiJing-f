const TOKEN_KEY = 'APP_TOKEN'
const USER_INFO_KEY = 'APP_USER_INFO'
const VISITOR_ID_KEY = 'APP_VISITOR_ID'
const SESSION_ID_KEY = 'APP_SESSION_ID'

function randomString(length = 8) {
  return Math.random().toString(36).slice(2, 2 + length)
}

function createId(prefix) {
  return `${prefix}_${Date.now()}_${randomString(10)}`
}

function pickFirstFilledValue(...values) {
  for (const value of values) {
    if (value === undefined || value === null) {
      continue
    }

    if (typeof value === 'string') {
      const trimmed = value.trim()
      if (trimmed) {
        return trimmed
      }
      continue
    }

    if (value !== '') {
      return value
    }
  }

  return ''
}

/**
 * 获取匿名游客 ID
 * 未登录用户也要有 visitor_id，方便游客感受度报告统计。
 */
function getVisitorId() {
  let visitorId = uni.getStorageSync(VISITOR_ID_KEY)

  if (!visitorId) {
    visitorId = createId('visitor')
    uni.setStorageSync(VISITOR_ID_KEY, visitorId)
  }

  return visitorId
}

/**
 * 获取本次游览会话 ID
 * 可以理解为：用户本次打开 APP / 本次游览过程。
 */
function getSessionId() {
  let sessionId = uni.getStorageSync(SESSION_ID_KEY)

  if (!sessionId) {
    sessionId = createId('session')
    uni.setStorageSync(SESSION_ID_KEY, sessionId)
  }

  return sessionId
}

/**
 * 重新生成游览会话 ID
 * 后面你做“进入某个景区现场导览模式”时可以调用。
 */
function resetSessionId() {
  const sessionId = createId('session')
  uni.setStorageSync(SESSION_ID_KEY, sessionId)
  return sessionId
}

function getToken() {
  return pickFirstFilledValue(
    uni.getStorageSync(TOKEN_KEY),
    uni.getStorageSync('token'),
    uni.getStorageSync('accessToken'),
    uni.getStorageSync('access_token'),
    ''
  )
}

function setToken(token) {
  const finalToken = token || ''
  uni.setStorageSync(TOKEN_KEY, finalToken)

  // 兼容老代码可能读取这些 key
  if (finalToken) {
    uni.setStorageSync('token', finalToken)
    uni.setStorageSync('accessToken', finalToken)
  }
}

function getUserInfo() {
  return uni.getStorageSync(USER_INFO_KEY) || null
}

function setUserInfo(userInfo) {
  uni.setStorageSync(USER_INFO_KEY, userInfo || null)
}

function saveLogin(loginData = {}) {
  const data = loginData.data && typeof loginData.data === 'object'
    ? loginData.data
    : {}

  const token = pickFirstFilledValue(
    loginData.token,
    loginData.accessToken,
    loginData.access_token,
    loginData.Authorization,
    loginData.authorization,
    data.token,
    data.accessToken,
    data.access_token,
    data.Authorization,
    data.authorization,
    ''
  )

  const userInfo = pickFirstFilledValue(
    loginData.userInfo,
    loginData.user,
    loginData.touristUser,
    data.userInfo,
    data.user,
    data.touristUser,
    data.tourist_user,
    data
  )

  if (token) {
    setToken(token)
  }

  if (userInfo) {
    setUserInfo(userInfo)
  }
}

function clearLogin() {
  uni.removeStorageSync(TOKEN_KEY)
  uni.removeStorageSync(USER_INFO_KEY)

  // 兼容老 key
  uni.removeStorageSync('token')
  uni.removeStorageSync('accessToken')
  uni.removeStorageSync('access_token')
}

function isLogin() {
  return !!getToken()
}

function normalizeUserId(value) {
  const text = value === undefined || value === null ? '' : String(value).trim()

  if (!text) return ''

  // visitor_ 只能作为未登录游客 ID，不能作为登录用户 ID
  if (text.startsWith('visitor_')) return ''

  // android-live2d-* 只能作为原生本地兜底 ID，不能作为登录用户 ID
  if (text.startsWith('android-live2d-')) return ''

  // anonymous 也不能作为真实登录用户
  if (text === 'anonymous') return ''

  /**
   * 注意：
   * 后端 AppUserService.register() 生成的真实业务 user_id 本来就是 tourist_xxx。
   * tourist_ 不是一定等于“临时游客”，不能在这里过滤掉。
   */
  return text
}

function normalizeNumericUserId(value) {
  const text = value === undefined || value === null ? '' : String(value).trim()
  if (!text) return ''
  return /^\d+$/.test(text) ? text : ''
}

function pickNumericUserId(...values) {
  for (const value of values) {
    const id = normalizeNumericUserId(value)
    if (id) return id
  }
  return ''
}

function pickBusinessUserId(...values) {
  for (const value of values) {
    const id = normalizeUserId(value)
    if (id) return id
  }
  return ''
}

function getUserInfoCandidates(userInfo = {}) {
  const nestedUserInfo = userInfo.userInfo || userInfo.user || userInfo.touristUser || {}
  const data = userInfo.data || {}
  const dataUserInfo = data.userInfo || data.user || data.touristUser || {}

  return {
    nestedUserInfo,
    data,
    dataUserInfo
  }
}

function getCurrentNumericUserId() {
  const userInfo = getUserInfo()

  if (!userInfo) return ''

  const { nestedUserInfo, data, dataUserInfo } = getUserInfoCandidates(userInfo)

  return pickNumericUserId(
    userInfo.id,
    userInfo.uid,
    nestedUserInfo.id,
    nestedUserInfo.uid,
    data.id,
    data.uid,
    dataUserInfo.id,
    dataUserInfo.uid
  )
}

function getCurrentUserId() {
  const userInfo = getUserInfo()

  if (!userInfo) return ''

  const { nestedUserInfo, data, dataUserInfo } = getUserInfoCandidates(userInfo)

  /**
   * 当前后端 tourist_user.user_id 是核心业务用户 ID。
   * AppUserService.register() 生成的就是 tourist_xxx，JWT 里也是这个 userId。
   * 用户画像、收藏、聊天、路线等表主要按 user_id 串联，所以这里必须优先返回业务 userId。
   */
  const businessId = pickBusinessUserId(
    userInfo.user_id,
    userInfo.userId,
    userInfo.user_id_str,
    userInfo.tourist_user_id,
    userInfo.touristUserId,
    userInfo.touristUserCode,
    userInfo.userCode,

    nestedUserInfo.user_id,
    nestedUserInfo.userId,
    nestedUserInfo.user_id_str,
    nestedUserInfo.tourist_user_id,
    nestedUserInfo.touristUserId,
    nestedUserInfo.touristUserCode,
    nestedUserInfo.userCode,

    data.user_id,
    data.userId,
    data.user_id_str,
    data.tourist_user_id,
    data.touristUserId,
    data.touristUserCode,
    data.userCode,

    dataUserInfo.user_id,
    dataUserInfo.userId,
    dataUserInfo.user_id_str,
    dataUserInfo.tourist_user_id,
    dataUserInfo.touristUserId,
    dataUserInfo.touristUserCode,
    dataUserInfo.userCode
  )

  if (businessId) {
    return businessId
  }

  /**
   * 部分接口会额外返回数据库自增 id，例如 5。
   * 只有在没有业务 userId 时，才用数字 id 兜底。
   */
  const numericId = getCurrentNumericUserId()
  if (numericId) {
    return numericId
  }

  const fallbackId = pickFirstFilledValue(
    userInfo.id,
    userInfo.uid,
    nestedUserInfo.id,
    nestedUserInfo.uid,
    data.id,
    data.uid,
    dataUserInfo.id,
    dataUserInfo.uid
  )

  return normalizeUserId(fallbackId)
}

/**
 * 后续 AI 问答、行为日志统一带这个身份信息。
 */
function getAuthPayload() {
  return {
    user_id: getCurrentUserId() || '',
    visitor_id: getVisitorId(),
    session_id: getSessionId()
  }
}

/**
 * 需要登录才能访问的页面可以调用这个。
 */
function requireLogin() {
  if (isLogin()) {
    return true
  }

  uni.showModal({
    title: '需要登录',
    content: '登录后可以同步收藏、咨询记录和个性化导览数据。',
    confirmText: '去登录',
    cancelText: '先看看',
    success: res => {
      if (res.confirm) {
        uni.navigateTo({
          url: '/pages/login/login'
        })
      }
    }
  })

  return false
}

export {
  getVisitorId,
  getSessionId,
  resetSessionId,
  getToken,
  setToken,
  getUserInfo,
  setUserInfo,
  saveLogin,
  clearLogin,
  isLogin,
  getCurrentUserId,
  getCurrentNumericUserId,
  getAuthPayload,
  requireLogin
}
