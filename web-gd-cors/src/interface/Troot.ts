type UserRole = "ADMIN" | "USER";

export interface UserType {
  id: string;
  name: string;
  email: string;
  role: UserRole;
  created: Date;
  updated: Date;
}

export interface PaginationInfoType {
  total: number;
  size?: number;
  current: number;
  pages: number;
}
