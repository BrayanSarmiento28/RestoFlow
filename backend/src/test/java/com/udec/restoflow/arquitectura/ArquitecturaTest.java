package com.udec.restoflow.arquitectura;

import com.udec.restoflow.application.port.out.EnviarCorreoPort;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>Principios SOLID y arquitectura hexagonal verificados automáticamente.</b>
 * <p>
 * Estas pruebas no prueban una funcionalidad: revisan cómo está organizado el código (sus {@code import} y sus
 * clases). Si alguien rompe una regla —por ejemplo, el dominio empieza a usar Spring o un controlador llama
 * directamente a un servicio—, la prueba falla en {@code mvn verify} y en GitHub Actions, y el error dice qué
 * archivo la rompió. Así SOLID no queda solo en un documento (ver docs/principios-solid.md).
 * <p>
 * Se implementan con JUnit y Java estándar (sin librerías extra) para que se entienda cómo funcionan.
 */
class ArquitecturaTest {

    private static final Path CODIGO = Paths.get("src/main/java/com/udec/restoflow");
    private static final String BASE = "com.udec.restoflow";

    /** Cada archivo .java de producción con la lista de sus import. */
    private static Map<Path, List<String>> imports;

    @BeforeAll
    static void leerCodigo() throws IOException {
        try (Stream<Path> archivos = Files.walk(CODIGO)) {
            imports = archivos.filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toMap(p -> p, ArquitecturaTest::importsDe));
        }
        assertThat(imports).as("No se encontró el código fuente en %s", CODIGO.toAbsolutePath()).hasSizeGreaterThan(50);
    }

    private static List<String> importsDe(Path archivo) {
        try {
            return Files.readAllLines(archivo, StandardCharsets.UTF_8).stream()
                    .map(String::trim)
                    .filter(l -> l.startsWith("import "))
                    .map(l -> l.replace("import static ", "").replace("import ", "").replace(";", "").trim())
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** "import x.y.Z" que no empiezan por ninguno de los prefijos permitidos, como "Archivo.java → import". */
    private static List<String> importsNoPermitidos(String carpeta, List<String> permitidos) {
        return imports.entrySet().stream()
                .filter(e -> e.getKey().startsWith(CODIGO.resolve(carpeta)))
                .flatMap(e -> e.getValue().stream()
                        .filter(i -> permitidos.stream().noneMatch(i::startsWith))
                        .map(i -> e.getKey().getFileName() + " → " + i))
                .sorted()
                .toList();
    }

    /** Clases (sin las internas ni package-info) de una carpeta del código compilado. */
    private static List<Class<?>> clasesDe(String carpeta) throws IOException {
        try (Stream<Path> archivos = Files.walk(CODIGO.resolve(carpeta))) {
            return archivos.filter(p -> p.toString().endsWith(".java") && !p.endsWith("package-info.java"))
                    .map(p -> BASE + "." + CODIGO.relativize(p).toString()
                            .replace(".java", "").replace('/', '.').replace('\\', '.'))
                    // El tipo explícito <Class<?>> hace que compile igual con Maven (javac) y con VS Code (Eclipse)
                    .<Class<?>>map(ArquitecturaTest::cargar)
                    .toList();
        }
    }

    private static Class<?> cargar(String nombre) {
        try {
            return Class.forName(nombre);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // D · Inversión de dependencias: las capas internas no conocen a las externas
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("D · El dominio es Java puro: no depende de Spring, JPA, la web ni de otras capas")
    void dominioSinDependencias() {
        assertThat(importsNoPermitidos("domain", List.of("java.", BASE + ".domain.")))
                .as("El dominio solo puede usar Java y el propio dominio")
                .isEmpty();
    }

    @Test
    @DisplayName("D · La aplicación solo conoce el dominio y sus puertos, nunca la infraestructura")
    void aplicacionNoConoceInfraestructura() {
        // Excepciones conscientes y documentadas: @Transactional (marca dónde empieza y termina una transacción)
        // y SLF4J (fachada de logs, no una implementación). Ver docs/principios-solid.md
        assertThat(importsNoPermitidos("application", List.of("java.", BASE + ".domain.", BASE + ".application.",
                "org.springframework.transaction.annotation.", "org.slf4j.")))
                .as("La capa de aplicación no puede importar infraestructura, JPA, web ni otros frameworks")
                .isEmpty();
    }

    @Test
    @DisplayName("D · Los controladores dependen de los puertos de entrada, no de los servicios concretos")
    void controladoresUsanPuertosDeEntrada() {
        assertThat(importsNoPermitidos("infrastructure/adapter/in", List.of("java.", "jakarta.validation.",
                "org.springframework.", "io.swagger.", "org.slf4j.", "com.fasterxml.jackson.annotation.",
                BASE + ".domain.", BASE + ".application.port.in.", BASE + ".infrastructure.adapter.in.",
                BASE + ".infrastructure.security.", BASE + ".infrastructure.config.")))
                .as("Un controlador no puede usar application.service ni los adaptadores de salida")
                .isEmpty();
    }

    // ---------------------------------------------------------------------------------------------
    // I · Segregación de interfaces y O/L · Abierto-cerrado y sustitución
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("I · Todos los puertos son interfaces pequeñas (máximo 10 operaciones)")
    void puertosSonInterfacesPequenas() throws IOException {
        List<Class<?>> puertos = Stream.concat(clasesDe("application/port/in").stream(),
                clasesDe("application/port/out").stream()).toList();

        assertThat(puertos).hasSizeGreaterThan(20).allSatisfy(p -> {
            assertThat(p.isInterface()).as("%s debe ser una interfaz", p.getSimpleName()).isTrue();
            long operaciones = Arrays.stream(p.getDeclaredMethods())
                    .filter(m -> Modifier.isAbstract(m.getModifiers())).count();
            assertThat(operaciones).as("%s tiene demasiadas operaciones", p.getSimpleName()).isBetween(1L, 10L);
        });
    }

    @Test
    @DisplayName("S/D · Cada servicio de la aplicación implementa un caso de uso (puerto de entrada)")
    void serviciosImplementanCasosDeUso() throws IOException {
        List<Class<?>> servicios = clasesDe("application/service").stream()
                .filter(c -> c.getSimpleName().endsWith("Service")).toList();

        assertThat(servicios).hasSizeGreaterThan(10).allSatisfy(s -> assertThat(s.getInterfaces())
                .as("%s debe implementar un *UseCase de application.port.in", s.getSimpleName())
                .anyMatch(i -> i.getPackageName().equals(BASE + ".application.port.in")));
    }

    @Test
    @DisplayName("O/L · Cada adaptador implementa un puerto de salida, así se puede cambiar por otro sin tocar la aplicación")
    void adaptadoresImplementanPuertosDeSalida() {
        List<Class<?>> adaptadores = Stream.of("infrastructure/adapter/out", "infrastructure/security")
                .<Class<?>>flatMap(c -> {
                    try {
                        return clasesDe(c).stream();
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                })
                .filter(c -> c.getSimpleName().endsWith("Adapter"))
                .toList();

        assertThat(adaptadores).hasSizeGreaterThan(10).allSatisfy(a -> assertThat(a.getInterfaces())
                .as("%s debe implementar un *Port de application.port.out", a.getSimpleName())
                .anyMatch(i -> i.getPackageName().equals(BASE + ".application.port.out")));
    }

    @Test
    @DisplayName("O · El correo tiene dos implementaciones intercambiables del mismo puerto (consola y Gmail)")
    void correoIntercambiable() throws IOException {
        assertThat(clasesDe("infrastructure/adapter/out/mail").stream()
                .filter(c -> !c.isInterface() && EnviarCorreoPort.class.isAssignableFrom(c))
                .map(Class::getSimpleName))
                .containsExactlyInAnyOrder("CorreoConsolaAdapter", "GmailCorreoAdapter");
    }
}
