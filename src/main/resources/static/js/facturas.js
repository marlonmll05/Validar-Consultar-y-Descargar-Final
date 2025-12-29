/* 
 * Verifica si existe un token SQL almacenado en localStorage.
 * Si no existe, redirige al usuario al login SQL.
 */
if (!localStorage.getItem('tokenSQL')) {
    window.location.href = 'loginsql.html';
}

/* 
 * Host actual de la aplicación.
 * Se utiliza para construir URLs dinámicas.
 */
const host = window.location.hostname;

/* 
 * Inicializa los campos de fecha del formulario
 * estableciendo la fecha actual como valor por defecto.
 */
document.addEventListener('DOMContentLoaded', () => {
    const fechaDesdeInput = document.getElementById('fechaDesde');
    const today = new Date().toISOString().split('T')[0];
    fechaDesdeInput.value = today;

    const fechaHastaInput = document.getElementById('fechaHasta');
    fechaHastaInput.value = today;
});


/**
 * Muestra un mensaje tipo toast en pantalla.
 *
 * @param {string} title - Título del mensaje
 * @param {string} message - Contenido del mensaje
 * @param {string} [type='success'] - Tipo de toast (success, error, info)
 * @param {number} [duration=6000] - Duración en milisegundos
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
                setTimeout(() => {
                    container.removeChild(toast);
                }, 300);
            }
        }, duration);
    }


    return toast;
}

/**
 * Actualiza la barra de progreso de un toast activo.
 *
 * @param {HTMLElement} toast - Toast a actualizar
 * @param {number} porcentaje - Porcentaje de avance (0–100)
 */
function actualizarToastProgreso(toast, porcentaje) {
    const progressBar = toast.querySelector('.toast-progress-bar');
    if (progressBar) {
        progressBar.style.width = `${porcentaje}%`;
    }
}

/**
 * Copia un texto al portapapeles.
 * Usa la API moderna Clipboard si está disponible,
 * de lo contrario utiliza un método alternativo.
 *
 * @param {string} texto - Texto a copiar
 * @returns {Promise<void>}
 */
function copiarAlPortapapeles(texto) {

    if (navigator.clipboard && window.isSecureContext) {
        return navigator.clipboard.writeText(texto);
    } 

    else {
        return new Promise((resolve, reject) => {
            const textarea = document.createElement('textarea');
            textarea.value = texto;
            textarea.style.position = 'fixed';
            textarea.style.left = '-9999px';
            textarea.style.top = '-9999px';
            
            document.body.appendChild(textarea);
            textarea.focus();
            textarea.select();
            
            try {
                const exitoso = document.execCommand('copy');
                document.body.removeChild(textarea);
                
                if (exitoso) {
                    resolve();
                } else {
                    reject(new Error('No se pudo copiar'));
                }
            } catch (err) {
                document.body.removeChild(textarea);
                reject(err);
            }
        });
    }
}

/**
 * Procesa la obtención y visualización de CUVs por lotes
 * para evitar sobrecargar el backend.
 *
 * @param {HTMLElement[]} filas - Celdas donde se mostrará el CUV
 * @param {number} [loteSize=30] - Tamaño del lote de procesamiento
 */
async function procesarCUVPorLotes(filas, loteSize = 30) {
    const toast = showToast('Procesando', 'Cargando CUVs', 'success', 999999, true);

    for (let i = 0; i < filas.length; i += loteSize) {
        const lote = filas.slice(i, i + loteSize);

        const promesas = lote.map(async cell => {
            const fila = cell.closest('tr');
            const nfact = fila.querySelector('td:nth-child(2)')?.textContent.trim();

            if (!nfact) {
                console.warn('No se encontró Nfact para esta fila');
                cell.innerHTML = 'No disponible';
                return;
            }

            const cuv = await obtenerCUV(nfact);

            const renderCUVConEditar = (valorCUV, editable = false) => {
                cell.innerHTML = `
                    <span class="valor-cuv">${valorCUV}</span>
                    <button class="btn-copiar-cuv" title="Copiar CUV" style="margin-left:5px;">📋</button>
                    ${editable ? '<button class="btn-editar-cuv">✏️</button>' : ''}
                `;

                const copiarBtn = cell.querySelector('.btn-copiar-cuv');
                copiarBtn.addEventListener('click', () => {
                    copiarAlPortapapeles(valorCUV)
                        .then(() => showToast('Copiado', `CUV copiado: ${valorCUV}`, 'success'))
                        .catch(err => showToast('Error', 'No se pudo copiar el CUV', 'error'));
                });

                if (editable) {
                    const editBtn = cell.querySelector('.btn-editar-cuv');
                    editBtn.addEventListener('click', () => {
                        renderInputCUV(valorCUV);
                    });
                }
            };

            const renderInputCUV = (valorInicial = '') => {
                cell.innerHTML = `
                    <input type="text" class="input-cuv" value="${valorInicial}" placeholder="Ingrese el CUV" style="width: 165px;" />
                    <button class="btn-save-cuv" title="Guardar">✔</button>
                    <button class="btn-cancel-cuv" title="Cancelar">✖</button>
                `;

                const input = cell.querySelector('.input-cuv');
                const saveBtn = cell.querySelector('.btn-save-cuv');
                const cancelBtn = cell.querySelector('.btn-cancel-cuv');

                saveBtn.addEventListener('click', async () => {
                    const valorCUV = input.value.trim();
                    let idEstadoValidacion = valorCUV ? 3 : 0;

                    try {
                        const exito = await agregarCUVCompleto(nfact, valorCUV, idEstadoValidacion);
                        if (exito) {
                            if (valorCUV) {
                                showToast('CUV guardado', `CUV ingresado y guardado para Nfact ${nfact}`, 'success');
                                renderCUVConEditar(valorCUV, true);
                            } else {
                                renderAgregarCUV();
                            }
                        } else {
                            showToast('Error', `No se ha generado JSON para ${nfact}`, 'error');
                        }
                    } catch (error) {
                        showToast('Error de red', error.message, 'error');
                    }
                });

                cancelBtn.addEventListener('click', () => {
                    if (valorInicial) {
                        renderCUVConEditar(valorInicial, true);
                    } else {
                        renderAgregarCUV();
                    }
                });
            };

            const renderAgregarCUV = () => {
                cell.innerHTML = `<button class="btn-cuv">+</button>`;
                const btn = cell.querySelector('.btn-cuv');
                btn.addEventListener('click', () => {
                    renderInputCUV();
                });
            };

            if (cuv) {
                renderCUVConEditar(cuv, false);
            } else {
                renderAgregarCUV();
            }
        });

        await Promise.all(promesas);

        const progreso = Math.min(100, Math.round(((i + lote.length) / filas.length) * 100));
        actualizarToastProgreso(toast, progreso);
    }

    actualizarToastProgreso(toast, 100);
    const mensaje = toast.querySelector('.toast-content p');
    if (mensaje) mensaje.textContent = 'CUVs cargados correctamente';
    setTimeout(() => {
        toast.classList.add('fadeOut');
        setTimeout(() => toast.remove(), 300);
    }, 1000);
}

/**
 * Maneja el envío del formulario de facturas.
 * Valida fechas, consulta el backend y renderiza los resultados.
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

    const fechaDesde = document.getElementById('fechaDesde').value;
    const fechaHasta = document.getElementById('fechaHasta').value;
    const tipoFechaCheckbox = document.getElementById('tipoFecha');

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

        params.append('tipoFecha', tipoFechaCheckbox.checked);
        
        for (const [key, value] of formData.entries()) {
            if (value.trim()) params.append(key, value);
        }

        try {
            showToast('Procesando', 'Buscando facturas...', 'success', 2000);

            const response = await fetch(`/filtros/facturas?${params.toString()}`);
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
                headersToShow.map(h => `<th>${h}</th>`).join('') + '<th>Doc Soporte</th>' + '<th>CUV</th></tr>';

            data.forEach(row => {
                const idMovDoc = row.idMovDoc || row.IdMovDoc || row.IDMOVDOC || row.ID || '';
                const headersHTML = headersToShow.map(h => `<td>${row[h] ?? ''}</td>`).join('');
                const filaHTML = `
                    <tr data-idmovdoc="${idMovDoc}">
                        <td class="checkbox-cell"><input type="checkbox" class="filaCheckbox custom-checkbox" value="${idMovDoc}"></td>
                        ${headersHTML}
                        <td>
                            <button class="btn-ver-doc">
                                Ver<p>Documento
                            </button>
                        </td>
                        <td class="cuv-cell" data-nfact="${row.Nfact}"></td>

                    </tr>
                `;
                body.innerHTML += filaHTML;

            });


            document.querySelectorAll('.btn-ver-doc').forEach(btn => {
                btn.addEventListener('click', (e) => {
                    const fila = e.target.closest('tr');
                    const idMovDoc = fila?.dataset?.idmovdoc;
                    const nfact = fila?.querySelector('td:nth-child(2)')?.textContent.trim();

                    if (idMovDoc && nfact) {
                        window.open(`soporte.html?idmovdoc=${encodeURIComponent(idMovDoc)}&nfact=${encodeURIComponent(nfact)}`, '_blank');
                    } else {
                        alert('Faltan datos para esta fila.');
                    }
                });
            });

            const celdasCUV = Array.from(document.querySelectorAll('.cuv-cell'));
            await procesarCUVPorLotes(celdasCUV, 30);

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

/**
 * Marca o desmarca todos los checkboxes de filas
 * según el estado del checkbox principal.
 * @param {HTMLInputElement} checkbox - Checkbox maestro
 */
function toggleSelectAll(checkbox) {
    const checkboxes = document.querySelectorAll('.filaCheckbox');
    checkboxes.forEach(cb => cb.checked = checkbox.checked);
}

/**
 * Selecciona o deselecciona todas las filas
 * y sincroniza el checkbox principal.
 * @param {boolean} seleccionar - true para seleccionar todo
 */
function seleccionarTodo(seleccionar) {
    const checkboxes = document.querySelectorAll('.filaCheckbox');
    checkboxes.forEach(cb => cb.checked = seleccionar);
    const selectAllCheckbox = document.getElementById('selectAll');
    if (selectAllCheckbox) selectAllCheckbox.checked = seleccionar;
}


/**
 * Descarga los documentos seleccionados ejecutando:
 * 1. Validación RIPS
 * 2. Descarga de paquetes ZIP
 * 3. Generación de reporte TXT
 */
async function descargarSeleccionados() {
    const boton = document.getElementById('descargarSeleccionadosBtn');
    const textoOriginal = boton.innerHTML;
    const boton2 = document.getElementById('subirboton');
    const textoOriginal2 = boton2.innerHTML;

    boton.disabled = true;
    boton.innerHTML = 'Descargando...';
    boton.style.opacity = '0.6';
    boton.style.cursor = 'not-allowed';

    boton2.disabled = true;
    boton2.innerHTML = 'Descargando...';
    boton2.style.opacity = '0.6';
    boton2.style.cursor = 'not-allowed';

    try {
        const tipo = document.getElementById('formatoSelect').value;
        const incluirXml = document.getElementById('incluirXML').checked;
        const seleccionados = [...document.querySelectorAll('.filaCheckbox')].filter(cb => cb.checked);
        
        if (seleccionados.length === 0) {
            showToast('Atención', "Selecciona al menos un documento.", 'error');
            return;
        }

        let directoryHandle;
        try {
            directoryHandle = await window.showDirectoryPicker();
            const permiso = await directoryHandle.requestPermission({ mode: 'readwrite' });
            if (permiso !== 'granted') {
                showToast('Error', 'Permiso denegado para escribir en la carpeta seleccionada.', 'error');
                return;
            }
        } catch (e) {
            showToast('Cancelado', 'Selección de carpeta cancelada.', 'error');
            return;
        }

        const toast = showToast('Procesando', `Descargando ${seleccionados.length} paquetes...`, 'success', 30000, true);
        actualizarToastProgreso(toast, 0);

        const reporte = [];
        const fechaHora = new Date().toLocaleString('es-CO', { 
            year: 'numeric', 
            month: '2-digit', 
            day: '2-digit',
            hour: '2-digit', 
            minute: '2-digit', 
            second: '2-digit' 
        });

        reporte.push('='.repeat(80));
        reporte.push(`REPORTE DE DESCARGA DE DOCUMENTOS`);
        reporte.push(`Fecha: ${fechaHora}`);
        reporte.push(`Total de documentos seleccionados: ${seleccionados.length}`);
        reporte.push(`Formato: ${tipo.toUpperCase()}`);
        reporte.push(`Incluir XML: ${incluirXml ? 'Sí' : 'No'}`);
        reporte.push('='.repeat(80));
        reporte.push('');

        // FASE 1: Solo ejecutar RIPS
        reporte.push('--- FASE 1: Ejecutar Rips ---');
        reporte.push('');

        const erroresRips = [];
        const documentosConNfact = [];

        // Preparar documentos y validar nfact
        for (const cb of seleccionados) {
            const row = cb.closest('tr');
            const nfact = row.querySelector('td:nth-child(2)').textContent.trim();
            const id = cb.value;
            
            if (!nfact) {
                reporte.push(`❌ ID ${id}: ERROR - Nfact no encontrado`);
                reporte.push('');
                showToast('Error', `Nfact no encontrado para ID ${id}`, 'error');
                erroresRips.push(id); // Contar como error
            } else {
                documentosConNfact.push({ id, nfact });
            }
        }

        // Ejecutar RIPS solo para documentos válidos
        for (let i = 0; i < documentosConNfact.length; i++) {
            const doc = documentosConNfact[i];
            const ripsError = await ejecutarRips(doc.nfact);
            
            if (ripsError) {
                erroresRips.push(doc.id);
                reporte.push(`❌ ${doc.nfact}: ERROR RIPS`);
                reporte.push(`   Detalle: ${ripsError}`);
                reporte.push('');
            }
            
            // Actualizar progreso RIPS (0-50%)
            const progresoRips = Math.round(((i + 1) / documentosConNfact.length) * 50);
            actualizarToastProgreso(toast, progresoRips);
        }

        // Filtrar documentos que pasaron RIPS
        const documentosValidos = documentosConNfact.filter(d => !erroresRips.includes(d.id));

        reporte.push('');
        reporte.push(`Resumen RIPS: ${documentosValidos.length} exitosos, ${erroresRips.length} fallidos`);
        reporte.push('='.repeat(80));
        reporte.push('');

        // Verificar si hay documentos válidos
        if (documentosValidos.length === 0) {
            reporte.push('❌ PROCESO FINALIZADO: Ningún documento pasó la validación RIPS');
            await guardarReporte(directoryHandle, reporte);
            
            showToast('Error', 'Ningún documento pasó validación RIPS. Se generó reporte con detalles.', 'error', 8000);
            
            setTimeout(() => {
                toast.classList.add('fadeOut');
                setTimeout(() => toast.remove(), 300);
            }, 1000);
            return;
        }

        // FASE 2: Solo descargar documentos válidos
        reporte.push('--- FASE 2: DESCARGA DE PAQUETES ---');
        reporte.push('');

        const erroresDescarga = [];
        const exitosos = [];

        for (let i = 0; i < documentosValidos.length; i++) {
            const doc = documentosValidos[i];
            const downloadResult = await descargarZip(doc.id, tipo, incluirXml, directoryHandle);
            
            if (downloadResult) {
                erroresDescarga.push(doc.nfact);
                reporte.push(`❌ ${doc.nfact}: ERROR EN DESCARGA`);
                reporte.push(`   Detalle: ${downloadResult}`);
                reporte.push('');
            } else {
                exitosos.push(doc.nfact);
                reporte.push(`✓ ${doc.nfact}: Descarga exitosa`);
                reporte.push('');
            }
            
            // Actualizar progreso descarga (50-100%)
            const progresoDescarga = 50 + Math.round(((i + 1) / documentosValidos.length) * 50);
            actualizarToastProgreso(toast, progresoDescarga);
        }

        const totalErrores = erroresRips.length + erroresDescarga.length;

        reporte.push('');
        reporte.push('='.repeat(80));
        reporte.push('RESUMEN FINAL');
        reporte.push('='.repeat(80));
        reporte.push(`Total procesados: ${seleccionados.length}`);
        reporte.push(`✓ Exitosos: ${exitosos.length}`);
        reporte.push(`❌ Fallidos (RIPS): ${erroresRips.length}`);
        reporte.push(`❌ Fallidos (Descarga): ${erroresDescarga.length}`);
        reporte.push(`Total errores: ${totalErrores}`);
        reporte.push('='.repeat(80));

        // Guardar reporte
        await guardarReporte(directoryHandle, reporte);

        document.querySelectorAll('.filaCheckbox').forEach(cb => cb.checked = false);
        const selectAllCheckbox = document.getElementById('selectAll');
        if (selectAllCheckbox) selectAllCheckbox.checked = false;

        setTimeout(() => {
            toast.classList.add('fadeOut');
            setTimeout(() => toast.remove(), 300);
        }, 1000);

        // Mostrar resultado final
        if (totalErrores === 0) {
            showToast('Éxito', 'Todos los documentos fueron descargados exitosamente. Ver reporte para detalles.', 'success', 8000);
        } else if (exitosos.length > 0) {
            showToast(
                'Completado con errores', 
                `✓ ${exitosos.length} exitosos | ❌ ${totalErrores} fallidos. Ver reporte_descarga.txt para detalles.`,
                'warning',
                10000
            );
        } else {
            showToast(
                'Proceso Fallido', 
                `Todos los documentos fallaron. Ver reporte_descarga.txt para detalles.`,
                'error',
                10000
            );
        }
    } finally {
        boton.disabled = false;
        boton.innerHTML = textoOriginal;
        boton.style.opacity = '1';
        boton.style.cursor = 'pointer';

        boton2.disabled = false;
        boton2.innerHTML = textoOriginal2;
        boton2.style.opacity = '1';
        boton2.style.cursor = 'pointer';
    }
}

/**
 * Guarda el reporte de descarga en un archivo TXT.
 * @param {FileSystemDirectoryHandle} directoryHandle
 * @param {string[]} lineasReporte
 */
async function guardarReporte(directoryHandle, lineasReporte) {
    try {
        const ahora = new Date();
        const bogota = new Date(ahora.toLocaleString('en-US', { timeZone: 'America/Bogota' }));
        
        const dia = String(bogota.getDate()).padStart(2, '0');
        const mes = String(bogota.getMonth() + 1).padStart(2, '0');
        const año = bogota.getFullYear();
        const hora = String(bogota.getHours()).padStart(2, '0');
        const minuto = String(bogota.getMinutes()).padStart(2, '0');
        const segundo = String(bogota.getSeconds()).padStart(2, '0');
        
        const timestamp = `${dia}-${mes}-${año}_${hora}-${minuto}-${segundo}`;
        const nombreArchivo = `reporte_descarga_${timestamp}.txt`;
        
        const fileHandle = await directoryHandle.getFileHandle(nombreArchivo, { create: true });
        const writable = await fileHandle.createWritable();
        
        const contenido = lineasReporte.join('\n');
        await writable.write(contenido);
        await writable.close();
        
        console.log(`✓ Reporte guardado: ${nombreArchivo}`);
    } catch (error) {
        console.error('Error al guardar reporte:', error);
        showToast('Advertencia', 'No se pudo guardar el archivo de reporte', 'warning', 5000);
    }
}

/**
 * Agrega el CUV completo de una factura en:
 * 1. Factura Final
 * 2. Rips_Transaccion
 *
 * @param {string} nFact - Número de factura
 * @param {string|null} cuv - Código Único de Validación
 * @param {number|null} idEstadoValidacion - Estado de validación RIPS
 * @returns {boolean} true si el proceso se ejecutó, false si falló
 */
async function agregarCUVCompleto(nFact, cuv, idEstadoValidacion) {
    let agregadoExitoso = false;
    
    const urlAgregar = `https://${host}:9876/api/sql/agregarcuv?nFact=${nFact}&ripsCuv=${cuv}`;
    try {
        const agregarResponse = await fetch(urlAgregar, { method: 'POST' });
        if (agregarResponse.ok) {
        if (cuv) {
            console.log(`✅ CUV agregado en Factura Final para NFact ${nFact}`);
            }
            agregadoExitoso = true;
        } else {
            console.error('❌ Error al agregar CUV para Factura Final');
            return false;
        }
    } catch (error) {
        console.error('❌ Error en la solicitud agregarcuv:', error);
        return false;
    }

    if (agregadoExitoso) {

        const params = new URLSearchParams({ nFact });
        if (cuv !== null && cuv !== undefined) params.append('cuv', cuv);
        if (idEstadoValidacion !== null && idEstadoValidacion !== undefined)
            params.append('idEstadoValidacion', idEstadoValidacion);

            
        const urlActualizar = `https://${host}:9876/api/sql/actualizarcuvrips?nFact=${nFact}&cuv=${cuv}&idEstadoValidacion=${idEstadoValidacion}`;
        try {
            const actualizarResponse = await fetch(urlActualizar, { 
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                }
            });
            if (actualizarResponse.ok) {
            if (cuv) {
                console.log(`✅ CUV agregado en Rips_Transaccion para NFact ${nFact} (Estado: ${idEstadoValidacion})`);
            }
            } else {
                const errorText = await actualizarResponse.text();
                console.error('❌ Error al agregar CUV en Rips_Transaccion:', errorText);

            }
        } catch (error) {
            console.error('❌ Error en la solicitud agregarcuvrips:', error);
        
        }
    }
    return true;
}

/**
 * Obtiene el CUV asociado a una factura.
 *
 * @param {string} nfact - Número de factura
 * @returns {Promise<string>} CUV encontrado o cadena vacía si no existe
 */
async function obtenerCUV(nfact) {
    const host = window.location.hostname;
    const url = `https://${host}:9876/api/sql/cuv?nFact=${nfact}`;
    
    try {
        const response = await fetch(url);
        if (!response.ok) {
            return '';
        }
        const data = await response.json();
        return data.Rips_CUV || '';
    } catch (error) {
        return '';
    }
}

/**
 * Ejecuta el proceso RIPS para una factura.
 * @param {string} nfact
 * @returns {string|null} Error o null si fue exitoso
 */
async function ejecutarRips(nfact) {
    try {
        const response = await fetch(`/api/sql/ejecutarRips?Nfact=${encodeURIComponent(nfact)}`);
        const result = await response.text();

        if (!response.ok) {
            return `Error al ejecutar RIPS para ${nfact}: ${result}`;
        }

        return null; 
    } catch (error) {
        return `Error de red al ejecutar RIPS para ${nfact}: ${error.message}`;
    }
}

/**
 * Descarga el ZIP de un documento.
 * @param {number} id
 * @param {string} tipo
 * @param {boolean} xml
 * @param {FileSystemDirectoryHandle} directoryHandle
 * @returns {string|false} Error o false si fue exitoso
 */
async function descargarZip(id, tipo, xml, directoryHandle) {
    const host = window.location.hostname;
    const url = `https://${host}:9876/facturas/generarzip/${id}/${tipo}/${xml}`; 
    try {
        const response = await fetch(url);

        if (!response.ok) {
            const errorText = await response.text();
            return errorText;
        }

        const blob = await response.blob();
        let fileName = `certificado_${id}.zip`;

        const contentDisposition = response.headers.get('Content-Disposition');
        if (contentDisposition && contentDisposition.includes('filename=')) {
            fileName = contentDisposition.split('filename=')[1].replace(/['"]/g, '').trim();
        }

        const fileHandle = await directoryHandle.getFileHandle(fileName, { create: true });
        const writable = await fileHandle.createWritable();
        await writable.write(blob);
        await writable.close();

        return false; 
    } catch (error) {
        return `Error de red para ID ${id}: ${error.message}`;
    }
}

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