// Configuración de la API

let productoSeleccionada = null;
let filaDetalleAbierta = null;

const API_BASE_URL = `https://${window.location.hostname}:9876/almacen`;

document.addEventListener('DOMContentLoaded', function() {

    document.getElementById('usuario').value = localStorage.getItem('usuario');
    
    cargarProductos();
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

// Manejar envío del formulario
document.getElementById('formRegistrar').addEventListener('submit', async function(e) {
    e.preventDefault();
    
    const codigo = document.getElementById('codigo').value;
    const nombre = document.getElementById('nombre').value;
    const unidadMedida = document.getElementById('unidadMedida').value;
    const categoriasKey = document.getElementById('categoriasKey').value;
    const stockMinimo = document.getElementById('stockMinimo').value;
    const observaciones = document.getElementById('observaciones').value;
    const usuario = document.getElementById('usuario').value;
    
    if (!codigo || !nombre || !unidadMedida || !categoriasKey || !stockMinimo || !observaciones || !usuario) {
        mostrarAlerta('Por favor complete todos los campos requeridos', 'error');
        return;
    }
    
    const params = new URLSearchParams({
        Codigo: codigo,
        Nombre: nombre,
        UnidadMedida: unidadMedida,
        CategoriasKey: categoriasKey,
        StockMinimo: stockMinimo,
        Usuario: usuario
    });
    
    if (observaciones && observaciones.trim() !== '') {
        params.append('Observaciones', observaciones);
    }
    
    try {
        const submitBtn = document.querySelector('button[type="submit"]');
        submitBtn.disabled = true;
        submitBtn.textContent = 'Guardando...';
        
        const response = await fetch(`${API_BASE_URL}/insertarProducto?${params.toString()}`, {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json'
            }
        });
        
        const data = await response.json();
        
        if (response.ok && data.success) {
            mostrarAlerta(`Producto registrado exitosamente.`, 'success');
            
            setTimeout(() => {
                cargarProductos();
                limpiarFormulario();
            }, 1000);
        } else {
            mostrarAlerta(`Error: ${data.error || 'No se pudo registrar el producto'}`, 'error');
        }
        
    } catch (error) {
        console.error('Error al insertar producto:', error);
        mostrarAlerta('Error de conexión con el servidor', 'error');
    } finally {
        const submitBtn = document.querySelector('button[type="submit"]');
        submitBtn.disabled = false;
        submitBtn.textContent = 'Guardar Producto';
    }
});

// Función para cargar y mostrar las productos
async function cargarProductos() {

    const tablaContainer = document.getElementById('tabla-productos');
    tablaContainer.innerHTML = '<div class="loading">Cargando datos...</div>';

    const nombre = document.getElementById('filtroNombre').value.trim();
    const codigo = document.getElementById('filtroCodigo').value.trim();
    const categoria = document.getElementById('filtroCategoria').value;
    const activo = document.getElementById('filtroActivo').value;

    const params = new URLSearchParams();

    params.append("Nombre", nombre);
    params.append("Codigo", codigo);
    params.append(
        "CategoriasKey",
        categoria && categoria !== "" ? categoria : -1
    );

    if (activo !== "") {
        params.append("Estado", activo); 
    }

    try {
        const response = await fetch(
            `${API_BASE_URL}/ListarProductos?${params.toString()}`,
            { method: 'GET' }
        );

        if (!response.ok) {
            throw new Error('Error al cargar los productos');
        }

        const result = await response.json();

        if (!result.success || !result.data || result.data.length === 0) {
            tablaContainer.innerHTML =
                '<div class="no-data">No hay productos registrados</div>';
            return;
        }

        const productos = result.data;

        let tablaHTML = `
            <table>
                <thead>
                    <tr>
                        <th>ID</th>
                        <th>Código</th>
                        <th>Nombre</th>
                        <th>UnidadMedida</th>
                        <th>Categoría</th>
                        <th>Control Stock</th>
                        <th>Stock Mínimo</th>
                        <th>Acciones</th>
                    </tr>
                </thead>
                <tbody>
        `;

        productos.forEach(producto => {

            const estaActivo = producto.Activo === true || producto.Activo === 1 || producto.Activo === '1';
            const textoBoton = estaActivo ? 'Desactivar' : 'Activar';
            const claseBoton = estaActivo ? 'btn-delete' : 'btn-success';
            tablaHTML += `
                <tr data-producto-id="${producto.Productos_Key}">
                    <td>${producto.Productos_Key}</td>
                    <td>${producto.Codigo}</td>
                    <td>${producto.Nombre}</td>
                    <td>${producto.UnidadMedida}</td>
                    <td>${producto.NombreCategoria}</td>
                    <td>${producto.ControlStock}</td>
                    <td>${producto.StockMinimo}</td>
                    <td>
                        <button class="btn btn-edit"
                            onclick="abrirModalEditar(
                                ${producto.Productos_Key},
                                '${producto.Codigo}',
                                '${producto.Nombre}',
                                '${producto.UnidadMedida}',
                                '${producto.CategoriasKey}',
                                '${producto.StockMinimo}',
                                '${producto.Activo}'
                            )">
                            Editar
                        </button>

                        <button class="btn btn-delete"
                            onclick="desactivarProducto(${producto.Productos_Key})">
                            ${textoBoton}
                        </button>
                    </td>
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

