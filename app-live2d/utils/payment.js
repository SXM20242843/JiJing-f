import request from './request'

function unwrapData(response) {
  if (!response) return null

  if (Object.prototype.hasOwnProperty.call(response, 'data')) {
    return response.data
  }

  return response
}

function buildQuery(params = {}) {
  return Object.keys(params)
    .filter(key => params[key] !== undefined && params[key] !== null && params[key] !== '')
    .map(key => `${encodeURIComponent(key)}=${encodeURIComponent(params[key])}`)
    .join('&')
}

/**
 * 根据管理员端二维码里的 shop_code 查询商户/设施信息。
 * 后端接口如果还没做，页面会自动降级为手动填写商户信息。
 */
export async function getPaymentShopInfo(shopCode) {
  if (!shopCode) return null

  const response = await request({
    url: `/api/app/payment/shop/info?shopCode=${encodeURIComponent(shopCode)}`,
    method: 'GET',
    showErrorToast: false
  })

  return unwrapData(response)
}

/**
 * 模拟支付成功并入库 payment_record。
 */
export async function submitSimulatePayment(data = {}) {
  const response = await request({
    url: '/api/app/payment/simulate',
    method: 'POST',
    data,
    needAuth: true,
    loading: true,
    loadingText: '正在提交支付...',
    showErrorToast: true
  })

  return unwrapData(response)
}

/**
 * 查询当前用户消费记录。
 */
export async function getPaymentRecords(params = {}) {
  const query = buildQuery(params)

  const response = await request({
    url: query ? `/api/app/payment/records?${query}` : '/api/app/payment/records',
    method: 'GET',
    needAuth: true,
    showErrorToast: false
  })

  return unwrapData(response)
}