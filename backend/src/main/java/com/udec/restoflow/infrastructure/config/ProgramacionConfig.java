package com.udec.restoflow.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Activa las tareas programadas (adaptadores de entrada "por reloj" en {@code adapter.in.programada}):
 * RestoFlow AI revisa el inventario cada hora y el registro de actividad se limpia cada madrugada.
 * Cada tarea se puede apagar por separado con su variable de entorno.
 */
@Configuration
@EnableScheduling
public class ProgramacionConfig {
}
