// Configuración de la API

let entradaSeleccionada = null;
let filaDetalleAbierta = null;
let inputProductoActivo = null;

const API_BASE_URL = `https://${window.location.hostname}:9876/almacen`;

// Inicializar
document.addEventListener('DOMContentLoaded', function() {
    const usuario = document.getElementById('usuario').value = localStorage.getItem('usuario');

    poblarSelectBodega();

    const now = new Date();
    const dateTimeLocal = new Date(now.getTime() - now.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
    document.getElementById('fecha').value = dateTimeLocal;
    
});

// Función para limpiar el formulario
function limpiarFormulario() {
    document.getElementById('formRegistrar').reset();
    const now = new Date();
    const dateTimeLocal = now.toISOString().slice(0, 16);
    document.getElementById('fecha').value = dateTimeLocal;
    document.getElementById('alert-container').innerHTML = '';
}

// Función para mostrar alertas
function mostrarAlerta(mensaje, tipo) {
    const alertContainer = document.getElementById('alert-container');
    const alertClass = tipo === 'success' ? 'alert-success' : 'alert-error';
    alertContainer.innerHTML = `<div class="alert ${alertClass}">${mensaje}</div>`;
    
    setTimeout(() => {
        alertContainer.innerHTML = '';
    }, 5000);
}

// Manejar envío del formulario
document.getElementById('formRegistrar').addEventListener('submit', async function(e) {
    e.preventDefault();

    const fecha = document.getElementById('fecha').value;
    const origen = document.getElementById('origen').value;
    const documento = document.getElementById('documento').value;
    const observaciones = document.getElementById('observaciones').value;
    const usuario = document.getElementById('usuario').value = localStorage.getItem('usuario');
    const bodegaKey = document.getElementById('bodegaKey').value;


    if (!fecha || !origen || !documento || !usuario || !bodegaKey) {
        mostrarAlerta('Por favor complete todos los campos requeridos', 'error');
        return;
    }
    
    const params = new URLSearchParams({
        Fecha: fecha.replace('T', ' ') + ':00',
        Origen: origen,
        Documento: documento,
        Usuario: usuario,
        Bodega_Key: bodegaKey
    });
    
    if (observaciones && observaciones.trim() !== '') {
        params.append('Observaciones', observaciones);
    }
    
    try {
        const submitBtn = document.querySelector('button[type="submit"]');
        submitBtn.disabled = true;
        submitBtn.textContent = 'Guardando...';
        
        const response = await fetch(`${API_BASE_URL}/insertarEntrada?${params.toString()}`, {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json'
            }
        });
        
        const data = await response.json();
        
        if (response.ok && data.success) {
            mostrarAlerta(`Entrada registrada exitosamente. ID: ${data.entradas_Key}`, 'success');

            mostrarDetalleEntrada(data.entradas_Key)
            
            setTimeout(() => {
                cargarListaProducto(data.entradas_Key);
            }, 1000);
        } else {
            mostrarAlerta(`Error: ${data.message || 'No se pudo registrar la entrada'}`, 'error');
        }
        
    } catch (error) {
        console.error('Error al insertar entrada:', error);
        mostrarAlerta('Error de conexión con el servidor', 'error');
    } finally {
        const submitBtn = document.querySelector('button[type="submit"]');
        submitBtn.disabled = false;
        submitBtn.textContent = 'Guardar Entrada';
    }
});


async function cargarListaProducto(entradasKey) {
    const tablaContainer = document.getElementById('tabla-productos');
    
    tablaContainer.innerHTML = '<div class="loading">Cargando datos...</div>';
    
    try {
        const token = localStorage.getItem('tokenSQL');
        
        const response = await fetch(`${API_BASE_URL}/ListarEntradasProducto?EntradasKey=${entradasKey}`, {
            method: 'GET',
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json'
            }
        });
        
        if (!response.ok) {
            throw new Error('Error al cargar las entradas');
        }
        
        const result = await response.json();
        
        if (!result.success || !result.data || result.data.length === 0) {
            tablaContainer.innerHTML = '<div class="no-data">No hay entradas registradas</div>';
            return;
        }
        
        const entradas = result.data;

        const totalGeneral = entradas.reduce((acc, entrada) =>{
            return acc + Number(entrada.TotalRenglon || 0);
        }, 0);
        


        let resumenHTML = `
            <div class="resumen-total" style="
                margin-bottom: 15px;
                padding: 15px 20px;
                background: #ffffff;
                border-radius: 8px;
                box-shadow: 0 2px 8px rgba(0,0,0,0.08);
                display: flex;
                justify-content: space-between;
                align-items: center;
            ">
                <div style="display: flex; align-items: center; gap: 15px;">
                    <span style="font-size: 18px; color: #6c757d;">
                        Total Entradas:
                    </span>
                    <span style="font-size: 20px; font-weight: bold; color: #2c3e50;">
                        $ ${formatearNumero(totalGeneral)}
                    </span>
                </div>
                
                <button class="btn btn-primary" onclick="abrirModalContabilizar(${entradasKey})">
                    Contabilizar
                </button>
            </div>
        `;

                        
        // Construir tabla HTML
        let tablaHTML = `
            <table>
                <thead>
                    <tr>
                        <th>ProductoCodigo</th>
                        <th>ProductoNombre</th>
                        <th>Cantidad</th>
                        <th>ValorCompra</th>
                        <th>Iva</th>
                        <th>TotalRenglon</th>
                        <th>Acciones</th>
                    </tr>
                </thead>
                <tbody>
        `;
        
        entradas.forEach(entrada => {

            tablaHTML += `
                <tr data-entrada-id="${entrada.Entradas_Key}">
                    <td>${entrada.ProductoCodigo}</td>
                    <td>${entrada.ProductoNombre}</td>
                    <td>${formatearNumero(entrada.Cantidad)}</td>
                    <td>${formatearNumero(entrada.ValorCompra)}</td>
                    <td>${entrada.Iva}</td>
                    <td>${formatearNumero(entrada.TotalRenglon)}</td>
                    <td>
                        <button class="btn btn-primary btn-wide" onclick="abrirModalEditar(
                                '${entrada.EntradasDet_Key}',
                                '${entrada.Productos_Key}',
                                '${entrada.ProductoCodigo}',
                                '${entrada.ProductoNombre}',
                                '${entrada.Cantidad}',
                                '${entrada.ValorCompra}',
                                '${entrada.Iva}')">
                            Editar
                        </button>

                        <button class="btn btn-primary btn-wide"
                            onclick="confirmarEliminarDetalle(this)"
                            data-entradasdet-key="${entrada.EntradasDet_Key}">
                            Eliminar
                        </button>
                    </td>
                </tr>
            `;
        });
        
        tablaHTML += `
                </tbody>
            </table>
        `;
        
        tablaContainer.innerHTML = resumenHTML + tablaHTML;
        
    } catch (error) {
        console.error('Error al cargar entradas:', error);
        tablaContainer.innerHTML = `
            <div class="no-data" style="color: #e74c3c;">
                Error al cargar los datos. Por favor, intente nuevamente.
            </div>
        `;
    }
}

let entradaActualKey = null;

function actualizarInfoEntrada(entradasKey) {
    entradaActualKey = entradasKey;
    document.getElementById('entrada-key-display').textContent = entradaActualKey;
}

function limpiarFormularioDetalle() {
    document.getElementById('formDetalle').reset();
    document.getElementById('alert-detalle').innerHTML = '';
}

async function guardarDetalle(e) {
    e.preventDefault();
    
    if (!entradaActualKey) {
        mostrarAlertaDetalle('Debe seleccionar una entrada primero desde la búsqueda o crearla', 'error');
        return;
    }
    
    const productosKey = document.getElementById('productosKey').dataset.productosKey;
    const cantidad = document.getElementById('cantidad').value;
    const valorCompra = document.getElementById('valorCompra').value;
    const iva = document.getElementById('iva').value;
    
    const params = new URLSearchParams({
        EntradasDet_Key: entradaActualKey,
        Productos_Key: productosKey,
        Cantidad: cantidad,
        ValorCompra: valorCompra,
        Iva: iva
    });
    
    try {
        const submitBtn = document.querySelector('#formDetalle button[type="submit"]');
        submitBtn.disabled = true;
        submitBtn.textContent = 'Guardando...';
        
        const token = localStorage.getItem('tokenSQL');
        
        const response = await fetch(`${API_BASE_URL}/InsertarEntradaDetalle?${params.toString()}`, {
            method: 'GET',
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json'
            }
        });
        
        const data = await response.json();
        
        if (response.ok && data.success) {
            mostrarAlertaDetalle('Producto agregado exitosamente', 'success');
            
            setTimeout(() => {
                limpiarFormularioDetalle();
                cargarListaProducto(entradaActualKey);
            }, 1500);
        } else {
            mostrarAlertaDetalle(`Error: ${data.message || 'No se pudo agregar el producto'}`, 'error');
        }
        
    } catch (error) {
        console.error('Error al insertar producto:', error);
        mostrarAlertaDetalle('Error de conexión con el servidor', 'error');
    } finally {
        const submitBtn = document.querySelector('#formDetalle button[type="submit"]');
        if (submitBtn) {
            submitBtn.disabled = false;
            submitBtn.textContent = 'Guardar Producto';
        }
    }
}

function mostrarAlertaDetalle(mensaje, tipo) {
    const alertContainer = document.getElementById('alert-detalle');
    if (!alertContainer) return;
    
    const alertClass = tipo === 'success' ? 'alert-success' : 'alert-error';
    alertContainer.innerHTML = `<div class="alert ${alertClass}">${mensaje}</div>`;
    
    setTimeout(() => {
        alertContainer.innerHTML = '';
    }, 5000);
}


async function seleccionarEntrada(entradasKey) {
    try {
        const tablaResultados = document.getElementById('tablaResultados');
        const filaSeleccionada = Array.from(tablaResultados.querySelectorAll('tbody tr'))
            .find(fila => fila.textContent.includes(entradasKey));

        if (!filaSeleccionada) {
            alert('Error al seleccionar la entrada');
            return;
        }

        const celdas = filaSeleccionada.querySelectorAll('td');
        
        const fechaTexto = celdas[1].textContent;
        const fechaParaInput = convertirFechaParaInput(fechaTexto);
        document.getElementById('fecha').value = fechaParaInput;
        
        const proveedorNombre = celdas[2].textContent.trim();
        const origen = proveedorNombre !== '-' ? proveedorNombre : '';
        document.getElementById('origen').value = origen;
        
        const documento = celdas[3].textContent;
        document.getElementById('documento').value = documento;
        document.getElementById('numeroDocumento').value = celdas[4].textContent;
        document.getElementById('bodegaKey').value = celdas[5].textContent;
        document.getElementById('usuario').value = celdas[6].textContent;
        
        const observaciones = celdas[7].textContent.trim();
        document.getElementById('observaciones').value = observaciones !== '-' ? observaciones : '';


        mostrarDetalleEntrada(entradasKey);

        cerrarModalBusqueda();

        await cargarListaProducto(entradasKey);

        const tablaProductos = document.getElementById('tabla-productos');
        if (tablaProductos) {
            tablaProductos.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }

    } catch (error) {
        console.error('Error al seleccionar entrada:', error);
        alert('Error al cargar los datos de la entrada seleccionada');
    }
}

async function buscarEntradas() {
    const fechaDesde = document.getElementById('buscarFechaDesde').value;
    const fechaHasta = document.getElementById('buscarFechaHasta').value;
    
    if (!fechaDesde || !fechaHasta) {
        alert('Por favor, ingrese las fechas desde y hasta');
        return;
    }

    const params = new URLSearchParams({
        FechaInicio: fechaDesde,
        FechaFin: fechaHasta
    });

    const origen = document.getElementById('buscarOrigen').value;
    const tipoDoc = document.getElementById('buscarDocumento').value;
    const numeroDesde = document.getElementById('buscarNumeroDesde').value;
    const numeroHasta = document.getElementById('buscarNumeroHasta').value;
    const idTercero = document.getElementById('buscarIdTercero').value;
    const bodegaKey = document.getElementById('buscarBodegaKey').value;

    if (origen) params.append('OrigenNombre', origen);
    if (tipoDoc) params.append('TipoDoc', tipoDoc);
    if (numeroDesde) params.append('NumeroDesde', numeroDesde);
    if (numeroHasta) params.append('NumeroHasta', numeroHasta);
    if (idTercero) params.append('IdTerceroKey', idTercero);
    if (bodegaKey) params.append('BodegaKey', bodegaKey);

    document.getElementById('resultadosBusqueda').style.display = 'block';
    document.getElementById('tablaResultados').innerHTML = '<div class="loading">Buscando entradas...</div>';

    try {
        const token = localStorage.getItem('tokenSQL');
        
        const response = await fetch(`${API_BASE_URL}/ListarEntradas?${params.toString()}`, {
            method: 'GET',
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json'
            }
        });

        if (!response.ok) {
            const dataError = await response.json();
            throw new Error(`Error ${response.status}: ${dataError.error}`);
        }

        const data = await response.json();

        if (data.success && data.data && data.data.length > 0) {
            mostrarResultadosEntrada(data.data);
            document.getElementById('totalResultados').textContent = data.total;
        } else {
            document.getElementById('tablaResultados').innerHTML = `
                <div class="no-resultados">
                    <p>No se encontraron resultados con los filtros seleccionados.</p>
                </div>
            `;
            document.getElementById('totalResultados').textContent = '0';
        }

    } catch (error) {
        console.error('Error al buscar entradas:', error);
        document.getElementById("tablaResultados").innerHTML = `
            <div class="no-resultados" style="color: #d32f2f;">
                <p>⚠ Error al buscar entradas</p>
                <p style="font-size: 14px;">${error.message}</p>
            </div>
        `;
    }
}

function mostrarResultadosEntrada(entradas) {
    const tabla = `
        <table class="tabla-resultados">
            <thead>
                <tr>
                    <th>ID</th>
                    <th>Fecha</th>
                    <th>Proveedor</th>
                    <th>Tipo</th>
                    <th>N° Doc</th>
                    <th>Bodega</th>
                    <th>Usuario</th>
                    <th>Observaciones</th>
                    <th>Estado</th>
                </tr>
            </thead>
            <tbody>
                ${entradas.map(entrada => `
                    <tr onclick="seleccionarEntrada(${entrada.Entradas_Key})">
                        <td>${entrada.Entradas_Key}</td>
                        <td>${formatearFecha(entrada.Fecha)}</td>
                        <td>${entrada.Proveedor_Nombre || '-'}</td>
                        <td>${entrada.Tipo}</td>
                        <td>${entrada.NumeroDocumento}</td>
                        <td>${entrada.Bodega_Key}</td>
                        <td>${entrada.Usuario}</td>
                        <td>${entrada.Observaciones || '-'}</td>
                        <td>
                            ${entrada.Anulado 
                                ? '<span class="badge badge-anulado">Anulado</span>' 
                                : '<span class="badge badge-activo">Activo</span>'}
                        </td>
                    </tr>
                `).join('')}
            </tbody>
        </table>
    `;

    document.getElementById('tablaResultados').innerHTML = tabla;
}


function convertirFechaParaInput(fechaTexto) {

    const partes = fechaTexto.split(', ');
    const [dia, mes, año] = partes[0].split('/');
    const hora = partes[1];
    return `${año}-${mes}-${dia}T${hora}`;
}

function limpiarBusqueda() {
    document.getElementById('formBusqueda').reset();
    document.getElementById('resultadosBusqueda').style.display = 'none';
    document.getElementById('totalResultados').textContent = '0';
    
    const hoy = new Date(); 
    const haceUnMes = new Date();
    haceUnMes.setMonth(haceUnMes.getMonth() - 1);
    
    const formatoFecha = (fecha) => {
        const año = fecha.getFullYear();
        const mes = String(fecha.getMonth() + 1).padStart(2, '0');
        const dia = String(fecha.getDate()).padStart(2, '0');
        return `${año}-${mes}-${dia}`;
    };
    
    document.getElementById('buscarFechaDesde').value = formatoFecha(haceUnMes);
    document.getElementById('buscarFechaHasta').value = formatoFecha(hoy);
}

function abrirModalBusqueda() {
    document.getElementById('modalBusqueda').style.display = 'flex';
    document.body.style.overflow = 'hidden';
    
    if (!document.getElementById('buscarFechaDesde').value) {
        limpiarBusqueda();
    }
}

function cerrarModalBusqueda() {
    document.getElementById('modalBusqueda').style.display = 'none';
    document.body.style.overflow = 'auto';
}

function formatearFecha(fechaSQL) {
    if (!fechaSQL) return '-';
    const fecha = new Date(fechaSQL);
    return fecha.toLocaleString('es-CO', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit'
    });
}

function formatearNumero(valor) {
    if (valor === null || valor === undefined || valor === '') return '0.00';

    return Number(valor).toLocaleString('es-CO', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
    });
}

function limpiarNumero(valor) {
    if (!valor) return null;

    return valor
        .toString()
        .replace(/\./g, '') // miles
        .replace(',', '.'); // decimal
}


let dropdownUpdateInterval = null;

function abrirDropdownProductos(inputId) {
    console.log('ABRIENDO DROPDOWN PARA:', inputId);
    const input = document.getElementById(inputId);
    const dropdown = document.getElementById('dropdownProductos');

    if (!input) {
        console.error('Input no encontrado:', inputId);
        return;
    }

    inputProductoActivo = input;

    const actualizarPosicion = () => {
        const rect = input.getBoundingClientRect();
        
        dropdown.style.position = 'fixed';
        dropdown.style.top = (rect.bottom + 5) + 'px'; 
        dropdown.style.left = rect.left + 'px';
        dropdown.style.width = rect.width + 'px';
    };

    dropdown.style.display = 'block';
    dropdown.style.zIndex = '1000000';
    

    actualizarPosicion();

    const onUpdate = () => actualizarPosicion();
    window.addEventListener('scroll', onUpdate, true);
    window.addEventListener('resize', onUpdate);
    
    dropdownUpdateInterval = setInterval(actualizarPosicion, 100);
    
    dropdown._updateListener = onUpdate;

    document.getElementById('buscarProductoTexto').value = '';
    buscarDropdownProductos();

    setTimeout(() => {
        document.getElementById('buscarProductoTexto').focus();
    }, 100);
}

function cerrarDropdownProductos() {
    const dropdown = document.getElementById('dropdownProductos');
    dropdown.style.display = 'none';
    

    if (dropdownUpdateInterval) {
        clearInterval(dropdownUpdateInterval);
        dropdownUpdateInterval = null;
    }
    
    if (dropdown._updateListener) {
        window.removeEventListener('scroll', dropdown._updateListener, true);
        window.removeEventListener('resize', dropdown._updateListener);
        delete dropdown._updateListener;
    }
}

async function buscarDropdownProductos() {
    const textoBusqueda = document.getElementById('buscarProductoTexto').value.trim().toLowerCase();
    const tabla = document.getElementById('resultadosProductos');
    tabla.innerHTML = '<div class="loading">Buscando productos...</div>';

    try {
        const params = new URLSearchParams({
            Tipo: 1,
            Id: 0,
            Id2: 0
        });

        const response = await fetch(`${API_BASE_URL}/ConsultarGenerales?${params.toString()}`, {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json'
            }
        });

        if (!response.ok) {
            throw new Error(`Error HTTP: ${response.status}`);
        }

        const result = await response.json();

        if (!result.success || !result.data || result.data.length === 0) {
            tabla.innerHTML = '<p style="padding: 20px; text-align: center;">No se encontraron productos.</p>';
            return;
        }

        let productosFiltrados = result.data;
        if (textoBusqueda) {
            productosFiltrados = result.data.filter(producto => 
                producto.Codigo?.toLowerCase().includes(textoBusqueda) ||
                producto.Nombre?.toLowerCase().includes(textoBusqueda)
            );
        }

        if (productosFiltrados.length === 0) {
            tabla.innerHTML = '<p style="padding: 20px; text-align: center;">No se encontraron productos con ese criterio.</p>';
            return;
        }

        let html = `
            <table>
                <thead>
                    <tr>
                        <th>Nombre</th>
                    </tr>
                </thead>
                <tbody>
        `;

        productosFiltrados.forEach(producto => {
            const productoEscapado = JSON.stringify(producto).replace(/'/g, "\\'");
            
            html += `
                <tr style="cursor: pointer;" onclick='seleccionarProducto(${productoEscapado})'>
                    <td>${producto.Nombre || '-'}</td>
                </tr>
            `;
        });

        html += `
                </tbody>
            </table>
        `;

        tabla.innerHTML = html;

    } catch (error) {
        console.error('Error al buscar productos:', error);
        tabla.innerHTML = '<p style="padding: 20px; text-align: center; color: red;">Error al buscar productos. Intente nuevamente.</p>';
    }
}

function seleccionarProducto(producto) {
    if (!inputProductoActivo) return;

    inputProductoActivo.value = `${producto.Codigo} - ${producto.Nombre}`;
    inputProductoActivo.dataset.productosKey = producto.Productos_Key;

    cerrarDropdownProductos();
}

document.addEventListener('click', function(event) {
    const dropdown = document.getElementById('dropdownProductos');
    const buscarInput = document.getElementById('buscarProductoTexto');
    
    if (dropdown && dropdown.style.display === 'block') {
        const clickEnDropdown = dropdown.contains(event.target);
        const clickEnBuscar = event.target === buscarInput;
        const clickEnInputProducto = event.target.id === 'productosKey' || event.target.id === 'edit-productosKey';
        
        if (!clickEnDropdown && !clickEnBuscar && !clickEnInputProducto) {
            cerrarDropdownProductos();
        }
    }
});

async function poblarSelectBodega() {

    try {

        const resp = await fetch(`${API_BASE_URL}/ConsultarGenerales?Tipo=2`);
        if (!resp.ok) throw new Error(await resp.json());


        const response = await resp.json();

        const data = response.data;

        console.log(data);


        if (Array.isArray(data)) {
            const opts = data.map(s =>
            `<option value="${s.IdBodegaKey}">
                ${s.Bodega ?? ''}
            </option>`
            ).join('');

            const selectBodega = document.getElementById('bodegaKey');
            const selectBuscarBodega = document.getElementById('buscarBodegaKey');
            selectBodega.innerHTML += opts;
            selectBuscarBodega.innerHTML += opts;

        }


        document.addEventListener('change', (e) => {
            const selectedId = e.target.value;
        });

    } catch (err) {
        console.error("Error poblando bodegas:", err);
    }
}

function abrirModalEditar(
    entradasDetKey,
    productosKey,
    productoCodigo,
    productoNombre,
    cantidad,
    valorCompra,
    iva
) {
    const modal = document.getElementById('modal-editar');

    modal.dataset.entradasDetKey = entradasDetKey;

    const inputProducto = document.getElementById('edit-productosKey');

    inputProducto.value = `${productoCodigo} - ${productoNombre}`;

    inputProducto.dataset.productosKey = productosKey;

    document.getElementById('edit-cantidad').value = formatearNumero(cantidad);
    document.getElementById('edit-valorCompra').value = formatearNumero(valorCompra);
    document.getElementById('edit-iva').value = iva;

    document.getElementById('alert-modal').innerHTML = '';
    modal.classList.add('show');
}

function cerrarModalEditar() {
    document.getElementById('modal-editar').classList.remove('show');
}

function mostrarAlertaModal(mensaje, tipo) {
    const alertContainer = document.getElementById('alert-modal');
    const alertClass = tipo === 'success' ? 'alert-success' : 'alert-error';
    alertContainer.innerHTML = `<div class="alert ${alertClass}">${mensaje}</div>`;
    
    setTimeout(() => {
        alertContainer.innerHTML = '';
    }, 5000);
}

document.getElementById('formEditar').addEventListener('submit', async function (e) {
    e.preventDefault();

    
    const modal = document.getElementById('modal-editar');
    const inputProducto = document.getElementById('edit-productosKey')

    
    const entradasDetKey = modal.dataset.entradasDetKey;
    const productosKey   = inputProducto.dataset.productosKey;
    const cantidad       = document.getElementById('edit-cantidad').value;
    const valorCompra    = document.getElementById('edit-valorCompra').value;
    const iva            = document.getElementById('edit-iva').value;

    if (!entradasDetKey) {
        mostrarAlertaModal('No se encontró la llave del detalle', 'error');
        return;
    }

    const params = new URLSearchParams({
        EntradasDetKey: entradasDetKey,
        ProductosKey: productosKey || '',
        Cantidad: limpiarNumero(cantidad),
        ValorCompra: limpiarNumero(valorCompra),
        Iva: iva
    });

    try {
        const btn = document.querySelector('#formEditar button[type="submit"]');
        btn.disabled = true;
        btn.textContent = 'Actualizando...';

        const response = await fetch(
            `${API_BASE_URL}/ActualizarEntradaDetalle?${params.toString()}`,
            {
                method: 'GET',
                headers: {
                    'Content-Type': 'application/json'
                }
            }
        );

        const data = await response.json();

        if (response.ok && data.success) {
            mostrarAlertaModal('Detalle actualizado correctamente', 'success');

            setTimeout(() => {
                cerrarModalEditar();
                cargarListaProducto(document.getElementById('entrada-key-display').textContent);
            }, 800);

        } else {
            mostrarAlertaModal(data.message || 'Error al actualizar detalle', 'error');
        }

    } catch (error) {
        console.error(error);
        mostrarAlertaModal('Error de conexión con el servidor', 'error');

    } finally {
        const btn = document.querySelector('#formEditar button[type="submit"]');
        btn.disabled = false;
        btn.textContent = 'Actualizar';
    }
});

async function confirmarEliminarDetalle(btn) {

    const entradasDetKey = btn.dataset.entradasdetKey;

    if (!entradasDetKey) {
        alert('No se encontró la llave del detalle');
        return;
    }

    if (!confirm('¿Está seguro de eliminar este ítem? Esta acción no se puede deshacer.')) {
        return;
    }

    try {
        btn.disabled = true;
        btn.textContent = 'Eliminando...';

        const response = await fetch(
            `${API_BASE_URL}/EliminarEntradaDetalle?EntradasDetKey=${entradasDetKey}`,
            { method: 'GET' }
        );

        const data = await response.json();

        if (response.ok && data.success) {
            alert('Ítem eliminado correctamente');

            cargarListaProducto(
                document.getElementById('entrada-key-display').textContent
            );

        } else {
            alert(data.message || 'Error al eliminar el ítem');
        }

    } catch (error) {
        console.error(error);
        alert('Error de conexión con el servidor');

    } finally {
        btn.disabled = false;
        btn.textContent = 'Eliminar';
    }
}

let entradasKeyContabilizar = null;

function abrirModalContabilizar(entradasKey) {
    
    entradasKeyContabilizar = entradasKey;

    document.getElementById('contabilizar-entradas-key').textContent = entradasKey;

    document.getElementById('alert-contabilizar').innerHTML = '';
    
    document.getElementById('modal-contabilizar').style.display = 'block';
}

function cerrarModalContabilizar() {
    document.getElementById('modal-contabilizar').style.display = 'none';
}

async function confirmarContabilizacion() {

    try {
        const response = await fetch(`/almacen/ContabilizarEntrada?entradasKey=${entradasKeyContabilizar}`, {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json'
            }
        });

        if (response.ok) {
            mostrarAlertaContabilizar('Entrada contabilizada exitosamente', 'success');
            
            setTimeout(() => {
                cerrarModalContabilizar();
                entradasKeyContabilizar = null;
            }, 1500);
        } else {
            const error = await response.json();
            mostrarAlertaContabilizar(`Error: ${error.error}`, 'error');
        }
    } catch (error) {
        console.error('Error al contabilizar:', error);
        mostrarAlertaContabilizar('Error al contabilizar la entrada', 'error');
    }
}

// Cerrar modal al hacer clic fuera de él
window.onclick = function(event) {
    const modal = document.getElementById('modal-contabilizar');
    if (event.target === modal) {
        cerrarModalContabilizar();
    }
}

function mostrarAlertaContabilizar(mensaje, tipo) {
    const alertContainer = document.getElementById('alert-contabilizar');
    const alertClass = tipo === 'success' ? 'alert-success' : 'alert-error';
    alertContainer.innerHTML = `<div class="alert ${alertClass}">${mensaje}</div>`;
    
    setTimeout(() => {
        alertContainer.innerHTML = '';
    }, 5000);
}

function mostrarDetalleEntrada(entradaKey) {
    document.getElementById('detalle-entrada').style.display = 'block';
    document.getElementById('entrada-key-display').textContent = entradaKey;
}

function ocultarDetalleEntrada() {
    document.getElementById('detalle-entrada').style.display = 'none';
    document.getElementById('entrada-key-display').textContent = '-';
}