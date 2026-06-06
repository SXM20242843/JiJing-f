// common/scenic-geofence.js

/**
 * 景区地理围栏配置
 *
 * 说明：
 * 1. parkId 必须和数据库 /api/app/parks 返回的 id 完全一致。
 * 2. 当前为了真机测试，灵山胜境坐标先写成你现在手机定位附近。
 * 3. 后面正式版本要把 latitude / longitude 改成真实景区中心点。
 * 4. radius 单位：米。
 */
export const SCENIC_GEOFENCES = [
  {
    // 数据库真实 ID：灵山胜境
    parkId: 'AREA_0001',
    parkName: '灵山胜境',

    // 当前测试坐标：你手机现在定位附近
    // 后面正式上线前要改成灵山胜境真实经纬度
    latitude: 43.993922,
    longitude: 125.390252,

    radius: 3000,
    intro: '用于测试 AI 数字人现场导览模式'
  },

  {
    // 数据库真实 ID：拈花湾禅意小镇
    parkId: 'AREA_0002',
    parkName: '拈花湾禅意小镇',

    // 这里先临时也写测试位置附近，方便你在当前城市测试是否能识别到景区
    // 后面正式上线前要改成拈花湾真实经纬度
    latitude: 43.993922,
    longitude: 125.390252,

    radius: 3000,
    intro: '用于测试 AI 数字人现场导览模式'
  }
]