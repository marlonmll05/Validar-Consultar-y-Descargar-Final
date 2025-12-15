const LOGIN_URL = `https://${location.hostname}:9876/api/sql/login`;

/**
 * Send user and password to login on SQL and set a token  
 * 
 * @param {string} username SQL User
 * @param {string} password SQL Password
 */

document.getElementById('login-form').addEventListener('submit', async (ev) => {
    ev.preventDefault();

    const username = document.getElementById('identificacion').value.trim();
    const password = document.getElementById('password').value;

    const res = await fetch(LOGIN_URL, {
        method: 'POST',
        headers: {'Content-Type':'application/json'},
        body: JSON.stringify({ username, password })
    });

    const errorDiv = document.getElementById('error-message');

    if (!res.ok) {
        const errorText = await res.text();
        errorDiv.textContent = errorText.replace("Error de base de datos:", "");
        return;
    }

    const data = await res.json();
    if (data.token) {

        console.log('Tokens guardados en loginsql.html:');    
        localStorage.setItem('tokenSQL', data.token);
        localStorage.setItem('servidor', data.servidor);
        localStorage.setItem('usuario', data.usuario);

        window.location.href = 'inicio.html';
    } else {
        errorDiv.textContent = 'Respuesta inválida del servidor.';
    }
});
