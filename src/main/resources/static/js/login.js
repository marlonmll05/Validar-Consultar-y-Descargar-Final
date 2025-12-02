
if (!localStorage.getItem('tokenSQL')) {
    window.location.href = 'loginsql.html';
}

document.addEventListener('DOMContentLoaded', function() {
    document.getElementById('login-form').addEventListener('submit', async function(event) {
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

        try{
            const response = await fetch(`https://${window.location.hostname}:9876/api/validador/login`, {
                method: 'POST',
                headers: {
                'Content-Type': 'application/json'
                },
                body: JSON.stringify(requestBody)
            });

            if (!response.ok) {

                const errorData = await response.text();

                console.error('Error HTTP:', response.status, errorData);
                errorDiv.textContent = `Error ${response.status}: ${errorData.error || errorData.message || errorData}`;
                return
            }

            const data = await response.json();

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
        }
        catch(error) {
            console.error('Error en la peticion', error);
            errorDiv.textContent = 'Error inesperado. Intenta de nuevo.';
        }     
    })
});

