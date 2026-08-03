import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { IngredientFormComponent } from '../ingredient-form/ingredient-form.component';
import { Ingredient } from '../../models/ingredient';
import { STORAGE_LOCATIONS, StorageLocation } from '../../models/storage-location';
import { IngredientService } from '../../services/ingredient.service';
import { ShoppingListService } from '../../services/shopping-list.service';

@Component({
  selector: 'app-ingredient-list',
  standalone: true,
  imports: [CommonModule, FormsModule, IngredientFormComponent],
  templateUrl: './ingredient-list.component.html',
  styleUrl: './ingredient-list.component.css',
})
export class IngredientListComponent implements OnInit {
  ingredients: Ingredient[] = [];
  loading = false;
  error: string | null = null;
  editingIngredient: Ingredient | null = null;
  trackedNames = new Set<string>();
  showForm = false;
  expiringSoon: Ingredient[] = [];

  readonly storageLocations = STORAGE_LOCATIONS;

  locationFilter: StorageLocation | '' = '';
  direction: 'asc' | 'desc' = 'asc';

  constructor(
    private ingredientService: IngredientService,
    private shoppingListService: ShoppingListService,
  ) {}

  ngOnInit(): void {
    this.load();
    this.loadTrackedNames();
    this.loadExpiringSoon();
  }

  private loadExpiringSoon(): void {
    this.ingredientService.getExpiringSoon().subscribe((data) => {
      this.expiringSoon = data;
    });
  }

  get expiringSoonNames(): string {
    return this.expiringSoon.map((ingredient) => ingredient.name).join(', ');
  }

  private loadTrackedNames(): void {
    this.shoppingListService.getAll().subscribe((items) => {
      this.trackedNames = new Set(items.map((item) => item.name.toLowerCase()));
    });
  }

  isTracked(ingredient: Ingredient): boolean {
    return this.trackedNames.has(ingredient.name.toLowerCase());
  }

  load(): void {
    this.loading = true;
    this.error = null;
    this.ingredientService
      .getAll({
        location: this.locationFilter || undefined,
        direction: this.direction,
      })
      .subscribe({
        next: (data) => {
          this.ingredients = data;
          this.loading = false;
        },
        error: () => {
          this.error = 'No se ha podido conectar con la API. ¿Está el backend arrancado?';
          this.loading = false;
        },
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
}
