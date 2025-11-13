
if (!sessionStorage.getItem('tokenSQL')) {
    window.location.href = 'loginsql.html';
}

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

function toggleSelectAll(checkbox) {
    const checkboxes = document.querySelectorAll('.filaCheckbox');
    checkboxes.forEach(cb => cb.checked = checkbox.checked);
}

function seleccionarTodo(seleccionar) {
    const checkboxes = document.querySelectorAll('.filaCheckbox');
    checkboxes.forEach(cb => cb.checked = seleccionar);
    const selectAllCheckbox = document.getElementById('selectAll');
    if (selectAllCheckbox) selectAllCheckbox.checked = seleccionar;
}

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

        const errores = [];

        for (let i = 0; i < seleccionados.length; i++) {
            const cb = seleccionados[i];
            const row = cb.closest('tr');
            const cuv = row.querySelector('.valor-cuv')?.textContent.trim();
            const nfact = row.querySelector('td:nth-child(2)').textContent.trim();
            const id = cb.value;

            if (!nfact) {
                showToast('Error', `No se encontró Nfact para ID ${id}`, 'error');
                errores.push({ id, tipo, error: 'Nfact no encontrado' });
                continue;
            }

            const ripsError = await ejecutarRips(nfact);
            if (ripsError) {
                showToast('Error', ripsError, 'error');
                errores.push({ id, tipo, error: ripsError });
                continue;
            }

            const downloadResult = await descargarZip(id, tipo, incluirXml, cuv, directoryHandle);
            if (downloadResult) {
                showToast('Error', downloadResult, 'error');
                errores.push({ id, tipo, error: downloadResult });
            }

            const progreso = Math.round(((i + 1) / seleccionados.length) * 100);
            actualizarToastProgreso(toast, progreso);
        }

        document.querySelectorAll('.filaCheckbox').forEach(cb => cb.checked = false);
        const selectAllCheckbox = document.getElementById('selectAll');
        if (selectAllCheckbox) selectAllCheckbox.checked = false;

        setTimeout(() => {
            toast.classList.add('fadeOut');
            setTimeout(() => toast.remove(), 300);
        }, 1000);

        if (errores.length === 0) {
            showToast('Éxito', 'Todos los documentos fueron descargados exitosamente.', 'success');
        } else {
            showToast('Completado con errores', `Se descargaron con éxito ${seleccionados.length - errores.length} de ${seleccionados.length} documentos.`, 'success');
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

async function descargarZip(id, tipo, xml, cuv, directoryHandle) {
    const host = window.location.hostname;
    const url = `https://${host}:9876/facturas/generarzip/${id}/${tipo}/${xml}?cuv=${encodeURIComponent(cuv || '')}`; 
    try {
        const response = await fetch(url);

        if (!response.ok) {
            const errorText = await response.text();
            return `Error al descargar ZIP para ID ${id}: ${errorText}`;
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