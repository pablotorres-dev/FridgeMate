import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { Ingredient } from '../../models/ingredient';
import { PRODUCT_TYPES, ProductType } from '../../models/product-type';
import { STORAGE_LOCATIONS, StorageLocation } from '../../models/storage-location';
import { IngredientService } from '../../services/ingredient.service';
import { ShoppingListService } from '../../services/shopping-list.service';

interface CartItem {
  name: string;
  unit?: string;
  quantity: number;
  currentQuantity?: number;
  minQuantity?: number;
  bought: boolean;
  custom: boolean;
}

interface PurchaseDraft {
  name: string;
  unit?: string;
  quantity: number;
  type: ProductType;
  storageLocation: StorageLocation;
  expirationDate?: string;
}

@Component({
  selector: 'app-shopping-mode',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './shopping-mode.component.html',
  styleUrl: './shopping-mode.component.css',
})
export class ShoppingModeComponent implements OnInit {
  phase: 'shopping' | 'review' = 'shopping';
  loading = false;
  error: string | null = null;
  saving = false;
  savedMessage: string | null = null;

  cartItems: CartItem[] = [];
  drafts: PurchaseDraft[] = [];

  newItemName = '';
  newItemUnit = '';
  newItemQuantity = 1;

  readonly productTypes = PRODUCT_TYPES;
  readonly storageLocations = STORAGE_LOCATIONS;

  constructor(
    private shoppingListService: ShoppingListService,
    private ingredientService: IngredientService,
  ) {}

  ngOnInit(): void {
    this.loadNeeded();
  }

  loadNeeded(): void {
    this.loading = true;
    this.error = null;
    this.shoppingListService.getNeeded().subscribe({
      next: (entries) => {
        this.cartItems = entries
          .filter((entry) => entry.quantityToBuy > 0)
          .map((entry) => ({
            name: entry.name,
            unit: entry.unit,
            quantity: entry.quantityToBuy,
            currentQuantity: entry.currentQuantity,
            minQuantity: entry.minQuantity,
            bought: false,
            custom: false,
          }));
        this.loading = false;
      },
      error: () => {
        this.error = 'No se ha podido conectar con la API. ¿Está el backend arrancado?';
        this.loading = false;
      },
    });
  }

  addCustomItem(): void {
    if (!this.newItemName.trim()) {
      return;
    }
    this.cartItems.push({
      name: this.newItemName.trim(),
      unit: this.newItemUnit.trim() || undefined,
      quantity: this.newItemQuantity,
      bought: true,
      custom: true,
    });
    this.newItemName = '';
    this.newItemUnit = '';
    this.newItemQuantity = 1;
  }

  removeItem(item: CartItem): void {
    this.cartItems = this.cartItems.filter((i) => i !== item);
  }

  get boughtCount(): number {
    return this.cartItems.filter((item) => item.bought).length;
  }

  startReview(): void {
    const bought = this.cartItems.filter((item) => item.bought);
    this.loading = true;
    this.ingredientService.getAll().subscribe({
      next: (allIngredients) => {
        this.drafts = bought.map((item) => this.toDraft(item, allIngredients));
        this.phase = 'review';
        this.loading = false;
      },
      error: () => {
        this.error = 'No se ha podido conectar con la API. ¿Está el backend arrancado?';
        this.loading = false;
      },
    });
  }

  private toDraft(item: CartItem, allIngredients: Ingredient[]): PurchaseDraft {
    const previous = allIngredients.find(
      (ingredient) => ingredient.name.toLowerCase() === item.name.toLowerCase(),
    );
    return {
      name: item.name,
      unit: item.unit,
      quantity: item.quantity,
      type: previous?.type ?? 'OTHER',
      storageLocation: previous?.storageLocation ?? 'PANTRY',
      expirationDate: undefined,
    };
  }

  removeDraft(draft: PurchaseDraft): void {
    this.drafts = this.drafts.filter((d) => d !== draft);
  }

  backToShopping(): void {
    this.phase = 'shopping';
  }

  saveAll(): void {
    if (this.drafts.length === 0) {
      return;
    }
    this.saving = true;
    const requests = this.drafts.map((draft) =>
      this.ingredientService.create({
        name: draft.name,
        quantity: draft.quantity,
        unit: draft.unit,
        type: draft.type,
        storageLocation: draft.storageLocation,
        expirationDate: draft.expirationDate || undefined,
      }),
    );

    forkJoin(requests).subscribe({
      next: () => {
        this.savedMessage = `✓ ${this.drafts.length} product${this.drafts.length === 1 ? '' : 's'} added to your inventory.`;
        this.drafts = [];
        this.saving = false;
        this.phase = 'shopping';
        this.loadNeeded();
      },
      error: () => {
        this.error = 'No se ha podido guardar. ¿Está el backend arrancado?';
        this.saving = false;
      },
    });
  }
}
