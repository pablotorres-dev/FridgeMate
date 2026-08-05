import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PRODUCT_TYPES } from '../../models/product-type';
import { Ingredient } from '../../models/ingredient';
import { STORAGE_LOCATIONS } from '../../models/storage-location';

const EMPTY_INGREDIENT: Ingredient = {
  name: '',
  quantity: 1,
  unit: '',
  type: 'OTHER',
  expirationDate: '',
  storageLocation: 'FRIDGE',
};

@Component({
  selector: 'app-ingredient-form',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './ingredient-form.component.html',
  styleUrl: './ingredient-form.component.css',
})
export class IngredientFormComponent implements OnChanges {
  @Input() ingredient: Ingredient | null = null;
  @Output() save = new EventEmitter<Ingredient>();
  @Output() cancel = new EventEmitter<void>();

  model: Ingredient = { ...EMPTY_INGREDIENT };

  readonly productTypes = PRODUCT_TYPES;
  readonly storageLocations = STORAGE_LOCATIONS;

  ngOnChanges(): void {
    this.model = this.ingredient ? { ...this.ingredient } : { ...EMPTY_INGREDIENT };
  }

  get isEditing(): boolean {
    return this.ingredient?.id != null;
  }

  submit(): void {
    this.save.emit({
      ...this.model,
      unit: this.model.unit || undefined,
      expirationDate: this.model.expirationDate || undefined,
    });
    if (!this.isEditing) {
      this.model = { ...EMPTY_INGREDIENT };
    }
  }

  cancelEdit(): void {
    this.cancel.emit();
  }
}
