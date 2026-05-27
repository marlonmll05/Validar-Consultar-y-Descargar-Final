// Configuración de la API

let salidaSeleccionada = null;
let filaDetalleAbierta = null;
let costoUnitarioSeleccionado = null;

const API_BASE_URL = `https://${window.location.hostname}:9876/almacen`;

document.addEventListener('DOMContentLoaded', function() {

    document.getElementById('usuario').value = localStorage.getItem('usuario');

    poblarSelectUnidadAtencion();
    poblarSelectBodega();


    const now = new Date();
    const dateTimeLocal = new Date(now.getTime() - now.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
    document.getElementById('fecha').value = dateTimeLocal;


    document.addEventListener('change', (e) => {
        if (e.target.id === 'bodegaKey' || e.target.id === 'buscarBodegaKey') {
            console.log(`Usuario seleccionó bodega Id: ${e.target.value}`);
        }
        if (e.target.id === 'idUnidadAtencion' || e.target.id === 'buscarIdUnidadAtencion') {
            console.log(`Usuario seleccionó UnidadAtencion Id: ${e.target.value}`);
        }
    });
    
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
    const destino = document.getElementById('destino').value;
    const documento = document.getElementById('documento').value;
    const observaciones = document.getElementById('observaciones').value;
    const usuario = document.getElementById('usuario').value;
    const idUnidadAtencion = document.getElementById('idUnidadAtencion').value;
    const bodegaKey = document.getElementById('bodegaKey').value;

    if (!fecha || !destino || !documento || !usuario || !idUnidadAtencion || !bodegaKey) {
        mostrarAlerta('Por favor complete todos los campos requeridos', 'error');
        return;
    }
    
    const params = new URLSearchParams({
        Fecha: fecha.replace('T', ' ') + ':00',
        Destino: destino,
        Documento: documento,
        Usuario: usuario,
        idUnidadAtencion: idUnidadAtencion,
        BodegaKey: bodegaKey
    });
    
    if (observaciones && observaciones.trim() !== '') {
        params.append('Observaciones', observaciones);
    }
    
    try {

        const submitBtn = document.querySelector('button[type="submit"]');
        submitBtn.disabled = true;
        submitBtn.textContent = 'Guardando...';
        
        const response = await fetch(`${API_BASE_URL}/insertarSalida?${params.toString()}`, {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json'
            }
        });
        
        const data = await response.json();
        
        if (response.ok && data.success) {
            mostrarAlerta(`Salida registrada exitosamente. ID: ${data.salidas_Key}`, 'success');

            actualizarInfoSalida(data.salidas_Key);

            setTimeout(() => {
                limpiarFormulario();
            }, 3000);
        } else {
            mostrarAlerta(`Error: ${data.message || 'No se pudo registrar la salida'}`, 'error');
        }
        
    } catch (error) {
        console.error('Error al insertar salida:', error);
        mostrarAlerta('Error de conexión con el servidor', 'error');
    } finally {
        const submitBtn = document.querySelector('button[type="submit"]');
        submitBtn.disabled = false;
        submitBtn.textContent = 'Guardar Salida';
    }
});

// Función para cargar y mostrar las salidas
async function buscarSalidas() {
    const fechaDesde = document.getElementById('buscarFechaDesde').value;
    const fechaHasta = document.getElementById('buscarFechaHasta').value;
    
    if (!fechaDesde || !fechaHasta) {
        alert('Por favor, ingrese las fechas desde y hasta');
        return;
    }

    const params = new URLSearchParams({
        fechaInicio: fechaDesde,
        fechaFin: fechaHasta
    });

    const idUnidadKey = document.getElementById('buscarIdUnidadAtencion').value;
    const bodegaKey = document.getElementById('buscarBodegaKey').value;
    const numeroDesde = document.getElementById('buscarNumeroDesde').value;
    const numeroHasta = document.getElementById('buscarNumeroHasta').value;
    const tipoDoc = document.getElementById('buscarTipoDoc')


    if (idUnidadKey) params.append('idUnidadKey', idUnidadKey);
    if (bodegaKey) params.append('bodegaKey', bodegaKey);
    if (numeroDesde) params.append('numeroDesde', numeroDesde);
    if (numeroHasta) params.append('numeroHasta', numeroHasta);
    if (tipoDoc) params.append('tipoDoc', tipoDoc);

    document.getElementById('resultadosBusqueda').style.display = 'block';
    document.getElementById('tablaResultados').innerHTML = '<div class="loading">Buscando salidas...</div>';

    try {
        const response = await fetch(`${API_BASE_URL}/ListarSalidas?${params.toString()}`, {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json'
            }
        });

        if (!response.ok) {
            const dataError = await response.json();
            throw new Error(`Error ${response.status}: ${dataError.message || dataError.error}`);
        }

        const data = await response.json();

        if (data.success && data.data && data.data.length > 0) {
            mostrarResultadosSalidas(data.data);
            document.getElementById('totalResultados').textContent = data.data.length;
        } else {
            document.getElementById('tablaResultados').innerHTML = `
                <div class="no-resultados">
                    <p>No se encontraron resultados con los filtros seleccionados.</p>
                </div>
            `;
            document.getElementById('totalResultados').textContent = '0';
        }

    } catch (error) {
        console.error('Error al buscar salidas:', error);
        document.getElementById('tablaResultados').innerHTML = `
            <div class="no-resultados" style="color: #d32f2f;">
                <p>❌ Error al buscar salidas</p>
                <p style="font-size: 14px;">${error.message}</p>
            </div>
        `;
    }
}

function mostrarResultadosSalidas(salidas) {
    let html = `
        <table>
            <thead>
                <tr>
                    <th>ID</th>
                    <th>Fecha</th>
                    <th>IdUnidadAtencion</th>
                    <th>Destino</th>
                    <th>Tipo</th>
                    <th>NumeroDocumento</th>
                    <th>Bodega_Key</th>
                    <th>Observaciones</th>
                    <th>Usuario</th>
                    <th>Anulado</th>
                    <th>Contabilizado</th>
                </tr>
            </thead>
            <tbody>
    `;

    salidas.forEach(salida => {
        
        console.log(salida.salidas_Key)
        // Formatear fecha
        const fechaObj = new Date(salida.fecha);
        const fechaFormateada = fechaObj.toLocaleString('es-CO', {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit'
        });
        
        html += `
            <tr onclick="seleccionarSalida(${salida.salidas_Key})" style="cursor: pointer;" data-salida-id="${salida.salidas_Key}">
                <td>${salida.salidas_Key}</td>
                <td>${fechaFormateada}</td>
                <td>${salida.idUnidadAtencion}</td>
                <td>${salida.destino}</td>
                <td>${salida.tipo}</td>
                <td>${salida.numeroDocumento}</td>
                <td>${salida.bodegaKey}</td>
                <td>${salida.observaciones || '-'}</td>
                <td>${salida.usuario}</td>
                <td>${salida.anulado}</td>
                <td>${salida.contabilizado}</td>

            </tr>
        `;
    });

    html += `
            </tbody>
        </table>
    `;

    document.getElementById('tablaResultados').innerHTML = html;
}


// Función para guardar detalle
async function guardarDetalle(e) {
    e.preventDefault();
    
    if (!salidaActualKey) {
        mostrarAlertaDetalle('Debe seleccionar una salida primero desde la búsqueda o crearla', 'error');
        return;
    }
    if (costoUnitarioSeleccionado == null) {
        mostrarAlertaDetalle('Debe seleccionar un producto válido', 'error');
        return;
    }

    const productosKey = document.getElementById('productosKey').dataset.productosKey;
    const cantidad = document.getElementById('cantidad').value;

    const params = new URLSearchParams({
        Salidas_Key: salidaActualKey,
        Productos_Key: productosKey,
        Cantidad: cantidad,
        CostoUnitario: costoUnitarioSeleccionado
    });
    
    try {
        const submitBtn = document.querySelector('#formDetalle button[type="submit"]');
        submitBtn.disabled = true;
        submitBtn.textContent = 'Guardando...';
        
        const token = localStorage.getItem('tokenSQL');
        
        const response = await fetch(`${API_BASE_URL}/InsertarSalidaDetalle?${params.toString()}`, {
            method: 'GET',
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json'
            }
        });
        
        const data = await response.json();
        
        if (response.ok && data.success) {
            mostrarAlertaDetalle('Producto agregado exitosamente', 'success');
            
            // Limpiar formulario y recargar tabla
            setTimeout(() => {
                limpiarFormularioDetalle();
                cargarListaProducto(salidaActualKey);
            }, 1500);
        } else {
            mostrarAlertaDetalle(`Error: ${data.message || 'No se pudo agregar el producto'}`, 'error');
            console.log("sucedio algo")
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


// Función para mostrar alertas en el área de detalle
function mostrarAlertaDetalle(mensaje, tipo) {
    const alertContainer = document.getElementById('alert-detalle');
    if (!alertContainer) return;
    
    const alertClass = tipo === 'success' ? 'alert-success' : 'alert-error';
    alertContainer.innerHTML = `<div class="alert ${alertClass}">${mensaje}</div>`;
    
    setTimeout(() => {
        alertContainer.innerHTML = '';
    }, 5000);
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

    // Función para actualizar posición
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
    costoUnitarioSeleccionado = producto.CostoUnitario;

    cerrarDropdownProductos();
}


// Cerrar dropdown al hacer click fuera
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

// Inicializar fechas al abrir el modal
function abrirModalBusqueda() {
    document.getElementById('modalBusqueda').style.display = 'flex';
    document.body.style.overflow = 'hidden';
    
    // Si los campos están vacíos, establecer fechas por defecto
    if (!document.getElementById('buscarFechaDesde').value) {
        limpiarBusqueda();
    }
}

function cerrarModalBusqueda() {
    document.getElementById('modalBusqueda').style.display = 'none';
    document.body.style.overflow = 'auto';
}

// Variable global para almacenar el Salidas_Key actual
let salidaActualKey = null;

// Función para actualizar la información mostrada de la salida
function actualizarInfoSalida(salidasKey) {
    salidaActualKey = salidasKey;
    document.getElementById('salida-key-display').textContent = salidaActualKey;
}

// Modificar seleccionarSalida para actualizar la info
async function seleccionarSalida(salidasKey) {
    try {

        actualizarInfoSalida(salidasKey);

        cerrarModalBusqueda();

        await cargarListaProducto(salidasKey);


    } catch (error) {
        console.error('Error al seleccionar salida:', error);
        alert('Error al cargar los datos de la salida seleccionada');
    }
}

// Función para limpiar el formulario de detalle
function limpiarFormularioDetalle() {
    document.getElementById('formDetalle').reset();
    document.getElementById('alert-detalle').innerHTML = '';
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


    } catch (err) {
        console.error("Error poblando bodegas:", err);
    }
}

async function poblarSelectUnidadAtencion() {
    try {

        const resp = await fetch(`${API_BASE_URL}/ConsultarGenerales?Tipo=4&Id=-1&Id2=-1`);
        if (!resp.ok) throw new Error(await resp.json());


        const response = await resp.json();

        const data = response.data;

        console.log(data);


        if (Array.isArray(data)) {
            const opts = data.map(s =>
            `<option value="${s.IdUnidadAtencion}">
                ${s.Unidadatencion ?? ''}
            </option>`
            ).join('');

            const selectUnidadAtencion = document.getElementById('idUnidadAtencion');
            const selectBuscarUnidadAtencion = document.getElementById('buscarIdUnidadAtencion');
            selectUnidadAtencion.innerHTML += opts;
            selectBuscarUnidadAtencion.innerHTML += opts;

        }

    } catch (err) {
        console.error("Error poblando UnidadAtencion:", err);
    }
}




async function cargarListaProducto(salidasKey) {
    const tablaContainer = document.getElementById('tabla-productos');
    
    tablaContainer.innerHTML = '<div class="loading">Cargando datos...</div>';
    
    try {
        const token = localStorage.getItem('tokenSQL');
        
        const response = await fetch(`${API_BASE_URL}/ListarSalidasProducto?SalidasKey=${salidasKey}`, {
            method: 'GET',
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json'
            }
        });
        
        if (!response.ok) {
            throw new Error('Error al cargar las salidas');
        }
        
        const result = await response.json();
        
        if (!result.success || !result.data || result.data.length === 0) {
            tablaContainer.innerHTML = '<div class="no-data">No hay salidas registradas</div>';
            return;
        }
        
        const salidas = result.data;

        const totalGeneral = salidas.reduce((acc, salida) =>{
            return acc + Number(salida.TotalRenglon || 0);
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
                max-width: 400px;
            ">
                <span style="font-size: 18px; color: #6c757d;">
                    Total Salidas:
                </span>
                <span style="font-size: 20px; font-weight: bold; color: #2c3e50;">
                    $ ${formatearNumero(totalGeneral)}
                </span>
            </div>
        `;

        let tablaHTML = `
            <table>
                <thead>
                    <tr>
                        <th>ProductoCodigo</th>
                        <th>ProductoNombre</th>
                        <th>Cantidad</th>
                        <th>CostoUnitario</th>
                        <th>TotalRenglon</th>
                        <th>Acciones</th>
                    </tr>
                </thead>
                <tbody>
        `;
        
        salidas.forEach(salida => {

            tablaHTML += `
                <tr data-salida-id="${salida.Salidas_Key}">
                    <td>${salida.ProductoCodigo}</td>
                    <td>${salida.ProductoNombre}</td>
                    <td>${formatearNumero(salida.Cantidad)}</td>
                    <td>${formatearNumero(salida.CostoUnitario)}</td>
                    <td>${formatearNumero(salida.TotalRenglon)}</td>
                    <td>
                        <button class="btn btn-primary btn-wide" onclick="abrirModalEditar(
                                '${salida.SalidasDet_Key}',
                                '${salida.Productos_Key}',
                                '${salida.ProductoCodigo}',
                                '${salida.ProductoNombre}',
                                '${salida.Cantidad}',
                                '${salida.CostoUnitario}')">
                            Editar
                        </button>

                        <button class="btn btn-primary btn-wide"
                            onclick="confirmarEliminarDetalle(this)"
                            data-salidas-det-key="${salida.SalidasDet_Key}">
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
        console.error('Error al cargar salidas:', error);
        tablaContainer.innerHTML = `
            <div class="no-data" style="color: #e74c3c;">
                Error al cargar los datos. Por favor, intente nuevamente.
            </div>
        `;
    }
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

async function confirmarEliminarDetalle(btn) {
    
    const salidasDetKey = btn.dataset.salidasDetKey;

    if (!salidasDetKey) {
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
            `${API_BASE_URL}/EliminarSalidaDetalle?SalidasDetKey=${salidasDetKey}`,
            { method: 'GET' }
        );

        const data = await response.json();

        if (response.ok && data.success) {
            alert('Ítem eliminado correctamente');

            cargarListaProducto(
                document.getElementById('salida-key-display').textContent
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


function abrirModalEditar(
    salidasDetKey,
    productosKey,
    productoCodigo,
    productoNombre,
    cantidad,
    costoUnitario

) {
    const modal = document.getElementById('modal-editar');

    modal.dataset.salidasDetKey = salidasDetKey;

    const inputProducto = document.getElementById('edit-productosKey');


    inputProducto.value = `${productoCodigo} - ${productoNombre}`;


    inputProducto.dataset.productosKey = productosKey;

    document.getElementById('edit-cantidad').value = formatearNumero(cantidad);
    document.getElementById('edit-costoUnitario').value = formatearNumero(costoUnitario);

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

    
    const salidasDetKey = modal.dataset.salidasDetKey;
    const productosKey   = inputProducto.dataset.productosKey;
    const cantidad       = document.getElementById('edit-cantidad').value;
    const costoUnitario  = document.getElementById('edit-costoUnitario').value;


    if (!salidasDetKey) {
        mostrarAlertaModal('No se encontró la llave del detalle', 'error');
        return;
    }

    const params = new URLSearchParams({
        SalidasDetKey: salidasDetKey,
        ProductosKey: productosKey || '',
        Cantidad: limpiarNumero(cantidad),
        CostoUnitario: limpiarNumero(costoUnitario)
    });

    try {
        const btn = document.querySelector('#formEditar button[type="submit"]');
        btn.disabled = true;
        btn.textContent = 'Actualizando...';

        const response = await fetch(
            `${API_BASE_URL}/ActualizarSalidaDetalle?${params.toString()}`,
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
                cargarListaProducto(document.getElementById('salida-key-display').textContent);
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

let salidasDetKeyContabilizar = null;

function abrirModalContabilizar(button) {

    salidasDetKeyContabilizar = button.getAttribute('data-salidasdet-key');
    

    document.getElementById('contabilizar-salidasdet-key').textContent = salidasDetKeyContabilizar;
    

    document.getElementById('alert-contabilizar').innerHTML = '';
    
    document.getElementById('modal-contabilizar').style.display = 'block';
}

function cerrarModalContabilizar() {
    document.getElementById('modal-contabilizar').style.display = 'none';
    salidasDetKeyContabilizar = null;
}

async function confirmarContabilizacion() {
    if (!salidasDetKeyContabilizar) {
        mostrarAlerta('alert-contabilizar', 'No se ha seleccionado un detalle', 'error');
        return;
    }

    try {

        const response = await fetch(`/api/contabilizar?salidasDetKey=${salidasDetKeyContabilizar}`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            }
        });

        if (response.ok) {
            mostrarAlerta('alert-contabilizar', 'Salida contabilizada exitosamente', 'success');
            
            setTimeout(() => {
                cerrarModalContabilizar();

                cargarProductos(salidasKeyGlobal);
            }, 1500);
        } else {
            const error = await response.text();
            mostrarAlerta('alert-contabilizar', `Error: ${error}`, 'error');
        }
    } catch (error) {
        console.error('Error al contabilizar:', error);
        mostrarAlerta('alert-contabilizar', 'Error al contabilizar la salida', 'error');
    }
}

// Cerrar modal al hacer clic fuera de él
window.onclick = function(event) {
    const modal = document.getElementById('modal-contabilizar');
    if (event.target === modal) {
        cerrarModalContabilizar();
    }
}


