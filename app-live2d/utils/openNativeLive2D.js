// utils/openNativeLive2D.js

import {
  API_BASE,
  BEHAVIOR_EVENT_PATH,
  NATIVE_LIVE2D_SOURCE
} from './api'
import { getAuthPayload, getToken } from './auth'
import {
  fetchDigitalHumanConfig,
  getCachedDigitalHumanConfig,
  normalizeDigitalHumanConfig
} from './digitalHuman'
import {
  markPendingGuideReturnCheck,
  switchActiveScenicVisit
} from './visit'

const LIVE2D_ACTION = 'com.rjb.live2d.OPEN_GUIDE'

let lastOpenTime = 0
let lastOpenKey = ''

const OPEN_NATIVE_LOCK_TIME = 1500

export async function openNativeLive2DGuide(options = {}) {
  // #ifdef APP-PLUS
  try {
    const systemInfo = uni.getSystemInfoSync()

    if (systemInfo.platform !== 'android') {
      uni.showToast({
        title: '当前仅支持 Android 真机',
        icon: 'none'
      })
      return false
    }

    if (typeof plus === 'undefined' || !plus.android) {
      uni.showToast({
        title: '请运行到 Android App 真机',
        icon: 'none'
      })
      return false
    }

    clearNativeGuideReturnState()

    const storedContext = getStoredAiContext()
    const authPayload = getAuthPayload()
    const storedParkId = safeGetStorage('currentParkId')
    const storedParkName = safeGetStorage('currentParkName')
    const storedGroupSize = safeGetStorage('groupSize')
    const storedTravelType = safeGetStorage('travelType')
    const storedVisitPreference = safeGetStorage('visitPreference')

    const token = pickFirstFilledValue(
      options.token,
      options.accessToken,
      options.access_token,
      options.authToken,
      options.auth_token,
      options.Authorization,
      options.authorization,
      getToken(),
      safeGetStorage('APP_TOKEN'),
      safeGetStorage('token'),
      safeGetStorage('accessToken'),
      safeGetStorage('access_token'),
      ''
    )

    const entry = getOption(options, 'entry', 'guide-page')

    const contextType = getOption(
      options,
      'contextType',
      uni.getStorageSync('aiContextType') || ''
    )

    const contextName = getOption(
      options,
      'contextName',
      uni.getStorageSync('aiContextName') || ''
    )

    const autoQuestion = getOption(
      options,
      'autoQuestion',
      uni.getStorageSync('aiAutoQuestion') || ''
    )

    const scenicName = pickFirstFilledValue(
      options.scenicName,
      options.scenic_name,
      safeGetStorage('selectedScenicName'),
      storedContext.currentSpotName,
      storedContext.areaName,
      '通用导览'
    )

    const scenicId = pickFirstFilledValue(
      options.scenicId,
      options.scenic_id,
      safeGetStorage('selectedScenicId'),
      storedContext.currentSpotId,
      storedContext.areaCode,
      ''
    )

    const areaName = getOption(options, 'areaName', storedContext.areaName || '')
    const areaCode = getOption(options, 'areaCode', storedContext.areaCode || '')
    const spotName = getOption(options, 'spotName', storedContext.currentSpotName || '')
    const spotId = getOption(options, 'spotId', storedContext.currentSpotId || '')

    const parkId = pickFirstFilledValue(
      options.parkId,
      options.park_id,
      storedParkId,
      areaCode,
      scenicId,
      ''
    )

    const configParkId = pickFirstFilledValue(
      options.parkId,
      options.park_id,
      storedParkId,
      ''
    )

    const cachedDigitalHumanConfig = configParkId
      ? getCachedDigitalHumanConfig(configParkId)
      : null
    const forceRefreshDigitalHuman =
      options.forceRefreshDigitalHuman === false ||
      options.forceRefreshDigitalHuman === 'false' ||
      options.force_refresh_digital_human === false ||
      options.force_refresh_digital_human === 'false'
        ? false
        : true
    const digitalHumanConfig = normalizeDigitalHumanConfig(
      configParkId
        ? await fetchDigitalHumanConfig(configParkId, {
            forceRefresh: forceRefreshDigitalHuman
          })
        : null,
      configParkId
    )

    const parkName = pickFirstFilledValue(
      options.parkName,
      options.park_name,
      storedParkName,
      areaName,
      scenicName,
      ''
    )

    const areaId = pickFirstFilledValue(
      options.areaId,
      options.area_id,
      areaCode,
      parkId,
      scenicId,
      ''
    )

    const source = pickFirstFilledValue(
      options.source,
      options.eventSource,
      options.event_source,
      NATIVE_LIVE2D_SOURCE
    )

    const mode = getOption(options, 'mode', '')
    const trigger = getOption(options, 'trigger', '')
    const distance = getOption(options, 'distance', '')
    const latitude = getOption(options, 'latitude', '')
    const longitude = getOption(options, 'longitude', '')
    const estimatedDuration = getOption(
      options,
      'estimatedDuration',
      getOption(options, 'estimated_duration', '')
    )
    const entrySource = getOption(
      options,
      'entrySource',
      getOption(options, 'entry_source', '')
    )

    const explicitVisitId = pickFirstFilledValue(
      options.visitId,
      options.visit_id,
      ''
    )
    const isOnsiteGuide = resolveIsOnsiteGuideMode(options, mode, entry)
    const requestedStartVisitGuide = false
    const isNonOnsiteExplain = isNonOnsiteExplainEntry(entry, mode)
    const optionVisitStatus = pickFirstFilledValue(
      options.visit_status,
      options.visitStatus,
      ''
    )
    const finalVisitStatus = isOnsiteGuide
      ? 'IN_AREA'
      : isNonOnsiteExplain && !optionVisitStatus
        ? 'NOT_IN_AREA'
        : optionVisitStatus
    const optionIsInsideArea = pickFirstFilledValue(
      options.is_inside_area,
      options.isInsideArea,
      ''
    )
    const finalIsInsideArea = isOnsiteGuide
      ? 'true'
      : isNonOnsiteExplain && !optionIsInsideArea
        ? 'false'
        : normalizeBooleanExtra(optionIsInsideArea)
    const rawLocationContext = getOption(
      options,
      'location_context',
      getOption(options, 'locationContext', '')
    )
    const locationContextText = rawLocationContext && typeof rawLocationContext === 'object'
      ? safeJsonStringify(rawLocationContext)
      : safeString(rawLocationContext)

    if (isOnsiteGuide && !explicitVisitId) {
      console.warn('[openNativeLive2DGuide] 现场导览缺少显式 visitId，已拦截打开：', {
        entry,
        mode,
        parkId,
        parkName,
        scenicId,
        scenicName
      })
      uni.showToast({
        title: '请先提交出行信息开启导览',
        icon: 'none'
      })
      return false
    }

    const rawAllowEndVisit = getOption(
      options,
      'allowEndVisit',
      getOption(options, 'allow_end_visit', undefined)
    )

    const allowEndVisit =
      rawAllowEndVisit === undefined
        ? isOnsiteGuide
        : rawAllowEndVisit === true || rawAllowEndVisit === 'true'

    const avatarId = pickFirstFilledValue(
      options.avatarId,
      options.avatar_id,
      digitalHumanConfig.avatarId,
      cachedDigitalHumanConfig?.avatarId,
      'guide_female_01'
    )

    const avatarName = pickFirstFilledValue(
      options.avatarName,
      options.avatar_name,
      digitalHumanConfig.avatarName,
      cachedDigitalHumanConfig?.avatarName,
      '灵灵'
    )

    const modelPath = pickFirstFilledValue(
      options.modelPath,
      options.model_path,
      digitalHumanConfig.modelPath,
      cachedDigitalHumanConfig?.modelPath,
      avatarId,
      'guide_female_01'
    )

    const clothesMode = getOption(
      options,
      'clothesMode',
      getOption(
        options,
        'clothes_mode',
        getOption(
          options,
          'avatarClothesMode',
          getOption(
            options,
            'avatar_clothes_mode',
            uni.getStorageSync('currentClothesMode') || ''
          )
        )
      )
    )

    const voiceId = pickFirstFilledValue(
      options.voiceId,
      options.voice_id,
      digitalHumanConfig.voiceId,
      cachedDigitalHumanConfig?.voiceId,
      'zhitian_emo'
    )

    const voiceName = pickFirstFilledValue(
      options.voiceName,
      options.voice_name,
      digitalHumanConfig.voiceName,
      cachedDigitalHumanConfig?.voiceName,
      ''
    )

    const welcomeText = pickFirstFilledValue(
      options.welcomeText,
      options.welcome_text,
      digitalHumanConfig.welcomeText,
      cachedDigitalHumanConfig?.welcomeText,
      `你好，我是${avatarName || 'AI数字人导游'}，很高兴为你服务。`
    )

    const finalDigitalHumanConfig = {
      ...digitalHumanConfig,
      parkId: safeString(configParkId || digitalHumanConfig.parkId),
      avatarId: safeString(avatarId),
      avatarName: safeString(avatarName),
      modelPath: safeString(modelPath),
      voiceId: safeString(voiceId),
      voiceName: safeString(voiceName),
      welcomeText: safeString(welcomeText)
    }

    const digitalHumanConfigJson = safeJsonStringify(finalDigitalHumanConfig)

    saveDigitalHumanConfigToStorage(finalDigitalHumanConfig, configParkId)

    console.log('[digital-human] resolve:', JSON.stringify({
      parkId: safeString(configParkId),
      fetchedAvatarId: safeString(digitalHumanConfig.avatarId),
      cachedAvatarId: safeString(cachedDigitalHumanConfig?.avatarId),
      finalAvatarId: safeString(avatarId),
      fetchedModelPath: safeString(digitalHumanConfig.modelPath),
      cachedModelPath: safeString(cachedDigitalHumanConfig?.modelPath),
      finalModelPath: safeString(modelPath)
    }, null, 2))

    const userId = normalizeRealUserId(pickFirstFilledValue(
      options.userId,
      options.user_id,
      options.currentUserId,
      options.current_user_id,
      authPayload.user_id || ''
    ))

    const visitorId = pickFirstFilledValue(
      options.visitorId,
      options.visitor_id,
      authPayload.visitor_id || ''
    )

    const sessionId = pickFirstFilledValue(
      options.sessionId,
      options.session_id,
      authPayload.session_id || ''
    )

    const conversationId = getOption(
      options,
      'conversationId',
      getOption(
        options,
        'conversation_id',
        uni.getStorageSync('guideConversationId') ||
          sessionId ||
          visitorId ||
          ''
      )
    )

    // 注意：这里不能再用 visitorId 兜底。
    // visitor_ / android-live2d-* 只能作为游客或演示标识，不能作为后端登录用户 ID。
    // tourist_xxx 是当前后端 tourist_user.user_id 的真实业务登录 ID，允许传入。
    const realUserId = userId || ''

    if (isOnsiteGuide && !realUserId) {
      console.warn('[openNativeLive2DGuide] 现场导览缺少真实登录用户ID，已拦截打开：', {
        entry,
        mode,
        rawUserId: options.userId || options.user_id || authPayload.user_id || '',
        visitorId
      })
      uni.showToast({
        title: '登录用户信息异常，请重新登录后再进入导览',
        icon: 'none'
      })
      return false
    }

    const visitId = explicitVisitId

    if (isOnsiteGuide && visitId) {
      markPendingGuideReturnCheck({
        visitId,
        areaName: parkName || areaName || scenicName
      })
    }

    const finalGroupSize = pickFirstFilledValue(
      options.groupSize,
      options.group_size,
      options.travelPeopleCount,
      options.travel_people_count,
      safeGetStorage('groupSize'),
      storedGroupSize,
      ''
    )

    const finalTravelType = pickFirstFilledValue(
      options.travelType,
      options.travel_type,
      safeGetStorage('travelType'),
      storedTravelType,
      ''
    )

    const finalVisitPreference = pickFirstFilledValue(
      options.visitPreference,
      options.visit_preference,
      options.travelPreference,
      options.travel_preference,
      safeGetStorage('visitPreference'),
      storedVisitPreference,
      ''
    )

    const scenicVisitTarget = resolveScenicVisitTarget({
      options,
      contextType,
      scenicId,
      scenicName,
      spotId,
      spotName,
      parkName
    })

    console.log('[scenic-stay] target:', JSON.stringify(scenicVisitTarget, null, 2))

    const openKey = [
      entry,
      scenicName,
      scenicId,
      contextType,
      contextName,
      areaId,
      areaCode,
      areaName,
      spotId,
      spotName,
      mode,
      trigger,
      entrySource,
      estimatedDuration,
      avatarId,
      modelPath,
      clothesMode,
      voiceId,
      finalDigitalHumanConfig.avatarId,
      finalDigitalHumanConfig.voiceId
    ].join('|')

    if (isOpenLocked(openKey)) {
      console.log('原生 Live2D 打开过于频繁，已拦截：', openKey)
      return false
    }

    markOpenLocked(openKey)

    const main = plus.android.runtimeMainActivity()
    const Intent = plus.android.importClass('android.content.Intent')

    const intent = new Intent(LIVE2D_ACTION)
    intent.addCategory(Intent.CATEGORY_DEFAULT)

    intent.putExtra('from', 'uni-app')
    intent.putExtra('entry', safeString(entry))

    intent.putExtra('apiBaseUrl', safeString(API_BASE))
    intent.putExtra('api_base_url', safeString(API_BASE))
    intent.putExtra('backendBaseUrl', safeString(API_BASE))
    intent.putExtra('backend_base_url', safeString(API_BASE))
    intent.putExtra('behaviorEventPath', safeString(BEHAVIOR_EVENT_PATH))
    intent.putExtra('behavior_event_path', safeString(BEHAVIOR_EVENT_PATH))
    intent.putExtra('source', safeString(source))

    intent.putExtra('scenicName', safeString(scenicName))
    intent.putExtra('scenicId', safeString(scenicId))
    intent.putExtra('scenic_name', safeString(scenicName))
    intent.putExtra('scenic_id', safeString(scenicId))

    intent.putExtra('contextType', safeString(contextType))
    intent.putExtra('contextName', safeString(contextName))
    intent.putExtra('autoQuestion', safeString(autoQuestion))

    intent.putExtra('areaName', safeString(areaName))
    intent.putExtra('areaCode', safeString(areaCode))
    intent.putExtra('areaId', safeString(areaId))
    intent.putExtra('area_name', safeString(areaName))
    intent.putExtra('area_code', safeString(areaCode))
    intent.putExtra('area_id', safeString(areaId))

    intent.putExtra('spotName', safeString(spotName))
    intent.putExtra('spotId', safeString(spotId))
    intent.putExtra('spot_name', safeString(spotName))
    intent.putExtra('spot_id', safeString(spotId))

    intent.putExtra('parkId', safeString(parkId))
    intent.putExtra('parkName', safeString(parkName))
    intent.putExtra('park_id', safeString(parkId))
    intent.putExtra('park_name', safeString(parkName))

    intent.putExtra('visitId', safeString(visitId))
    intent.putExtra('visit_id', safeString(visitId))
    intent.putExtra('groupSize', safeString(finalGroupSize))
    intent.putExtra('group_size', safeString(finalGroupSize))
    intent.putExtra('travelPeopleCount', safeString(finalGroupSize))
    intent.putExtra('travel_people_count', safeString(finalGroupSize))
    intent.putExtra('travelType', safeString(finalTravelType))
    intent.putExtra('travel_type', safeString(finalTravelType))
    intent.putExtra('visitPreference', safeString(finalVisitPreference))
    intent.putExtra('visit_preference', safeString(finalVisitPreference))
    intent.putExtra('travelPreference', safeString(finalVisitPreference))
    intent.putExtra('travel_preference', safeString(finalVisitPreference))
    intent.putExtra('estimatedDuration', safeString(estimatedDuration))
    intent.putExtra('estimated_duration', safeString(estimatedDuration))
    intent.putExtra('entrySource', safeString(entrySource))
    intent.putExtra('entry_source', safeString(entrySource))

    intent.putExtra('avatarId', safeString(avatarId))
    intent.putExtra('avatarName', safeString(avatarName))
    intent.putExtra('avatar_id', safeString(avatarId))
    intent.putExtra('avatar_name', safeString(avatarName))
    intent.putExtra('modelPath', safeString(modelPath))
    intent.putExtra('model_path', safeString(modelPath))
    intent.putExtra('digitalHumanConfigJson', safeString(digitalHumanConfigJson))
    intent.putExtra('digital_human_config_json', safeString(digitalHumanConfigJson))

    intent.putExtra('clothesMode', safeString(clothesMode))
    intent.putExtra('clothes_mode', safeString(clothesMode))
    intent.putExtra('avatarClothesMode', safeString(clothesMode))
    intent.putExtra('avatar_clothes_mode', safeString(clothesMode))

    intent.putExtra('voiceId', safeString(voiceId))
    intent.putExtra('voiceName', safeString(voiceName))
    intent.putExtra('voice_id', safeString(voiceId))
    intent.putExtra('voice_name', safeString(voiceName))

    intent.putExtra('welcomeText', safeString(welcomeText))
    intent.putExtra('welcome_text', safeString(welcomeText))

    intent.putExtra('token', safeString(token))
    intent.putExtra('accessToken', safeString(token))
    intent.putExtra('access_token', safeString(token))
    intent.putExtra('authToken', safeString(token))
    intent.putExtra('auth_token', safeString(token))
    intent.putExtra('Authorization', safeString(token))
    intent.putExtra('authorization', safeString(token))

    intent.putExtra('user_id', safeString(realUserId))
    intent.putExtra('userId', safeString(realUserId))
    intent.putExtra('currentUserId', safeString(realUserId))
    intent.putExtra('current_user_id', safeString(realUserId))

    intent.putExtra('login_user_id', safeString(userId))
    intent.putExtra('loginUserId', safeString(userId))

    intent.putExtra('visitor_id', safeString(visitorId))
    intent.putExtra('visitorId', safeString(visitorId))

    intent.putExtra('session_id', safeString(sessionId))
    intent.putExtra('sessionId', safeString(sessionId))

    intent.putExtra('conversation_id', safeString(conversationId))
    intent.putExtra('conversationId', safeString(conversationId))

    intent.putExtra('scene_code', safeString(spotId || scenicId))
    intent.putExtra('scene_name', safeString(spotName || scenicName))
    // 非现场入口不要把 scenicId/areaId 当作 current_spot 传给 Android
    // 否则 routeStartType 会被错误变成 current_spot，导致 AI 返回现场路线
    intent.putExtra('current_spot_id', isOnsiteGuide ? safeString(spotId) : '')
    intent.putExtra('current_spot_name', isOnsiteGuide ? safeString(spotName) : '')

    intent.putExtra('mode', safeString(mode))
    intent.putExtra('guideMode', safeString(mode))
    intent.putExtra('guide_mode', safeString(mode))
    intent.putExtra('isOnsiteGuide', isOnsiteGuide ? 'true' : 'false')
    intent.putExtra('is_onsite_guide', isOnsiteGuide ? 'true' : 'false')
    intent.putExtra('visit_status', safeString(finalVisitStatus))
    intent.putExtra('visitStatus', safeString(finalVisitStatus))
    intent.putExtra('is_inside_area', safeString(finalIsInsideArea))
    intent.putExtra('isInsideArea', safeString(finalIsInsideArea))
    intent.putExtra('startVisitGuide', requestedStartVisitGuide ? 'true' : 'false')
    intent.putExtra('start_visit_guide', requestedStartVisitGuide ? 'true' : 'false')
    intent.putExtra('allowEndVisit', allowEndVisit ? 'true' : 'false')
    intent.putExtra('allow_end_visit', allowEndVisit ? 'true' : 'false')
    intent.putExtra('trigger', safeString(trigger))
    intent.putExtra('distance', safeString(distance))
    intent.putExtra('latitude', safeString(latitude))
    intent.putExtra('longitude', safeString(longitude))
    intent.putExtra('location_context', safeString(locationContextText))
    intent.putExtra('locationContext', safeString(locationContextText))

    const extras = {
      entry: safeString(entry),

      apiBaseUrl: safeString(API_BASE),
      api_base_url: safeString(API_BASE),
      backendBaseUrl: safeString(API_BASE),
      backend_base_url: safeString(API_BASE),
      behaviorEventPath: safeString(BEHAVIOR_EVENT_PATH),
      behavior_event_path: safeString(BEHAVIOR_EVENT_PATH),
      source: safeString(source),

      token: maskToken(token),
      accessToken: maskToken(token),
      access_token: maskToken(token),
      authToken: maskToken(token),
      auth_token: maskToken(token),
      Authorization: maskToken(token),
      authorization: maskToken(token),

      userId: safeString(realUserId),
      user_id: safeString(realUserId),
      currentUserId: safeString(realUserId),
      current_user_id: safeString(realUserId),
      loginUserId: safeString(userId),
      login_user_id: safeString(userId),
      visitorId: safeString(visitorId),
      visitor_id: safeString(visitorId),
      sessionId: safeString(sessionId),
      session_id: safeString(sessionId),
      conversationId: safeString(conversationId),
      conversation_id: safeString(conversationId),
      visitId: safeString(visitId),
      visit_id: safeString(visitId),

      parkId: safeString(parkId),
      park_id: safeString(parkId),
      parkName: safeString(parkName),
      park_name: safeString(parkName),

      areaId: safeString(areaId),
      area_id: safeString(areaId),
      areaName: safeString(areaName),
      area_name: safeString(areaName),
      areaCode: safeString(areaCode),
      area_code: safeString(areaCode),

      scenicId: safeString(scenicId),
      scenic_id: safeString(scenicId),
      scenicName: safeString(scenicName),
      scenic_name: safeString(scenicName),

      spotName: safeString(spotName),
      spot_name: safeString(spotName),
      spotId: safeString(spotId),
      spot_id: safeString(spotId),

      scene_name: safeString(spotName || scenicName),
      scene_code: safeString(spotId || scenicId),
      // 非现场入口不要把 scenicId 当作 current_spot
      current_spot_id: isOnsiteGuide ? safeString(spotId) : '',
      current_spot_name: isOnsiteGuide ? safeString(spotName) : '',

      groupSize: safeString(finalGroupSize),
      group_size: safeString(finalGroupSize),
      travelPeopleCount: safeString(finalGroupSize),
      travel_people_count: safeString(finalGroupSize),
      travelType: safeString(finalTravelType),
      travel_type: safeString(finalTravelType),
      visitPreference: safeString(finalVisitPreference),
      visit_preference: safeString(finalVisitPreference),
      travelPreference: safeString(finalVisitPreference),
      travel_preference: safeString(finalVisitPreference),
      estimatedDuration: safeString(estimatedDuration),
      estimated_duration: safeString(estimatedDuration),
      entrySource: safeString(entrySource),
      entry_source: safeString(entrySource),

      contextType: safeString(contextType),
      contextName: safeString(contextName),
      autoQuestion: safeString(autoQuestion),

      mode: safeString(mode),
      guideMode: safeString(mode),
      guide_mode: safeString(mode),
      isOnsiteGuide: isOnsiteGuide ? 'true' : 'false',
      is_onsite_guide: isOnsiteGuide ? 'true' : 'false',
      visit_status: safeString(finalVisitStatus),
      visitStatus: safeString(finalVisitStatus),
      is_inside_area: safeString(finalIsInsideArea),
      isInsideArea: safeString(finalIsInsideArea),
      startVisitGuide: requestedStartVisitGuide ? 'true' : 'false',
      start_visit_guide: requestedStartVisitGuide ? 'true' : 'false',
      allowEndVisit: allowEndVisit ? 'true' : 'false',
      allow_end_visit: allowEndVisit ? 'true' : 'false',
      trigger: safeString(trigger),
      distance: safeString(distance),
      latitude: safeString(latitude),
      longitude: safeString(longitude),
      location_context: safeString(locationContextText),
      locationContext: safeString(locationContextText),

      avatarId: safeString(avatarId),
      avatar_id: safeString(avatarId),
      avatarName: safeString(avatarName),
      avatar_name: safeString(avatarName),
      modelPath: safeString(modelPath),
      model_path: safeString(modelPath),
      digitalHumanConfigJson: summarizeDigitalHumanConfig(finalDigitalHumanConfig),
      digital_human_config_json: summarizeDigitalHumanConfig(finalDigitalHumanConfig),
      clothesMode: safeString(clothesMode),
      clothes_mode: safeString(clothesMode),
      avatarClothesMode: safeString(clothesMode),
      avatar_clothes_mode: safeString(clothesMode),
      voiceId: safeString(voiceId),
      voice_id: safeString(voiceId),
      voiceName: safeString(voiceName),
      voice_name: safeString(voiceName),
      welcomeText: safeString(welcomeText),
      welcome_text: safeString(welcomeText)
    }

    console.log('[openNativeLive2DGuide] extras:', JSON.stringify(extras, null, 2))

    console.log('[openNativeLive2DGuide] key fields:', JSON.stringify({
      hasToken: !!token,
      userId: extras.userId || extras.user_id,
      visitId: extras.visitId || extras.visit_id,
      parkId: extras.parkId || extras.park_id,
      parkName: extras.parkName || extras.park_name,
      areaId: extras.areaId || extras.area_id,
      areaCode: extras.areaCode || extras.area_code,
      scenicId: extras.scenicId || extras.scenic_id,
      scenicName: extras.scenicName || extras.scenic_name,
      spotId: extras.spotId || extras.spot_id,
      spotName: extras.spotName || extras.spot_name,
      groupSize: extras.groupSize || extras.group_size,
      travelType: extras.travelType || extras.travel_type,
      visitPreference: extras.visitPreference || extras.visit_preference,
      estimatedDuration: extras.estimatedDuration || extras.estimated_duration,
      entrySource: extras.entrySource || extras.entry_source,
      apiBaseUrl: extras.apiBaseUrl || extras.api_base_url,
      behaviorEventPath: extras.behaviorEventPath || extras.behavior_event_path,
      source: extras.source,
      entry: extras.entry,
      mode: extras.mode,
      guideMode: extras.guideMode || extras.guide_mode,
      isOnsiteGuide: extras.isOnsiteGuide || extras.is_onsite_guide,
      visitStatus: extras.visitStatus || extras.visit_status,
      isInsideArea: extras.isInsideArea || extras.is_inside_area,
      avatarId: extras.avatarId || extras.avatar_id,
      modelPath: extras.modelPath || extras.model_path,
      voiceId: extras.voiceId || extras.voice_id,
      startVisitGuide: extras.startVisitGuide || extras.start_visit_guide,
      allowEndVisit: extras.allowEndVisit || extras.allow_end_visit
    }, null, 2))

    if (scenicVisitTarget) {
      try {
        const scenicStayResult = await Promise.race([
          switchActiveScenicVisit({
            visitId,
            userId: userId || realUserId || '',
            parkId,
            scenicId: scenicVisitTarget.scenicId,
            scenicName: scenicVisitTarget.scenicName,
            enterSource: scenicVisitTarget.enterSource
          }),
          new Promise(resolve => {
            setTimeout(() => {
              resolve({
                success: false,
                timeout: true,
                message: 'scenic stay enter timeout'
              })
            }, 1000)
          })
        ])

        if (scenicStayResult && scenicStayResult.timeout) {
          console.warn('[scenic-stay] timeout:', JSON.stringify(scenicStayResult, null, 2))
        }

        console.log('[scenic-stay] result:', JSON.stringify(scenicStayResult, null, 2))
      } catch (error) {
        console.warn('[scenic-stay] failed before native open:', error)
      }
    }

    main.startActivity(intent)
    return true
  } catch (e) {
    console.error('打开原生 Live2D 页面失败：', e)

    uni.showModal({
      title: '打开失败',
      content:
        '没有打开 Android 原生 Live2D 页面。\n\n' +
        '请检查：\n' +
        '1. Android 原生 Live2D App 是否已经安装到这台手机\n' +
        '2. AndroidManifest.xml 里是否有 com.rjb.live2d.OPEN_GUIDE\n' +
        '3. MainActivity 是否设置 android:exported="true"\n' +
        '4. 现在是否是运行到 Android 真机 App，而不是浏览器/H5',
      showCancel: false
    })
    return false
  }
  // #endif

  // #ifndef APP-PLUS
  uni.showToast({
    title: '浏览器不能打开原生 Activity，请运行到 Android 真机',
    icon: 'none'
  })
  return false
  // #endif
}

function isOpenLocked(openKey) {
  const now = Date.now()

  if (lastOpenKey === openKey && now - lastOpenTime < OPEN_NATIVE_LOCK_TIME) {
    return true
  }

  return false
}

function markOpenLocked(openKey) {
  lastOpenKey = openKey
  lastOpenTime = Date.now()
}

function clearNativeGuideReturnState() {
  try {
    uni.removeStorageSync('NATIVE_GUIDE_RETURN_CONTEXT')
    uni.removeStorageSync('NATIVE_GUIDE_RETURNING')
    uni.removeStorageSync('NATIVE_GUIDE_RETURN_LOCK')
  } catch (error) {
    console.log('清理原生导览返回残留失败：', error)
  }
}

function getOption(options, key, fallback) {
  if (Object.prototype.hasOwnProperty.call(options, key)) {
    return options[key]
  }

  return fallback
}

function getStoredAiContext() {
  const cache = uni.getStorageSync('aiContext')

  if (!cache) {
    return {}
  }

  if (typeof cache === 'object') {
    return cache || {}
  }

  try {
    return JSON.parse(cache) || {}
  } catch (e) {
    return {}
  }
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

function resolveIsOnsiteGuideMode(options, mode, entry = '') {
  const explicit = getOption(
    options,
    'isOnsiteGuide',
    getOption(options, 'is_onsite_guide', undefined)
  )

  if (explicit === true || explicit === 'true') {
    return true
  }

  if (explicit === false || explicit === 'false') {
    return false
  }

  const entryText = safeString(entry).toLowerCase()
  return mode === 'onsite' ||
    entryText.includes('onsite') ||
    entryText.includes('continue-onsite-guide')
}

/**
 * 判断当前入口是否属于非现场数字人讲解页。
 * 这类页面点击"推荐路线"应按行前路线处理，不应走现场动态路线。
 */
function isNonOnsiteExplainEntry(entry = '', mode = '') {
  const entryLower = safeString(entry).toLowerCase()
  const modeLower = safeString(mode).toLowerCase()

  // 非现场入口关键词
  if (entryLower.includes('scenic-list-explain')
      || entryLower.includes('scenic-detail-explain')
      || entryLower.includes('park-detail-explain')
      || entryLower.includes('spot-detail-explain')) {
    return true
  }

  // 非现场 mode
  if (modeLower === 'scenic_explain'
      || modeLower === 'spot_explain'
      || modeLower === 'normal'
      || modeLower === '') {
    return true
  }

  return false
}

function resolveScenicVisitTarget({ options, contextType, scenicId, scenicName, spotId, spotName, parkName }) {
  const explicitTrack = getOption(options, 'trackScenicVisit', undefined)
  const shouldTrack = explicitTrack === undefined ? contextType === 'scenic' : !!explicitTrack

  if (!shouldTrack) {
    return null
  }

  const targetScenicId = pickFirstFilledValue(
    options.activeScenicId,
    options.active_scenic_id,
    options.scenicTrackId,
    options.scenic_track_id,
    spotId,
    scenicId,
    ''
  )

  if (!targetScenicId) {
    return null
  }

  return {
    scenicId: targetScenicId,
    scenicName: pickFirstFilledValue(
      options.activeScenicName,
      options.active_scenic_name,
      options.scenicTrackName,
      options.scenic_track_name,
      spotName,
      scenicName,
      parkName,
      ''
    ),
    enterSource: getOption(
      options,
      'enterSource',
      getOption(options, 'enter_source', 'ai_guide_click')
    )
  }
}

function normalizeBooleanExtra(value) {
  if (value === true || value === 'true') {
    return 'true'
  }

  if (value === false || value === 'false') {
    return 'false'
  }

  return safeString(value)
}

function safeGetStorage(key) {
  try {
    return uni.getStorageSync(key)
  } catch (error) {
    console.warn(`[openNativeLive2DGuide] 读取本地缓存失败: ${key}`, error)
    return ''
  }
}

function safeSetStorage(key, value) {
  try {
    uni.setStorageSync(key, value)
  } catch (error) {
    console.warn(`[openNativeLive2DGuide] 写入本地缓存失败: ${key}`, error)
  }
}

function saveDigitalHumanConfigToStorage(config = {}, parkId = '') {
  const finalParkId = safeString(parkId || config.parkId)

  if (!finalParkId) {
    return
  }

  safeSetStorage(`currentDigitalHumanConfig_${finalParkId}`, {
    ...config,
    parkId: finalParkId
  })
}

function safeJsonStringify(value) {
  try {
    return JSON.stringify(value || {})
  } catch (error) {
    console.warn('[openNativeLive2DGuide] 数字人配置序列化失败:', error)
    return ''
  }
}

function summarizeDigitalHumanConfig(config = {}) {
  return safeJsonStringify({
    parkId: config.parkId || '',
    avatarId: config.avatarId || '',
    avatarName: config.avatarName || '',
    modelPath: config.modelPath || '',
    voiceId: config.voiceId || '',
    voiceName: config.voiceName || '',
    isEnabled: config.isEnabled
  })
}

function safeString(value) {
  if (value === undefined || value === null) {
    return ''
  }

  return String(value)
}

function normalizeRealUserId(value) {
  const text = safeString(value).trim()

  if (!text) {
    return ''
  }

  if (
    text === 'anonymous' ||
    text.startsWith('visitor_') ||
    text.startsWith('android-live2d-')
  ) {
    return ''
  }

  // 后端 tourist_user.user_id 的真实业务 ID 本来就是 tourist_xxx，不能过滤。
  return text
}

function maskToken(token) {
  const text = safeString(token).trim()

  if (!text) {
    return ''
  }

  if (text.length <= 12) {
    return `${text.slice(0, 2)}***${text.slice(-2)}`
  }

  return `${text.slice(0, 6)}***${text.slice(-6)}`
}
