import { ProductType } from './product-type';
import { StorageLocation } from './storage-location';

export interface Ingredient {
  id?: number;
  name: string;
  quantity: number;
  unit?: string;
  type: ProductType;
  expirationDate?: string;
  storageLocation: StorageLocation;
  addedAt?: string;
}
