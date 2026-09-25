---
title: Spring Security básico
sidebar_position: 1
---

# Spring Security básico

En cuanto añades `spring-boot-starter-security` al `pom.xml`, tu aplicación cambia de comportamiento sin que escribas una sola línea de configuración: **todos los endpoints pasan a requerir autenticación**, y Spring Boot genera un usuario `user` con una contraseña aleatoria que imprime en los logs al arrancar. Es una demostración de "seguro por defecto" — el punto de partida es bloquearlo todo, y tú decides explícitamente qué abrir.

## `SecurityFilterChain`

En vez de la contraseña generada, definimos nuestra propia configuración:

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**").permitAll()
                        .anyRequest().authenticated());
        return http.build();
    }
}
```

- `csrf(AbstractHttpConfigurer::disable)` — la protección CSRF existe para clientes con sesión y cookies (un navegador). Nuestro API es *stateless* (sin sesión, cada petición lleva su propio JWT), así que CSRF no aplica.
- `sessionCreationPolicy(STATELESS)` — Spring Security no crea ni usa `HttpSession`. Cada petición se autentica desde cero a partir del token, no de un estado guardado en el servidor.
- `authorizeHttpRequests` — `/auth/**` (registro y login) es público; todo lo demás requiere estar autenticado.
- `@EnableMethodSecurity` — habilita `@PreAuthorize` sobre métodos de controller, usado más adelante en [Roles y autorización](./roles-y-autorizacion).

La clase real de `examples/03-security-jwt` añade dos piezas más a esta cadena, que se explican en las páginas siguientes: el filtro que valida el JWT de cada petición ([Autenticación con JWT](./autenticacion-jwt)) y las respuestas 401/403 en JSON ([Roles y autorización](./roles-y-autorizacion#respuestas-de-error-consistentes)).

## `PasswordEncoder`

Nunca se guarda una contraseña en texto plano. `BCryptPasswordEncoder` aplica un hash de un solo sentido (no se puede "deshacer") con una sal aleatoria incorporada, por lo que dos usuarios con la misma contraseña obtienen hashes distintos:

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

Se usa al registrar (`passwordEncoder.encode(...)`, ver [Autenticación con JWT](./autenticacion-jwt)) y, indirectamente, al hacer login: Spring Security compara la contraseña recibida contra el hash guardado con `passwordEncoder.matches(...)`, nunca comparando strings directamente.

## `UserDetailsService` respaldado por una entidad real

Spring Security necesita saber cómo cargar un usuario y sus roles. Se lo decimos implementando `UserDetailsService`:

```java
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("No existe el usuario " + username));
        return new UserPrincipal(user);
    }
}
```

`UserPrincipal` es una clase propia que implementa `UserDetails` envolviendo nuestra entidad `User` — así el resto de Spring Security (y nuestros propios controllers, vía `@AuthenticationPrincipal`) puede acceder tanto a lo que Spring Security necesita (`getUsername()`, `getPassword()`, `getAuthorities()`) como al `User` completo con su `id`, que hace falta para comprobar quién es el dueño de una tarea.

Con `UserDetailsServiceImpl` y `passwordEncoder()` como los únicos beans de ese tipo en el contexto, Spring Security ensambla automáticamente un `AuthenticationManager` capaz de validar credenciales contra la base de datos — sin que tengamos que conectarlos a mano.

Ese `AuthenticationManager` existe, pero Spring Security no lo publica como bean que se pueda inyectar. Como `AuthServiceImpl` lo necesita para el login, `SecurityConfig` lo expone con un `@Bean` de una línea:

```java
@Bean
public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
    return config.getAuthenticationManager();
}
```

`AuthenticationConfiguration` es donde Spring Security guarda el que ya montó: el `@Bean` solo lo hace visible, no crea uno nuevo.
