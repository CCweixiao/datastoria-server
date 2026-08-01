export interface ConnectionConfig {
  id?: string;
  revision?: number;
  name: string;
  url: string;
  user: string;
  password: string;
  cluster: string;
  remark?: string;
  editable: boolean;
}
