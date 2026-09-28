import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { Login } from './pages/login/login';
import { Inicio } from './pages/inicio/inicio';
import { RecuperarContrasena } from './pages/recuperar-contrasena/recuperar-contrasena';
import { Usuarios } from './pages/usuarios/usuarios';
import { Estructura } from './layout/estructura/estructura';
import { adminGuard, authGuard } from './core/guards/auth.guard';

const routes: Routes = [
  { path: 'login', component: Login, title: 'Iniciar sesión · RestoFlow' },
  { path: 'recuperar', component: RecuperarContrasena, title: 'Recuperar contraseña · RestoFlow' },
  {
    path: '',
    component: Estructura,
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'inicio', pathMatch: 'full' },
      { path: 'inicio', component: Inicio, title: 'Dashboard · RestoFlow' },
      { path: 'usuarios', component: Usuarios, canActivate: [adminGuard], title: 'Cuentas del personal · RestoFlow' },
    ],
  },
  { path: '**', redirectTo: 'inicio' },
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule],
})
export class AppRoutingModule {}
