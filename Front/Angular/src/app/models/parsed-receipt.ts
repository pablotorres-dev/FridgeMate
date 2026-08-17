import { ProductType } from './product-type';
import { StorageLocation } from './storage-location';

/** One product read off a till receipt. Type and storage are guesses to confirm. */
export interface ReceiptItem {
  name: string;
  quantity: number;
  unit?: string;
  type: ProductType;
  storageLocation: StorageLocation;
}

export interface ParsedReceipt {
  store?: string;
  items: ReceiptItem[];
}
