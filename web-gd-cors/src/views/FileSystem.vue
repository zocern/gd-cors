<script setup lang="ts">
import { ElMessage } from "element-plus";
import {
  ArrowRight,
  Check,
  Close,
  FolderAdd,
  Operation,
} from "@element-plus/icons-vue";
import { computed, nextTick, ref, watch } from "vue";
import { fileAPI } from "../services/file.ts";
import { useRoute, useRouter } from "vue-router";
import { filesize } from "filesize";
import type {
  FileUploadInfo,
  FileShowType,
  FolderTempInfoType,
  FileRawInfoType,
  editingType,
  TrailItemType,
} from "../interface/TfileSystem.ts";
import type { AxiosProgressEvent } from "axios";
import type { VirtualElement } from "@popperjs/core";

const deleteVirtualRef = ref<VirtualElement | null>(null);

defineOptions({ name: "FileController" });

const route = useRoute();
const router = useRouter();

const breadcrumbTrail = ref<TrailItemType[]>([{ id: null, name: "全部文件" }]);
// 处于重命名状态的文件 ID
const renamingId = ref<string | null>(null);

// 面包屑名称缓存：ID -> Name
const folderCache = new Map();

// 格式化日期
const formatDate = (dateString: Date) => {
  if (!dateString) return "-";
  const date = new Date(dateString);
  return date.toLocaleString("zh-CN");
};

// 当前路径的 ID 数组（从根到当前文件夹）
const currentPathIds = computed(() => {
  const idStr = route.query.id;
  if (typeof idStr === "string" && idStr) {
    return idStr
      .split(",")
      .map((id) => id.trim())
      .filter(Boolean);
  }
  return [];
});

// 当前文件夹的 ID
const currentFolderId = computed(() => {
  return currentPathIds.value.length > 0
    ? currentPathIds.value[currentPathIds.value.length - 1]
    : null;
});

// 重新加载目录，取消新建文件夹
const reloadContent = async () => {
  try {
    const res = await fileAPI.getFolderList(currentFolderId.value);
    if (res.code === 200) {
      fileList.value = res.data
        .map((item: FileRawInfoType) => ({
          ...item,
          editing: 0,
        }))
        .sort((a: FileRawInfoType, b: FileRawInfoType) => {
          if (a.folder && !b.folder) return -1;
          if (!a.folder && b.folder) return 1;
          return a.name.localeCompare(b.name, "zh-CN");
        });
      existingNew.value = false;
    } else {
      ElMessage.error("加载失败：" + res.msg);
    }
  } catch (error) {
    console.error("加载失败:", error);
    ElMessage.warning("加载失败！请联系管理员");
  }
};

// 加载面包屑和文件列表
const loadContent = async () => {
  try {
    await reloadContent();

    const trail: TrailItemType[] = [{ id: null, name: "全部文件" }];

    // 1. 过滤出缓存中不存在的 ID
    const missingIds = currentPathIds.value.filter(
      (id) => !folderCache.has(id),
    );

    // 2. 仅请求缺失的 ID
    if (missingIds.length > 0) {
      const promises = missingIds.map((id) => fileAPI.getRawInformation(id));
      const responses = await Promise.all(promises);

      for (let i = 0; i < responses.length; i++) {
        const res = responses[i];
        const id = missingIds[i];
        if (res.code === 200) {
          // 写入缓存
          folderCache.set(id, res.data.name);
        } else {
          ElMessage.error("加载失败：" + res.msg);
          await router.replace({ path: "/file" });
          return;
        }
      }
    }

    // 3. 从缓存构建面包屑
    for (const id of currentPathIds.value) {
      if (folderCache.has(id)) {
        trail.push({ id: id, name: folderCache.get(id) });
      }
    }

    breadcrumbTrail.value = trail;
  } catch (error) {
    console.error("加载失败:", error);
    ElMessage.warning("加载失败！请联系管理员");
  }
};

// 监听路由变化（包括前进/后退/初始加载）
watch(
  () => route.query.id,
  () => loadContent(),
  { immediate: true },
);

// 点击文件夹时
const pushId = async (id: string) => {
  const newPath = [...currentPathIds.value, id];
  await router.push({ path: "/file", query: { id: newPath.join(",") } });
};

// 点击面包屑时
const navigateToTrail = async (index: number) => {
  // 取前 index 个 ID（因为 breadcrumbTrail[0] 是根，对应空路径）
  const targetPath = currentPathIds.value.slice(0, index);
  const idStr = targetPath.length ? targetPath.join(",") : undefined;
  await router.push({
    path: "/file",
    query: idStr ? { id: idStr } : {},
  });
};

// 当前文件列表
const fileList = ref<(FileRawInfoType | FolderTempInfoType)[]>([]);
// 是否有文件处于编辑状态
const isAnyEditing = computed(
  () =>
    Array.isArray(fileList.value) &&
    fileList.value.some((f) => f.editing !== 0),
);
// 是否已有新建文件夹未保存
const existingNew = ref<boolean>(false);
// 点击新建文件夹按钮
const clickCreateFolder = async (): Promise<void> => {
  if (existingNew.value) {
    ElMessage.warning("请先保存或取消新建文件夹");
    return;
  }
  const parentId = currentFolderId.value;

  const existingNames = new Set(
    fileList.value
      .filter((item) => item.folder === true)
      .map((item) => item.name),
  );

  let newName = "新建文件夹";
  for (let counter = 1; existingNames.has(newName); counter++) {
    newName = `新建文件夹(${counter + 1})`;
  }

  // 生成唯一的临时 ID 用于聚焦
  const tempId = `temp-${Date.now()}`;
  const tempInfo: FolderTempInfoType = {
    id: tempId,
    name: newName,
    parentId: parentId,
    folder: true,
    editing: 1,
  };

  fileList.value = [tempInfo, ...fileList.value];

  // 等待 DOM 更新后聚焦输入框
  await nextTick();
  // 查找第一个新建文件夹输入框（新建的文件夹总是放在列表第一位）
  // 更精确地查找：第一个 .file-item 下的 .createFolder-input
  const firstFileItem = document.querySelector(".file-item");
  if (firstFileItem) {
    const inputWrapper = firstFileItem.querySelector(".createFolder-input");
    if (inputWrapper) {
      // Element Plus 的 el-input 内部会有一个 input 元素
      const inputEl = inputWrapper.querySelector("input");
      if (inputEl) {
        inputEl.focus();
      }
    }
  }
  existingNew.value = true;
};

// 是否正在重命名文件
const isRenaming = ref<boolean>(false);

// 点击重命名文件按钮
const clickRenameButton = async (file: FileRawInfoType) => {
  renamingId.value = file.id;
  // 清空其他项的编辑态
  if (Array.isArray(fileList.value)) {
    fileList.value.forEach((f) => {
      f.editing = 0;
    });
  }

  file.editing = 2;
  await nextTick();
  const inputEl: HTMLInputElement | null = document.querySelector(
    ".file-item .name-input input",
  );
  if (inputEl) {
    inputEl.focus();
  }
};

// 是否显示文件信息对话框
const infoDialogVisible = ref<boolean>(false);
// 当前查看的文件信息
const fileInfo = ref<FileShowType | null>(null);
// 点击查看文件信息按钮
const clickInfoButton = async (id: string) => {
  try {
    const res = await fileAPI.getInformation(id);
    if (res.code === 200) {
      fileInfo.value = res.data;
      infoDialogVisible.value = true;
    } else {
      ElMessage.error("获取信息失败：" + res.msg);
    }
  } catch (error) {
    console.error("获取信息失败:", error);
    ElMessage.warning("获取信息失败！请联系管理员");
  }
};

/*上传/更新文件信息相关
 * */
// 上传文件信息表单数据
const uploadInfoForm = ref<FileUploadInfo | null>(null);
// 是否显示上传文件信息对话框
const uploadInfoDialogVisible = ref<boolean>(false);
// 当前上传文件的 ID
const currentFileId = ref<string>("");
// 当前操作类型：'upload' 或 'update'
const currentOperation = ref<"upload" | "update">("upload");
// 是否正在加载文件信息
const isLoadingFileInfo = ref<boolean>(false);
// 打开上传文件信息弹窗
const openUploadInfoDialog = async (
  id: string,
  operation: "upload" | "update" = "upload",
): Promise<void> => {
  currentFileId.value = id;
  currentOperation.value = operation;

  if (operation === "update") {
    // 更新操作：先加载现有信息
    isLoadingFileInfo.value = true;

    try {
      const res = await fileAPI.getInformation(id);
      if (res.code === 200) {
        // 填充现有信息到表单
        uploadInfoForm.value = {
          projectName: res.data.projectName || "",
          projectStartDate: res.data.projectStartDate || null,
          projectDuration: res.data.projectDuration || 0,
          projectManager: res.data.projectManager || "",
          projectManagerSecond: res.data.projectManagerSecond || "",
          projectLocation: res.data.projectLocation || "",
          projectPartner: res.data.projectPartner || "",
        };
      } else {
        ElMessage.error("获取信息失败：" + res.msg);
        uploadInfoDialogVisible.value = false;
      }
      uploadInfoDialogVisible.value = true;
    } catch (error) {
      console.error("获取信息失败:", error);
      ElMessage.warning("获取信息失败！请联系管理员");
      uploadInfoDialogVisible.value = false;
    } finally {
      isLoadingFileInfo.value = false;
    }
  } else {
    // 上传操作：重置表单
    uploadInfoForm.value = {
      projectName: "",
      projectStartDate: null,
      projectDuration: 0,
      projectManager: "",
      projectManagerSecond: "",
      projectLocation: "",
      projectPartner: "",
    };
    uploadInfoDialogVisible.value = true;
  }
};
// 提交上传/更新文件信息
const submitUploadInfo = async (): Promise<void> => {
  if (!uploadInfoForm.value!.projectName) {
    ElMessage.warning("请输入项目名称");
    return;
  }

  try {
    let res;
    if (currentOperation.value === "update") {
      // 调用更新接口
      res = await fileAPI.putUpdateFileInfo(
        currentFileId.value,
        uploadInfoForm.value!,
      );
    } else {
      // 调用上传接口
      res = await fileAPI.postUploadFileInfo(
        currentFileId.value,
        uploadInfoForm.value!,
      );
    }

    if (res.code === 200) {
      ElMessage.success(
        currentOperation.value === "update"
          ? "文件信息更新成功！"
          : "文件信息上传成功！",
      );
      uploadInfoDialogVisible.value = false;
      await reloadContent();
    } else {
      ElMessage.error(
        (currentOperation.value === "update" ? "更新" : "上传") +
          "失败：" +
          (res.msg || "未知错误"),
      );
    }
  } catch (error) {
    console.error(
      (currentOperation.value === "update" ? "更新" : "上传") + "文件信息失败:",
      error,
    );
    ElMessage.warning(
      (currentOperation.value === "update" ? "更新" : "上传") +
        "失败！请联系管理员",
    );
  }
};

/*创建/重命名文件夹相关
 * */
// 创建文件夹
const createFolder = async (
  parentId: string | null,
  newName: string,
): Promise<void> => {
  try {
    const res = await fileAPI.postCreateFolder(parentId, newName);
    if (res.code === 200) {
      await reloadContent();
      ElMessage.success("创建成功！");
    } else {
      ElMessage.error("创建失败：" + res.msg);
    }
  } catch (error) {
    console.error("创建文件夹失败:", error);
    ElMessage.warning("创建文件夹失败！请联系管理员");
  } finally {
    isRenaming.value = false;
  }
};
// 重命名文件夹
const renameFF = async (myId: string, newName: string): Promise<void> => {
  try {
    const res = await fileAPI.putRenameFile(myId, newName);
    if (res.code === 200) {
      // 更新缓存中的名称
      folderCache.set(myId, newName);
      await reloadContent();
      ElMessage.success("重命名成功！");
    } else {
      ElMessage.error("重命名失败：" + res.msg);
    }
  } finally {
    isRenaming.value = false;
    renamingId.value = "";
  }
};
// 判断打钩执行的是新建还是重命名
const checkOrRename = async (
  editing: editingType,
  fatherId: string | null,
  myId: string,
  newName: string,
): Promise<void> => {
  isRenaming.value = true;
  if (editing === 1) await createFolder(fatherId, newName);
  else if (editing === 2) await renameFF(myId, newName);
};
// 判断确认按钮是否应该显示 loading 状态
const isConfirmButtonLoading = (
  file: FileRawInfoType | FolderTempInfoType,
): boolean => {
  // 如果不在执行操作，不显示 loading
  if (!isRenaming.value) return false;

  // 如果是新建文件夹（editing === 1），显示 loading
  if (file.editing === 1) return true;

  // 如果是重命名（editing === 2），只有当前文件显示 loading
  if (file.editing === 2) {
    return renamingId.value === file.id;
  }

  return false;
};
// 判断确认按钮是否应该被禁用
const isConfirmButtonDisabled = (
  file: FileRawInfoType | FolderTempInfoType,
): boolean => {
  // 如果正在执行操作，禁用所有按钮（防止重复点击）
  if (isRenaming.value) return true;

  // 如果是重命名状态（editing === 2）
  if (file.editing === 2) {
    // 如果 renamingId 有值但不等于当前文件 ID，说明有其他文件正在重命名，禁用当前按钮
    if (renamingId.value && renamingId.value !== file.id) {
      return true;
    }
  }

  // 其他情况不禁用
  return false;
};
// 取消编辑操作（取消新建文件夹或重命名）
const cancelEdit = async () => {
  // 清空所有状态
  renamingId.value = "";
  isRenaming.value = false;
  // 重新加载内容，这会重置所有文件的 editing 状态
  await reloadContent();
};

/*删除文件相关
 * */
// 是否显示删除确认弹窗
const deleteConfirmVisible = ref(false);
// 删除的目标文件 ID
const deleteTargetId = ref<string | null>(null);
// 删除文件
const deleteFile = async (id: string) => {
  try {
    const res = await fileAPI.deleteDeleteFile(id);
    if (res.code === 200) {
      await reloadContent();
      ElMessage.success("删除成功！");
    } else {
      ElMessage.error("删除失败：" + res.msg);
    }
  } catch (error) {
    console.error("删除失败:", error);
    ElMessage.warning("删除失败！请联系管理员");
  }
};

/*移动文件相关
 */
// 要移动的文件对象
const moveSourceFile = ref<FileRawInfoType | null>(null);
// 目标文件夹 ID
const moveTargetFolderId = ref<string>("");
// 是否显示移动文件对话框
const moveDialogVisible = ref(false);
// 打开移动文件对话框
const openMoveDialog = (file: FileRawInfoType): void => {
  if (!file || file.folder) return;
  moveSourceFile.value = file;
  moveTargetFolderId.value = "";
  moveDialogVisible.value = true;
};
// 选择移动目标文件夹
const onMoveFolderNodeClick = (data: FileRawInfoType) => {
  if (data && data.id) moveTargetFolderId.value = data.id;
};
// 加载文件夹树
const loadFolderTree = async (node: any, resolve: any): Promise<void> => {
  try {
    const res = await fileAPI.getFolderList(null);
    if (res.code === 200) {
      const folders = Array.isArray(res.data)
        ? res.data.filter((item) => item.folder === true)
        : [];
      const children = folders.map((item) => ({
        id: item.id,
        label: item.name,
        leaf: false,
      }));
      resolve(children);
    } else {
      ElMessage.error("加载文件夹失败：" + (res.msg || "未知错误"));
      resolve([]);
    }
  } catch (error) {
    console.error("加载文件夹失败:", error);
    ElMessage.warning("加载文件夹失败！请联系管理员");
    resolve([]);
  }
};
// 确认移动文件
const confirmMove = async () => {
  if (!moveSourceFile.value || moveTargetFolderId.value == null) {
    ElMessage.warning("请选择要移动到的文件夹");
    return;
  }

  try {
    const res = await fileAPI.putMoveFile(
      moveSourceFile.value.id,
      moveTargetFolderId.value,
    );
    if (res.code === 200) {
      ElMessage.success("移动成功！");
      moveDialogVisible.value = false;
      moveSourceFile.value = null;
      moveTargetFolderId.value = "";
      await reloadContent();
    } else {
      ElMessage.error("移动失败：" + (res.msg || "未知错误"));
    }
  } catch (error) {
    console.error("移动文件失败:", error);
    ElMessage.warning("移动失败！请联系管理员");
  }
};

/*拖拽移动文件相关
 */
// 当前正在被拖拽的文件 ID
const draggingFileId = ref<string>("");
// 当前拖拽悬停的文件夹 ID
const hoverFolderId = ref<string>("");
// 拖拽开始：记录被拖动的文件 ID
const onFileDragStart = (file: FileRawInfoType) => {
  if (file && !file.folder) {
    draggingFileId.value = file.id;
  } else {
    draggingFileId.value = "";
  }
};
// 拖拽经过文件夹（主要用于配合 .prevent）
const onFolderDragOver = () => {};
// 拖拽进入文件夹：设置高亮
const onFolderDragEnter = (targetFolder: FileRawInfoType) => {
  if (targetFolder && targetFolder.folder) {
    hoverFolderId.value = targetFolder.id;
  }
};
// 拖拽离开文件夹：取消高亮
const onFolderDragLeave = (targetFolder: FileRawInfoType) => {
  if (hoverFolderId.value === targetFolder.id) {
    hoverFolderId.value = "";
  }
};
// 在文件夹上放下文件：调用移动接口
const onFolderDrop = async (targetFolder: FileRawInfoType) => {
  if (!draggingFileId.value) return;
  if (!targetFolder || !targetFolder.folder) return;

  const fileId = draggingFileId.value;
  draggingFileId.value = "";
  if (hoverFolderId.value === targetFolder.id) {
    hoverFolderId.value = "";
  }

  try {
    const res = await fileAPI.putMoveFile(fileId, targetFolder.id);
    if (res.code === 200) {
      await reloadContent();
      ElMessage.success("移动成功！");
    } else {
      ElMessage.error("移动失败：" + (res.msg || "未知错误"));
    }
  } catch (error) {
    console.error("移动文件失败:", error);
    ElMessage.warning("移动失败！请联系管理员");
  }
};

/*下载文件相关
 * */
// 是否正在下载文件
const isDownloading = ref<boolean>(false);
// 下载进度百分比
const downloadPercent = ref<number>(0);
// 正在下载的文件名
const downloadingFileName = ref<string>("");
// 下载文件
const downloadFile = async (id: string, name: string) => {
  isDownloading.value = true;
  downloadPercent.value = 0;
  downloadingFileName.value = name || "";
  ElMessage.success("开始下载！");
  const res = await fileAPI.getDownloadFile(id, (event: AxiosProgressEvent) => {
    if (!event.total) return;
    downloadPercent.value = Number(
      ((event.loaded / event.total) * 100).toFixed(2),
    );
  });
  const contentDisposition = res.headers.get("Content-Disposition");
  const fileUrl = URL.createObjectURL(res.data);

  // 提取文件名，兼容 filename= 和 filename*=，并处理 + 号为空格的问题
  let filename = "downloaded-file";
  if (contentDisposition) {
    console.log("Content-Disposition:", contentDisposition);
    // 匹配 filename= 或 filename*= 的值，自动去除引号
    const match = contentDisposition.match(
      /filename\*?=['"]?(?:UTF-8'')?([^;"']+)['"]?/i,
    );
    if (match && match[1]) {
      // 先将 URL 编码中的 + 替换为 %20，再进行解码
      filename = decodeURIComponent(match[1].replace(/\+/g, "%20"));
    }
  }

  const link = document.createElement("a");
  link.href = fileUrl;
  link.download = filename;
  link.click();

  URL.revokeObjectURL(fileUrl);
  isDownloading.value = false;
};

// 计算文件大小
const calculateFileSize = (size: string): string => {
  return filesize(size, { standard: "jedec" });
};

// 前往上传界面
const gotoUpload = () => {
  router.push({
    name: "Upload",
    query: {
      folderId: route.query.id,
    },
  });
};

/*更新文件相关
 * */
const chooseId = ref<string | null>(null);
const fileInput = ref<HTMLInputElement | null>(null);
// 是否正在上传文件
const isUploading = ref<boolean>(false);
// 上传进度百分比
const uploadPercent = ref<number>(0);
// 正在上传的文件名
const uploadingFileName = ref<string>("");
// 打开文件选择器
const openFileChooser = (id: string) => {
  chooseId.value = id;
  fileInput.value?.click();
};
// 处理选中的文件
const handleFileChange = async (e: Event) => {
  const files = (e.target as HTMLInputElement).files;
  if (!files || files.length === 0) return;

  const selectedFile = files[0];

  // 设置上传状态
  isUploading.value = true;
  uploadPercent.value = 0;
  uploadingFileName.value = selectedFile.name;

  const formData = new FormData();
  formData.append("file", selectedFile);

  try {
    const res = await fileAPI.postUpdateFile(
      chooseId.value!,
      formData,
      (progressEvent: AxiosProgressEvent) => {
        if (progressEvent.total) {
          uploadPercent.value = Number(
            ((progressEvent.loaded / progressEvent.total) * 100).toFixed(2),
          );
        }
      },
    );

    if (res.code === 200) await reloadContent();
    else ElMessage.error("文件更新失败！");
  } catch (error) {
    console.error("文件更新失败:", error);
    ElMessage.error("文件更新失败！请联系管理员");
  } finally {
    // 清空选择的文件，允许下次选择同一个文件
    if (fileInput.value) fileInput.value.value = "";
    chooseId.value = null;
    setTimeout(() => {
      isUploading.value = false;
    }, 1500);
    isUploading.value = false;
    uploadPercent.value = 0;
    uploadingFileName.value = "";
  }
};
</script>

<template>
  <el-container>
    <input
      ref="fileInput"
      type="file"
      style="display: none"
      @change="handleFileChange"
      :multiple="false"
    />
    <el-main style="margin: 0 400px">
      <!-- 上传进度条 -->
      <div v-if="isUploading" class="upload-progress-fixed">
        <div style="margin-bottom: 10px">
          正在上传文件：{{ uploadingFileName || "未知文件" }}
        </div>
        <el-progress
          :percentage="uploadPercent"
          :stroke-width="12"
          style="margin-bottom: 5px"
        />
      </div>

      <!-- 下载进度条 -->
      <div v-if="isDownloading" class="download-progress-fixed">
        <div style="margin-bottom: 10px">
          正在下载文件：{{ downloadingFileName || "未知文件" }}
        </div>
        <el-progress
          :percentage="downloadPercent"
          :stroke-width="12"
          style="margin-bottom: 5px"
        />
      </div>

      <div class="header-section">
        <!-- 面包屑 -->
        <el-breadcrumb style="margin: 0" :separator-icon="ArrowRight">
          <el-breadcrumb-item
            v-for="(item, index) in breadcrumbTrail"
            :key="`${item.id}-${index}`"
            :to="undefined"
            @click="navigateToTrail(index)"
          >
            {{ item.name }}
          </el-breadcrumb-item>
        </el-breadcrumb>

        <!-- 文件上传按钮（会跳转到上传界面） -->
        <el-button
          type="default"
          size="large"
          class="upload-button"
          @click="gotoUpload()"
          round
        >
          <el-icon><Upload /></el-icon>
          上传文件
        </el-button>

        <!-- 新建文件夹按钮 -->
        <el-button
          type="primary"
          size="large"
          class="createFolder-button"
          @click="clickCreateFolder()"
        >
          <el-icon><FolderAdd /></el-icon>
          <span style="font-size: 12px">新建文件夹</span>
        </el-button>
      </div>

      <div
        v-for="file in fileList"
        :key="file.id"
        class="file-item"
        :class="{ 'folder-drop-hover': hoverFolderId === file.id }"
      >
        <!-- 编辑状态 -->
        <div v-if="file.editing !== 0" class="file-item-editing">
          <el-input class="name-input" v-model="file.name" clearable />
          <el-button
            type="primary"
            @click="
              checkOrRename(file.editing!, currentFolderId, file.id, file.name)
            "
            size="small"
            :loading="isConfirmButtonLoading(file)"
            :disabled="isConfirmButtonDisabled(file)"
          >
            <el-icon><Check /></el-icon>
          </el-button>
          <el-button @click="cancelEdit()" size="small">
            <el-icon><Close /></el-icon>
          </el-button>
        </div>

        <!-- 正常状态 -->
        <div v-else class="file-item-normal">
          <!-- 文件夹 -->
          <div
            v-if="file.folder"
            class="folder-link file-name"
            roleType="button"
            tabindex="0"
            @click="pushId(file.id)"
            @keydown.enter="pushId(file.id)"
            @keydown.space.prevent="pushId(file.id)"
            @dragover.prevent="onFolderDragOver"
            @dragenter.prevent="onFolderDragEnter(file)"
            @dragleave.prevent="onFolderDragLeave(file)"
            @drop="onFolderDrop(file)"
          >
            {{ file.name }}
          </div>

          <!-- 文件 -->
          <div
            v-else
            class="file-name"
            draggable="true"
            @dragstart="onFileDragStart(file)"
          >
            <span class="file-name-text">{{ file.name }}</span>
            <span class="file-size-text">{{
              calculateFileSize(file.size)
            }}</span>
          </div>
          <el-dropdown trigger="click" size="large" :hide-on-click="false">
            <el-button
              size="default"
              :disabled="isAnyEditing && file.editing === 0"
            >
              菜单
              <el-icon class="el-icon--right" size="large"
                ><Operation
              /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <!-- 文件已上传信息时显示：更新和查看 -->
                <template v-if="!file.folder && file.association">
                  <el-dropdown-item
                    @click="openUploadInfoDialog(file.id, 'update')"
                    >更新文件信息</el-dropdown-item
                  >
                  <el-dropdown-item @click="clickInfoButton(file.id)"
                    >查看文件信息</el-dropdown-item
                  >
                </template>
                <!-- 文件未上传信息时显示：上传 -->
                <el-dropdown-item
                  v-if="!file.folder && !file.association"
                  @click="openUploadInfoDialog(file.id, 'upload')"
                  >上传文件信息</el-dropdown-item
                >

                <el-dropdown-item
                  @click="openFileChooser(file.id)"
                  v-if="!file.folder"
                  >更新文件</el-dropdown-item
                >
                <el-dropdown-item
                  @click="downloadFile(file.id, file.name)"
                  v-if="!file.folder"
                  >下载</el-dropdown-item
                >
                <el-dropdown-item
                  @click="openMoveDialog(file)"
                  v-if="!file.folder"
                  >移动</el-dropdown-item
                >
                <el-dropdown-item @click="clickRenameButton(file)"
                  >重命名</el-dropdown-item
                >
                <el-dropdown-item
                  @click="
                    (e: MouseEvent) => {
                      deleteTargetId = file.id;
                      const { clientX, clientY } = e;

                      deleteVirtualRef = {
                        getBoundingClientRect: () =>
                          ({
                            width: 0,
                            height: 0,
                            top: clientY,
                            bottom: clientY,
                            left: clientX,
                            right: clientX,
                          }) as DOMRect,
                      };

                      deleteConfirmVisible = true;
                    }
                  "
                >
                  删除
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>

      <!-- 上传/更新文件信息弹窗 -->
      <el-dialog
        v-model="uploadInfoDialogVisible"
        :title="currentOperation === 'update' ? '更新文件信息' : '上传文件信息'"
        width="600px"
      >
        <el-form
          :model="uploadInfoForm"
          label-width="120px"
          v-loading="isLoadingFileInfo"
          element-loading-text="正在加载文件信息..."
        >
          <el-form-item required label="项目名称">
            <el-input v-model="uploadInfoForm!.projectName" />
          </el-form-item>
          <el-form-item label="项目创建日期">
            <el-date-picker
              v-model="uploadInfoForm!.projectStartDate"
              type="date"
              placeholder="选择日期"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
            />
          </el-form-item>
          <el-form-item label="项目工期(天)">
            <el-input-number
              v-model="uploadInfoForm!.projectDuration"
              :min="0"
            />
          </el-form-item>
          <el-form-item label="项目负责人">
            <el-input v-model="uploadInfoForm!.projectManager" />
          </el-form-item>
          <el-form-item label="项目第二负责人">
            <el-input v-model="uploadInfoForm!.projectManagerSecond" />
          </el-form-item>
          <el-form-item label="项目实施位置">
            <el-input v-model="uploadInfoForm!.projectLocation" />
          </el-form-item>
          <el-form-item label="项目乙方单位">
            <el-input v-model="uploadInfoForm!.projectPartner" />
          </el-form-item>
        </el-form>
        <template #footer>
          <span class="dialog-footer">
            <el-button @click="uploadInfoDialogVisible = false">取消</el-button>
            <el-button
              type="primary"
              @click="submitUploadInfo"
              :disabled="isLoadingFileInfo"
            >
              {{ currentOperation === "update" ? "确定更新" : "确定上传" }}
            </el-button>
          </span>
        </template>
      </el-dialog>

      <!-- 文件信息弹窗 -->
      <el-dialog v-model="infoDialogVisible" title="文件详情" width="500px">
        <el-form :model="fileInfo" label-width="120px">
          <el-form-item label="所属项目名称">
            <span>{{ fileInfo!.projectName || "-" }}</span>
          </el-form-item>
          <el-form-item label="项目创建日期">
            <span>{{ fileInfo!.projectStartDate || "-" }}</span>
          </el-form-item>
          <el-form-item label="项目工期">
            <span>{{
              fileInfo!.projectDuration
                ? fileInfo!.projectDuration + " 天"
                : "-"
            }}</span>
          </el-form-item>
          <el-form-item label="项目负责人">
            <span>{{ fileInfo!.projectManager || "-" }}</span>
          </el-form-item>
          <el-form-item label="项目第二负责人">
            <span>{{ fileInfo!.projectManagerSecond || "-" }}</span>
          </el-form-item>
          <el-form-item label="项目实施位置">
            <span>{{ fileInfo!.projectLocation || "-" }}</span>
          </el-form-item>
          <el-form-item label="项目乙方单位">
            <span>{{ fileInfo!.projectPartner || "-" }}</span>
          </el-form-item>
          <el-form-item label="创建时间">
            <span>{{ formatDate(fileInfo!.created) }}</span>
          </el-form-item>
          <el-form-item label="更新时间">
            <span>{{ formatDate(fileInfo!.updated) }}</span>
          </el-form-item>
        </el-form>
      </el-dialog>

      <!-- 移动文件弹窗 -->
      <el-dialog v-model="moveDialogVisible" title="移动文件" width="400px">
        <div style="margin-bottom: 12px">选择要移动到的文件夹：</div>
        <el-tree
          node-key="id"
          lazy
          :load="loadFolderTree"
          :expand-on-click-node="false"
          @node-click="onMoveFolderNodeClick"
        />
        <template #footer>
          <span class="dialog-footer">
            <el-button @click="moveDialogVisible = false">取消</el-button>
            <el-button type="primary" @click="confirmMove">确定</el-button>
          </span>
        </template>
      </el-dialog>
    </el-main>
    <el-popconfirm
      v-model:visible="deleteConfirmVisible"
      :virtual-ref="deleteVirtualRef"
      virtual-triggering
      title="确定要删除吗？"
      confirm-button-type="danger"
      @confirm="deleteFile(deleteTargetId!)"
      @cancel="deleteConfirmVisible = false"
      :append-to-body="true"
      confirm-button-text="确认"
      cancel-button-text="取消"
    />
  </el-container>
</template>

<style scoped lang="scss">
.file-item {
  font-size: 16px;
  padding: 0 16px;
  margin-bottom: 0;
  border-radius: 0;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color);
  border-bottom: none;
  box-shadow: none;
  transition:
    background-color 0.2s ease,
    border-color 0.2s ease;
  cursor: pointer;
  display: flex;
  align-items: center;
  min-height: 48px;
}

.file-item:first-child {
  border-top-left-radius: 8px;
  border-top-right-radius: 8px;
}

.file-item:last-child {
  border-bottom: 1px solid var(--el-border-color);
  border-bottom-left-radius: 8px;
  border-bottom-right-radius: 8px;
}

.file-item:hover {
  background: color-mix(in srgb, var(--el-color-primary) 6%, transparent);
  border-color: color-mix(
    in srgb,
    var(--el-color-primary) 25%,
    var(--el-border-color)
  );
}

.file-item:active {
  background: color-mix(in srgb, var(--el-color-primary) 8%, transparent);
}

.el-breadcrumb {
  padding: 16px 24px;
  margin-bottom: 20px;

  .el-breadcrumb__item {
    .el-breadcrumb__inner {
      font-size: 14px;
      transition: color 0.2s ease;

      &:not(.is-disabled) {
        cursor: pointer;

        &:hover {
          color: var(--el-color-primary);
        }
      }

      &.is-disabled {
        cursor: default;
        font-weight: normal;
      }
    }

    .el-breadcrumb__separator {
      margin: 0 8px;
      color: var(--el-text-color-secondary);
    }
  }
}

.header-section {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  padding: 10px 16px;
  min-height: 50px;
}

.upload-button {
  margin-left: auto;
}

.createFolder-button {
  margin-right: 16px;
}

.name-input {
  font-size: 16px;
  width: 200px;
  min-height: 28px;
  margin-right: 16px;
  :deep(input::placeholder) {
    color: var(--el-text-color-placeholder);
  }
  :deep(.el-input__wrapper) {
    background: var(--el-bg-color);
  }
}

.file-item-editing {
  display: flex;
  align-items: center;
  width: 100%;
  min-height: 48px;
  background: var(--el-bg-color);
  padding: 8px 10px;
}

.file-item-normal {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  min-height: 48px;
}

.file-name {
  color: var(--el-text-color-primary);
  display: flex;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-size-text {
  margin-left: auto;
  flex-shrink: 0;
  margin-right: 12px;
  color: var(--el-text-color-secondary);
}

.action-button {
  flex-shrink: 0;
  margin-left: 12px;
}

/* 编辑状态按钮间距与尺寸优化 */
.file-item-editing :deep(.el-button) {
  margin-left: 6px;
}

/* 文件/文件夹图标伪元素（无需改模板） */
.file-item-normal .folder-link::before {
  content: "📁";
  margin-right: 8px;
  font-size: 16px;
}

.file-item-normal .file-name:not(.folder-link)::before {
  content: "📄";
  margin-right: 8px;
  font-size: 16px;
}

.file-item-normal .folder-link {
  color: var(--el-color-primary);
  font-weight: 600;
}

.file-item-normal .folder-link:hover {
  text-decoration: underline;
}

.folder-drop-hover {
  background-color: color-mix(
    in srgb,
    var(--el-color-primary) 10%,
    transparent
  );
  border-radius: 4px;
}

.dropdown-item-delete-fullSpan {
  display: block;
  width: 100%;
  height: 100%;
  box-sizing: border-box;
  cursor: pointer;
  padding: 7px 20px;
}

::v-deep(.el-dropdown-menu__item.my-class) {
  padding: 0;
}

.download-progress-fixed {
  position: fixed;
  left: 16px;
  bottom: 16px;
  width: 400px;
  padding: 8px 12px;
  background-color: rgba(0, 0, 0, 0.65);
  border-radius: 6px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
  z-index: 2000;

  :deep(.el-progress-bar__outer) {
    background-color: rgba(255, 255, 255, 0.15);
  }

  :deep(.el-progress__text) {
    color: #fff;
  }
}

.upload-progress-fixed {
  position: fixed;
  left: 16px;
  bottom: 80px;
  width: 400px;
  padding: 8px 12px;
  background-color: rgba(0, 0, 0, 0.65);
  border-radius: 6px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
  z-index: 2000;

  :deep(.el-progress-bar__outer) {
    background-color: rgba(255, 255, 255, 0.15);
  }

  :deep(.el-progress__text) {
    color: #fff;
  }
}
</style>
