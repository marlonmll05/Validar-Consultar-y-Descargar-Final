
if (!sessionStorage.getItem('tokenSQL')) {
    window.location.href = 'loginsql.html';
}

document.addEventListener('DOMContentLoaded', function() {
    document.getElementById('login-form').addEventListener('submit', function(event) {
    event.preventDefault();

    const documentType = document.getElementById('document-type').value;
    const username = document.getElementById('identificacion').value;
    const password = document.getElementById('password').value;
    const nit = document.getElementById('nit').value;

    const errorDiv = document.getElementById('error-message');

    if (nit.length < 5) {
        errorDiv.textContent = 'El NIT debe tener al menos 5 caracteres.'; 
        return; 
    } else {
        errorDiv.textContent = ''; 
    }

    const tipoDocumento = {
        'cedula-ciudadania': 'CC',
        'cedula-extranjeria': 'CE',
        'pasaporte': 'PS',
        'documento-extranjero': 'DE'
    }[documentType];

    const requestBody = {
        persona: {
        identificacion: {
            tipo: tipoDocumento,
            numero: username
        }
        },
        clave: password,
        nit: nit,
        tipoMecanismoValidacion: 0,
        reps: true
    };

    console.log('Enviando petición de login:', requestBody);

    fetch(`https://${window.location.hostname}:9876/api/validador/login`, {
        method: 'POST',
        headers: {
        'Content-Type': 'application/json'
        },
        body: JSON.stringify(requestBody)
    })
    .then(response => {
        
        if (response.status === 500) {
        return response.json()
            .then(errorData => {
            console.error('Error 500', errorData);
            errorDiv.textContent = errorData.error || 'Error al conectar con el servidor';
            throw new Error('Internal Server Error');
            })
            .catch(jsonError => {
            console.error('Error parseando JSON del 500:', jsonError);
            errorDiv.textContent = 'Error al conectar con el servidor';
            throw new Error('Internal Server Error');
            });
        }

        if (!response.ok) {
        return response.json()
            .then(errorData => {
            console.error('Error HTTP:', response.status, errorData);
            errorDiv.textContent = errorData.error || errorData.message || 'Error en el servidor';
            throw new Error(`HTTP ${response.status}`);
            })
            .catch(jsonError => {
            console.error('Error parseando JSON:', jsonError);
            errorDiv.textContent = 'Error en el servidor';
            throw new Error(`HTTP ${response.status}`);
            });
        }
                
        return response.json();
    })

    .then(data => {
        if (data) {
        console.log('Envio Exitoso:', data);
        
        if (data.token) {
            sessionStorage.setItem('token', data.token);
            sessionStorage.setItem('pestanaActiva', 'true');
            window.location.href = 'validador.html';
        } else if (data.errors && Array.isArray(data.errors) && data.errors.length > 0) {
            console.warn('Errores de validación:', data.errors);
            errorDiv.innerHTML = data.errors.map(error => `<p>${error}</p>`).join('');
        } else {
            errorDiv.textContent = ''; 
        }
        }
    })
    .catch(error => {
        console.error(error);
    })
    });
});
