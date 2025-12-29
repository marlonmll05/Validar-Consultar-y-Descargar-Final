/**
 * Verifica si existe un token de sesión almacenado en localStorage.
 * Si no existe, redirige al usuario a la pantalla de login SQL.
 */
if (!localStorage.getItem('tokenSQL')) {
    window.location.href = 'loginsql.html';
}

/**
 * Espera a que el DOM esté completamente cargado
 * para asegurar que los elementos HTML estén disponibles.
 */
document.addEventListener('DOMContentLoaded', function () {

    /**
     * Maneja el envío del formulario de login.
     * Evita la recarga de la página y ejecuta
     * el proceso de autenticación contra el backend.
     *
     * @param {SubmitEvent} event - Evento submit del formulario
     */
    document.getElementById('login-form').addEventListener('submit', async function (event) {
        event.preventDefault();

        /**
         * Obtiene los valores ingresados por el usuario
         * desde los campos del formulario.
         */
        const documentType = document.getElementById('document-type').value;
        const username = document.getElementById('identificacion').value;
        const password = document.getElementById('password').value;
        const nit = document.getElementById('nit').value;

        /**
         * Referencia al contenedor donde se mostrarán
         * los mensajes de error al usuario.
         *
         * @type {HTMLElement}
         */
        const errorDiv = document.getElementById('error-message');

        /**
         * Validación básica del NIT.
         * Debe contener al menos 5 caracteres.
         */
        if (nit.length < 5) {
            errorDiv.textContent = 'El NIT debe tener al menos 5 caracteres.';
            return;
        } else {
            errorDiv.textContent = '';
        }

        /**
         * Mapeo del tipo de documento seleccionado
         * al formato requerido por el backend.
         *
         * @type {string}
         */
        const tipoDocumento = {
            'cedula-ciudadania': 'CC',
            'cedula-extranjeria': 'CE',
            'pasaporte': 'PS',
            'documento-extranjero': 'DE'
        }[documentType];

        /**
         * Construcción del cuerpo de la petición
         * para el servicio de autenticación.
         *
         * @type {Object}
         */
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

        try {
            /**
             * Envía la petición POST al servicio de login
             * del validador usando fetch.
             */
            const response = await fetch(
                `https://${window.location.hostname}:9876/api/validador/login`,
                {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify(requestBody)
                }
            );

            /**
             * Manejo de errores HTTP (status distinto de 2xx).
             */
            if (!response.ok) {
                const errorData = await response.text();
                console.error('Error HTTP:', response.status, errorData);
                errorDiv.textContent =
                    `Error ${response.status}: ${errorData.error || errorData.message || errorData}`;
                return;
            }

            /**
             * Conversión de la respuesta del backend a JSON.
             *
             * @type {Object}
             */
            const data = await response.json();

            if (data) {
                console.log('Envío exitoso:', data);

                /**
                 * Si el backend retorna un token,
                 * se guarda en sessionStorage y se redirige
                 * a la pantalla del validador.
                 */
                if (data.token) {
                    sessionStorage.setItem('token', data.token);
                    sessionStorage.setItem('pestanaActiva', 'true');
                    window.location.href = 'validador.html';

                /**
                 * Si existen errores de validación,
                 * se muestran en pantalla.
                 */
                } else if (Array.isArray(data.errors) && data.errors.length > 0) {
                    console.warn('Errores de validación:', data.errors);
                    errorDiv.innerHTML = data.errors
                        .map(error => `<p>${error}</p>`)
                        .join('');
                } else {
                    errorDiv.textContent = '';
                }
            }
        } catch (error) {
            /**
             * Manejo de errores inesperados durante la petición.
             */
            console.error('Error en la petición', error);
            errorDiv.textContent = 'Error inesperado. Intenta de nuevo.';
        }
    });
});
