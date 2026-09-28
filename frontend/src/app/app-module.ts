import { NgModule, provideBrowserGlobalErrorListeners } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { ReactiveFormsModule } from '@angular/forms';
import { HTTP_INTERCEPTORS, provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { AppRoutingModule } from './app-routing-module';
import { App } from './app';
import { Login } from './pages/login/login';
import { Inicio } from './pages/inicio/inicio';
import { PanelMarca } from './components/panel-marca/panel-marca';
import { RecuperarContrasena } from './pages/recuperar-contrasena/recuperar-contrasena';
import { Usuarios } from './pages/usuarios/usuarios';
import { Estructura } from './layout/estructura/estructura';
import { AuthInterceptor } from './core/interceptors/auth.interceptor';

@NgModule({
  declarations: [App, Login, Inicio, PanelMarca, RecuperarContrasena, Usuarios, Estructura],
  imports: [BrowserModule, ReactiveFormsModule, AppRoutingModule],
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideHttpClient(withInterceptorsFromDi()),
    { provide: HTTP_INTERCEPTORS, useClass: AuthInterceptor, multi: true },
  ],
  bootstrap: [App],
})
export class AppModule {}
