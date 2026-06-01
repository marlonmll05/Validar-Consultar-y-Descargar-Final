# ASCLEPIUS WEB

Sistema web integral para la validación, generación y gestión de facturas y documentos de soporte de cobro en instituciones de salud del departamento del Atlántico, Colombia.

Actualmente en producción en 20 centros de salud con más de 50 usuarios activos.

## Características Principales

### Gestión de Facturas
- Consulta avanzada de facturas con múltiples filtros
- Descarga individual o masiva (JSON, XML, CUV)
- Visualización de detalles: paciente, usuario generador, CUV
- Registro manual de CUV para facturas validadas localmente
- Generación de reportes de éxito y errores
- Acceso directo a documentos de soporte

### Validación con DIAN
- Integración con API Docker SISPRO para validación en tiempo real
- Validación individual o por lotes
- Detección automática de errores con feedback inmediato
- Editor integrado de JSON para corrección de errores
- Almacenamiento automático de CUV en base de datos
- Descarga completa de documentos validados

### Gestión de Documentos de Soporte
- Consulta con filtros avanzados (atención, EPS, contrato, fechas, etc.)
- Generación automática según tipo de atención (epicrisis, resumen, procedimientos)
- Anexado de documentos adicionales por registro de atención
- Exportación individual, masiva o por cuenta de cobro
- Visualización y eliminación de documentos asociados
- Validación de cuentas de cobro con reporte de errores
- Descarga selectiva (solo soportes o paquete completo)

## Stack Tecnológico

**Backend:**
- Java 21
- Spring Boot 3.4.3
- SQL Server 2008+
- REST APIs
- Maven

**Frontend:**
- JavaScript (ES6+)
- HTML5
- CSS3

**DevOps & Integración:**
- Docker (API SISPRO)
- HTTPS/SSL
- Git

**Seguridad:**
- Conexión HTTPS con certificado SSL
- Autenticación mediante credenciales SQL con token secreto
- Puerto seguro: 9876

## Impacto

- 20 centros de salud activos
- 50+ usuarios diarios
- 80% de reducción en tiempos de validación
- 20+ tipos de documentos gestionados
- 17 meses de desarrollo

## Instituciones en Producción

1. Prosalud S.A.S (Sabanalarga)
2. Hospital de Santo Tomás
3. Hospital de Galapa
4. Clínica Santa Ana de Baranoa
5. Hospital Materno Infantil de Soledad
6. Hospital de Juan de Acosta
7. Hospital de Manatí
8. ESE Ana Maria Rodriguez, Fundación
9. Hospital de Usiacurí
10. Hospital de Ponedera
11. ESE Unidad Local Salud de Suan
12. Hospital de Luruaco
13. Hospital de Sabanagrande
14. Hospital de Palmar de Varela
15. Hospital de Polonuevo
16. Sanidad IPS
17. Hospital Campo de la Cruz
18. Hospital Juan de Acosta
19. Hospital de Tubara
20. Hospital de Puerto Colombia

## Módulos Principales

**Módulo de Facturación:**
- Consulta con filtros dinámicos
- Generación de JSON estructurado
- Descarga individual y masiva
- Gestión de CUV

**Módulo de Validación DIAN:**
- Conexión con API Docker SISPRO
- Validación en tiempo real
- Procesamiento por lotes
- Manejo de errores y reintentos

**Módulo de Documentos de Soporte:**
- Generación automática de 20+ tipos de PDF
- Clasificación por tipificación
- Exportación flexible
- Control de versiones

## Requisitos del Sistema

**Servidor:**
- SO: Windows Server 2016+ o Linux (Ubuntu 20.04+)
- Java: JDK 21+
- Base de datos: SQL Server 2008 o superior
- RAM: Mínimo 4GB (recomendado 8GB)
- Almacenamiento: 8GB disponibles

**Cliente (Validación SISPRO):**
- SO: Windows 10 versión 19045 o superior
- Docker Desktop instalado y configurado
- Conexión a red local del servidor
- Certificado SSL instalado 

**Red:**
- Acceso: Red local (LAN/Wi-Fi)
- Puerto: 9876 (HTTPS)
- Protocolo: HTTPS con certificado válido

### 1. Despliegue

```bash
# Compilar proyecto
mvn clean package

# Ejecutar aplicación
java -jar gestion-hospitalaria.jar
```

### 2. Acceso

```
URL: https://[nombre-del-dominio]:9876/inicio.html
```

## Funcionalidades Destacadas

**Automatización de Procesos:**
- Validación masiva: Procesamiento por lotes de hasta 100 facturas simultáneas
- Generación automática: Creación de documentos según tipo de atención
- Reportes automáticos: Generación de informes de éxito y errores

**Cumplimiento Normativo:**
- Alineado con resoluciones de la DIAN
- Compatible con estándares SISPRO
- Validación de RIPS según Ministerio de Salud
- Facturación electrónica según normativa vigente

**Trazabilidad:**
- Registro de todas las operaciones por usuario
- Historial completo de modificaciones
- Seguimiento de errores y resoluciones

## Seguridad

- **Cifrado:** Comunicación HTTPS con certificado SSL/TLS
- **Autenticación:** Sistema de tokens secretos
- **Autorización:** Control de acceso basado en roles
- **Auditoría:** Registro de todas las acciones críticas
- **Backup:** Respaldos automáticos diarios

## Integraciones

**APIs Gubernamentales:**
- SISPRO: Sistema Integral de Información de la Protección Social
- DIAN: Dirección de Impuestos y Aduanas Nacionales

**Formatos Soportados:**
- JSON: Factura electrónica estructurada
- XML: Documento fiscal estándar
- PDF: Documentos de soporte múltiples
- CUV: Código Único de Validación

## Soporte y Mantenimiento

**Servicios Incluidos:**
- Actualizaciones periódicas: Mejoras y correcciones
- Soporte técnico: 6 días a la semana
- Tiempo de respuesta: Atención prioritaria a incidencias críticas
- Capacitación inicial: Incluida en la implementación
- Mantenimiento preventivo: Monitoreo constante

**Contacto:**
- Email: marlonfadim@hotmail.com
- Horario: Lunes a Sábado, 8:00 AM - 6:00 PM
- Emergencias críticas: Respuesta en menos de 30 minutos

## Autor

**Desarrollado por:** Marlon Morales Llanos  
**Empresa:** GESTION INTEGRAL HOSPITALARIA S.A.S  
**LinkedIn:** [linkedin.com/in/marlon-moralesll](https://linkedin.com/in/marlon-moralesll)  
**GitHub:** [github.com/marlonmll05](https://github.com/marlonmll05)
