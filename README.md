# Validar, Consultar o Generar Facturas y Documentos de Soporte

Aplicación diseñada exclusivamente para los centros de salud del departamento del Atlántico, Colombia.

---

## Funcionamiento General

La aplicación cuenta con dos opciones principales, accesibles desde la página de inicio:

## 1. Consulta y Descarga de Facturas
En esta sección, el usuario puede:

-Consultar todas las facturas registradas según diferentes filtros.

-Descargar las facturas de forma individual o masiva.

-Las facturas descargadas incluyen JSON, XML si se selecciona la opcion y CUV si la factura ya ha sido validada.

-Visualizar detalles relevantes como:

-Nombre del paciente.

-Usuario que generó la factura.

-CUV devuelto por la DIAN.

-Añadir CUV manualmente en caso de que la factura haya sido validada localmente.

-Descargar un reporte de las facturas descargadas exitosamente y las que presentan errores

-Acceso directo al documento de soporte de cada factura.

Además, en el apartado del documento de soporte, el usuario puede seleccionar qué tipo de historia clínica desea descargar, según el caso:
Epicrisis, resumen de atención, procedimientos de apoyo diagnóstico, entre otros.


## 2. Validación de Facturas con la DIAN
Esta sección, permite validar las facturas electrónicas utilizando una API Docker provista por SISPRO. Para poder conectarse es necesario contar con un usuario y contraseña válidos.

Una vez autenticado, el usuario puede aplicar filtros para listar facturas pendientes de validación. A partir de allí:

-Las facturas se pueden validar de forma individual o por grupos.

-Si una factura presenta errores, la interfaz mostrará los detalles automáticamente.

-A través del botón “Ver JSON”, el usuario puede:

-Visualizar el contenido de la factura.

-Editar directamente los campos con errores.

-Guardar los cambios y volver a validar.

-Si la factura es válida, el sistema devuelve el CUV correspondiente, que se almacena automáticamente en la base de datos.

-Las facturas pueden descargarse e incluyen JSON, XML y CUV.

---

## 3. Gestionar Documentos Soporte

En esta sección, el usuario puede:

- Consultar documentos de soporte asociados a las atenciones médicas según diferentes filtros.

- Filtrar por: registro de atención, historia clínica, EPS, contrato, área de atención, fechas, número de factura, cuenta de cobro y cantidad de soportes.

- Visualizar detalles relevantes como:
  - Estado de la atención
  - Paciente y contrato asociado
  - Número de factura generada
  - Cantidad de documentos de soporte

- Generar automáticamente documentos de soporte según el tipo de atención (epicrisis, resumen de atención, procedimientos, etc.).

- Anexar documentos adicionales a cada registro de atención segun su ID.

- Exportar documentos de forma individual o masiva.

- Exportar por cuenta de cobro para facilitar la gestión administrativa.

- Visualizar todos los documentos asociados a una atención específica y eliminarlos cuando sea necesario.

- Validar Cuenta de Cobro y ver todas las atenciones que presentan errores en formato de reporte.

- Seleccionar si descargar solo los soportes o incluir todo los archivos dentro del zip (JSON, XML, CUV y SOPORTES).


## Requisitos Tecnicos

Para utilizar el módulo de validación es necesario contar con un equipo con Windows 10 versión 19045 o superior.

Importante: Algunos hospitales que no cuentan con un servidor actualizado han optado por usar equipos alternos exclusivamente para la instalación y ejecución de la API Docker.

---

## Características Técnicas

- **Arquitectura:** Cliente-servidor con API REST
- **Base de datos:** [SQL SERVER 2008]
- **Tecnologías utilizadas:** 
  - Frontend: [HTML5, CSS3, JavaScript]
  - Backend: [Java Spring Boot]
  - Integración: API Docker SISPRO
- **Seguridad:** 
    - Conexión HTTPS con certificado SSL
    - Autenticación mediante credenciales SQL con token secreto

- **Puerto de acceso:** 9876


## Ventajas del Sistema

- **Automatización:** Reducción significativa de tiempo en la generación y validación de facturas electrónicas.
- **Validación en tiempo real:** Corrección inmediata de errores antes del envío oficial a la DIAN.
- **Trazabilidad completa:** Registro detallado de todas las operaciones realizadas por cada usuario.
- **Gestión centralizada:** Control unificado de facturas y documentos de soporte desde una sola plataforma.
- **Cumplimiento normativo:** Alineado con las exigencias de la DIAN y SISPRO para el sector salud.
- **Exportación flexible:** Descarga individual o masiva según las necesidades del usuario.


## Acceso a la Aplicación

Para conectarse, es necesario cumplir con los siguientes requisitos:

- Estar conectado a la **misma red Wi-Fi** del servidor.
- Haber instalado el **certificado SSL** correspondiente.
- Acceder mediante el protocolo **HTTPS** en el siguiente formato:

https://[nombre-del-dominio]:9876/inicio.html

---

## Tiempo de Desarrollo

**11 meses aproximadamente.**

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
- ESE Ana Maria Rodriguez, Fundacion
- Hospital de Usiacurí
- Hospital de Ponedera
- ESE Unidad Local Salud de Suan
- Hospital de Luruaco
- Hospital de Sabanagrande
- Hospital de Juan de Acosta
- Hospital de Palmar de Varela
- Hospital de Polonuevo
- Sanidad IPS
- Hospital Campo de la Cruz
  
---

## Soporte y Mantenimiento

- **Actualizaciones:** El sistema recibe actualizaciones periódicas para mejoras y correcciones.
- **Soporte técnico:** Atención personalizada disponible 6 días a la semana a través del correo marlonfadim@hotmail.com
- **Tiempo de respuesta:** Atención prioritaria a incidencias críticas y soporte continuo durante el horario laboral.
- **Capacitación:** Se ofrece capacitación inicial al personal de cada institución durante la implementación.
- **Mantenimiento preventivo:** Monitoreo constante del sistema para garantizar su óptimo funcionamiento.

---

## Autor

**Creado y diseñado por:** Marlon Morales Llanos  
**Empresa:** GIHOS S.A.S


## Contacto

Para mayor informacion escribirme al correo:
**marlonfadim@hotmail.com**

