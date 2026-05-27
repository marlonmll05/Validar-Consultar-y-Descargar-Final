// Configuración de la API
let categoriaSeleccionada = null;
let filaDetalleAbierta = null;

const API_BASE_URL = `https://${window.location.hostname}:9876/almacen`;

// Inicializar fecha actual
document.addEventListener('DOMContentLoaded', function() {

    cargarCategorias();
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
    
    // Obtener valores del formulario
    const codigo = document.getElementById('codigo').value;
    const nombre = document.getElementById('nombre').value;
    const descripcion = document.getElementById('descripcion').value
    
    // Validar campos requeridos
    if (!codigo || !nombre || !descripcion) {
        mostrarAlerta('Por favor complete todos los campos requeridos', 'error');
        return;
    }
    
    // Construir URL con parámetros
    const params = new URLSearchParams({
        Codigo: codigo,
        Nombre: nombre,
    });
    
    // Agregar observaciones solo si tiene valor
    if (descripcion && descripcion.trim() !== '') {
        params.append('Descripcion', descripcion);
    }
    
    try {
        // Mostrar indicador de carga
        const submitBtn = document.querySelector('button[type="submit"]');
        submitBtn.disabled = true;
        submitBtn.textContent = 'Guardando...';
        
        // Realizar petición POST
        const response = await fetch(`${API_BASE_URL}/insertarCategoria?${params.toString()}`, {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json'
            }
        });
        
        const data = await response.json();
        
        if (response.ok && data.success) {
            mostrarAlerta(`Categoria registrada exitosamente.`, 'success');
            
            // Recargar la tabla y limpiar formulario
            setTimeout(() => {
                cargarCategorias();
                limpiarFormulario();
            }, 1000);
        } else {
            mostrarAlerta(`Error: ${data.message || 'No se pudo registrar el categoria'}`, 'error');
        }
        
    } catch (error) {
        console.error('Error al insertar categoria:', error);
        mostrarAlerta('Error de conexión con el servidor', 'error');
    } finally {
        const submitBtn = document.querySelector('button[type="submit"]');
        submitBtn.disabled = false;
        submitBtn.textContent = 'Guardar Categoria';
    }
});

// Función para cargar y mostrar las categorias
async function cargarCategorias() {
    const tablaContainer = document.getElementById('tabla-categorias');
    
    tablaContainer.innerHTML = '<div class="loading">Cargando datos...</div>';

    const nombre = document.getElementById('filtroNombre').value.trim();
    const codigo = document.getElementById('filtroCodigo').value.trim();
    const cuentaContable = document.getElementById('filtroCuentaContable').value.trim();
    const activo = document.getElementById('filtroActivo').value;


    const params = new URLSearchParams();

    params.append("Nombre", nombre);
    params.append("Codigo", codigo);
    params.append("CuentaContable", cuentaContable);

    if (activo !== "") {
        params.append("Estado", activo); 
    }

    try {
        // Realizar petición GET
        const response = await fetch(`${API_BASE_URL}/ListarCategorias?${params.toString()}`, {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json'
            }
        });
        
        if (!response.ok) {

            const data = await response.json();
            console.log('error: ', data);
            throw new Error('Error al cargar las categorias');
        }
        
        const result = await response.json();
        
        if (!result.success || !result.data || result.data.length === 0) {
            tablaContainer.innerHTML = '<div class="no-data">No hay categorias registradas</div>';
            return;
        }
        
        const categorias = result.data;
        
        // Construir tabla HTML
        let tablaHTML = `
            <table>
                <thead>
                    <tr>
                        <th>ID</th>
                        <th>Código</th>
                        <th>Nombre</th>
                        <th>Descripcion</th>
                        <th>CuentaContable</th>
                        <th>Activo</th>
                        <th>Acciones</th>
                    </tr>
                </thead>
                <tbody>
        `;
        
        categorias.forEach(categoria => {

            const estaActivo = categoria.Estado === true || categoria.Estado === 1 || categoria.Estado === '1';
            const textoBoton = estaActivo ? 'Desactivar' : 'Activar';

            tablaHTML += `
                <tr data-categoria-id="${categoria.CategoriasKey}">
                    <td>${categoria.CategoriasKey}</td>
                    <td>${categoria.Codigo}</td>
                    <td>${categoria.Nombre}</td>
                    <td>${categoria.Descripcion}</td>
                    <td>${categoria.CuentaContable}</td>
                    <td>${categoria.Estado}</td>
                    <td>
                        <button 
                            class="btn btn-edit"
                            onclick="abrirModalEditar(
                                '${categoria.CategoriasKey}',
                                '${categoria.Codigo}',
                                '${categoria.Nombre}',
                                '${categoria.Descripcion}',
                                '${categoria.Estado}',

                            )">
                            Editar
                        </button>
                        <button class="btn btn-delete" onclick="desactivarCategoria(${categoria.CategoriasKey})">${textoBoton}</button>
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
        console.error('Error al cargar categorias:', error);
        tablaContainer.innerHTML = `
            <div class="no-data" style="color: #e74c3c;">
                Error al cargar los datos. Por favor, intente nuevamente.
            </div>
        `;
    }
}                                    

function abrirModalEditar(categoriasKey, codigo, nombre, descripcion, estado) {

    document.getElementById('edit-categorias-key').value = categoriasKey;
    document.getElementById('edit-codigo').value = codigo;
    document.getElementById('edit-nombre').value = nombre;
    document.getElementById('edit-descripcion').value = descripcion;
    document.getElementById('edit-estado').value = estado == 'true';
    
    document.getElementById('alert-modal').innerHTML = '';
    
    document.getElementById('modal-editar').classList.add('show');
}

// Función para cerrar el modal
function cerrarModalEditar() {
    document.getElementById('modal-editar').classList.remove('show');
}

async function desactivarCategoria(categoriasKey){
    try{

        const response = await fetch(`${API_BASE_URL}/DesactivarCategoria?CategoriaKey=${categoriasKey}`)

        if (!response.ok){
            const dataError = await response.json();
            mostrarAlertaModal('Error al desactivar categoria: ', dataError.error, 'error');

            console.log("Error al desactivar: ", dataError);
            throw new Error('Error al desactivar categoria', dataError);
        }

        const data = await response.json();
        console.log('exitoso', data)
        cargarCategorias();

    }catch(error){
        console.error('Error al cargar categorias:', error);
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

    const categoriaKey = document.getElementById('edit-categorias-key').value;
    const codigo = document.getElementById('edit-codigo').value;
    const nombre = document.getElementById('edit-nombre').value;
    const descripcion = document.getElementById('edit-descripcion').value;
    const activo = document.getElementById('edit-estado').value === "true"; 

    if (!codigo || !nombre || !descripcion) {
        mostrarAlertaModal('Complete todos los campos obligatorios', 'error');
        return;
    }

    const params = new URLSearchParams({
        CategoriasKey: categoriaKey,
        Codigo: codigo,
        Nombre: nombre,
        Descripcion: descripcion,
        Activo: activo
    });

    try {
        const btn = document.querySelector('#formEditar button[type="submit"]');
        btn.disabled = true;
        btn.textContent = 'Actualizando...';

        const response = await fetch(`${API_BASE_URL}/ActualizarCategoria?${params.toString()}`, {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json'
            }
        });

        const data = await response.json();

        if (response.ok && data.success) {
            mostrarAlertaModal('Categoria actualizado correctamente', 'success');

            setTimeout(() => {
                cerrarModalEditar();
                cargarCategorias(); 
            }, 800);
        } else {
            mostrarAlertaModal(data.message || 'Error al actualizar categoria', 'error');
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
