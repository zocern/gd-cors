// 向量状态页相关配置与类型

export interface VectorStatusItem {
  id: string;
  name: string;
  size: string;
  storageKey: string;
  status: string;
  retryCount: number;
  created: string;
  updated: string;
  errorMsg: string | null;
}

export interface VectorStatusResponse {
  records: VectorStatusItem[];
  total: string;
  size: string;
  current: string;
  pages: string;
}

export const VECTOR_STATUS_PAGE_SIZE_DEFAULT = 10;
export const VECTOR_STATUS_PAGE_SIZE_OPTIONS = [10, 20, 50];
export const VECTOR_STATUS_SEARCH_DEBOUNCE = 500;

export const buildVectorStatusParams = (options: {
  pageNum: number;
  pageSize: number;
  keyword?: string;
  status?: string;
  start?: string;
  end?: string;
}) => {
  const { pageNum, pageSize, keyword, status, start, end } = options;
  const params: Record<string, unknown> = {
    pageNum,
    pageSize,
  };

  const trimmedKeyword = keyword?.trim();
  if (trimmedKeyword) {
    params.keyword = trimmedKeyword;
  }

  const trimmedStatus = status?.trim();
  if (trimmedStatus) {
    params.status = trimmedStatus;
  }

  const trimmedStart = start?.trim();
  if (trimmedStart) {
    params.start = trimmedStart;
  }

  const trimmedEnd = end?.trim();
  if (trimmedEnd) {
    params.end = trimmedEnd;
  }

  return params;
};
