/**
 * Verifica si existe un token SQL almacenado en localStorage.
 * Si no existe, redirige al usuario a la pantalla de login.
 */
if (!localStorage.getItem('tokenSQL')) {
    window.location.href = 'loginsql.html';
}

/**
 * Valida el acceso del usuario al módulo de Cuenta Cobro
 * consultando el parámetro correspondiente en el backend.
 * Si el acceso es denegado, redirige a la pantalla de inicio.
 */
window.addEventListener("DOMContentLoaded", async () => {
    try {
        const response = await fetch("/api/sql/validar-parametro-cuenta");

        if (!response.ok) {
            const errorText = await response.text();
            console.log("Ocurrió un error:", errorText);
            return;
        }

        const resultado = await response.text();

        if (resultado !== "1") {
            console.log("Acceso denegado. Redirigiendo...");
            window.location.href = 'inicio.html';
        }
    } catch (error) {
        console.log("Error al hacer la petición:", error);
    }
});

/**
 * Host actual utilizado para llamadas a servicios.
 * @type {string}
 */
const host = window.location.hostname;

/**
 * Inicializa las fechas del formulario colocando
 * la fecha actual en los campos Fecha Desde y Fecha Hasta.
 */
document.addEventListener('DOMContentLoaded', () => {
    const today = new Date().toISOString().split('T')[0];
    document.getElementById('fechaDesde').value = today;
    document.getElementById('fechaHasta').value = today;
});

/**
 * Muestra un mensaje tipo toast en pantalla.
 *
 * @param {string} title - Título del mensaje
 * @param {string} message - Contenido del mensaje
 * @param {string} [type='success'] - Tipo de toast (success, error, info)
 * @param {number} [duration=6000] - Duración del toast en milisegundos
 * @param {boolean} [showProgress=false] - Indica si muestra barra de progreso
 * @returns {HTMLElement} Toast generado
 */
function showToast(title, message, type = 'success', duration = 6000, showProgress = false) {
    const container = document.getElementById('toastContainer');
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;

    toast.innerHTML = `
        <div class="toast-content">
            <strong>${title}</strong>
            <button class="close-toast" onclick="this.parentElement.parentElement.remove()">✖</button>
            <p>${message}</p>
            ${showProgress ? `
            <div class="toast-progress-container">
                <div class="toast-progress-bar" style="width: 0%;"></div>
            </div>` : ''}
        </div>
    `;

    container.appendChild(toast);

    if (!showProgress) {
        setTimeout(() => {
            if (toast.parentElement) {
                toast.classList.add('fadeOut');
                setTimeout(() => container.removeChild(toast), 300);
            }
        }, duration);
    }

    return toast;
}

/**
 * Actualiza el progreso visual de un toast con barra.
 *
 * @param {HTMLElement} toast - Elemento toast
 * @param {number} porcentaje - Porcentaje de avance (0 a 100)
 */
function actualizarToastProgreso(toast, porcentaje) {
    const progressBar = toast.querySelector('.toast-progress-bar');
    if (progressBar) {
        progressBar.style.width = `${porcentaje}%`;
    }
}

/**
 * Maneja el envío del formulario de filtros de facturas.
 * Realiza validaciones, consulta el backend y renderiza
 * los resultados en una tabla dinámica.
 *
 * @param {SubmitEvent} e - Evento submit del formulario
 */
document.getElementById('facturasForm').addEventListener('submit', async function (e) {
    e.preventDefault();

    const submitBtn = e.submitter || document.querySelector('#facturasForm button[type="submit"]');
    const originalHTML = submitBtn ? submitBtn.innerHTML : '';

    if (submitBtn) {
        submitBtn.disabled = true;
        submitBtn.innerHTML = 'Buscando...';
        submitBtn.style.opacity = '0.6';
        submitBtn.style.cursor = 'not-allowed';
    }

    try {
        const fechaDesde = document.getElementById('fechaDesde').value;
        const fechaHasta = document.getElementById('fechaHasta').value;

        if (!fechaDesde || !fechaHasta) {
            showToast('Error', 'Las fechas son obligatorias.', 'error');
            return;
        }

        if (new Date(fechaDesde) > new Date(fechaHasta)) {
            showToast('Error', 'La Fecha Desde no puede ser mayor que la Fecha Hasta.', 'error');
            return;
        }

        const formData = new FormData(e.target);
        const params = new URLSearchParams();

        for (const [key, value] of formData.entries()) {
            if (value.trim()) params.append(key, value);
        }

        showToast('Procesando', 'Buscando facturas...', 'success', 2000);

        const response = await fetch(`/filtros/cuenta-cobro?${params.toString()}`);
        const data = await response.json();

        if (!response.ok) {
            showToast('Error', JSON.stringify(data), 'error');
            return;
        }

        const head = document.getElementById('tablaHead');
        const body = document.getElementById('tablaBody');
        document.getElementById('accionesDiv').style.display = 'block';

        head.innerHTML = '';
        body.innerHTML = '';

        if (data.length === 0) {
            showToast('Error', 'No se encontraron resultados.', 'error');
            body.innerHTML = `<tr><td colspan="10" class="empty-state">No se encontraron resultados</td></tr>`;
            return;
        }

        const headers = Object.keys(data[0]).filter(h =>
            !['IdTerceroKey', 'NoContrato', 'IdMovDoc'].includes(h)
        );

        head.innerHTML =
            '<tr><th><input type="checkbox" id="selectAll"></th>' +
            headers.map(h => `<th>${h}</th>`).join('') +
            '<th>Radicar</th>';

        data.forEach(row => {
            body.innerHTML += `
                <tr>
                    <td><input type="checkbox" class="filaCheckbox"></td>
                    ${headers.map(h => `<td>${row[h] ?? ''}</td>`).join('')}
                    <td><button class="btn-radicar">Radicar</button></td>
                </tr>
            `;
        });

        showToast('Búsqueda completada', `Se encontraron ${data.length} resultados`, 'success');

    } catch (error) {
        showToast('Error', error.message, 'error');
    } finally {
        if (submitBtn) {
            submitBtn.disabled = false;
            submitBtn.innerHTML = originalHTML;
            submitBtn.style.opacity = '1';
            submitBtn.style.cursor = 'pointer';
        }
    }
});

/**
 * Carga la lista de terceros desde el backend
 * y los renderiza en el select correspondiente.
 */
async function cargarTerceros() {
    try {
        const response = await fetch('/filtros/terceros');
        const data = await response.json();
        const select = document.getElementById('idTercero');

        data.forEach(tercero => {
            const option = document.createElement('option');
            option.value = tercero.idTerceroKey;
            option.textContent = tercero.nomTercero;
            select.appendChild(option);
        });
    } catch (error) {
        showToast("Error", "Error al cargar terceros: " + error.message, 'error');
    }
}

document.addEventListener('DOMContentLoaded', cargarTerceros);

/**
 * Carga los contratos asociados a un tercero seleccionado.
 */
document.getElementById('idTercero').addEventListener('change', async function () {
    const selectContratos = document.getElementById('noContrato');
    selectContratos.innerHTML = '<option value="">Seleccione un contrato</option>';

    if (!this.value) return;

    try {
        const response = await fetch(`/filtros/contratos?idTerceroKey=${this.value}`);
        const data = await response.json();

        data.forEach(contrato => {
            const option = document.createElement('option');
            option.value = contrato.noContrato;
            option.textContent = contrato.nomContrato;
            selectContratos.appendChild(option);
        });
    } catch (error) {
        showToast("Error", "Error al cargar contratos: " + error.message, 'error');
    }
});
