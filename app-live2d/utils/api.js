const H5_API_BASE = 'http://127.0.0.1:8080'

// APP接口后端地址：必须能访问 /api/app/parks、/api/app/parks/hot、Chat问答、行为上报等接口
const APP_PLUS_API_BASE = 'http://10.75.131.131:8080'

// Live2D H5资源地址
const APP_PLUS_WEB_BASE = 'http://10.75.131.131:5174'

// 图片静态资源后端地址：必须能访问 /uploads/xxx.png
const H5_IMAGE_BASE = 'http://10.75.131.51:8080'
const APP_PLUS_IMAGE_BASE = 'http://10.75.131.51:8080'

const APP_PLUS_LIVE2D_URL = `${APP_PLUS_WEB_BASE}/static/live2d/index.html`

// 原生数字人路线推荐 / 行为事件接口路径
const BEHAVIOR_EVENT_PATH = '/api/app/behavior/event'
const NATIVE_LIVE2D_SOURCE = 'native-live2d-guide'

let API_BASE = H5_API_BASE
let IMAGE_BASE = H5_IMAGE_BASE

// #ifdef APP-PLUS
API_BASE = APP_PLUS_API_BASE
IMAGE_BASE = APP_PLUS_IMAGE_BASE
// #endif

export {
  API_BASE,
  IMAGE_BASE,
  H5_API_BASE,
  APP_PLUS_API_BASE,
  H5_IMAGE_BASE,
  APP_PLUS_IMAGE_BASE,
  APP_PLUS_WEB_BASE,
  APP_PLUS_LIVE2D_URL,
  BEHAVIOR_EVENT_PATH,
  NATIVE_LIVE2D_SOURCE
}
