import { Component, input } from '@angular/core';

/**
 * Símbolo de RestoFlow: plato con tenedor y cuchillo; las ondas representan el flujo de pedidos.
 * Se dibuja con el color del texto (currentColor), así sirve sobre fondos claros u oscuros.
 * Uso: <app-logo [tamano]="22" />
 */
@Component({
  selector: 'app-logo',
  standalone: false,
  template: `
    <svg [attr.width]="tamano()" [attr.height]="tamano()" viewBox="0 0 64 64" role="img" aria-label="RestoFlow">
      <circle cx="32" cy="32" r="15" fill="none" stroke="currentColor" stroke-width="3.6" />
      <path d="M24.5 29.5q3.75-4 7.5 0t7.5 0M24.5 36.5q3.75-4 7.5 0t7.5 0" fill="none" stroke="currentColor"
            stroke-width="3" stroke-linecap="round" />
      <path d="M10 14v9a2.5 2.5 0 0 0 5 0v-9M12.5 14v36" fill="none" stroke="currentColor" stroke-width="2.4"
            stroke-linecap="round" />
      <path d="M53 50V14c-3 2-4.5 7-4.5 12.5 0 2 1 3 2.5 3H53" fill="currentColor" stroke="currentColor"
            stroke-width="1.6" stroke-linejoin="round" stroke-linecap="round" />
    </svg>
  `,
  styles: `:host { display: inline-flex; line-height: 0; }`,
})
export class Logo {
  readonly tamano = input(24);
}
