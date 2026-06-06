// utils/mapMockData.js

export const demoParkMap = {
  id: 'park_xihu',
  name: '西湖风景名胜区',
  address: '浙江省杭州市西湖区',
  latitude: 30.259297,
  longitude: 120.130663,
  desc: '西湖风景名胜区是杭州代表性文旅场景，适合进行 AI 数字人讲解、路线推荐和现场导览演示。',
  scenics: [
    {
      id: 'scenic_duanqiao',
      name: '断桥残雪',
      latitude: 30.258725,
      longitude: 120.147415,
      intro: '西湖十景之一，也是游客进入西湖后常见的打卡点。',
      sort: 1
    },
    {
      id: 'scenic_baidi',
      name: '白堤',
      latitude: 30.258107,
      longitude: 120.142673,
      intro: '连接断桥与孤山的经典游览路线，适合步行游览。',
      sort: 2
    },
    {
      id: 'scenic_sudi',
      name: '苏堤春晓',
      latitude: 30.241798,
      longitude: 120.133841,
      intro: '西湖十景之一，是体现杭州历史文化与自然景观融合的重要景点。',
      sort: 3
    },
    {
      id: 'scenic_leifengta',
      name: '雷峰塔',
      latitude: 30.233867,
      longitude: 120.148533,
      intro: '西湖南岸标志性景点，适合数字人讲解白蛇传文化故事。',
      sort: 4
    },
    {
      id: 'scenic_santanyinyue',
      name: '三潭印月',
      latitude: 30.241794,
      longitude: 120.141063,
      intro: '西湖代表性景观之一，适合做文化讲解与拍照推荐。',
      sort: 5
    }
  ]
}