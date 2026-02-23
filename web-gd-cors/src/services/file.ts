import { apiClient, rawApiClient } from "./client.ts";
import type { FileUploadInfo } from "@/interface/TfileSystem.ts";
import type { AxiosProgressEvent } from "axios";

const FILE_API_BASE_URL = "/files";

export const fileAPI = {
  // 获取文件列表
  getFolderList(folderId: string | null) {
    return apiClient.get(`${FILE_API_BASE_URL}`, {
      params: {
        id: folderId,
      },
    });
  },

  // 获取文件原始信息
  getRawInformation(id: string) {
    return apiClient.get(`${FILE_API_BASE_URL}/${id}`);
  },

  // 获取文件信息
  getInformation(id: string) {
    return apiClient.get(`${FILE_API_BASE_URL}/${id}/info`);
  },

  // 创建新文件夹
  postCreateFolder(id: string | null, name: string) {
    return apiClient.post(`${FILE_API_BASE_URL}/folder`, {
      parentId: id,
      name: name,
    });
  },

  // 重命名文件/文件夹
  putRenameFile(id: string, name: string) {
    return apiClient.put(`${FILE_API_BASE_URL}/${id}/rename`, {
      newName: name,
    });
  },

  // 删除文件/文件夹
  deleteDeleteFile(id: string) {
    return apiClient.delete(`${FILE_API_BASE_URL}/${id}`);
  },

  // 下载文件（支持进度回调）
  getDownloadFile(
    id: string,
    onProgress?: (event: AxiosProgressEvent) => void,
  ) {
    return rawApiClient.get(`${FILE_API_BASE_URL}/${id}/content`, {
      responseType: "blob",
      onDownloadProgress: onProgress,
    });
  },

  // 上传文件
  postUploadFile(
    id: string,
    file: FormData,
    onProgress?: (event: AxiosProgressEvent) => void,
  ) {
    return rawApiClient.post(`${FILE_API_BASE_URL}/upload`, file, {
      params: {
        "parent-id": id,
      },
      onUploadProgress: onProgress,
    });
  },

  // 更新文件
  postUpdateFile(
    id: string,
    file: FormData,
    onProgress?: (event: AxiosProgressEvent) => void,
  ) {
    return rawApiClient.post(`${FILE_API_BASE_URL}/update`, file, {
      params: {
        id: id,
      },
      onUploadProgress: onProgress,
    });
  },

  // 上传文件信息
  postUploadFileInfo(fileMetadataId: string, info: FileUploadInfo) {
    return apiClient.post(`${FILE_API_BASE_URL}/info`, {
      fileMetadataId: fileMetadataId,
      projectName: info.projectName,
      projectStartDate: info.projectStartDate,
      projectDuration: info.projectDuration,
      projectManager: info.projectManager,
      projectManagerSecond: info.projectManagerSecond,
      projectLocation: info.projectLocation,
      projectPartner: info.projectPartner,
    });
  },

  // 更新文件信息
  putUpdateFileInfo(fileMetadataId: string, info: FileUploadInfo) {
    return apiClient.put(`${FILE_API_BASE_URL}/info`, {
      fileMetadataId: fileMetadataId,
      projectName: info.projectName,
      projectStartDate: info.projectStartDate,
      projectDuration: info.projectDuration,
      projectManager: info.projectManager,
      projectManagerSecond: info.projectManagerSecond,
      projectLocation: info.projectLocation,
      projectPartner: info.projectPartner,
    });
  },

  // 移动文件至文件夹
  putMoveFile(id: string, targetFolderId: string) {
    return apiClient.put(`${FILE_API_BASE_URL}/${id}/move`, {
      id: id,
      newParentId: targetFolderId,
    });
  },
};
