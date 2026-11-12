import { Injectable, inject } from '@angular/core';
import { HttpErrorResponse, HttpEvent, HttpHandler, HttpInterceptor, HttpRequest } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { environment } from '../../../environments/environment';
import { debeAdjuntarToken, debeCerrarSesion } from '../util/acceso';

/** Agrega "Authorization: Bearer <token>" a cada petición al API. Si el token vence, cierra la sesión. */
@Injectable()
export class AuthInterceptor implements HttpInterceptor {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  intercept(req: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
    const token = this.auth.token;
    const peticion = debeAdjuntarToken(req.url, environment.apiUrl, token) ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

    return next.handle(peticion).pipe(
      catchError((err: HttpErrorResponse) => {
        if (debeCerrarSesion(err.status, !!token)) {
          this.auth.logout();
          this.router.navigate(['/login']);
        }
        return throwError(() => err);
      }),
    );
  }
}
