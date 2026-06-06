<template>
  <view v-if="show" class="trip-popup-mask">
    <view class="trip-popup-sheet" @click.stop>
      <view class="trip-popup-header">
        <view class="trip-popup-title">{{ title }}</view>
        <view class="trip-popup-subtitle">
          {{ subtitle }}
        </view>
      </view>

      <view class="trip-popup-section">
        <view class="trip-popup-label">同行人数</view>
        <view class="trip-popup-options">
          <view
            v-for="item in groupSizeOptions"
            :key="item"
            class="trip-popup-option"
            :class="{ active: form.groupSize === item }"
            @click="selectOption('groupSize', item)"
          >
            {{ item }}
          </view>
        </view>
      </view>

      <view class="trip-popup-section">
        <view class="trip-popup-label">出行类型</view>
        <view class="trip-popup-options">
          <view
            v-for="item in travelTypeOptions"
            :key="item"
            class="trip-popup-option"
            :class="{ active: form.travelType === item }"
            @click="selectOption('travelType', item)"
          >
            {{ item }}
          </view>
        </view>
      </view>

      <view class="trip-popup-section">
        <view class="trip-popup-label">本次偏好</view>
        <view class="trip-popup-options">
          <view
            v-for="item in visitPreferenceOptions"
            :key="item"
            class="trip-popup-option"
            :class="{ active: form.visitPreference === item }"
            @click="selectOption('visitPreference', item)"
          >
            {{ item }}
          </view>
        </view>
      </view>

      <view class="trip-popup-actions">
        <view class="trip-popup-btn ghost" @click="handleCancel">
          {{ cancelText }}
        </view>
        <view
          class="trip-popup-btn primary"
          :class="{ disabled: !canConfirm }"
          @click="handleConfirm"
        >
          {{ confirmText }}
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, reactive, watch } from 'vue'
import {
  TRIP_GROUP_SIZE_OPTIONS,
  TRIP_TRAVEL_TYPE_OPTIONS,
  TRIP_VISIT_PREFERENCE_OPTIONS,
  normalizeTripInfoSelection
} from '@/utils/visit'

const props = defineProps({
  show: {
    type: Boolean,
    default: false
  },
  title: {
    type: String,
    default: '本次出行信息'
  },
  subtitle: {
    type: String,
    default: '开启导览前，先告诉我这次想怎么逛'
  },
  cancelText: {
    type: String,
    default: '取消'
  },
  confirmText: {
    type: String,
    default: '确认并开启导览'
  }
})

const emit = defineEmits(['cancel', 'confirm'])

const groupSizeOptions = TRIP_GROUP_SIZE_OPTIONS
const travelTypeOptions = TRIP_TRAVEL_TYPE_OPTIONS
const visitPreferenceOptions = TRIP_VISIT_PREFERENCE_OPTIONS

const form = reactive({
  groupSize: '',
  travelType: '',
  visitPreference: ''
})

const canConfirm = computed(() => {
  return !!(form.groupSize && form.travelType && form.visitPreference)
})

watch(
  () => props.show,
  value => {
    if (value) {
      resetForm()
    }
  }
)

function resetForm() {
  form.groupSize = ''
  form.travelType = ''
  form.visitPreference = ''
}

function selectOption(field, value) {
  form[field] = value
}

function handleCancel() {
  emit('cancel')
}

function handleConfirm() {
  if (!canConfirm.value) {
    return
  }

  emit('confirm', normalizeTripInfoSelection(form))
}
</script>

<style scoped>
.trip-popup-mask {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.44);
  display: flex;
  align-items: flex-end;
  justify-content: center;
  z-index: 9999;
}

.trip-popup-sheet {
  width: 100%;
  background: #ffffff;
  border-radius: 28rpx 28rpx 0 0;
  padding: 28rpx 24rpx calc(28rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
}

.trip-popup-header {
  margin-bottom: 18rpx;
}

.trip-popup-title {
  font-size: 32rpx;
  font-weight: 700;
  color: #1f2937;
}

.trip-popup-subtitle {
  margin-top: 8rpx;
  font-size: 24rpx;
  line-height: 1.6;
  color: #6b7280;
}

.trip-popup-section + .trip-popup-section {
  margin-top: 20rpx;
}

.trip-popup-label {
  font-size: 26rpx;
  font-weight: 600;
  color: #374151;
}

.trip-popup-options {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-top: 14rpx;
}

.trip-popup-option {
  min-width: 140rpx;
  padding: 14rpx 20rpx;
  border-radius: 999rpx;
  background: #f3f4f6;
  color: #4b5563;
  font-size: 24rpx;
  text-align: center;
  box-sizing: border-box;
}

.trip-popup-option.active {
  background: #18b368;
  color: #ffffff;
}

.trip-popup-actions {
  display: flex;
  gap: 16rpx;
  margin-top: 30rpx;
}

.trip-popup-btn {
  flex: 1;
  height: 76rpx;
  line-height: 76rpx;
  border-radius: 999rpx;
  text-align: center;
  font-size: 25rpx;
  font-weight: 600;
}

.trip-popup-btn.ghost {
  background: #f3f4f6;
  color: #4b5563;
}

.trip-popup-btn.primary {
  background: #18b368;
  color: #ffffff;
}

.trip-popup-btn.primary.disabled {
  background: #cbd5e1;
  color: #ffffff;
}
</style>
