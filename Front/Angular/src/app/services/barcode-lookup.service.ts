import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, map, catchError, of } from 'rxjs';

export interface BarcodeProduct {
  name: string;
  brand?: string;
  quantity?: string;
}

interface OpenFoodFactsResponse {
  status: number;
  product?: {
    product_name?: string;
    brands?: string;
    quantity?: string;
  };
}

@Injectable({ providedIn: 'root' })
export class BarcodeLookupService {
  private readonly baseUrl = 'https://world.openfoodfacts.org/api/v2/product';

  constructor(private http: HttpClient) {}

  lookup(barcode: string): Observable<BarcodeProduct | null> {
    const url = `${this.baseUrl}/${barcode}.json?fields=product_name,brands,quantity`;
    return this.http.get<OpenFoodFactsResponse>(url).pipe(
      map((response) => {
        if (response.status !== 1 || !response.product?.product_name) {
          return null;
        }
        return {
          name: response.product.product_name,
          brand: response.product.brands,
          quantity: response.product.quantity,
        };
      }),
      catchError(() => of(null)),
    );
  }
}
