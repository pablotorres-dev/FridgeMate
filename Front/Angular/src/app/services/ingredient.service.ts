import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Ingredient } from '../models/ingredient';
import { StorageLocation } from '../models/storage-location';

export interface IngredientFilters {
  location?: StorageLocation;
  direction?: 'asc' | 'desc';
}

@Injectable({ providedIn: 'root' })
export class IngredientService {
  private readonly baseUrl = `${environment.apiUrl}/ingredients`;

  constructor(private http: HttpClient) {}

  getAll(filters: IngredientFilters = {}): Observable<Ingredient[]> {
    let params = new HttpParams();
    if (filters.location) {
      params = params.set('location', filters.location);
    }
    if (filters.direction) {
      params = params.set('direction', filters.direction);
    }
    return this.http.get<Ingredient[]>(this.baseUrl, { params });
  }

  getExpiringSoon(): Observable<Ingredient[]> {
    return this.http.get<Ingredient[]>(`${this.baseUrl}/expiring`);
  }

  create(ingredient: Ingredient): Observable<Ingredient> {
    return this.http.post<Ingredient>(this.baseUrl, ingredient);
  }

  update(id: number, ingredient: Ingredient): Observable<Ingredient> {
    return this.http.put<Ingredient>(`${this.baseUrl}/${id}`, ingredient);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
