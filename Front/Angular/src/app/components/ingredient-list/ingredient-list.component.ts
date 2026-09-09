import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { IngredientFormComponent } from '../ingredient-form/ingredient-form.component';
import { BarcodeScannerComponent } from '../barcode-scanner/barcode-scanner.component';
import { Ingredient } from '../../models/ingredient';
import { STORAGE_LOCATIONS, StorageLocation } from '../../models/storage-location';
import { IngredientService } from '../../services/ingredient.service';
import { ShoppingListService } from '../../services/shopping-list.service';
import { BarcodeLookupService } from '../../services/barcode-lookup.service';
import { ReceiptService } from '../../services/receipt.service';
import { ReceiptHandoffService } from '../../services/receipt-handoff.service';
import { MODEL_BUSY, retryWhenBusy } from '../../services/retry-when-busy';

@Component({
  selector: 'app-ingredient-list',
  standalone: true,
  imports: [CommonModule, FormsModule, IngredientFormComponent, BarcodeScannerComponent],
  templateUrl: './ingredient-list.component.html',
  styleUrl: './ingredient-list.component.css',
})
export class IngredientListComponent implements OnInit {
  ingredients: Ingredient[] = [];
  visibleIngredients: Ingredient[] = [];
  loading = false;
  error: string | null = null;
  editingIngredient: Ingredient | null = null;
  trackedNames = new Set<string>();
  showForm = false;
  showScanner = false;
  scanMessage: string | null = null;
  expiringSoon: Ingredient[] = [];
  receiptAvailable = false;
  scanning = false;
  receiptMessage: string | null = null;

  readonly storageLocations = STORAGE_LOCATIONS;

  locationFilter: StorageLocation | '' = '';
  direction: 'asc' | 'desc' = 'asc';

  constructor(
    private ingredientService: IngredientService,
    private shoppingListService: ShoppingListService,
    private barcodeLookupService: BarcodeLookupService,
    private receiptService: ReceiptService,
    private receiptHandoff: ReceiptHandoffService,
    private router: Router,
  ) {}

  ngOnInit(): void {
    this.load();
    this.loadTrackedNames();
    this.loadExpiringSoon();
    // Hide the button rather than offer one that can only fail on a server
    // with no API key configured.
    this.receiptService.getStatus().subscribe({
      next: (status) => (this.receiptAvailable = status.available),
      error: () => (this.receiptAvailable = false),
    });
  }

  /**
   * The photo is read here, but the products are filed in Shop: assigning type,
   * storage and expiry to a whole batch is a screen that already exists there,
   * and a second copy of it would be a second thing to keep working.
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
    this.receiptMessage = null;

    this.receiptService
      .scan(file)
      .pipe(
        retryWhenBusy((attempt, of) => {
          this.receiptMessage = `Gemini is busy right now — waiting and trying again (${attempt} of ${of})…`;
        }),
      )
      .subscribe({
        next: (receipt) => {
          this.scanning = false;
          if (receipt.items.length === 0) {
            this.receiptMessage =
              "Couldn't make out any products on that photo. Try again with the whole receipt in frame.";
            return;
          }
          this.receiptHandoff.hand(receipt);
          this.router.navigate(['/shop']);
        },
        error: (response) => {
          this.scanning = false;
          this.receiptMessage =
            response.status === MODEL_BUSY
              ? 'Gemini is busy right now — it does that when demand spikes, and it passes. Try the receipt again in a minute.'
              : "Couldn't read that receipt. Try again in better light, with the whole receipt flat and in frame.";
        },
      });
  }

  private loadExpiringSoon(): void {
    this.ingredientService.getExpiringSoon().subscribe((data) => {
      this.expiringSoon = data;
    });
  }

  /**
   * The server answers "expires before three days from now", which is also true
   * of anything that went off last month. They are split here because food that
   * is already bad and food to eat tomorrow call for different reactions — and
   * calling both "expiring soon" quietly hides the first.
   */
  get expired(): Ingredient[] {
    return this.expiringSoon.filter((ingredient) => this.isExpired(ingredient));
  }

  get expiringWithin3Days(): Ingredient[] {
    return this.expiringSoon.filter((ingredient) => !this.isExpired(ingredient));
  }

  /** Dates arrive as YYYY-MM-DD, which compares correctly as plain text. */
  isExpired(ingredient: Ingredient): boolean {
    return !!ingredient.expirationDate && ingredient.expirationDate < this.today;
  }

  /** "en-CA" is ISO-shaped, and local time is what the user means by today. */
  private get today(): string {
    return new Date().toLocaleDateString('en-CA');
  }

  namesOf(ingredients: Ingredient[]): string {
    return ingredients.map((ingredient) => ingredient.name).join(', ');
  }

  private loadTrackedNames(): void {
    this.shoppingListService.getAll().subscribe((items) => {
      this.trackedNames = new Set(items.map((item) => item.name.toLowerCase()));
    });
  }

  isTracked(ingredient: Ingredient): boolean {
    return this.trackedNames.has(ingredient.name.toLowerCase());
  }

  /**
   * Fetches the whole kitchen once. Filtering and sorting then happen here,
   * where the data already is.
   *
   * <p>Each filter change used to be a round trip: about 650ms, of which some
   * 480 was simply reaching the server and coming back. A kitchen holds a few
   * dozen items, so asking a database in another region to narrow that list
   * cost far more than doing it in the page.
   */
  load(): void {
    this.loading = true;
    this.error = null;
    this.ingredientService.getAll().subscribe({
      next: (data) => {
        this.ingredients = data;
        this.applyFilters();
        this.loading = false;
      },
      error: () => {
        this.error = 'No se ha podido conectar con la API. ¿Está el backend arrancado?';
        this.loading = false;
      },
    });
  }

  /** Runs on every filter change, and costs nothing: no request is made. */
  applyFilters(): void {
    const matching = this.locationFilter
      ? this.ingredients.filter((i) => i.storageLocation === this.locationFilter)
      : [...this.ingredients];

    const factor = this.direction === 'desc' ? -1 : 1;
    this.visibleIngredients = matching.sort((a, b) => {
      // Undated items sit at the end whichever way round it is, matching the
      // nullsLast the server used to apply.
      if (!a.expirationDate && !b.expirationDate) {
        return 0;
      }
      if (!a.expirationDate) {
        return 1;
      }
      if (!b.expirationDate) {
        return -1;
      }
      // Dates are YYYY-MM-DD, which orders correctly as plain text.
      return factor * a.expirationDate.localeCompare(b.expirationDate);
    });
  }

  toggleForm(): void {
    this.showForm = !this.showForm;
    if (!this.showForm) {
      this.editingIngredient = null;
    }
  }

  editIngredient(ingredient: Ingredient): void {
    this.editingIngredient = ingredient;
    this.showForm = true;
  }

  cancelEdit(): void {
    this.editingIngredient = null;
    this.showForm = false;
  }

  saveIngredient(ingredient: Ingredient): void {
    const request =
      this.editingIngredient?.id != null
        ? this.ingredientService.update(this.editingIngredient.id, ingredient)
        : this.ingredientService.create(ingredient);

    request.subscribe(() => {
      this.editingIngredient = null;
      this.showForm = false;
      this.load();
      this.loadExpiringSoon();
    });
  }

  deleteIngredient(id: number | undefined): void {
    if (id == null) {
      return;
    }
    this.ingredientService.delete(id).subscribe(() => {
      this.load();
      this.loadExpiringSoon();
    });
  }

  addToShoppingList(ingredient: Ingredient): void {
    this.shoppingListService
      .create({ name: ingredient.name, unit: ingredient.unit })
      .subscribe(() => this.trackedNames.add(ingredient.name.toLowerCase()));
  }

  openScanner(): void {
    this.scanMessage = null;
    this.showScanner = true;
  }

  onScanned(barcode: string): void {
    this.showScanner = false;
    this.barcodeLookupService.lookup(barcode).subscribe((product) => {
      if (product) {
        this.scanMessage = null;
        this.editingIngredient = {
          name: product.brand ? `${product.name} (${product.brand})` : product.name,
          quantity: 1,
          type: 'OTHER',
          storageLocation: 'FRIDGE',
        };
      } else {
        this.scanMessage = `No se encontró ningún producto para el código ${barcode}. Añádelo a mano.`;
        this.editingIngredient = null;
      }
      this.showForm = true;
    });
  }
}
