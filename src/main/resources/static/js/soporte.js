// ============================================================================
// VARIABLES GLOBALES
// ============================================================================
let abortController;
let rptServiceSeleccionado = '';
let idDoc = '';


// ============================================================================
// UTILIDADES - Funciones auxiliares
// ============================================================================

/**
 * Muestra un mensaje toast en la pantalla
 * @param {string} titulo - Título del toast
 * @param {string} mensaje - Mensaje del toast
 * @param {string} tipo - Tipo: 'info', 'success', 'error'
 * @param {number|null} duracion - Duración en ms (null = no desaparece)
 * @returns {HTMLElement} El elemento toast creado
 */
function showToast(titulo, mensaje, tipo = 'info', duracion = 4000) {
    let container = document.querySelector('.toast-container');
    
    if (!container) {
        container = document.createElement('div');
        container.className = 'toast-container';
        document.body.appendChild(container);
    }

    const toast = document.createElement('div');
    toast.className = `toast toast-${tipo}`;
    toast.innerHTML = `<strong>${titulo}</strong><div>${mensaje}</div>`;
    container.appendChild(toast);

    if (duracion !== null) {
        setTimeout(() => {
            toast.remove();
            if (container.children.length === 0) {
                container.remove();
            }
        }, duracion);
    }

    return toast;
}

/**
 * Selecciona o deselecciona todas las filas de la tabla
 * @param {boolean} seleccionar - true para seleccionar, false para deseleccionar
 */
function seleccionarTodasFilas(seleccionar) {
    const checkboxes = document.querySelectorAll('.filaCheckbox');
    checkboxes.forEach(cb => cb.checked = seleccionar);
}


// ============================================================================
// MANEJO DE TABLA - Carga y renderizado de datos
// ============================================================================
/**
 * Carga los datos de la tabla desde el servidor
 */

async function cargarDatosTabla() {
    if (abortController) {
        abortController.abort();
    }

    abortController = new AbortController(); 
    const signal = abortController.signal;

    const params = new URLSearchParams(window.location.search);
    const idMovDoc = params.get('idmovdoc');

    if (!idMovDoc) {
        document.getElementById('tablaBody').innerHTML = `<tr><td colspan="10">No se recibió el idMovDoc.</td></tr>`;
        return;
    }

    try {
        document.getElementById('tablaBody').innerHTML = `<tr><td colspan="10" class="loading">Cargando...</td></tr>`;

        const response = await fetch(`/facturas/admision?idMovDoc=${encodeURIComponent(idMovDoc)}&idDoc=${encodeURIComponent(idDoc)}`, {
            signal
        });

        const data = await response.json();

        const head = document.getElementById('tablaHead');
        const body = document.getElementById('tablaBody');
        const accionesDiv = document.getElementById('accionesDiv');

        head.innerHTML = '';
        body.innerHTML = '';
        accionesDiv.style.display = 'block';

        if (!response.ok) {
            body.innerHTML = `<tr><td colspan="10">Error: ${JSON.stringify(data)}</td></tr>`;
            return;
        }

        if (!Array.isArray(data) || data.length === 0) {
            body.innerHTML = `<tr><td colspan="10" class="empty-state">No se encontraron resultados</td></tr>`;
            return;
        }

        renderizarTabla(data, head, body);

    } catch (error) {
        if (error.name === 'AbortError') {
            console.log('Petición anterior cancelada.');
            return;
        }
        console.error(error);
        document.getElementById('tablaBody').innerHTML = `<tr><td colspan="10">Error al cargar datos.</td></tr>`;
    }
}

/**
 * @param {Array} data - Datos a mostrar
 * @param {HTMLElement} head - Elemento thead
 * @param {HTMLElement} body - Elemento body
 */

function renderizarTabla(data, head, body) {
        const columnasOcultas = ['CantidadDoc'];
    const headers = Object.keys(data[0]).filter(h => !columnasOcultas.includes(h));

    head.innerHTML = '<tr><th></th>' + headers.map(h => `<th>${h}</th>`).join('') + '<th>Documento</th></tr>';

    data.forEach(row => {
        const filaHTML = `
            <tr>
                <td><input type="checkbox" class="filaCheckbox"></td>
                ${headers.map(h => `<td>${row[h] ?? ''}</td>`).join('')}
                <td>
                    <button class="btn-ver-docu">
                        <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                            <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"></path>
                            <polyline points="7 10 12 15 17 10"></polyline>
                            <line x1="12" y1="15" x2="12" y2="3"></line>
                        </svg>
                        Descargar
                    </button>
                </td>
            </tr>
        `;
        body.innerHTML += filaHTML;
    });

    agregarEventosDescargaIndividual();

}


// ============================================================================
// DESCARGA DE DOCUMENTOS - Individual
// ============================================================================

/**
 * Agrega eventos de clic a los botones de descarga individual
 */
function agregarEventosDescargaIndividual() {
    document.querySelectorAll('.btn-ver-docu').forEach(btn => {
        btn.addEventListener('click', async () => {
            await descargarDocumentoIndividual(btn);
        });
    });
}

/**
 * 
 * @param {HTMLElement} btn - Boton que disparo el evento 
 */
async function descargarDocumentoIndividual(btn){
    const fila = btn.closest('tr');
    const celdaIdAdmision = fila?.querySelector('td:nth-child(2)');
    const celdaNombreArchivo = fila?.querySelector('td:nth-last-child(2)');

    const idAdmision = celdaIdAdmision?.textContent.trim();
    const nombreArchivo = celdaNombreArchivo?.textContent.trim().replace(/\s+/g, '_');
    const select = document.getElementById('idSoporte');
    const nombreSoporte = select?.value;

    if (!nombreSoporte || nombreSoporte.trim() === '') {
        showToast('Error', 'Este tipo de documento soporte no se puede descargar', 'error');
        return;
    }

    if (idAdmision && nombreArchivo && nombreSoporte) {
        const url = `/descargar-pdf?idAdmision=${idAdmision}&nombreArchivo=${nombreArchivo}&nombreSoporte=${encodeURIComponent(nombreSoporte)}`;

        btn.dataset.originalHtml = btn.innerHTML;
        btn.innerHTML = 'Generando...';
        btn.disabled = true;

        let dirHandle;
        let toastDescargando;

        try {
            dirHandle = await window.showDirectoryPicker();
            toastDescargando = showToast('Descargando', `Descargando ${nombreArchivo}.pdf`, 'info', null);

            const response = await fetch(url);

            if (!response.ok) {
                const mensaje = await response.text();
                showToast('Error', mensaje || 'Ocurrió un error al guardar el PDF.', 'error');
                return;
            }

            const blob = await response.blob();
            const fileHandle = await dirHandle.getFileHandle(`${nombreArchivo}.pdf`, { create: true });
            const writable = await fileHandle.createWritable();
            await writable.write(blob);
            await writable.close();

            showToast('Éxito', `PDF guardado correctamente como ${nombreArchivo}.pdf`, 'success');
        } catch (error) {
            console.error(error);
            if (error.name === 'AbortError') {
                showToast('Cancelado', 'No se seleccionó carpeta.', 'info');
            } else {
                showToast('Error', 'Error al generar o guardar el PDF.', 'error');
            }
        } finally {
            if (toastDescargando && toastDescargando.isConnected) {
                toastDescargando.remove();
            }

            btn.innerHTML = btn.dataset.originalHtml || 'Descargar';
            btn.disabled = false;
        }
    } else {
        showToast('Error', 'Faltan datos en la fila para generar el PDF.', 'error');
    }
}

// ============================================================================
// DESCARGA DE DOCUMENTOS POR LOTES
// ============================================================================

/**
 * Descarga múltiples documentos seleccionados
 * @param {HTMLElement} btn - Botón de descarga masiva
 */
async function descargarDocumentosSeleccionados(btn) {
    const seleccionados = [...document.querySelectorAll('.filaCheckbox:checked')];

    // Validar selección
    if (seleccionados.length === 0) {
        alert('Selecciona al menos un documento.');
        return;
    }

    // Validar soporte
    const select = document.getElementById('idSoporte');
    const nombreSoporte = select?.value;

    if (!nombreSoporte || nombreSoporte.trim() === '') {
        showToast('Error', 'Este tipo de documento soporte no se puede descargar', 'error');
        return;
    }

    // Seleccionar carpeta de destino
    let dirHandle;
    try {
        dirHandle = await window.showDirectoryPicker();
        await dirHandle.requestPermission({ mode: 'readwrite' });
    } catch (err) {
        showToast('Cancelado', 'No se seleccionó una carpeta.', 'info');
        return;
    }

    // Configuración de lotes
    const LOTE_SIZE = 5;
    const total = seleccionados.length;
    let contador = 1;

    // Deshabilitar controles durante descarga
    select.disabled = true;
    btn.disabled = true;
    btn.style.opacity = '0.6';
    btn.style.cursor = 'not-allowed';

    // Procesar en lotes
    for (let i = 0; i < seleccionados.length; i += LOTE_SIZE) {
        const lote = seleccionados.slice(i, i + LOTE_SIZE);
        const loteActual = Math.floor(i / LOTE_SIZE) + 1;
        const totalLotes = Math.ceil(seleccionados.length / LOTE_SIZE);

        showToast('Procesando', `Procesando lote ${loteActual} de ${totalLotes} (${lote.length} archivos)`, 'info');

        // Procesar lote actual
        await procesarLoteDescargas(lote, dirHandle, nombreSoporte, contador, total, loteActual);
        contador += lote.length;

        // Pausa entre lotes
        if (i + LOTE_SIZE < seleccionados.length) {
            await new Promise(resolve => setTimeout(resolve, 1000));
        }
    }

    showToast('Éxito', 'Todos los archivos se procesaron.', 'success');

    // Restaurar controles
    select.disabled = false;
    btn.disabled = false;
    btn.style.opacity = '1';
    btn.style.cursor = 'pointer';
}

/**
 * Procesa un lote de descargas
 * @param {Array} lote - Checkboxes seleccionados
 * @param {FileSystemDirectoryHandle} dirHandle - Carpeta de destino
 * @param {string} nombreSoporte - Nombre del soporte seleccionado
 * @param {number} contador - Contador inicial
 * @param {number} total - Total de archivos
 * @param {number} loteActual - Número del lote actual
 */
async function procesarLoteDescargas(lote, dirHandle, nombreSoporte, contador, total, loteActual) {
    const promesasLote = lote.map(async (checkbox, index) => {
        return await descargarArchivoDelLote(
            checkbox, 
            dirHandle, 
            nombreSoporte, 
            contador + index, 
            total
        );
    });

    try {
        const resultados = await Promise.all(promesasLote);
        const exitosos = resultados.filter(r => r.success).length;
        const fallidos = resultados.filter(r => !r.success).length;

        if (fallidos > 0) {
            showToast('Lote completado', `Lote ${loteActual}: ${exitosos} exitosos, ${fallidos} fallidos`, 'error');
        } else {
            showToast('Lote completado', `Lote ${loteActual}: ${exitosos} archivos descargados`, 'success');
        }
    } catch (error) {
        console.error('Error en el lote:', error);
        showToast('Error', `Error en lote ${loteActual}: ${error.message}`, 'error');
    }
}

/**
 * Descarga un archivo individual dentro de un lote
 * @param {HTMLElement} checkbox - Checkbox de la fila
 * @param {FileSystemDirectoryHandle} dirHandle - Carpeta de destino
 * @param {string} nombreSoporte - Nombre del soporte
 * @param {number} numeroActual - Número actual del archivo
 * @param {number} total - Total de archivos
 * @returns {Object} Resultado de la descarga
 */
async function descargarArchivoDelLote(checkbox, dirHandle, nombreSoporte, numeroActual, total) {
    const fila = checkbox.closest('tr');
    const celdaIdAdmision = fila?.querySelector('td:nth-child(2)');
    const celdaNombreArchivo = fila?.querySelector('td:nth-last-child(2)');

    const idAdmision = celdaIdAdmision?.textContent.trim();
    const nombreArchivo = celdaNombreArchivo?.textContent.trim().replace(/\s+/g, '_');

    if (!idAdmision || !nombreArchivo || !nombreSoporte) {
        showToast('Error', 'Faltan datos en la fila para descargar.', 'error');
        return { success: false, nombre: 'Archivo sin nombre', error: 'Faltan datos' };
    }

    let toastDescargando;

    try {
        toastDescargando = showToast('Descargando', `Descargando ${numeroActual} de ${total}: ${nombreArchivo}.pdf`, 'info', null);

        // Descargar archivo
        const response = await fetch(
            `/descargar-pdf?idAdmision=${idAdmision}&nombreArchivo=${nombreArchivo}&nombreSoporte=${encodeURIComponent(nombreSoporte)}`
        );

        if (!response.ok) throw new Error(`Error: ${response.status}`);

        const blob = await response.blob();

        // Verificar permisos
        const permiso = await dirHandle.queryPermission({ mode: 'readwrite' });
        if (permiso !== 'granted') {
            const nuevoPermiso = await dirHandle.requestPermission({ mode: 'readwrite' });
            if (nuevoPermiso !== 'granted') {
                showToast('Error', `Permiso denegado para guardar ${nombreArchivo}`, 'error');
                return { success: false, nombre: nombreArchivo, error: 'Permiso denegado' };
            }
        }

        // Guardar archivo
        const fileHandle = await dirHandle.getFileHandle(`${nombreArchivo}.pdf`, { create: true });
        const writable = await fileHandle.createWritable();
        await writable.write(blob);
        await writable.close();

        checkbox.checked = false;

        return { success: true, nombre: nombreArchivo };

    } catch (error) {
        console.error(error);
        showToast('Error', `Fallo al guardar ${nombreArchivo}: ${error.message}`, 'error');
        return { success: false, nombre: nombreArchivo, error: error.message };
    } finally {
        if (toastDescargando && toastDescargando.isConnected) {
            toastDescargando.remove();
        }
    }
}

// ============================================================================
// CARGA DE SOPORTES
// ============================================================================
/**
 * Carga la lista de soportes disponibles
 */
async function cargarSoporte() {
    try {
        const response = await fetch('/soporte');
        const data = await response.json();
        const select = document.getElementById('idSoporte');

        let opcionPorDefecto = null;

        data.forEach(soporte => {
            const option = document.createElement('option');
            option.value = soporte.nombreRptService ?? '';
            option.textContent = soporte.nombreDocSoporte;
            option.dataset.idDoc = soporte.Id;
            select.appendChild(option);
            
            if (option.textContent === 'Resumen de atención u hoja de evolución'){
                opcionPorDefecto = option;
            }
        });

        if (opcionPorDefecto){
            opcionPorDefecto.selected = true;
            rptServiceSeleccionado = opcionPorDefecto.value;
            idDoc = opcionPorDefecto.dataset.idDoc;
        }

        select.addEventListener('change', async () =>{
            const selected = select.options[select.selectedIndex];
            rptServiceSeleccionado = selected.value;
            idDoc = selected.dataset.idDoc;

            await cargarDatosTabla();
        });

        await cargarDatosTabla();
    } catch (error) {
        showToast("Error", "Error al cargar documentos: " + error.message, 'error');
    }
}



// ============================================================================
// INICIALIZACIÓN
// ============================================================================
/**
 * Inicializa la aplicación cuando el DOM está listo
 */
document.addEventListener('DOMContentLoaded', async () => {
    // Obtener parámetros de URL
    const params = new URLSearchParams(window.location.search);
    const nfact = params.get('nfact');

    // Establecer título
    document.getElementById('tituloFactura').textContent = `FACTURA ${nfact ?? 'Desconocida'}`;

    // Cargar soportes
    await cargarSoporte();

    // Event listener para descarga múltiple
    document.addEventListener('click', async (e) => {
        if (e.target && e.target.id === 'btnDescargarSeleccionados') {
            await descargarDocumentosSeleccionados(e.target);
        }
    });
});