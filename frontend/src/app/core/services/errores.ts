import { HttpErrorResponse } from '@angular/common/http';

/** Convierte un error del API en un mensaje entendible para el usuario. */
export function mensajeDeError(err: HttpErrorResponse): string {
  if (err.status === 0) return 'No se pudo conectar con el servidor. Verifica que el backend esté encendido.';
  if (err.status === 403) return 'Tu rol no tiene permiso para esta acción.';
  const campos = err.error?.campos ? Object.values(err.error.campos as Record<string, string>).join('. ') : '';
  return campos || err.error?.mensaje || 'No se pudo completar la solicitud.';
}
