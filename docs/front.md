# 0. Filosofía de estilos

**CSS exclusivamente estructural.** El frontend no define estética: no hay colores, fuentes, gradientes,
sombras, animaciones ni transiciones. Todo el CSS se limita a propiedades de layout y espaciado para que los
componentes sean funcionales y navegables.

**Propiedades permitidas:** `display`, `flex`, `grid`, `gap`, `padding`, `margin`, `width`, `height`,
`overflow`, `border` (solo como separador funcional, Ej. tablas e inputs), `list-style`, `text-align`,
`cursor`, `white-space`, `position`, `top`, `left`, `right`, `bottom`, `z-index`.

**Propiedades prohibidas:** `color`, `font-family`, `font-size`, `font-weight`, `background`,
`background-color`, `border-radius`, `box-shadow`, `text-shadow`, `transition`, `animation`,
`transform`, `opacity`, `filter`, `gradient`, `text-decoration` (salvo `underline` funcional en links).

**Excepción:** `font-weight: bold` se permite exclusivamente para marcar el link activo de navegación.

El sitio debe ser usable con estilos mínimos. Un usuario puede envolver todo en un framework CSS o escribir
CSS custom sin reestructurar componentes ni clases.

# 1. Stack detallado de tecnologías y dependencias

## 1.1. Lenguaje y framework

| Capa      | Tecnología              | Versión | Para                                                    |
|-----------|-------------------------|---------|---------------------------------------------------------|


## 1.2. Dependencias a instalar

| Dependencia | Motivo |
|-------------|--------|

# 2. Estilos

**Ver punto 0**

# 3. Subida de DB

## 3.1. ...

**Pasos:**

## 3.2. ...

**Pasos:**

## 3.3. ...

**Pasos:**

# 4. Descarga de DB

# 5. Páginas / Views

## 5.1. Carga de archivo / Inicio (``)

**Ruta:** No tiene ruta propia. Se muestra condicionalmente en `#/` cuando no hay DB cargada.

**Propósito:**

**Query parameters(?**

## 5.2. Lista de sources (``)

**Ruta:** ``

**Propósito:**

**Query parameters(?**

## 5.3. Lista de autores (``)

**Ruta:** ``

**Propósito:**

**Query parameters(?**

| Param        | Tipo     |
|--------------|----------|

## 5.4. ... (``)

**Ruta:** ``

**Propósito:**

**Query parameters(?**

| Param        | Tipo     |
|--------------|----------|

## 5.5. ... (``)

**Ruta:** ``

**Propósito:**

**Query parameters(?**

| Param        | Tipo     |
|--------------|----------|

# 6. Paginación

# 7. Transaccionalidad
