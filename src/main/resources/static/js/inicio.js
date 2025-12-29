/**
 * Verifica si existe el token SQL en localStorage.
 * Si no existe, redirige al login SQL.
 */
if (!localStorage.getItem('tokenSQL')) {
    window.location.href = 'loginsql.html';
}

lucide.createIcons();

/**
 * Maneja la apertura y cierre de los dropdowns del menú.
 * Solo permite un dropdown activo a la vez.
 */
document.querySelectorAll('.dropdown-toggle').forEach((btn) => {
  btn.addEventListener('click', () => {

    const currentDropdown = btn.closest('.dropdown');

    document.querySelectorAll('.dropdown').forEach((drop) => {
      if (drop !== currentDropdown) {
        drop.classList.remove('active');
      }
    });

    currentDropdown.classList.toggle('active');
  });
});

/**
 * Cierra cualquier dropdown activo al hacer clic
 * fuera del componente dropdown.
 *
 * @param {MouseEvent} e - Evento de clic del documento
 */
document.addEventListener('click', function (e) {
    if (!e.target.closest('.dropdown')) {
        const dropdown = document.querySelector('.dropdown');
        if (dropdown) dropdown.classList.remove('active');
    }
});

/**
 * Cierra el dropdown al seleccionar una opción del menú.
 */
document.querySelectorAll('.dropdown-item').forEach(item => {
    item.addEventListener('click', function () {
        const dropdown = document.querySelector('.dropdown');
        if (dropdown) dropdown.classList.remove('active');
    });
});

/**
 * Al cargar la página, valida los permisos del usuario
 * para acceder a los módulos de Soporte y Cuenta Cobro.
 */
window.addEventListener("DOMContentLoaded", async () => {

  /* ===== VALIDAR ACCESO A SOPORTE ===== */
  try {
    const response = await fetch("/api/sql/validar-parametro-soporte");

    if (!response.ok) {
      console.log("Ocurrió un error", await response.text());
      return;
    }

    const resultado = await response.text();

    if (resultado !== "1") {
      const atencionesLink = document.querySelector("#menuAtenciones");
      if (atencionesLink) {
        const dropdownAtenciones = atencionesLink.closest(".dropdown");
        if (dropdownAtenciones) {
          dropdownAtenciones.classList.add("dropdown-disabled");
        }
      }
    }
  } catch (error) {
    console.error("Error al validar acceso a Soporte", error);
  }

  /* ===== VALIDAR ACCESO A CUENTA COBRO ===== */
  try {
    const responseCuenta = await fetch("/api/sql/validar-parametro-cuenta");

    if (!responseCuenta.ok) {
      console.log("Ocurrió un error", await responseCuenta.text());
      return;
    }

    const resultadoCuenta = await responseCuenta.text();

    if (resultadoCuenta !== "1") {
      const cuentaCobroLink = document.querySelector("#menuCuentaCobro");
      if (cuentaCobroLink) {
        const dropdownCuentaCobro = cuentaCobroLink.closest(".dropdown");
        if (dropdownCuentaCobro) {
          dropdownCuentaCobro.classList.add("dropdown-disabled");
        }
      }
    }
  } catch (error) {
    console.error("Error al validar acceso a Cuenta Cobro", error);
  }
});

/**
 * Redirige al validador si existe una sesión activa,
 * de lo contrario redirige al login.
 *
 * @param {Event} event - Evento de clic
 */
function irAlValidador(event) {
    event.preventDefault(); 
    
    const token = sessionStorage.getItem('token');
    const pestanaActiva = sessionStorage.getItem('pestanaActiva');
    
    if (token && pestanaActiva) {
        window.location.href = 'validador.html';
    } else {
        window.location.href = 'login.html'; 
    }
}

/**
 * Referencia al botón de atenciones (card).
 * @type {HTMLElement|null}
 */
const atencionesButton = document.getElementById("cardAtenciones");

/**
 * Verifica si existe un token de sesión
 * para el consumo de la API Docker.
 */
window.addEventListener('DOMContentLoaded', function () {
    const token = sessionStorage.getItem('token');
    if (!token) {
        console.log('Usuario no autenticado para la API Docker');
    }
});

/**
 * Muestra el nombre del usuario en la interfaz
 * si existe en localStorage.
 */
window.addEventListener('DOMContentLoaded', function () {
    const usuario = localStorage.getItem('usuario');
    if (usuario) {
        document.getElementById('username').textContent = usuario;
    }
});

/**
 * Cierra la sesión del usuario limpiando
 * localStorage y sessionStorage.
 */
function cerrarSesion() {
    if (confirm('¿Está seguro que desea cerrar sesión?')) {
        sessionStorage.clear();
        localStorage.clear();
        window.location.href = 'loginsql.html';
    }
}
