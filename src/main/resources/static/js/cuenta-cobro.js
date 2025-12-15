
if (!localStorage.getItem('tokenSQL')) {
    window.location.href = 'loginsql.html';
}

// Validación de Acceso
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
            return;
        }

    } catch (error) {
        console.log("Error al hacer la petición:", error);
    }
});

const host = window.location.hostname;

document.addEventListener('DOMContentLoaded', () => {
    const fechaDesdeInput = document.getElementById('fechaDesde');
    const today = new Date().toISOString().split('T')[0];
    fechaDesdeInput.value = today;

    const fechaHastaInput = document.getElementById('fechaHasta');
    fechaHastaInput.value = today;
});

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
                setTimeout(() => {
                    container.removeChild(toast);
                }, 300);
            }
        }, duration);
    }


    return toast;
}

function actualizarToastProgreso(toast, porcentaje) {
    const progressBar = toast.querySelector('.toast-progress-bar');
    if (progressBar) {
        progressBar.style.width = `${porcentaje}%`;
    }
}




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

    const fechaDesde = document.getElementById('fechaDesde').value;
    const fechaHasta = document.getElementById('fechaHasta').value;

    try {
        if (!fechaDesde) {
            showToast('Error', 'La Fecha Desde es obligatoria.', 'error');
            return;
        }
        if (!fechaHasta) {
            showToast('Error', 'La Fecha Hasta es obligatoria.', 'error');
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

        try {
            showToast('Procesando', 'Buscando facturas...', 'success', 2000);

            const response = await fetch(`/filtros/cuenta-cobro?${params.toString()}`);
            const data = await response.json();

            if (!response.ok) {
                showToast('Error', typeof data === 'string' ? data : JSON.stringify(data), 'error');
                return;
            }

            const tabla = document.getElementById('resultados');
            const head = document.getElementById('tablaHead');
            const body = document.getElementById('tablaBody');
            const accionesDiv = document.getElementById('accionesDiv');

            accionesDiv.style.display = 'block';
            head.innerHTML = '';
            body.innerHTML = '';

            if (data.length === 0) {
                showToast('Error', 'No se encontraron resultados.', 'error')
                body.innerHTML = `<tr><td colspan="10" class="empty-state">No se encontraron resultados</td></tr>`;
                return;
            }

            const headers = Object.keys(data[0]);
            const headersToShow = headers.filter(h => h !== 'IdTerceroKey' && h !== 'NoContrato' && h !== 'IdMovDoc');
            head.innerHTML = '<tr><th class="checkbox-cell"><input type="checkbox" id="selectAll" onclick="toggleSelectAll(this)"></th>' +
                headersToShow.map(h => `<th>${h}</th>`).join('') + '<th>Radicar</th>';

            data.forEach(row => {
                const idMovDoc = row.idMovDoc || row.IdMovDoc || row.IDMOVDOC || row.ID || '';
                const headersHTML = headersToShow.map(h => `<td>${row[h] ?? ''}</td>`).join('');
                const filaHTML = `
                    <tr data-idmovdoc="${idMovDoc}">
                        <td class="checkbox-cell"><input type="checkbox" class="filaCheckbox custom-checkbox" value="${idMovDoc}"></td>
                        ${headersHTML}
                        <td>
                            <button class="btn-radicar">
                                Radicar<p>Cuenta
                            </button>
                        </td>
                    </tr>
                `;
                body.innerHTML += filaHTML;

            });

            showToast('Búsqueda completada', `Se encontraron ${data.length} resultados`, 'success');

        } catch (err) {
            showToast('Error', "Error en la solicitud: " + err.message, 'error');
        }

    } finally {
        if (submitBtn) {
            submitBtn.disabled = false;
            submitBtn.innerHTML = originalHTML;
            submitBtn.style.opacity = '1';
            submitBtn.style.cursor = 'pointer';
        }
    }
});

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

document.getElementById('idTercero').addEventListener('change', async function() {
    const idTerceroKey = this.value;
    const selectContratos = document.getElementById('noContrato');
    selectContratos.innerHTML = '<option value="">Seleccione un contrato</option>';

    if (idTerceroKey) {
        try {
            const response = await fetch(`/filtros/contratos?idTerceroKey=${idTerceroKey}`);
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
    }
});