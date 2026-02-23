// 上传文件信息的接口
export interface FileUploadInfo {
  projectName: string;
  projectStartDate: Date | null;
  projectDuration: number;
  projectManager: string;
  projectManagerSecond: string;
  projectLocation: string;
  projectPartner: string;
}

// 展示文件信息的接口
export interface FileShowType extends FileUploadInfo {
  created: Date;
  updated: Date;
}

// 0: 正常态, 1: 新建文件夹, 2: 重命名文件
export type editingType = 0 | 1 | 2;

export interface FolderTempInfoType {
  id: string;
  name: string;
  parentId: string | null;
  folder: true;
  editing: 1;
}

export interface FileRawInfoType {
  id: string;
  name: string;
  parentId: string | null;
  folder: boolean;
  size: string;
  createdBy: string;
  updatedBy: string;
  created: Date;
  updated: Date;
  editing?: editingType;
  association?: boolean; // 标识文件是否已上传信息
}

// 面包屑元素接口
export interface TrailItemType {
  id: string | null;
  name: string;
}
