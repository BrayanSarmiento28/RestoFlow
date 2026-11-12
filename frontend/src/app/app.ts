import { Component, signal } from '@angular/core';

@Component({
  selector: 'app-root',
  standalone: false,
  templateUrl: './app.html',
})
export class App {
  protected readonly title = signal('restoflow-frontend');
}
