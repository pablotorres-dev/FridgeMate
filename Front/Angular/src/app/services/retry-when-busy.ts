import { HttpErrorResponse } from '@angular/common/http';
import { MonoTypeOperatorFunction, retry, throwError, timer } from 'rxjs';

/**
 * The server answers 429 when the model is out of capacity — a condition that
 * clears on its own — as opposed to 502 for one that is actually broken.
 */
export const MODEL_BUSY = 429;

/**
 * Waits longer after each refusal. The server has already spent about fifteen
 * seconds retrying before it gives up and answers 429, so these rounds sit on
 * top of that: roughly a minute of persistence in total.
 */
const DELAYS_MS = [3000, 6000, 12000];

/**
 * Retries a request while the model is merely busy, and gives up immediately on
 * anything else — a rejected key or a bad request would fail identically however
 * many times it is sent, and repeating it only delays the error the user needs.
 *
 * @param onRetry called before each wait, so the page can say what it is doing
 *                rather than appear frozen
 */
export function retryWhenBusy<T>(
  onRetry?: (attempt: number, of: number) => void,
): MonoTypeOperatorFunction<T> {
  return retry<T>({
    count: DELAYS_MS.length,
    delay: (error, attempt) => {
      if (!(error instanceof HttpErrorResponse) || error.status !== MODEL_BUSY) {
        return throwError(() => error);
      }
      onRetry?.(attempt, DELAYS_MS.length);
      return timer(DELAYS_MS[attempt - 1]);
    },
  });
}
