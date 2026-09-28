import { Injectable, inject } from '@angular/core';
import { HttpErrorResponse, HttpEvent, HttpHandler, HttpInterceptor, HttpRequest } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { environment } from '../../../environments/environment';

/** Agrega "Authorization: Bearer <token>" a cada petición al API. Si el token vence, cierra la sesión. */
@Injectable()
export class AuthInterceptor implements HttpInterceptor {
  private auth = inject(AuthService);
  private router = inject(Router);

  intercept(req: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
    const token = this.auth.token;
    const esApi = req.url.startsWith(environment.apiUrl);
    const peticion = token && esApi ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

    return next.handle(peticion).pipe(
      catchError((err: HttpErrorResponse) => {
        if (err.status === 401 && token) {
          this.auth.logout();
          this.router.navigate(['/login']);
        }
        return throwError(() => err);
      }),
    );
  }
}
