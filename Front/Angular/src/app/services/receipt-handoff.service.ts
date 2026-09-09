import { Injectable } from '@angular/core';
import { ParsedReceipt } from '../models/parsed-receipt';

/**
 * Carries a scanned receipt from wherever it was photographed to the screen
 * that files it away.
 *
 * <p>A receipt can be scanned from the inventory, which is where you are when
 * you have just come home, but the step that assigns type, storage and expiry
 * to a batch lives in Shop. Rather than build a second copy of that step, the
 * scan happens where the button is and the result is handed over.
 *
 * <p>Reading it takes it: a receipt is filed once, and a later visit to Shop
 * should start empty rather than replaying the last shop.
 */
@Injectable({ providedIn: 'root' })
export class ReceiptHandoffService {
  private pending: ParsedReceipt | null = null;

  hand(receipt: ParsedReceipt): void {
    this.pending = receipt;
  }

  take(): ParsedReceipt | null {
    const receipt = this.pending;
    this.pending = null;
    return receipt;
  }
}
