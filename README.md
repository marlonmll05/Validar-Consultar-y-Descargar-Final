# Validar-Consultar-y-Descargar-Final

# Validar, Consultar o Generar Facturas y Documentos de Soporte

Aplicación diseñada exclusivamente para los centros de salud del departamento del Atlántico, Colombia.

---

## Funcionamiento General

La aplicación cuenta con dos opciones principales, accesibles desde la página de inicio:

## 1. Consulta y Descarga de Facturas
En esta sección, el usuario puede:

-Consultar todas las facturas registradas según diferentes filtros.

-Descargar las facturas de forma individual o masiva.

-Visualizar detalles relevantes como:

-Nombre del paciente.

-Usuario que generó la factura.

-CUV devuelto por la DIAN.

-Añadir CUV manualmente en caso de que la factura haya sido validada localmente.

-Acceso directo al documento de soporte de cada factura.

Además, en el apartado del documento de soporte, el usuario puede seleccionar qué tipo de historia clínica desea descargar, según el caso:
Epicrisis, resumen de atención, procedimientos de apoyo diagnóstico, entre otros.


## 2. Validación de Facturas con la DIAN
Esta sección, permite validar las facturas electrónicas usando una API Docker provista por SISPRO. Para poder conectarse es necesario contar con un usuario y contraseña válidos.

Una vez autenticado, el usuario puede aplicar filtros para listar facturas pendientes de validación. A partir de allí:

-Las facturas se pueden validar de forma individual o por grupos.

-Si una factura presenta errores, la interfaz mostrará los detalles automáticamente.

-A través del botón “Ver JSON”, el usuario puede:

-Visualizar el contenido de la factura.

-Editar directamente los campos con errores.

-Guardar los cambios y volver a validar.

-Si la factura es válida, el sistema devuelve el CUV correspondiente, que se almacena automáticamente en la base de datos.

---

## Requisitos Tecnicos

Para utilizar el módulo de validación es necesario contar con un equipo con Windows 10 versión 19045 o superior.

Importante: Algunos hospitales que no cuentan con un servidor actualizado han optado por usar equipos alternos exclusivamente para la instalación y ejecución de la API Docker.

---

## Acceso a la Aplicación

Para conectarse, es necesario cumplir con los siguientes requisitos:

- Estar conectado a la **misma red Wi-Fi** del servidor.
- Haber instalado el **certificado SSL** correspondiente.
- Acceder mediante el protocolo **HTTPS** en el siguiente formato:

https://[nombre-del-dominio]:9876/inicio.html

---

## Tiempo de Desarrollo

**5 meses aproximadamente.**

---

## Entidades Donde Está en Funcionamiento

Esta aplicación ha sido instalada y se encuentra actualmente en funcionamiento en:

- Prosalud S.A.S (Sabanalarga)
- Hospital de Santo Tomás
- Hospital de Galapa
- Clínica Santa Ana de Baranoa
- Hospital Materno Infantil de Soledad
- Hospital de Juan de Acosta
- Hospital de Manatí
- Hospital de Arenal
- Hospital de Usiacurí
- Hospital de Ponedera
- ESE Unidad Local Salud de Suan
- Hospital de Luruaco
- Hospital de Sabanagrande
- Hospital de Juan de Acosta
- Hospital de Palmar de Varela
- Hospital de Polonuevo
- E.S.E Unidad Local de Suan
  
---

## Autor

**Creado y diseñado por:** Marlon Morales Llanos  
**Empresa:** GIHOS S.A.S


## Contacto

Para mayor informacion escribirme al correo:
marlonfadim@hotmail.com

