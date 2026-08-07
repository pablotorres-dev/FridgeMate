import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, of, tap, catchError, map } from 'rxjs';
import { environment } from '../../environments/environment';
import { AuthCredentials, AuthUser } from '../models/auth-user';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly baseUrl = `${environment.apiUrl}/auth`;

  /** The signed-in account, or null. Read by the guard and the nav bar. */
  currentUser: AuthUser | null = null;

  /** True once the session has been checked, so the guard only asks the server once. */
  private sessionChecked = false;

  constructor(private http: HttpClient) {}

  login(credentials: AuthCredentials): Observable<AuthUser> {
    return this.http
      .post<AuthUser>(`${this.baseUrl}/login`, credentials)
      .pipe(tap((user) => this.onSignedIn(user)));
  }

  register(credentials: AuthCredentials): Observable<AuthUser> {
    return this.http
      .post<AuthUser>(`${this.baseUrl}/register`, credentials)
      .pipe(tap((user) => this.onSignedIn(user)));
  }

  logout(): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/logout`, {}).pipe(
      tap(() => {
        this.currentUser = null;
        this.sessionChecked = true;
      }),
    );
  }

  /**
   * Asks the server whether the stored cookie is still valid. This is what keeps
   * you signed in across visits: the browser holds the cookie, and on startup the
   * app simply confirms it rather than asking for the password again.
   */
  loadSession(): Observable<AuthUser | null> {
    if (this.sessionChecked) {
      return of(this.currentUser);
    }
    return this.http.get<AuthUser>(`${this.baseUrl}/me`).pipe(
      tap((user) => this.onSignedIn(user)),
      map((user) => user as AuthUser | null),
      catchError(() => {
        // A 401 here just means "not signed in", which is a normal first visit.
        this.currentUser = null;
        this.sessionChecked = true;
        return of(null);
      }),
    );
  }

  private onSignedIn(user: AuthUser): void {
    this.currentUser = user;
    this.sessionChecked = true;
  }
}
