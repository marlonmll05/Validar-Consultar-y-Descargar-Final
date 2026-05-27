// Configuración de la API

let productoSeleccionada = null;
let filaDetalleAbierta = null;

const API_BASE_URL = `https://${window.location.hostname}:9876/almacen`;

document.addEventListener('DOMContentLoaded', function() {
    
    cargarKardex();

    const today = new Date().toISOString().split('T')[0];
    const fechaIniInput = document.getElementById('fechaIni').value = today;
    const fechaHastaInput = document.getElementById('fechaFin').value = today;
});

// Función para limpiar el formulario
function limpiarFormulario() {
    document.getElementById('formRegistrar').reset();
    const now = new Date();
    const dateTimeLocal = now.toISOString().slice(0, 16);

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

// Función para cargar y mostrar el kardex
async function cargarKardex() {

    const tablaContainer = document.getElementById('tabla-productos');
    tablaContainer.innerHTML = '<div class="loading">Cargando datos...</div>';

    const productosKey = document.getElementById('filtroProducto')?.value || -1;
    const fechaIni = document.getElementById('fechaIni')?.value;
    const fechaFin = document.getElementById('fechaFin')?.value;
    const tipo = document.getElementById('filtroTipo')?.value || 'TODOS';
    const documento = document.getElementById('filtroDocumento')?.value || '';
    const numeroDocumento = document.getElementById('filtroNumeroDocumento')?.value || -1;

    if (!fechaIni || !fechaFin) {
        tablaContainer.innerHTML =
            '<div class="no-data">Debe seleccionar un rango de fechas</div>';
        return;
    }

    const params = new URLSearchParams({
        Productos_Key: productosKey,
        FechaIni: fechaIni,
        FechaFin: fechaFin,
        Tipo: tipo,
        Documento: documento,
        NumeroDocumento: numeroDocumento
    });

    try {
        const response = await fetch(
            `${API_BASE_URL}/ListarKardex?${params.toString()}`,
            { method: 'GET' }
        );

        if (!response.ok) {
            throw new Error('Error al cargar el kardex');
        }

        const result = await response.json();

        if (!result.success || !result.data || result.data.length === 0) {
            tablaContainer.innerHTML =
                '<div class="no-data">No hay movimientos registrados</div>';
            return;
        }

        const kardex = result.data;

        let tablaHTML = `
            <table>
                <thead>
                    <tr>
                        <th>ID</th>
                        <th>Fecha</th>
                        <th>Código</th>
                        <th>Producto</th>
                        <th>Tipo</th>
                        <th>Documento</th>
                        <th>N° Documento</th>
                        <th>Cantidad</th>
                        <th>Costo Ant.</th>
                        <th>Saldo Ant.</th>
                        <th>Nuevo Saldo</th>
                        <th>Nuevo Costo</th>
                        <th>Nuevo Total</th>
                        <th>Usuario</th>
                    </tr>
                </thead>
                <tbody>
        `;

        kardex.forEach(item => {
            tablaHTML += `
                <tr class="${item.Tipo === 'ENTRADA' ? 'entrada' : 'salida'}">
                    <td>${item.Kardex_Key}</td>
                    <td>${new Date(item.Fecha).toLocaleDateString()}</td>
                    <td>${item.CodigoProducto}</td>
                    <td>${item.NombreProducto}</td>
                    <td>${item.Tipo}</td>
                    <td>${item.Documento}</td>
                    <td>${item.NumeroDocumento}</td>
                    <td>${item.Cantidad}</td>
                    <td>${item.CostoUnitarioAnt}</td>
                    <td>${item.SaldoCostoAnt}</td>
                    <td>${item.NuevoSaldoCant}</td>
                    <td>${item.NuevoCostoUnit}</td>
                    <td>${item.NuevoSaldoCosto}</td>
                    <td>${item.Usuario}</td>
                </tr>
            `;
        });

        tablaHTML += `
                </tbody>
            </table>
        `;

        tablaContainer.innerHTML = tablaHTML;

    } catch (error) {
        console.error('Error:', error);
        tablaContainer.innerHTML = `
            <div class="no-data" style="color:#e74c3c">
                Error al cargar los datos
            </div>
        `;
    }
}

function abrirModalEditar(productosKey, codigo, nombre, unidadMedida, categoriasKey, stockMinimo, activo) {

    document.getElementById('edit-productos-key').value = productosKey;
    document.getElementById('edit-codigo').value = codigo;
    document.getElementById('edit-nombre').value = nombre;
    document.getElementById('edit-unidadMedida').value = unidadMedida;
    document.getElementById('edit-categoriasKey').value = categoriasKey;
    document.getElementById('edit-stockMinimo').value = stockMinimo;
    document.getElementById('edit-activo').value = activo == 'true';
    
    document.getElementById('alert-modal').innerHTML = '';
    

    document.getElementById('modal-editar').classList.add('show');
}

// Función para cerrar el modal
function cerrarModalEditar() {
    document.getElementById('modal-editar').classList.remove('show');
}

async function desactivarProducto(productosKey){

    try{

        const response = await fetch(`${API_BASE_URL}/DesactivarProducto?ProductoKey=${productosKey}`)

        if (!response.ok){
            const dataError = await response.json();

            console.log("Error al desactivar: ", dataError);
            throw new Error('Error al desactivar producto', dataError);
        }

        const data = await response.json();
        console.log('exitoso', data)
        cargarProductos();

    }catch(error){
        console.error('Error al cargar productos:', error);
    }

    
}

// Función para mostrar alertas en el modal
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

    const productoKey = document.getElementById('edit-productos-key').value;
    const codigo = document.getElementById('edit-codigo').value;
    const nombre = document.getElementById('edit-nombre').value;
    const unidadMedida = document.getElementById('edit-unidadMedida').value;
    const categoriasKey = document.getElementById('edit-categoriasKey').value;
    const stockMinimo = document.getElementById('edit-stockMinimo').value;
    const activo = document.getElementById('edit-activo').value == 'true';

    if (!codigo || !nombre || !unidadMedida || !categoriasKey || !stockMinimo) {
        mostrarAlertaModal('Complete todos los campos obligatorios', 'error');
        return;
    }

    const params = new URLSearchParams({
        ProductoKey: productoKey,
        Codigo: codigo,
        Nombre: nombre,
        UnidadMedida: unidadMedida,
        CategoriasKey: categoriasKey,
        StockMinimo: stockMinimo,
        activo: activo
    });

    try {
        const btn = document.querySelector('#formEditar button[type="submit"]');
        btn.disabled = true;
        btn.textContent = 'Actualizando...';

        const response = await fetch(`${API_BASE_URL}/ActualizarProducto?${params.toString()}`, {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json'
            }
        });

        const data = await response.json();

        if (response.ok && data.success) {
            mostrarAlertaModal('Producto actualizado correctamente', 'success');

            setTimeout(() => {
                cerrarModalEditar();
                cargarProductos(); 
            }, 800);
        } else {
            mostrarAlertaModal(data.error || 'Error al actualizar producto', 'error');
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
