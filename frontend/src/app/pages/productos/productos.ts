import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { CatalogoService } from '../../core/services/catalogo.service';
import { mensajeDeError } from '../../core/services/errores';
import { pesos } from '../../core/util/formato';
import { Categoria, Producto } from '../../core/entities';

/** Menú del restaurante: el administrador crea categorías y productos, cambia precios y los activa o desactiva. */
@Component({
  selector: 'app-productos',
  standalone: false,
  templateUrl: './productos.html',
  styleUrl: './productos.scss',
})
export class Productos implements OnInit {
  private fb = inject(FormBuilder);
  private catalogoApi = inject(CatalogoService);

  protected readonly pesos = pesos;
  protected readonly productos = signal<Producto[]>([]);
  protected readonly categorias = signal<Categoria[]>([]);
  protected readonly editandoId = signal<number | null>(null);
  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly exito = signal<string | null>(null);

  protected readonly form = this.fb.group({
    nombre: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(100)]],
    descripcion: ['', Validators.maxLength(300)],
    precio: [null as number | null, [Validators.required, Validators.min(1)]],
    categoriaId: [null as number | null, Validators.required],
  });
  protected readonly nuevaCategoria = this.fb.nonNullable.control('', [Validators.required, Validators.minLength(3)]);

  ngOnInit(): void {
    this.cargar();
  }

  protected guardar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const datos = {
      nombre: (v.nombre ?? '').trim(),
      descripcion: v.descripcion?.trim() || null,
      precio: Number(v.precio),
      categoriaId: Number(v.categoriaId),
    };
    const id = this.editandoId();
    const peticion = id ? this.catalogoApi.editarProducto(id, datos) : this.catalogoApi.crearProducto(datos);
    this.guardando.set(true);
    this.limpiarMensajes();
    peticion.subscribe({
      next: (p) => {
        this.guardando.set(false);
        this.exito.set(id ? `Producto ${p.nombre} actualizado.` : `Producto ${p.nombre} creado.`);
        this.nuevo();
        this.cargar();
      },
      error: (err: HttpErrorResponse) => {
        this.guardando.set(false);
        this.error.set(mensajeDeError(err));
      },
    });
  }

  protected editar(p: Producto): void {
    this.editandoId.set(p.id);
    this.form.reset({ nombre: p.nombre, descripcion: p.descripcion ?? '', precio: p.precio, categoriaId: p.categoriaId });
    this.limpiarMensajes();
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  protected nuevo(): void {
    this.editandoId.set(null);
    this.form.reset({ nombre: '', descripcion: '', precio: null, categoriaId: null });
  }

  protected alternar(p: Producto): void {
    this.limpiarMensajes();
    this.catalogoApi.cambiarDisponibilidad(p.id, !p.activo).subscribe({
      next: (actualizado) => {
        this.exito.set(`${actualizado.nombre} ${actualizado.activo ? 'disponible para la venta' : 'desactivado'}.`);
        this.cargar();
      },
      error: (err: HttpErrorResponse) => this.error.set(mensajeDeError(err)),
    });
  }

  protected crearCategoria(): void {
    if (this.nuevaCategoria.invalid) {
      this.nuevaCategoria.markAsTouched();
      return;
    }
    this.limpiarMensajes();
    this.catalogoApi.crearCategoria(this.nuevaCategoria.value.trim()).subscribe({
      next: (c) => {
        this.exito.set(`Categoría ${c.nombre} creada.`);
        this.nuevaCategoria.reset();
        this.form.controls.categoriaId.setValue(c.id);
        this.cargar();
      },
      error: (err: HttpErrorResponse) => this.error.set(mensajeDeError(err)),
    });
  }

  protected invalido(campo: 'nombre' | 'descripcion' | 'precio' | 'categoriaId'): boolean {
    const control = this.form.controls[campo];
    return control.invalid && control.touched;
  }

  private cargar(): void {
    this.catalogoApi.categorias().subscribe({
      next: (lista) => this.categorias.set(lista),
      error: (err: HttpErrorResponse) => this.error.set(mensajeDeError(err)),
    });
    this.catalogoApi.productos().subscribe({
      next: (lista) => this.productos.set(lista),
      error: (err: HttpErrorResponse) => this.error.set(mensajeDeError(err)),
    });
  }

  private limpiarMensajes(): void {
    this.error.set(null);
    this.exito.set(null);
  }
}
