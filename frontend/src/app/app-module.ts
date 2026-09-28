import { NgModule, provideBrowserGlobalErrorListeners } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { ReactiveFormsModule } from '@angular/forms';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { AppRoutingModule } from './app-routing-module';
import { App } from './app';
import { Login } from './pages/login/login';
import { Inicio } from './pages/inicio/inicio';
import { PanelMarca } from './components/panel-marca/panel-marca';
import { RecuperarContrasena } from './pages/recuperar-contrasena/recuperar-contrasena';

@NgModule({
  declarations: [App, Login, Inicio, PanelMarca, RecuperarContrasena],
  imports: [BrowserModule, ReactiveFormsModule, AppRoutingModule],
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideHttpClient(withInterceptorsFromDi()),
  ],
  bootstrap: [App],
})
export class AppModule {}
