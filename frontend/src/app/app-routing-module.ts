import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { Login } from './pages/login/login';
import { Inicio } from './pages/inicio/inicio';
import { RecuperarContrasena } from './pages/recuperar-contrasena/recuperar-contrasena';
import { Usuarios } from './pages/usuarios/usuarios';
import { Estructura } from './layout/estructura/estructura';
import { Pedidos } from './pages/pedidos/pedidos';
import { Cocina } from './pages/cocina/cocina';
import { Productos } from './pages/productos/productos';
import { adminGuard, authGuard, rolGuard } from './core/guards/auth.guard';

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
      {
        path: 'pedidos', component: Pedidos, canActivate: [rolGuard],
        data: { roles: ['MESERO', 'ADMIN'] }, title: 'Pedidos · RestoFlow',
      },
      {
        path: 'cocina', component: Cocina, canActivate: [rolGuard],
        data: { roles: ['COCINA', 'ADMIN'] }, title: 'Cocina · RestoFlow',
      },
      { path: 'productos', component: Productos, canActivate: [adminGuard], title: 'Productos · RestoFlow' },
    ],
  },
  { path: '**', redirectTo: 'inicio' },
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule],
})
export class AppRoutingModule {}
