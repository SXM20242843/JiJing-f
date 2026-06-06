import { IMAGE_BASE } from './api'

export function resolveImageUrl(url) {
  const rawUrl = String(url || '').trim()

  if (!rawUrl) return ''

  if (rawUrl.startsWith('http://') || rawUrl.startsWith('https://')) {
    return rawUrl
  }

  if (rawUrl.startsWith('//')) {
    return `https:${rawUrl}`
  }

  if (rawUrl.startsWith('/static/')) {
    return rawUrl
  }

  // 后端返回 /uploads/xxx.png
  if (rawUrl.startsWith('/')) {
    return `${IMAGE_BASE}${rawUrl}`
  }

  // 兜底：后端返回 uploads/xxx.png
  return `${IMAGE_BASE}/${rawUrl}`
}