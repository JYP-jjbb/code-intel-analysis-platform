<template>
  <el-card>
    <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px;">
      <h3 style="margin: 0;">历史任务</h3>
      <el-button size="small" @click="loadTasks">刷新</el-button>
    </div>
    <el-table :data="tasks" stripe>
      <el-table-column prop="taskId" label="taskId" min-width="200" />
      <el-table-column prop="taskType" label="类型" width="140" />
      <el-table-column prop="status" label="状态" width="120">
        <template #default="scope">
          <el-tag :type="statusTagType(scope.row.status)">{{ scope.row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" min-width="180" />
      <el-table-column prop="updatedAt" label="更新时间" min-width="180" />
      <el-table-column prop="message" label="消息" min-width="200" />
      <el-table-column label="跳转" width="120">
        <template #default="scope">
          <el-button size="small" text @click="goToTask(scope.row)">查看</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { fetchTaskList } from "../api/taskApi.js";

const router = useRouter();
const tasks = ref([]);

const loadTasks = async () => {
  try {
    const response = await fetchTaskList(50);
    tasks.value = response.tasks || [];
  } catch (error) {
    ElMessage.error(error.message || "加载任务失败");
  }
};

const statusTagType = (status) => {
  if (status === "SUCCESS") return "success";
  if (status === "FAILED") return "danger";
  if (status === "RUNNING") return "warning";
  return "info";
};

const goToTask = (row) => {
  if (row.taskType === "NUTERA") {
    router.push("/workbench/nutera");
  } else if (row.taskType === "CODE_REVIEW") {
    router.push("/workbench/code-review");
  }
};

onMounted(() => {
  loadTasks();
});
</script>
