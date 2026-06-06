<template>
  <view class="page">
    <view class="top-card">
      <view class="page-title">即境 AI 助手</view>
      <view class="page-subtitle">
        可以帮你查景区、做行前规划，也可以根据景区名称打开 AI 数字人讲解。
      </view>
    </view>

    <view class="quick-grid">
      <view
        class="quick-item"
        v-for="item in quickActions"
        :key="item.key"
        @click="handleQuickAction(item.key)"
      >
        <view class="quick-icon">{{ item.icon }}</view>
        <view class="quick-label">{{ item.label }}</view>
        <view class="quick-desc">{{ item.desc }}</view>
      </view>
    </view>

    <view class="ask-card">
      <view class="card-title">问问即境</view>
      <view class="card-desc">
        输入景区名称或行前需求，我会帮你判断下一步该去哪里。
      </view>

      <view class="input-box">
        <textarea
          class="question-input"
          v-model="question"
          placeholder="例如：介绍灵山胜境 / 帮我规划灵山胜境路线"
          maxlength="120"
          auto-height
        ></textarea>
      </view>

      <view
        class="send-btn"
        :class="{ disabled: handling }"
        @click="handleAsk"
      >
        {{ handling ? '处理中...' : '发送' }}
      </view>

      <view class="example-list">
        <view
          class="example-chip"
          v-for="item in examples"
          :key="item"
          @click="askExample(item)"
        >
          {{ item }}
        </view>
      </view>
    </view>

    <view class="reply-card" v-if="assistantTip">
      <view class="reply-title">{{ assistantTitle }}</view>
      <view class="reply-text">{{ assistantTip }}</view>

      <view class="match-list" v-if="matchedParks.length">
        <view
          class="match-item"
          v-for="park in matchedParks"
          :key="park.id || park.name"
          @click="openParkFromMatch(park)"
        >
          <view class="match-name">{{ park.name || '未命名景区' }}</view>
          <view class="match-desc">{{ formatShortDesc(park.desc, 42) }}</view>
        </view>
      </view>

      <view class="reply-action" v-if="showGoListButton" @click="goScenicList">
        去选择景区
      </view>
    </view>

    <TripInfoPopup
      :show="showTripInfoPopup"
      :subtitle="tripPopupSubtitle"
      :cancel-text="tripPopupCancelText"
      :confirm-text="tripPopupConfirmText"
      @cancel="handleTripInfoCancel"
      @confirm="handleTripInfoConfirm"
    />
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { API_BASE, NATIVE_LIVE2D_SOURCE } from '@/utils/api'
import { openNativeLive2DGuide } from '@/utils/openNativeLive2D.js'
import {
  getCurrentOnsiteStatus,
  takeOnsiteGuideContext
} from '@/common/onsite-guide.js'
import TripInfoPopup from '@/components/TripInfoPopup.vue'
import {
  useTripInfoConfirm,
  logTripInfoSelection
} from '@/utils/visit'

const PARKS_API = `${API_BASE}/api/app/parks`
const SCENIC_LIST_MODE_KEY = 'scenicListMode'
const GUIDE_CONVERSATION_ID_KEY = 'guideConversationId'

let opening = false

const {
  showTripInfoPopup,
  openTripInfoConfirm,
  cancelTripInfoConfirm,
  consumePendingTripInfoAction
} = useTripInfoConfirm()

const DEFAULT_DIGITAL_HUMAN_CONFIG = {
  avatarId: 'guide_female_01',
  avatarName: '灵灵',
  clothesMode: '',
  voiceId: 'zhitian_emo',
  voiceName: '知甜',
  welcomeText: ''
}

const quickActions = [
  {
    key: 'recommend',
    icon: '荐',
    label: '推荐景区',
    desc: '看看热门目的地'
  },
  {
    key: 'list',
    icon: '景',
    label: '打开景区列表',
    desc: '浏览全部景区'
  },
  {
    key: 'favorite',
    icon: '藏',
    label: '我的收藏',
    desc: '查看已收藏景点'
  },
  {
    key: 'help',
    icon: '助',
    label: '使用帮助',
    desc: '了解常用功能'
  }
]

const examples = [
  '帮我推荐一个景区',
  '打开景区列表',
  '介绍灵山胜境',
  '帮我规划灵山胜境路线',
  '推荐路线',
  '生成路线'
]

const question = ref('')
const handling = ref(false)
const parksLoaded = ref(false)
const parkList = ref([])
const pendingOnsiteContext = ref(null)
const matchedParks = ref([])
const showGoListButton = ref(false)
const assistantTitle = ref('行前规划助手')
const assistantTip = ref('你可以让我推荐景区、打开景区列表，或输入具体景区名称开始 AI 数字人讲解。')
const tripInfoScene = ref('')

const onsiteParkName = computed(() => {
  const context = pendingOnsiteContext.value || {}
  return context.parkName || context.areaName || '当前景区'
})
const tripPopupSubtitle = computed(() => {
  if (tripInfoScene.value === 'route') {
    return '生成路线前，先告诉我这次想怎么逛。'
  }

  return '开启导览前，先告诉我这次想怎么逛'
})
const tripPopupCancelText = computed(() => {
  return '取消'
})
const tripPopupConfirmText = computed(() => {
  return tripInfoScene.value === 'route' ? '生成专属路线' : '确认并开启导览'
})

onShow(() => {
  primeOnsiteGuide()
  fetchParks().catch(error => {
    console.log('AI 助手预加载景区列表失败：', error)
  })
})

function primeOnsiteGuide() {
  const directContext = normalizeOnsiteContext(takeOnsiteGuideContext())
  const currentStatus = getCurrentOnsiteStatus()
  const statusContext = currentStatus?.inside
    ? normalizeOnsiteContext(currentStatus.context)
    : null
  const context = directContext || statusContext

  if (!context) {
    pendingOnsiteContext.value = null
    return
  }

  pendingOnsiteContext.value = context

  // AI 助手页不再作为首页现场导览的自动中转页，现场导览由首页直接拉起原生页。
}

function normalizeOnsiteContext(context) {
  if (!context || typeof context !== 'object') {
    return null
  }

  const parkName = context.parkName || context.areaName || ''
  const parkId = context.parkId || context.park_id || context.areaId || context.area_id || ''

  if (!parkName && !parkId) {
    return null
  }

  return {
    ...context,
    parkId,
    parkName
  }
}

function openTripInfoWithScene(scene, action) {
  tripInfoScene.value = scene
  openTripInfoConfirm(action)
}

function handleQuickAction(key) {
  if (key === 'recommend') {
    goRecommendParks()
    return
  }

  if (key === 'list') {
    goScenicList()
    return
  }

  if (key === 'favorite') {
    uni.navigateTo({
      url: '/pages/mine/favorite'
    })
    return
  }

  if (key === 'help') {
    uni.navigateTo({
      url: '/pages/help/help'
    })
  }
}

function askExample(text) {
  question.value = text
  handleUserIntent(text)
}

function handleAsk() {
  const text = question.value.trim()

  if (!text) {
    uni.showToast({
      title: '请输入想咨询的内容',
      icon: 'none'
    })
    return
  }

  handleUserIntent(text)
}

async function handleUserIntent(text) {
  if (handling.value) {
    return
  }

  handling.value = true
  matchedParks.value = []
  showGoListButton.value = false
  assistantTitle.value = '正在理解你的需求'
  assistantTip.value = '我正在判断是打开景区列表、匹配具体景区，还是做行前规划引导。'

  try {
    if (isOpenScenicListIntent(text)) {
      assistantTitle.value = '打开景区列表'
      assistantTip.value = '正在为你打开景区列表。'
      goScenicList()
      return
    }

    const routeIntent = isRouteIntent(text)
    const parks = await matchParksByQuestion(text)

    if (routeIntent) {
      if (parks.length === 1) {
        assistantTitle.value = '生成专属路线'
        assistantTip.value = `已找到“${parks[0].name}”，请先补充本次出行信息。`
        openRoutePlanForPark(parks[0], text)
        return
      }

      if (parks.length > 1) {
        matchedParks.value = parks
        assistantTitle.value = '请选择景区'
        assistantTip.value = '请选择你想规划路线的景区，我会继续生成专属路线。'
        return
      }

      if (pendingOnsiteContext.value) {
        const onsitePark = normalizePark({
          ...pendingOnsiteContext.value,
          id: pendingOnsiteContext.value.parkId || pendingOnsiteContext.value.areaCode || '',
          name: onsiteParkName.value
        })
        assistantTitle.value = '生成专属路线'
        assistantTip.value = `将基于“${onsitePark.name}”为你生成路线，请先补充本次出行信息。`
        openRoutePlanForPark(onsitePark, text || `请为${onsitePark.name}生成一条游览路线`)
        return
      }

      assistantTitle.value = '需要先选择景区'
      assistantTip.value = '你想规划哪个景区的路线？可以先选择一个景区。'
      showGoListButton.value = true
      return
    }

    if (parks.length === 1) {
      assistantTitle.value = '已匹配景区'
      assistantTip.value = `已找到“${parks[0].name}”，正在打开 AI 数字人讲解。`
      await openParkGuide(parks[0], text)
      return
    }

    if (parks.length > 1) {
      matchedParks.value = parks
      assistantTitle.value = '找到多个可能的景区'
      assistantTip.value = '请选择你想了解的景区，我会带着你的原问题打开 AI 数字人讲解。'
      return
    }

    if (isRecommendIntent(text)) {
      assistantTitle.value = '推荐景区'
      assistantTip.value = '正在为你打开热门推荐景区。'
      goRecommendParks()
      return
    }

    assistantTitle.value = '我可以这样帮你'
    assistantTip.value = '你可以输入“打开景区列表”“帮我推荐一个景区”，或直接输入具体景区名称，比如“介绍灵山胜境”。'
    showGoListButton.value = true
  } catch (error) {
    console.log('AI 助手处理意图失败：', error)
    assistantTitle.value = '暂时没理解成功'
    assistantTip.value = '我暂时无法读取景区数据，你可以先打开景区列表查看。'
    showGoListButton.value = true
  } finally {
    handling.value = false
  }
}

function isOpenScenicListIntent(text) {
  const value = safeString(text).toLowerCase().replace(/\s+/g, '')

  return [
    '打开景区列表',
    '查看景区',
    '看看景区',
    '有哪些景区',
    '景区列表',
    '全部景区'
  ].some(keyword => value.includes(keyword))
}

function isRecommendIntent(text) {
  const value = safeString(text).toLowerCase().replace(/\s+/g, '')

  return (
    /推荐.*景区/.test(value) ||
    /景区.*推荐/.test(value) ||
    value.includes('推荐一个景区') ||
    value.includes('推荐一个目的地') ||
    value.includes('热门景区') ||
    value.includes('去哪玩') ||
    value.includes('去哪里玩')
  )
}

function isRouteIntent(text) {
  const value = normalizeMatchText(text)

  return (
    value.includes('路线') ||
    value.includes('规划') ||
    value.includes('生成路线') ||
    value.includes('怎么走') ||
    value.includes('游览顺序') ||
    value.includes('推荐路线')
  )
}

function goRecommendParks() {
  uni.setStorageSync(SCENIC_LIST_MODE_KEY, 'hot')
  uni.switchTab({
    url: '/pages/scenic/scenic'
  })
}

function goScenicList() {
  uni.setStorageSync(SCENIC_LIST_MODE_KEY, 'all')
  uni.switchTab({
    url: '/pages/scenic/scenic'
  })
}

function requestGet(url) {
  return new Promise((resolve, reject) => {
    uni.request({
      url,
      method: 'GET',
      success: (res) => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve(res.data)
        } else {
          reject(new Error(`HTTP ${res.statusCode}`))
        }
      },
      fail: (err) => reject(err)
    })
  })
}

async function fetchParks(force = false) {
  if (parksLoaded.value && !force) {
    return parkList.value
  }

  const res = await requestGet(PARKS_API)
  const list = unwrapParkList(res)

  parkList.value = list.map(normalizePark).filter(item => item.name)
  parksLoaded.value = true

  return parkList.value
}

function unwrapParkList(res) {
  const data = res?.data ?? res

  if (Array.isArray(data)) {
    return data
  }

  if (Array.isArray(data?.records)) {
    return data.records
  }

  if (Array.isArray(data?.list)) {
    return data.list
  }

  if (Array.isArray(res?.list)) {
    return res.list
  }

  return []
}

function normalizePark(item = {}) {
  const id = safeString(
    item.id ||
      item.parkId ||
      item.park_id ||
      item.areaId ||
      item.area_id ||
      item.areaCode ||
      item.area_code
  )
  const name = safeString(item.name || item.parkName || item.park_name || item.areaName || item.area_name)

  return {
    ...item,
    id,
    name,
    desc: safeString(item.desc || item.description || item.introduction || ''),
    aliases: normalizeAliasList(item)
  }
}

function normalizeAliasList(item = {}) {
  const aliases = [
    item.alias,
    item.aliasName,
    item.alias_name,
    item.shortName,
    item.short_name
  ]

  if (Array.isArray(item.aliases)) {
    aliases.push(...item.aliases)
  }

  if (Array.isArray(item.tags)) {
    aliases.push(...item.tags)
  }

  return aliases.map(safeString).filter(Boolean)
}

async function matchParksByQuestion(text) {
  const parks = await fetchParks()
  const query = normalizeMatchText(text)

  if (!query) {
    return []
  }

  const scored = parks
    .map(park => ({
      park,
      score: getParkMatchScore(query, park)
    }))
    .filter(item => item.score > 0)
    .sort((a, b) => b.score - a.score)

  const topScore = scored[0]?.score || 0
  const matches = scored
    .filter(item => item.score === topScore || item.score >= 90)
    .map(item => item.park)

  return dedupeParks(matches).slice(0, 5)
}

function getParkMatchScore(query, park) {
  const names = [park.name, ...(park.aliases || [])]
  let score = 0

  names.forEach(name => {
    const normalizedName = normalizeMatchText(name)

    if (!normalizedName || normalizedName.length < 2) {
      return
    }

    if (query.includes(normalizedName)) {
      score = Math.max(score, 100 + normalizedName.length)
      return
    }

    if (normalizedName.length >= 4 && query.includes(normalizedName.slice(0, 4))) {
      score = Math.max(score, 92)
      return
    }

    if (normalizedName.length >= 3 && query.includes(normalizedName.slice(0, 3))) {
      score = Math.max(score, 86)
      return
    }

    if (normalizedName.length >= 2 && query.includes(normalizedName.slice(0, 2))) {
      score = Math.max(score, 72)
    }
  })

  return score
}

function dedupeParks(list) {
  const seen = new Set()

  return list.filter(park => {
    const key = park.id || park.name

    if (!key || seen.has(key)) {
      return false
    }

    seen.add(key)
    return true
  })
}

function normalizeMatchText(text) {
  return safeString(text)
    .toLowerCase()
    .replace(/[“”"'\s,，。.!！?？、/\\（）()【】\[\]《》<>：:；;]/g, '')
    .replace(/风景名胜区|风景区|景区|景点|公园|旅游区|旅游景区/g, '')
    .trim()
}

function formatShortDesc(desc, maxLength = 42) {
  const text = safeString(desc)
    .replace(/\s+/g, '')
    .replace(/&nbsp;/g, '')
    .trim()

  if (!text) {
    return '暂无景区简介'
  }

  return text.length > maxLength ? `${text.slice(0, maxLength)}...` : text
}

async function openParkFromMatch(park) {
  const text = question.value.trim() || `请介绍${park.name}景区`

  if (isRouteIntent(text)) {
    openRoutePlanForPark(park, text)
    return
  }

  await openParkGuide(park, text)
}

function openRoutePlanForPark(park, text) {
  openTripInfoWithScene('route', () =>
    openParkGuide(park, text || `请为${park.name}生成一条游览路线`, {
      mode: 'route_planning',
      trigger: 'route-planning',
      allowEndVisit: false,
      startVisitGuide: false
    })
  )
}

async function openParkGuide(park, autoQuestion, extraOptions = {}) {
  const basePark = normalizePark(park)
  const parkId = basePark.id
  const guideMode = extraOptions.mode || 'scenic_explain'
  const trigger = extraOptions.trigger || 'tab-ai-assistant'

  uni.showLoading({
    title: '正在打开',
    mask: true
  })

  try {
    const parkDetail = parkId ? await getParkDetailForGuide(parkId) : null
    const finalParkName = safeString(parkDetail?.name || basePark.name || '当前景区')
    const finalParkId = safeString(parkDetail?.id || parkId || '')
    const areaId = safeString(parkDetail?.areaId || parkDetail?.area_id || finalParkId)
    const digitalHumanConfig = normalizeDigitalHumanConfig(
      parkDetail?.digitalHumanConfig ||
        parkDetail?.digital_human_config ||
        parkDetail ||
        basePark,
      finalParkName
    )
    const finalAutoQuestion = safeString(autoQuestion || `请介绍${finalParkName}景区`)

    uni.removeStorageSync(GUIDE_CONVERSATION_ID_KEY)
    uni.setStorageSync('selectedScenicName', finalParkName)
    uni.setStorageSync('selectedScenicId', finalParkId)
    uni.setStorageSync('aiContextType', 'park')
    uni.setStorageSync('aiContextName', finalParkName)
    uni.setStorageSync('aiAutoQuestion', finalAutoQuestion)
    uni.setStorageSync('aiContext', JSON.stringify({
      page: 'guide_assistant',
      areaId,
      areaCode: finalParkId,
      areaName: finalParkName,
      parkId: finalParkId,
      parkName: finalParkName,
      currentSpotId: '',
      currentSpotName: '',
      source: NATIVE_LIVE2D_SOURCE,
      mode: guideMode,
      trigger,
      digitalHumanConfig
    }))

    await openNativeLive2DGuide({
      entry: 'guide-ai-park',
      source: NATIVE_LIVE2D_SOURCE,

      scenicName: finalParkName,
      scenicId: finalParkId,

      contextType: 'park',
      contextName: finalParkName,

      parkId: finalParkId,
      parkName: finalParkName,
      areaId,
      areaCode: finalParkId,
      areaName: finalParkName,

      autoQuestion: finalAutoQuestion,
      mode: guideMode,
      trigger,
      allowEndVisit: extraOptions.allowEndVisit === true,
      startVisitGuide: extraOptions.startVisitGuide === true
    })
  } catch (error) {
    console.log('AI 助手打开景区讲解失败：', error)
    uni.showToast({
      title: '打开景区讲解失败',
      icon: 'none'
    })
  } finally {
    uni.hideLoading()
  }
}

function buildDefaultDigitalHumanConfig(targetName = '') {
  return {
    ...DEFAULT_DIGITAL_HUMAN_CONFIG,
    welcomeText: targetName
      ? `你好，我是你的AI数字人导游。现在为你讲解“${targetName}”。`
      : '你好，我是你的AI数字人导游，很高兴为你服务。'
  }
}

function normalizeDigitalHumanConfig(source = {}, targetName = '') {
  const raw =
    source?.digitalHumanConfig ||
    source?.digital_human_config ||
    source?.digitalHuman ||
    source?.digital_human ||
    source ||
    {}

  const fallback = buildDefaultDigitalHumanConfig(targetName)

  const avatarId = raw.avatarId || raw.avatar_id || ''
  const avatarName = raw.avatarName || raw.avatar_name || ''
  const clothesMode =
    raw.clothesMode ||
    raw.clothes_mode ||
    raw.avatarClothesMode ||
    raw.avatar_clothes_mode ||
    ''
  const voiceId = raw.voiceId || raw.voice_id || raw.voice || ''
  const voiceName = raw.voiceName || raw.voice_name || ''
  const welcomeText = raw.welcomeText || raw.welcome_text || ''

  return {
    avatarId: safeString(avatarId || fallback.avatarId),
    avatarName: safeString(avatarName || fallback.avatarName),
    clothesMode: safeString(clothesMode || fallback.clothesMode),
    voiceId: safeString(voiceId || fallback.voiceId),
    voiceName: safeString(voiceName || fallback.voiceName),
    welcomeText: safeString(welcomeText || fallback.welcomeText)
  }
}

async function getParkDetailForGuide(parkId) {
  if (!parkId) {
    return null
  }

  try {
    const res = await requestGet(`${API_BASE}/api/app/parks/${encodeURIComponent(parkId)}`)
    return res?.data || null
  } catch (error) {
    console.log('AI 助手获取景区详情失败，使用列表数据：', error)
    return null
  }
}

function handleTripInfoCancel() {
  cancelTripInfoConfirm()
  tripInfoScene.value = ''
  opening = false
}

function handleTripInfoConfirm(selection) {
  logTripInfoSelection(selection)

  const action = consumePendingTripInfoAction()

  Promise.resolve(typeof action === 'function' ? action(selection) : null)
    .catch(error => {
      console.log('AI 助手打开导览失败：', error)
    })
    .finally(() => {
      tripInfoScene.value = ''
      setTimeout(() => {
        opening = false
      }, 800)
    })
}

function safeString(value) {
  if (value === undefined || value === null) {
    return ''
  }

  return String(value)
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #f5f7fb;
  padding: 24rpx;
  box-sizing: border-box;
}

.top-card,
.ask-card,
.reply-card {
  background: #ffffff;
  border-radius: 24rpx;
  padding: 28rpx;
  box-shadow: 0 12rpx 28rpx rgba(15, 23, 42, 0.06);
}

.top-card {
  margin-bottom: 22rpx;
}

.page-title {
  font-size: 40rpx;
  font-weight: 700;
  color: #111827;
}

.page-subtitle {
  margin-top: 12rpx;
  font-size: 25rpx;
  color: #667085;
  line-height: 1.7;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18rpx;
  margin-bottom: 22rpx;
}

.quick-item {
  min-height: 156rpx;
  background: #ffffff;
  border-radius: 22rpx;
  padding: 22rpx;
  box-sizing: border-box;
  box-shadow: 0 10rpx 24rpx rgba(15, 23, 42, 0.05);
}

.quick-icon {
  width: 48rpx;
  height: 48rpx;
  border-radius: 16rpx;
  background: #eff6ff;
  color: #2563eb;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24rpx;
  font-weight: 700;
}

.quick-label {
  margin-top: 16rpx;
  font-size: 28rpx;
  font-weight: 700;
  color: #1f2937;
}

.quick-desc {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #7b8794;
  line-height: 1.4;
}

.ask-card {
  margin-bottom: 22rpx;
}

.card-title,
.reply-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #111827;
}

.card-desc,
.reply-text {
  margin-top: 10rpx;
  font-size: 24rpx;
  color: #667085;
  line-height: 1.65;
}

.input-box {
  margin-top: 22rpx;
  background: #f8fafc;
  border: 1rpx solid #e5e7eb;
  border-radius: 18rpx;
  padding: 18rpx;
  box-sizing: border-box;
}

.question-input {
  width: 100%;
  min-height: 92rpx;
  font-size: 26rpx;
  line-height: 1.6;
  color: #111827;
}

.send-btn {
  margin-top: 18rpx;
  height: 84rpx;
  border-radius: 18rpx;
  background: #2563eb;
  color: #ffffff;
  font-size: 28rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.send-btn.disabled {
  opacity: 0.6;
}

.example-list {
  display: flex;
  flex-wrap: wrap;
  gap: 14rpx;
  margin-top: 20rpx;
}

.example-chip {
  max-width: 100%;
  padding: 12rpx 18rpx;
  border-radius: 999rpx;
  background: #f3f4f6;
  color: #475467;
  font-size: 23rpx;
  line-height: 1.4;
}

.reply-card {
  margin-bottom: 24rpx;
}

.match-list {
  margin-top: 18rpx;
  display: flex;
  flex-direction: column;
  gap: 14rpx;
}

.match-item {
  padding: 18rpx;
  border-radius: 18rpx;
  background: #f8fafc;
  border: 1rpx solid #e5e7eb;
}

.match-name {
  font-size: 26rpx;
  font-weight: 700;
  color: #1f2937;
}

.match-desc {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #7b8794;
  line-height: 1.5;
}

.reply-action {
  margin-top: 20rpx;
  height: 76rpx;
  border-radius: 18rpx;
  background: #eef2ff;
  color: #3154d4;
  font-size: 26rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
