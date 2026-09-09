import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Ingredient } from '../../models/ingredient';
import { PRODUCT_TYPES, ProductType } from '../../models/product-type';
import { STORAGE_LOCATIONS, StorageLocation } from '../../models/storage-location';
import { IngredientService } from '../../services/ingredient.service';
import { ParsedReceipt } from '../../models/parsed-receipt';
import { ReceiptService } from '../../services/receipt.service';
import { ReceiptHandoffService } from '../../services/receipt-handoff.service';
import { MODEL_BUSY, retryWhenBusy } from '../../services/retry-when-busy';
import { ShoppingListService } from '../../services/shopping-list.service';

interface CartItem {
  name: string;
  unit?: string;
  quantity: number;
  currentQuantity?: number;
  minQuantity?: number;
  bought: boolean;
  custom: boolean;
  /** Read off a receipt, and only a guess — the review step confirms it. */
  suggestedType?: ProductType;
  suggestedLocation?: StorageLocation;
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

  receiptAvailable = false;
  scanning = false;
  receiptMessage: string | null = null;
  receiptError: string | null = null;

  readonly productTypes = PRODUCT_TYPES;
  readonly storageLocations = STORAGE_LOCATIONS;

  constructor(
    private shoppingListService: ShoppingListService,
    private ingredientService: IngredientService,
    private receiptService: ReceiptService,
    private receiptHandoff: ReceiptHandoffService,
  ) {}

  ngOnInit(): void {
    // A receipt scanned from the inventory arrives here already read. Someone
    // holding a receipt has finished shopping, so the checklist would be a
    // screen to click past: they go straight to assigning storage.
    const handed = this.receiptHandoff.take();
    if (handed) {
      this.cartItems = this.toCartItems(handed);
      this.receiptMessage = this.describeAdded(handed);
      this.startReview();
    } else {
      this.loadNeeded();
    }

    // Hide the button rather than offer one that can only fail on a server
    // with no API key configured.
    this.receiptService.getStatus().subscribe({
      next: (status) => (this.receiptAvailable = status.available),
      error: () => (this.receiptAvailable = false),
    });
  }

  /** Everything a receipt names is marked bought and removable: the reading can
   *  be wrong, and the user is the one who was actually at the till. */
  private toCartItems(receipt: ParsedReceipt): CartItem[] {
    return receipt.items.map((item) => ({
      name: item.name,
      unit: item.unit,
      quantity: item.quantity,
      bought: true,
      custom: true,
      suggestedType: item.type,
      suggestedLocation: item.storageLocation,
    }));
  }

  private describeAdded(receipt: ParsedReceipt): string {
    const count = receipt.items.length;
    const from = receipt.store ? ' from ' + receipt.store : '';
    return `✓ Added ${count} product${count === 1 ? '' : 's'}${from}. Check them before finishing.`;
  }

  /**
   * A photographed receipt fills the cart in one go. Everything it finds is
   * marked as bought and removable, because the reading can be wrong and the
   * user is the one who was actually at the till.
   */
  onReceiptSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    // Cleared so that choosing the same photo again still fires a change.
    input.value = '';
    if (!file) {
      return;
    }

    this.scanning = true;
    this.receiptError = null;
    this.receiptMessage = null;

    this.receiptService
      .scan(file)
      .pipe(
        // The same model as the nutrition estimate, and it runs out of
        // capacity the same way. Waited out rather than shown as a failure.
        retryWhenBusy((attempt, of) => {
          this.receiptMessage = `Gemini is busy right now — waiting and trying again (${attempt} of ${of})…`;
        }),
      )
      .subscribe({
        next: (receipt) => {
          this.cartItems = [...this.cartItems, ...this.toCartItems(receipt)];
          this.scanning = false;
          this.receiptMessage =
            receipt.items.length > 0
              ? this.describeAdded(receipt)
              : "Couldn't make out any products on that photo. Try again with the whole receipt in frame.";
        },
        error: (response) => {
          this.scanning = false;
          this.receiptMessage = null;
          this.receiptError = this.describeScanFailure(response.status);
        },
      });
  }

  private describeScanFailure(status: number): string {
    switch (status) {
      case MODEL_BUSY:
        // Reached only once the server and the page have both run out of
        // patience, so it names the cause instead of blaming the photo.
        return 'Gemini is busy right now — it does that when demand spikes, and it passes. Try the receipt again in a minute.';
      case 503:
        return "Receipt scanning isn't configured on this server.";
      default:
        return "Couldn't read that receipt. Try again in better light, with the whole receipt flat and in frame.";
    }
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
      // What this product was last time beats a guess from a receipt, which in
      // turn beats a blanket default.
      type: previous?.type ?? item.suggestedType ?? 'OTHER',
      storageLocation: previous?.storageLocation ?? item.suggestedLocation ?? 'PANTRY',
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
    // One request for the whole shop. This used to fire one per product, so a
    // scanned receipt meant twenty-six round trips to file a single shop.
    const purchases = this.drafts.map((draft) => ({
      name: draft.name,
      quantity: draft.quantity,
      unit: draft.unit,
      type: draft.type,
      storageLocation: draft.storageLocation,
      expirationDate: draft.expirationDate || undefined,
    }));

    this.ingredientService.createAll(purchases).subscribe({
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
