import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { Login } from './pages/login/login';
import { Inicio } from './pages/inicio/inicio';
import { RecuperarContrasena } from './pages/recuperar-contrasena/recuperar-contrasena';

const routes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: 'login', component: Login, title: 'Iniciar sesión · RestoFlow' },
  { path: 'recuperar', component: RecuperarContrasena, title: 'Recuperar contraseña · RestoFlow' },
  { path: 'inicio', component: Inicio, title: 'Inicio · RestoFlow' },
  { path: '**', redirectTo: 'login' },
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule],
})
export class AppRoutingModule {}
