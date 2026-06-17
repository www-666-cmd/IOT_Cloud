<script setup lang="ts">
import { ref } from 'vue'
import { useAuthStore } from '../../stores/auth'
import { ElMessage } from 'element-plus'
import { User, Message, Key } from '@element-plus/icons-vue'

const authStore = useAuthStore()

const profileForm = ref({
  username: authStore.user?.username || '',
  email: authStore.user?.email || '',
  avatar: authStore.user?.avatar || ''
})

const passwordForm = ref({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const activeTab = ref('profile')

function updateProfile() {
  ElMessage.success('个人信息更新成功')
}

function changePassword() {
  if (passwordForm.value.newPassword !== passwordForm.value.confirmPassword) {
    ElMessage.error('两次输入的密码不一致')
    return
  }
  ElMessage.success('密码修改成功')
  passwordForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
}
</script>

<template>
  <div class="user-profile">
    <div class="page-header">
      <h2>个人中心</h2>
      <p>管理您的个人信息和账户安全</p>
    </div>

    <el-row :gutter="20">
      <el-col :span="8">
        <el-card>
          <div class="user-card">
            <el-avatar :size="80" :icon="User" />
            <h3>{{ authStore.user?.username }}</h3>
            <p class="user-role">{{ authStore.user?.role === 'admin' ? '管理员' : '普通用户' }}</p>
            <div class="user-stats">
              <div class="stat">
                <div class="stat-value">{{ authStore.user?.createdAt ? new Date(authStore.user.createdAt).toLocaleDateString() : '-' }}</div>
                <div class="stat-label">注册时间</div>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="16">
        <el-card>
          <el-tabs v-model="activeTab">
            <el-tab-pane label="基本信息" name="profile">
              <el-form :model="profileForm" label-width="100px" style="max-width: 500px;">
                <el-form-item label="用户名">
                  <el-input v-model="profileForm.username" disabled :prefix-icon="User" />
                </el-form-item>
                <el-form-item label="邮箱">
                  <el-input v-model="profileForm.email" :prefix-icon="Message" />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" @click="updateProfile">保存修改</el-button>
                </el-form-item>
              </el-form>
            </el-tab-pane>

            <el-tab-pane label="修改密码" name="password">
              <el-form :model="passwordForm" label-width="100px" style="max-width: 500px;">
                <el-form-item label="原密码">
                  <el-input v-model="passwordForm.oldPassword" type="password" show-password :prefix-icon="Key" />
                </el-form-item>
                <el-form-item label="新密码">
                  <el-input v-model="passwordForm.newPassword" type="password" show-password :prefix-icon="Key" />
                </el-form-item>
                <el-form-item label="确认密码">
                  <el-input v-model="passwordForm.confirmPassword" type="password" show-password :prefix-icon="Key" />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" @click="changePassword">修改密码</el-button>
                </el-form-item>
              </el-form>
            </el-tab-pane>
          </el-tabs>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.page-header {
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0 0 8px;
  font-size: 22px;
  color: var(--text-primary);
}

.page-header p {
  margin: 0;
  color: var(--text-muted);
  font-size: 14px;
}

.user-card {
  text-align: center;
  padding: 20px 0;
}

.user-card h3 {
  margin: 15px 0 5px;
  font-size: 20px;
  color: var(--text-primary);
}

.user-role {
  color: var(--text-muted);
  font-size: 14px;
  margin-bottom: 20px;
}

.user-stats {
  display: flex;
  justify-content: center;
  gap: 30px;
  padding-top: 20px;
  border-top: 1px solid var(--border-light);
}

.stat-value {
  font-size: 16px;
  font-weight: bold;
  color: var(--text-primary);
}

.stat-label {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 4px;
}
</style>
