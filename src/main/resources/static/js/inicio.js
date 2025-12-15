if (!localStorage.getItem('tokenSQL')) {
    window.location.href = 'loginsql.html';
}

lucide.createIcons();


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


document.addEventListener('click', function(e) {
    if (!e.target.closest('.dropdown')) {
    document.querySelector('.dropdown').classList.remove('active');
    }
});

document.querySelectorAll('.dropdown-item').forEach(item => {
    item.addEventListener('click', function() {
    document.querySelector('.dropdown').classList.remove('active');
    });
});

window.addEventListener("DOMContentLoaded", async () => {
  try {
    const response = await fetch("/api/sql/validar-parametro-soporte");

    if (!response.ok) {
      console.log("ocurrio un error", await response.text());
      return;
    }

    const resultado = await response.text();

    if (resultado !== "1") {
      console.log("Acceso denegado al modulo de soporte, respuesta:", resultado);

      const atencionesLink = document.querySelector("#menuAtenciones");
      if (atencionesLink) {
        const dropdownAtenciones = atencionesLink.closest(".dropdown");
        if (dropdownAtenciones) {
          dropdownAtenciones.classList.add("dropdown-disabled");
        }
      }
    } else {
      console.log("Acceso otorgado a atenciones");
    }
  } catch (error) {
    console.error("Error al hacer la peticion para Documentos Soporte", error);
  }

  try {
    const responseCuenta = await fetch("/api/sql/validar-parametro-cuenta");

    if (!responseCuenta.ok) {
      console.log("ocurrio un error", await responseCuenta.text());
      return;
    }

    const resultadoCuenta = await responseCuenta.text();

    if (resultadoCuenta !== "1") {
      console.log("Acceso denegado al modulo de cuenta cobro, respuesta:", resultadoCuenta);

      const cuentaCobroLink = document.querySelector("#menuCuentaCobro");
      if (cuentaCobroLink) {
        const dropdownCuentaCobro = cuentaCobroLink.closest(".dropdown");
        if (dropdownCuentaCobro) {
          dropdownCuentaCobro.classList.add("dropdown-disabled");
        }
      }
    } else {
      console.log("Acceso otorgado a cuenta cobro");
    }
  } catch (error) {
    console.error("Error al hacer la peticion para Cuenta Cobro", error);
  }

});



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

const atencionesButton = document.getElementById("cardAtenciones");


window.addEventListener('DOMContentLoaded', function() {
    const token = sessionStorage.getItem('token');
    if (!token) {
        console.log('Usuario no autenticado para la api docker');

    }
});

// Mostrar nombre de usuario al cargar la página
window.addEventListener('DOMContentLoaded', function() {
    const usuario = localStorage.getItem('usuario');
    if (usuario) {
        document.getElementById('username').textContent = usuario;
    }
});

function cerrarSesion() {
    if (confirm('¿Está seguro que desea cerrar sesión?')) {
        sessionStorage.clear();
        localStorage.clear();
        
        window.location.href = 'loginsql.html';
    }
}
