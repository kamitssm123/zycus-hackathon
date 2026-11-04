/** Mirrors com.zycus.hackathon.common.dto.ApiError — keep both sides in sync. */
export interface ApiErrorBody {
  code: string;
  message: string;
  details?: { field: string; message: string }[];
  path?: string;
  requestId?: string;
  timestamp?: string;
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
  hasNext: boolean;
}

export interface Item {
  id: string;
  name: string;
  description?: string;
  done: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ItemPayload {
  name: string;
  description?: string;
  done?: boolean;
}
