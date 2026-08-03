export interface ShoppingListEntry {
  id: number;
  name: string;
  unit?: string;
  minQuantity: number;
  currentQuantity: number;
  quantityToBuy: number;
}
