<template>
  <el-container class="vector-status-page">
    <el-header class="page-header">
      <div class="left-actions">
        <el-button
          type="primary"
          size="large"
          @click="goBackToFileSystem"
          round
        >
          <el-icon><ArrowLeft /></el-icon>
          返回文件
        </el-button>
        <h1 class="page-title">向量状态</h1>
      </div>
      <div class="filter-bar">
        <el-select
          v-model="statusFilter"
          placeholder="按状态筛选"
          clearable
          size="default"
          class="status-select"
        >
          <el-option label="全部状态" value="" />
          <el-option label="待处理" value="PENDING" />
          <el-option label="处理中" value="PROCESSING" />
          <el-option label="成功" value="SUCCESS" />
          <el-option label="失败" value="FAILED" />
          <el-option label="不支持" value="UNSUPPORTED" />
        </el-select>
        <el-config-provider :locale="zhCn">
          <el-date-picker
            v-model="dateRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            format="YYYY-MM-DD HH:mm:ss"
            value-format="YYYY-MM-DDTHH:mm:ss"
            :default-time="defaultTime"
            class="date-range-picker"
          />
        </el-config-provider>
        <el-input
          v-model="searchKeyword"
          placeholder="按文件名搜索"
          clearable
          class="search-input"
          size="default"
        />
        <el-button
          type="primary"
          size="default"
          class="search-button"
          @click="handleSearch"
        >
          查询
        </el-button>
        <el-button size="default" class="reset-button" @click="handleReset">
          清空
        </el-button>
      </div>
    </el-header>

    <el-main>
      <el-table
        v-loading="loading"
        size="large"
        :data="tableData"
        style="width: 100%"
        row-style="height: 60px"
      >
        <el-table-column type="index" label="序号" width="70" align="center" />
        <el-table-column
          prop="name"
          label="文件名称"
          width="190"
          show-overflow-tooltip
          align="center"
        />
        <el-table-column
          prop="size"
          label="文件大小"
          width="120"
          align="center"
        >
          <template #default="scope">
            {{ formatFileSize(scope.row.size) }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="130" align="center">
          <template #default="scope">
            <el-tag :type="statusTagType(scope.row.status)" size="default">
              {{ getStatusText(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="retryCount"
          label="重试次数"
          width="90"
          align="center"
        />
        <el-table-column
          prop="created"
          label="创建时间"
          width="160"
          align="center"
        >
          <template #default="scope">
            {{ formatDate(scope.row.created) }}
          </template>
        </el-table-column>
        <el-table-column
          prop="updated"
          label="更新时间"
          width="160"
          align="center"
        >
          <template #default="scope">
            {{ formatDate(scope.row.updated) }}
          </template>
        </el-table-column>
        <el-table-column
          prop="errorMsg"
          label="错误信息"
          width="140"
          show-overflow-tooltip
          align="center"
        >
          <template #default="scope">
            <span v-if="scope.row.errorMsg" style="color: #f56c6c">
              {{ scope.row.errorMsg }}
            </span>
            <span v-else style="color: #909399">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center" fixed="right">
          <template #default="scope">
            <el-button
              v-if="scope.row.status?.toUpperCase() === 'FAILED'"
              type="primary"
              size="default"
              :loading="retryingId === scope.row.id"
              @click="handleRetry(scope.row)"
            >
              重试
            </el-button>
            <span v-else style="color: #909399">-</span>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pagination-wrapper"
        v-if="!loading && total > 0"
        size="large"
        background
        layout="total, prev, pager, next, sizes"
        :total="total"
        :page-size="pageSize"
        :page-sizes="VECTOR_STATUS_PAGE_SIZE_OPTIONS"
        :current-page="pageNum"
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { ArrowLeft } from "@element-plus/icons-vue";
import { vectorAPI } from "../../services/file.ts";
import { filesize } from "filesize";
import type { VectorStatusItem, VectorStatusResponse } from "./config.ts";
import {
  VECTOR_STATUS_PAGE_SIZE_DEFAULT,
  VECTOR_STATUS_PAGE_SIZE_OPTIONS,
  buildVectorStatusParams,
} from "./config.ts";
import { ElConfigProvider } from "element-plus";
import zhCn from "element-plus/es/locale/lang/zh-cn";

defineOptions({ name: "VectorStatus" });

const router = useRouter();
const loading = ref(false);
const tableData = ref<VectorStatusItem[]>([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(VECTOR_STATUS_PAGE_SIZE_DEFAULT);
const searchKeyword = ref("");
const statusFilter = ref("");
const dateRange = ref<[string, string] | null>(null);
// 实际已生效的查询参数（点击“查询”后同步）
const appliedSearchKeyword = ref("");
const appliedStatusFilter = ref("");
const appliedDateRange = ref<[string, string] | null>(null);
const retryingId = ref<string | null>(null);
// 设置选择日期后的默认时间点
const defaultTime = [
  new Date(2000, 1, 1, 0, 0, 0), // 起始时间默认 00:00:00
  new Date(2000, 1, 1, 23, 59, 59), // 结束时间默认 23:59:59
];

const handleRetry = async (row: VectorStatusItem) => {
  if (!row.id) return;
  retryingId.value = row.id;
  const currentParams = {
    pageNum: pageNum.value,
    pageSize: pageSize.value,
    keyword: appliedSearchKeyword.value,
    status: appliedStatusFilter.value,
    start: appliedDateRange.value?.[0],
    end: appliedDateRange.value?.[1],
  };
  try {
    const res = await vectorAPI.postRetryVectorTask(row.storageKey);
    if (res?.code === 200) {
      ElMessage.success("重试已提交");
      await loadList(currentParams);
    } else {
      ElMessage.error("重试失败，请稍后重试");
    }
  } catch (e) {
    console.error("重试失败", e);
    ElMessage.error("重试失败，请稍后重试");
  } finally {
    retryingId.value = null;
  }
};

const loadList = async (
  overrideParams?: {
    pageNum: number;
    pageSize: number;
    keyword: string;
    status?: string;
    start?: string;
    end?: string;
  },
  silent = false,
) => {
  if (!silent) loading.value = true;
  try {
    const [start, end] = Array.isArray(appliedDateRange.value)
      ? appliedDateRange.value
      : [undefined, undefined];
    const params = overrideParams
      ? buildVectorStatusParams(overrideParams)
      : buildVectorStatusParams({
          pageNum: pageNum.value,
          pageSize: pageSize.value,
          keyword: appliedSearchKeyword.value,
          status: appliedStatusFilter.value,
          start,
          end,
        });
    const res = await vectorAPI.getVectorDBList(params);
    if (res?.code === 200 && res?.data) {
      const data = res.data as VectorStatusResponse;
      tableData.value = Array.isArray(data.records) ? data.records : [];
      total.value = Number(data.total) || 0;
      // 同步后端返回的当前页码
      if (data.current) {
        pageNum.value = Number(data.current) || 1;
      }
      // 同步后端返回的每页大小
      if (data.size) {
        pageSize.value = Number(data.size) || VECTOR_STATUS_PAGE_SIZE_DEFAULT;
      }
    } else {
      tableData.value = [];
      total.value = 0;
    }
  } catch (e) {
    console.error("加载向量状态失败", e);
    if (!silent) ElMessage.error("加载向量状态失败，请稍后重试");
    tableData.value = [];
    total.value = 0;
  } finally {
    if (!silent) loading.value = false;
  }
};

const onPageChange = (p: number) => {
  pageNum.value = p;
  loadList();
};

const onSizeChange = (size: number) => {
  pageSize.value = size;
  pageNum.value = 1;
  loadList();
};

// 点击“查询”时应用筛选条件并请求后端
const handleSearch = () => {
  appliedSearchKeyword.value = searchKeyword.value;
  appliedStatusFilter.value = statusFilter.value;
  appliedDateRange.value = dateRange.value;
  pageNum.value = 1;
  loadList();
};

// 点击“清空”时重置所有查询条件并刷新列表
const handleReset = () => {
  searchKeyword.value = "";
  statusFilter.value = "";
  dateRange.value = null;
  appliedSearchKeyword.value = "";
  appliedStatusFilter.value = "";
  appliedDateRange.value = null;
  pageNum.value = 1;
  loadList();
};

const formatDate = (val: unknown) => {
  if (val == null || val === "") return "-";
  try {
    const date = new Date(val as string);
    if (isNaN(date.getTime())) return "-";
    return date.toLocaleString("zh-CN", {
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
      hour12: false,
    });
  } catch {
    return "-";
  }
};

const formatFileSize = (size: unknown) => {
  if (size == null || size === "") return "-";
  try {
    const sizeNum = Number(size);
    if (isNaN(sizeNum)) return "-";
    return filesize(sizeNum, { base: 2 }) as string;
  } catch {
    return "-";
  }
};

const statusTagType = (status: unknown) => {
  const s = String(status ?? "").toUpperCase();
  if (s === "SUCCESS") return "success";
  if (s === "FAILED" || s === "ERROR" || s === "UNSUPPORTED") return "danger";
  if (s === "PENDING" || s === "WAITING" || s === "PROCESSING")
    return "warning";
  return "info";
};

const getStatusText = (status: unknown) => {
  const s = String(status ?? "").toUpperCase();
  if (s === "PENDING") return "待处理";
  if (s === "PROCESSING") return "处理中";
  if (s === "SUCCESS") return "成功";
  if (s === "FAILED") return "失败";
  if (s === "UNSUPPORTED") return "不支持";
  return status ? String(status) : "-";
};

const goBackToFileSystem = () => {
  router.push({ name: "FileSystem" });
};

const POLL_INTERVAL = 5000;
let pollTimer: ReturnType<typeof setInterval> | null = null;

onMounted(() => {
  loadList();
  pollTimer = setInterval(() => {
    if (!loading.value && !retryingId.value) loadList(undefined, true);
  }, POLL_INTERVAL);
});

onBeforeUnmount(() => {
  if (pollTimer) clearInterval(pollTimer);
});
</script>

<style scoped lang="scss">
.vector-status-page {
  padding: 20px;
  max-width: 1300px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 0 0 16px 0;
  border-bottom: 1px solid var(--el-border-color-light);

  .page-title {
    margin: 0;
    font-size: 20px;
    font-weight: 600;
  }
}

.left-actions {
  display: flex;
  align-items: center;
  gap: 20px;
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
}

.status-select {
  width: 150px;
}

.date-range-picker {
  max-width: 360px;
}

.search-input {
  width: 160px;
}

.search-button,
.reset-button {
  margin-left: 4px;
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
</style>
