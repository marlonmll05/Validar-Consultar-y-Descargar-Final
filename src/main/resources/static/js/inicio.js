if (!sessionStorage.getItem('tokenSQL')) {
    window.location.href = 'loginsql.html';
}

lucide.createIcons();

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

window.addEventListener('DOMContentLoaded', function() {
    const token = sessionStorage.getItem('token');
    if (!token) {
        console.log('Usuario no autenticado');

    }
});

// Mostrar nombre de usuario al cargar la página
window.addEventListener('DOMContentLoaded', function() {
    const usuario = sessionStorage.getItem('usuario');
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
