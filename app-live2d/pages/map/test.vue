<template>
  <view class="page">
    <map
      class="map"
      :latitude="latitude"
      :longitude="longitude"
      :scale="scale"
      :markers="markers"
      :show-location="true"
    ></map>

    <view class="bottom">
      <button class="btn" @click="getMyLocation">获取当前位置</button>
      <text class="tip">{{ tip }}</text>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'

const latitude = ref(30.259297)
const longitude = ref(120.130663)
const scale = ref(14)
const tip = ref('默认显示西湖附近')

const markers = ref([
  {
    id: 1,
    latitude: 30.259297,
    longitude: 120.130663,
    title: '西湖景区',
    width: 32,
    height: 32,
    callout: {
      content: '西湖景区',
      display: 'ALWAYS',
      padding: 8,
      borderRadius: 8
    }
  }
])

function getMyLocation() {
  uni.getLocation({
    type: 'gcj02',
    isHighAccuracy: true,
    success(res) {
      latitude.value = res.latitude
      longitude.value = res.longitude
      tip.value = `定位成功：${res.latitude}, ${res.longitude}`

      markers.value = [
        {
          id: 1,
          latitude: res.latitude,
          longitude: res.longitude,
          title: '我的位置',
          width: 32,
          height: 32,
          callout: {
            content: '我的位置',
            display: 'ALWAYS',
            padding: 8,
            borderRadius: 8
          }
        }
      ]
    },
    fail(err) {
      console.log('定位失败', err)
      tip.value = '定位失败，请检查定位权限'
      uni.showModal({
        title: '定位失败',
        content: '请确认手机定位已开启，并允许 APP 使用定位权限。',
        showCancel: false
      })
    }
  })
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #f5f7f4;
}

.map {
  width: 750rpx;
  height: 900rpx;
}

.bottom {
  padding: 28rpx;
}

.btn {
  height: 80rpx;
  line-height: 80rpx;
  border-radius: 999rpx;
  background: #16a34a;
  color: #ffffff;
  font-size: 28rpx;
}

.btn::after {
  border: none;
}

.tip {
  display: block;
  margin-top: 20rpx;
  font-size: 24rpx;
  color: #666;
  word-break: break-all;
}
</style>