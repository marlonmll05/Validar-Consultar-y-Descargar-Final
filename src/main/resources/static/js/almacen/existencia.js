// Configuración de la API

let productoSeleccionada = null;
let filaDetalleAbierta = null;

const API_BASE_URL = `https://${window.location.hostname}:9876/almacen`;

// Inicializar fecha actual
document.addEventListener('DOMContentLoaded', function() {
    
    cargarExistencia();
    poblarSelectBodega();
    poblarSelectCategoria();

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

// Función para cargar y mostrar las existencias
async function cargarExistencia() {

    const tablaContainer = document.getElementById('tabla-productos');
    tablaContainer.innerHTML = '<div class="loading">Cargando existencias...</div>';

    const productos = document.getElementById('productosKey')?.dataset.productosKey || '';
    const bodegas = document.getElementById('bodegaKey')?.value || '';
    const categoria = document.getElementById('categoriaKey')?.value || '';

    const params = new URLSearchParams();

    if (productos.trim() !== '') {
        params.append('Productos', productos); 
    }

    if (bodegas.trim() !== '') {
        params.append('Bodegas', bodegas); 
    }

    if (categoria && categoria !== '-1') {
        params.append('Categoria', categoria);
    }

    try {
        const response = await fetch(
            `${API_BASE_URL}/ListarExistencias?${params.toString()}`,
            { method: 'GET' }
        );

        if (!response.ok) {
            throw new Error('Error al cargar existencias');
        }

        const result = await response.json();

        if (!result.success || !result.data || result.data.length === 0) {
            tablaContainer.innerHTML =
                '<div class="no-data">No hay existencias registradas</div>';
            return;
        }

        const existencias = result.data;

        let tablaHTML = `
            <table>
                <thead>
                    <tr>
                        <th>Código</th>
                        <th>Producto</th>
                        <th>Unidad</th>
                        <th>Bodega</th>
                        <th>Cantidad</th>
                        <th>Costo Prom.</th>
                        <th>Valor Inventario</th>
                    </tr>
                </thead>
                <tbody>
        `;

        existencias.forEach(item => {
            tablaHTML += `
                <tr>
                    <td>${item.Codigo}</td>
                    <td>${item.Nombre}</td>
                    <td>${item.UnidadMedida}</td>
                    <td>${item.Bodegas_Key}</td>
                    <td>${item.CantidadActual}</td>
                    <td>${item.CostoPromedio}</td>
                    <td>${item.ValorInventario}</td>
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
                Error al cargar las existencias
            </div>
        `;
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

async function poblarSelectBodega() {

    try {

        const resp = await fetch(`${API_BASE_URL}/ConsultarGenerales?Tipo=2`);
        if (!resp.ok) throw new Error(await resp.json());


        const response = await resp.json();

        const data = response.data;

        console.log(data);


        if (Array.isArray(data)) {
            const opt = data.map(s =>
            `
            <option value="${s.IdBodegaKey}">
                ${s.Bodega}
            </option>
            `
            );

            const selectBodega = document.getElementById('bodegaKey');
            selectBodega.innerHTML += opt;


        }



    } catch (err) {
        console.error("Error poblando bodegas:", err);
    }
}

async function poblarSelectCategoria() {

    try {
        const resp = await fetch(`${API_BASE_URL}/ListarCategorias`);
        if (!resp.ok){
            throw new Error("Error al listar categorias", resp.json());
        }

        const response = await resp.json();

        const data = response.data;

        if (Array.isArray(data)){
            const opt = data.map(s =>

            `
            <option value="${s.CategoriasKey}">
                ${s.Nombre}
            </option>
            `
            );

            const selectCategoria = document.getElementById('categoriaKey');
            selectCategoria.innerHTML += opt;
        }

    
    }
    catch (err){
        console.error("Error poblando select de categorias", err);
    }
}


document.addEventListener('change', (e) =>{

    const {name, value} = e.target;

    if (name === 'bodegaKey'){
        console.log("usuario selecciono bodega: ", value);
    }

    if (name === 'categoriaKey'){
        console.log("usuario selecciono categoria: ", value);
    }
})

let dropdownUpdateInterval = null;
let productosSeleccionados = [];

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

function estaSeleccionado(productosKey) {
    return productosSeleccionados.some(p => p.Productos_Key === productosKey);
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

            const seleccionado = estaSeleccionado(producto.Productos_Key);
            const productoEscapado = JSON.stringify(producto).replace(/'/g, "\\'");

            html += `
                    <tr 
                        data-producto-key="${producto.Productos_Key}"
                        class="${seleccionado ? 'seleccionado' : ''}"
                        onclick='toggleProducto(${productoEscapado}, event)'
                    >

                    <td>
                        ${producto.Nombre || '-'}
                    </td>
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
};

function toggleProducto(producto, event) {
    event.stopPropagation();
    const index = productosSeleccionados.findIndex(
        p => p.Productos_Key === producto.Productos_Key
    );

    if (index >= 0) {
        productosSeleccionados.splice(index, 1);
    } else {
        productosSeleccionados.push({
            Productos_Key: producto.Productos_Key,
            Codigo: producto.Codigo,
            Nombre: producto.Nombre
        });
    }

    actualizarInputProductos();
    actualizarEstadoVisual(producto.Productos_Key); 
}

function actualizarEstadoVisual(productoKey) {
    const fila = document.querySelector(
        `tr[data-producto-key="${productoKey}"]`
    );

    if (!fila) return;

    fila.classList.toggle('seleccionado');
}

function actualizarInputProductos() {
    if (!inputProductoActivo) return;

    if (productosSeleccionados.length === 0) {
        inputProductoActivo.value = '';
        inputProductoActivo.dataset.productosKey = '';
        return;
    }

    inputProductoActivo.value = productosSeleccionados
        .map(p => `${p.Codigo} - ${p.Nombre}`)
        .join(', ');

    inputProductoActivo.dataset.productosKey = productosSeleccionados
        .map(p => p.Productos_Key)
        .join(',');
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
