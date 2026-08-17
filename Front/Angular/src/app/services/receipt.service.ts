import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, from, switchMap } from 'rxjs';
import { environment } from '../../environments/environment';
import { ParsedReceipt } from '../models/parsed-receipt';

/**
 * A receipt is long and narrow, and the print is small, so the photo needs
 * enough resolution to stay readable. This is the long edge; a phone camera
 * produces several times more than that for no gain.
 */
const MAX_EDGE = 2000;
const JPEG_QUALITY = 0.85;

@Injectable({ providedIn: 'root' })
export class ReceiptService {
  private readonly baseUrl = `${environment.apiUrl}/receipt`;

  constructor(private http: HttpClient) {}

  /** Whether the server has an AI provider configured at all. */
  getStatus(): Observable<{ available: boolean }> {
    return this.http.get<{ available: boolean }>(`${this.baseUrl}/status`);
  }

  scan(file: File): Observable<ParsedReceipt> {
    return from(this.shrink(file)).pipe(
      switchMap((image) => {
        const form = new FormData();
        form.append('image', image, 'receipt.jpg');
        // No Content-Type is set on purpose: the browser has to add the
        // multipart boundary itself, and setting the header strips it.
        return this.http.post<ParsedReceipt>(`${this.baseUrl}/scan`, form);
      }),
    );
  }

  /**
   * Shrinks the photo before it leaves the phone. A camera picture is several
   * megabytes, which is slow to upload on mobile data and costs more to read
   * than the extra detail is worth.
   *
   * <p>Re-encoding also normalises the format: an iPhone may hand over HEIC,
   * and what leaves here is always JPEG.
   */
  private async shrink(file: File): Promise<Blob> {
    try {
      const bitmap = await createImageBitmap(file);
      const scale = Math.min(1, MAX_EDGE / Math.max(bitmap.width, bitmap.height));
      const canvas = document.createElement('canvas');
      canvas.width = Math.round(bitmap.width * scale);
      canvas.height = Math.round(bitmap.height * scale);

      const context = canvas.getContext('2d');
      if (!context) {
        return file;
      }
      context.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
      bitmap.close();

      const blob = await new Promise<Blob | null>((resolve) =>
        canvas.toBlob(resolve, 'image/jpeg', JPEG_QUALITY),
      );
      return blob ?? file;
    } catch {
      // A format the browser can't decode still deserves a try: the server
      // accepts the original formats too.
      return file;
    }
  }
}
