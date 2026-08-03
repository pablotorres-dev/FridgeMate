import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { ShoppingListEntry } from '../../models/shopping-list-entry';
import { ShoppingListService } from '../../services/shopping-list.service';
import { IngredientService } from '../../services/ingredient.service';

@Component({
  selector: 'app-shopping-list-needed',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './shopping-list-needed.component.html',
  styleUrl: './shopping-list-needed.component.css',
})
export class ShoppingListNeededComponent implements OnInit {
  entries: ShoppingListEntry[] = [];
  loading = false;
  error: string | null = null;
  restockingId: number | null = null;

  constructor(
    private shoppingListService: ShoppingListService,
    private ingredientService: IngredientService,
  ) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error = null;
    this.shoppingListService.getNeeded().subscribe({
      next: (data) => {
        this.entries = data;
        this.loading = false;
      },
      error: () => {
        this.error = 'No se ha podido conectar con la API. ¿Está el backend arrancado?';
        this.loading = false;
      },
    });
  }

  get neededCount(): number {
    return this.entries.filter((entry) => entry.quantityToBuy > 0).length;
  }

  markBought(entry: ShoppingListEntry): void {
    this.restockingId = entry.id;
    this.ingredientService.getAll().subscribe({
      next: (allIngredients) => {
        const previous = allIngredients.find(
          (ingredient) => ingredient.name.toLowerCase() === entry.name.toLowerCase(),
        );
        this.ingredientService
          .create({
            name: entry.name,
            quantity: entry.quantityToBuy,
            unit: entry.unit,
            type: previous?.type ?? 'OTHER',
            storageLocation: previous?.storageLocation ?? 'PANTRY',
          })
          .subscribe({
            next: () => {
              this.restockingId = null;
              this.load();
            },
            error: () => {
              this.restockingId = null;
            },
          });
      },
      error: () => {
        this.restockingId = null;
      },
    });
  }
}
