
// ============================================
// SEGURIDAD Y VALIDACIÓN DE ACCESO
// ============================================


// Validar token en localStorage
if (!localStorage.getItem('tokenSQL')) {
    window.location.href = 'loginsql.html';
}

// Validación de Acceso al módulo de soporte
window.addEventListener("DOMContentLoaded", async () => {
    try {
        const response = await fetch("/api/sql/validar-parametro-soporte");

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

// ============================================
// CONFIGURACIÓN GLOBAL Y CONSTANTES
// ============================================

const host = window.location.hostname;
const tabla = document.getElementById('resultadosTabla');

// Constantes de validación de archivos
const archivosPorFila = new Map();
const MAX_TAM_MB = 20; 
const TIPOS_PERMITIDOS = ['application/pdf', 'image/png', 'image/jpeg', 'image/gif', 'image/webp'];

// Endpoints del API
const ENDPOINT_ANEXOS = `https://${host}:9876/api/insertar-pdf`;
const ENDPOINT_LISTA_PDFS = `https://${host}:9876/api/admisiones/lista-pdfs`;
const ENDPOINT_VER_PDF    = `https://${host}:9876/api/admisiones/ver-pdf`;
const ENDPOINT_ELIMINAR_PDF = `https://${host}:9876/api/eliminar-pdf`;
const ENDPOINT_SOPORTE = `https://${host}:9876/api/soportes-anexos`;

//Variables de Paginacion 
const PAGE_SIZE = 250;
let resultadosGlobal = [];
let paginaActual = 1;
let camposMostrarGlobal = [
  "IdAtencion",
  "NomContrato",
  "Paciente",
  "Cerrada",
  "Liquidada",
  "NoContrato",
  "NFact",
  "EntornoIngreso",
  "CantSoporte"
];


// Variables de busqueda 
let currentController = null;
let listaSoporte = [];

// Configuracion de debugging
const DEBUG_ENVIO = false; 

// Referencias a elementos del DOM
const exportarCuentaCobroSwitch = document.getElementById("exportarCuentaCobro");
const mostrarFacturadasSwitch = document.getElementById("mostrarFacturadas");
const numeroFacturaInput = document.getElementById("numeroFactura");  
const cuentaCobroInput = document.getElementById("cuentaCobro");

// Referencias a modales
const modalTipos = document.getElementById('modalTiposArchivo');
const btnModalTiposSi = document.getElementById('btnModalTiposSi');
const btnModalTiposNo = document.getElementById('btnModalTiposNo');
const btnModalTiposCancelar = document.getElementById("btnModalTiposCancel")


// ============================================
// FUNCIONES UTILITARIAS
// ============================================

/**
 * Formatea una fecha de yyyy-MM-dd a yyyyMMdd
 */
function escapeHtml(text) {
    const map = { '&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#039;' };
    return String(text).replace(/[&<>"']/g, m => map[m]);
}

/**
 * Escapa caracteres HTML para prevenir inyección XSS
 */
function formatDate(date) {
    const [year, month, day] = date.split('-');
    return `${year}${month}${day}`;
}

// ============================================
// SISTEMA DE NOTIFICACIONES (TOAST)
// ============================================

/**
 * Muestra un mensaje toast en la interfaz
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
 * Actualiza la barra de progreso de un toast
 */
function actualizarToastProgreso(toast, porcentaje) {
    const progressBar = toast.querySelector('.toast-progress-bar');
    if (progressBar) {
        progressBar.style.width = `${porcentaje}%`;
    }
}

// ============================================
// MODALES DE CONFIRMACIÓN
// ============================================

/**
 * Abre el modal de tipos de archivo
 */
function abrirModalTipos() {
  modalTipos.style.display = 'flex'; 
}

/**
 * Cierra el modal de tipos de archivo
 */
function cerrarModalTipos() {
  modalTipos.style.display = 'none';
}

/**
 * Muestra un modal de confirmación simple (Sí/No)
 */
function showModalConfirm(message) {
    return new Promise((resolve) => {
    const modal = document.getElementById("modalConfirm");
    const msg = document.getElementById("modalMessage");
    const btnOk = document.getElementById("btnModalOk");
    const btnCancel = document.getElementById("btnModalCancel");

    msg.textContent = message;
    modal.style.display = "flex";

    function close(result) {
        modal.style.display = "none";
        btnOk.removeEventListener("click", okHandler);
        btnCancel.removeEventListener("click", cancelHandler);
        resolve(result);
    }

    function okHandler() { close(true); }
    function cancelHandler() { close(false); }

    btnOk.addEventListener("click", okHandler);
    btnCancel.addEventListener("click", cancelHandler);
    });
}

/**
 * Muestra modal para guardar archivos (Anexar/Reemplazar/Cancelar)
 */
function showModalGuardar(message) {
    return new Promise((resolve) => {
    const modal = document.getElementById("modalGuardar");
    const msg = document.getElementById("modalGuardarMessage");
    const btnCancel = document.getElementById("btnModalGuardarCancel");
    const btnAnexar = document.getElementById("btnModalGuardarAnexar");
    const btnReemplazar = document.getElementById("btnModalGuardarReemplazar");

    msg.textContent = message;
    modal.style.display = "flex";

    function close(result) {
        modal.style.display = "none";
        btnCancel.removeEventListener("click", cancelHandler);
        btnAnexar.removeEventListener("click", anexarHandler);
        btnReemplazar.removeEventListener("click", reemplazarHandler);
        resolve(result);
    }

    function cancelHandler()   { close(false); }
    function anexarHandler()   { close("anexar"); }
    function reemplazarHandler(){ close("reemplazar"); }

    btnCancel.addEventListener("click", cancelHandler);
    btnAnexar.addEventListener("click", anexarHandler);
    btnReemplazar.addEventListener("click", reemplazarHandler);
    });
}


// ============================================
// GESTIÓN DE SELECTS Y FORMULARIOS
// ============================================

/**
 * Llena los campos de fecha con la actual y hace una peticion para obtener la lista de soportes
 */
async function inicializarPagina() {
    const today = new Date().toISOString().split('T')[0]; // Obtener la fecha actual
    document.getElementById('fechaDesde').value = today; 
    document.getElementById('fechaHasta').value = today; 

    try {
        const r = await fetch(ENDPOINT_SOPORTE); // Solicitud para obtener datos de soporte
        if (!r.ok) throw new Error(await r.text());
        const data = await r.json(); 
        listaSoporte = Array.isArray(data) ? data : []; 
    } catch (err) {
        console.error('Error cargando soporte:', err);
        listaSoporte = []; 
    }
}

/**
 * Puebla un select con datos del servidor
 */
async function poblarSelect(selectId, idTabla, id, valueField, textField) {
    const select = document.getElementById(selectId);
    if (!select) return;

    try {
    const url = `https://${host}:9876/api/selects-filtro?idTabla=${idTabla}&id=${id}`;
    const r = await fetch(url);
    if (!r.ok) throw new Error(await r.text());
    const data = await r.json();

    select.querySelectorAll("option:not([value=''])").forEach(opt => opt.remove());

    data.forEach(item => {
        const opt = document.createElement("option");
        opt.value = item[valueField];      
        opt.textContent = item[textField];
        select.appendChild(opt);
    });
    } catch (err) {
    console.error(`Error cargando ${selectId}:`, err);

    select.querySelectorAll("option:not([value=''])").forEach(opt => opt.remove());
    const errorOpt = document.createElement("option");
    errorOpt.value = "";
    errorOpt.textContent = "❌ Error al cargar";
    select.appendChild(errorOpt);
    }
}

/**
 * Puebla el select de soportes (con opción de ver todos)
 */
async function poblarSelectSoporte(selectEl) {
    const rowKey = selectEl.dataset.rowkey;
    const switchEl = document.getElementById(`verTodos-${rowKey}`);
    const usarTodos = switchEl && switchEl.checked;


    selectEl.innerHTML = `<option value="">-- Selecciona --</option>`;

    try {
    let data = [];

    if (usarTodos) {
        const resp = await fetch(`https://${host}:9876/api/soportes-anexos-completo`);
        if (!resp.ok) throw new Error(await resp.text());
        data = await resp.json();
    } else {
        data = listaSoporte;
    }

    if (Array.isArray(data)) {
        const opts = data.map(s =>
        `<option value="${s.Id}" data-tipodoc="${s.TipoDocumento}">
            ${escapeHtml(s.nombreDocSoporte ?? '')}
        </option>`
        ).join('');
        selectEl.insertAdjacentHTML('beforeend', opts);
    }

    selectEl.addEventListener('change', (e) => {
        const selectedId = e.target.value;
        console.log(`Usuario seleccionó soporte Id: ${selectedId}`);
    });

    selectEl.dataset.loaded = "1";
    } catch (err) {
    console.error("Error poblando soportes:", err);
    selectEl.innerHTML = `<option value="">Error al cargar</option>`;
    }
}


// ============================================
// SISTEMA DE PAGINACIÓN
// ============================================

/**
 * Renderiza una página específica de resultados
 */
function renderTablaPagina(pagina) {
  const table = document.getElementById('resultadosTabla');
  const tbody = document.getElementById('tablaBody');
  const cardTitle = document.querySelector('.card-title-table');

  if (!Array.isArray(resultadosGlobal) || resultadosGlobal.length === 0) {
    table.style.display = 'none';
    return;
  }

  const totalRegistros = resultadosGlobal.length;
  const totalPaginas = Math.ceil(totalRegistros / PAGE_SIZE);

  if (pagina < 1) pagina = 1;
  if (pagina > totalPaginas) pagina = totalPaginas;
  paginaActual = pagina;

  tbody.innerHTML = '';

  const inicio = (pagina - 1) * PAGE_SIZE;
  const fin = Math.min(inicio + PAGE_SIZE, totalRegistros);
  const slice = resultadosGlobal.slice(inicio, fin);

  if (cardTitle) {
    cardTitle.textContent = `Resultados (${totalRegistros}) - Página ${pagina}/${totalPaginas}`;
  }

  slice.forEach((row, idx) => {
    const rowKey = row["IdAtencion"] ?? `fila-${inicio + idx}`;
    const idAdmision = row["IdAdmision"] ?? '';
    const idAtencion = row["IdAtencion"] ?? '';
    const idPacienteKey = row["IdPacienteKey"] ?? '';

    const tr = document.createElement('tr');
    tr.dataset.rowkey = rowKey;
    tr.dataset.idadmision = idAdmision;
    tr.dataset.idpacientekey = idPacienteKey;
    tr.dataset.idatencion = idAtencion;

    // Checkbox selección
    const tdCheckbox = document.createElement('td');
    const checkbox = document.createElement('input');
    checkbox.type = 'checkbox';
    checkbox.className = 'checkbox-row';
    checkbox.dataset.rowkey = rowKey;
    tdCheckbox.appendChild(checkbox);
    tr.appendChild(tdCheckbox);

    // Columnas normales
    camposMostrarGlobal.forEach(h => {
      const td = document.createElement('td');
      td.textContent = (row[h] !== null && row[h] !== undefined) ? row[h] : '';
      tr.appendChild(td);
    });

    // Columna 1: acciones
    const tdAccion1 = document.createElement('td');
    tdAccion1.classList.add("anexar-col");

    const btnAnexar = document.createElement('button');
    btnAnexar.type = "button";
    btnAnexar.textContent = "Anexar";
    btnAnexar.className = "btn-success btn-anexar";
    btnAnexar.innerHTML = '<i class="fa-solid fa-paperclip"></i> Anexar';
    btnAnexar.dataset.rowkey = rowKey;
    tdAccion1.appendChild(btnAnexar);

    const btnGenerar = document.createElement('button');
    btnGenerar.type = "button";
    btnGenerar.textContent = "Generación Automática";
    btnGenerar.className = "btn-primary btn-generar";
    btnGenerar.dataset.rowkey = rowKey;
    btnGenerar.dataset.idadmision = idAdmision;
    btnGenerar.dataset.idpacientekey = idPacienteKey;
    btnGenerar.dataset.idatencion = idAtencion;
    tdAccion1.appendChild(btnGenerar);

    // Columna 2: docs
    const tdAccion2 = document.createElement('td');
    tdAccion2.classList.add("doc-col");

    const btnExportar = document.createElement('button');
    btnExportar.type = "button";
    btnExportar.textContent = "Exportar";
    btnExportar.className = "btn-warning btn-exportar";
    btnExportar.innerHTML = '<i class="fa-solid fa-file-export"></i> Exportar';
    btnExportar.dataset.rowkey = rowKey;
    btnExportar.dataset.idadmision = idAdmision;
    btnExportar.dataset.idpacientekey = idPacienteKey;
    btnExportar.dataset.nfact = row["NFact"] || '';
    tdAccion2.appendChild(btnExportar);

    const btnVerPdfs = document.createElement('button');
    btnVerPdfs.type = "button";
    btnVerPdfs.textContent = "Ver Documentos";
    btnVerPdfs.className = "btn-secondary btn-verpdfs";
    btnVerPdfs.dataset.rowkey = rowKey;
    btnVerPdfs.dataset.idadmision = idAdmision;
    btnVerPdfs.dataset.idatencion = idAtencion;
    tdAccion2.appendChild(btnVerPdfs);

    tr.appendChild(tdAccion1);
    tr.appendChild(tdAccion2);

    tbody.appendChild(tr);

    // Fila panel anexar
    const trPanel = document.createElement('tr');
    trPanel.className = 'anexar-row';
    trPanel.style.display = 'none';
    trPanel.dataset.rowkey = rowKey;

    const tdPanel = document.createElement('td');
    tdPanel.colSpan = camposMostrarGlobal.length + 3;

    tdPanel.innerHTML = `
      <div class="anexar-panel">
        <h4>Adjuntar archivos para IdAtencion: <strong>${row["IdAtencion"] ?? ''}</strong></h4>
        <div class="anexar-actions">
          <div class="dropzone" data-rowkey="${rowKey}">
            <p>Arrastra aquí tus archivos (PDF o imágenes)</p>
            <button type="button" class="btn-picker" data-input="fileInput-${rowKey}">Seleccionar archivos</button>
            <input type="file" id="fileInput-${rowKey}" class="file-input" accept=".pdf,image/*" multiple hidden>
          </div>

          <div class="form-group soporte-group">
            <label for="idSoporte-${rowKey}">Documento Soporte</label>
            <select id="idSoporte-${rowKey}" name="idSoporte" data-rowkey="${rowKey}" data-loaded="0" required>
              <option value="" disabled selected>-- Selecciona --</option>
            </select>
          </div>

          <ul class="file-list" id="fileList-${rowKey}"></ul>
        </div>

        <div class="panel-footer" style="margin-top:10px; display:flex; width:100%;">
          <div class="form-group ver-todos-group" style="margin-top:6px;">
            <label style="font-size:14px; display:flex; align-items:center; gap:8px;">
              <span>Ver todas las tipificaciones</span>
              <label class="switch">
                <input type="checkbox" id="verTodos-${rowKey}" data-rowkey="${rowKey}">
                <span class="slider round"></span>
              </label>
            </label>
          </div>

          <button
            type="button"
            class="btn-success btn-guardar"
            data-rowkey="${rowKey}"
            data-idadmision="${idAdmision}"
            data-idpacientekey="${idPacienteKey}"
            style="margin-left:auto; min-width:120px; padding:12px 18px; font-size:15px; border-radius:8px; white-space:nowrap;"
          >
            Guardar
          </button>
        </div>

        <div class="error" id="error-${rowKey}" style="display:none;"></div>
      </div>
    `;

    trPanel.appendChild(tdPanel);
    tbody.appendChild(trPanel);

    if (!archivosPorFila.has(rowKey)) {
      archivosPorFila.set(rowKey, []);
    }
  });

  table.style.display = 'table';
  renderPaginacion();
}

/**
 * Renderiza los controles de paginación
 */
function renderPaginacion() {
  const pagDiv = document.getElementById('pagination');
  if (!pagDiv) return;

  const totalRegistros = resultadosGlobal.length;
  const totalPaginas = Math.ceil(totalRegistros / PAGE_SIZE);

  pagDiv.innerHTML = '';

  if (totalPaginas <= 1) {
    return;
  }

  const btnPrev = document.createElement('button');
  btnPrev.textContent = 'Anterior';
  btnPrev.disabled = paginaActual === 1;
  btnPrev.addEventListener('click', () => renderTablaPagina(paginaActual - 1));
  pagDiv.appendChild(btnPrev);

  for (let p = 1; p <= totalPaginas; p++) {
    if (p === 1 || p === totalPaginas || Math.abs(p - paginaActual) <= 2) {
      const btn = document.createElement('button');
      btn.textContent = p;
      if (p === paginaActual) {
        btn.disabled = true;
        btn.classList.add('active-page');
      }
      btn.addEventListener('click', () => renderTablaPagina(p));
      pagDiv.appendChild(btn);
    } else if (
      (p === 2 && paginaActual > 4) ||
      (p === totalPaginas - 1 && paginaActual < totalPaginas - 3)
    ) {
      const span = document.createElement('span');
      span.textContent = '...';
      pagDiv.appendChild(span);
    }
  }

  const btnNext = document.createElement('button');
  btnNext.textContent = 'Siguiente';
  btnNext.disabled = paginaActual === totalPaginas;
  btnNext.addEventListener('click', () => renderTablaPagina(paginaActual + 1));
  pagDiv.appendChild(btnNext);
}


// ============================================
// BÚSQUEDA Y FILTROS 
// ============================================

/**
 * Maneja el formulario de búsqueda de atenciones
 */

document.getElementById('mostrarFacturadas').addEventListener('change', function() {
    this.dataset.touched = 'true';
});

document.getElementById('filtrosForm').addEventListener('submit', function (e) {
    e.preventDefault();

    const submitBtn = e.submitter;
    const originalHTML = submitBtn ? submitBtn.innerHTML : '';

    if (submitBtn) {
        submitBtn.disabled = true;
        submitBtn.innerHTML = 'Buscando...';
        submitBtn.style.opacity = '0.6';
        submitBtn.style.cursor = 'not-allowed';
    }

    const errorMsg = document.getElementById('errorMsg');
    const emptyState = document.getElementById('emptyState');
    const table = document.getElementById('resultadosTabla');
    const thead = document.getElementById('tablaHead');
    const tbody = document.getElementById('tablaBody');

    errorMsg.textContent = "";
    emptyState.style.display = 'none';
    table.style.display = 'none';
    thead.innerHTML = '';
    tbody.innerHTML = '';

    if (currentController) {
    currentController.abort();
    }
    currentController = new AbortController();

    const toast = showToast("Buscando", "Obteniendo resultados...", "info", 0, true);

    let progreso = 0;
    const intervalo = setInterval(() => {
    progreso += 15;
    if (progreso > 95) progreso = 95; 
    actualizarToastProgreso(toast, progreso);
    }, 100);

    const params = new URLSearchParams();
    const campos = {
    IdAtencion: "registroAtencion",
    HistClinica: "histClinica",
    Cliente: "eps",
    NoContrato: "contrato",
    IdAreaAtencion: "idAreaAtencion",
    IdUnidadAtencion: "idUnidadAtencionCur",
    nFact: "numeroFactura",
    nCuentaCobro: "cuentaCobro",
    cantSoportes: "cantSoportes"
    };

    const fechaDesde = document.getElementById('fechaDesde').value;  // "yyyy-MM-dd"
    const fechaHasta = document.getElementById('fechaHasta').value;  // "yyyy-MM-dd"
    const formattedFechaDesde = formatDate(fechaDesde);  // "yyyyMMdd"
    const formattedFechaHasta = formatDate(fechaHasta);  // "yyyyMMdd"

    params.append('FechaDesde', formattedFechaDesde);
    params.append('FechaHasta', formattedFechaHasta);

    const checkboxFacturadas = document.getElementById('mostrarFacturadas');
    const mostrarFacturadasValue = checkboxFacturadas.dataset.touched 
    ? (checkboxFacturadas.checked ? 1 : 0) 
    : null;

    if (mostrarFacturadasValue !== null) {
    params.append('soloFacturados', mostrarFacturadasValue);
    }

    Object.entries(campos).forEach(([paramName, inputId]) => {
    const el = document.getElementById(inputId);
    if (el && el.value.trim() !== "") {
        params.append(paramName, el.value.trim());
    }
    });

    const url = `https://${host}:9876/filtros/atenciones?${params.toString()}`;
    console.log("URL generada:", url);

    fetch(url, { signal: currentController.signal })
    .then(response => {
        if (!response.ok) {
        return response.text().then(text => { throw new Error(text); });
        }
        return response.json();
    })
    .then(data => {
    clearInterval(intervalo);
    actualizarToastProgreso(toast, 100);
    setTimeout(() => toast.remove(), 400);

    const emptyState = document.getElementById('emptyState');
    const table = document.getElementById('resultadosTabla');
    const thead = document.getElementById('tablaHead');
    const tbody = document.getElementById('tablaBody');
    const errorMsg = document.getElementById('errorMsg');
    const cardTitle = document.querySelector('.card-title-table');

    errorMsg.textContent = "";
    emptyState.style.display = 'none';
    table.style.display = 'none';
    thead.innerHTML = '';
    tbody.innerHTML = '';

    if (!Array.isArray(data) || data.length === 0) {
        emptyState.style.display = 'block';
        if (cardTitle) cardTitle.textContent = 'Resultados (0)';
        showToast("Sin resultados", "No se encontraron registros.", "warning", 4000);
        resultadosGlobal = [];
        renderPaginacion(); 
        return;
    }

    resultadosGlobal = data; 

    if (cardTitle) {
        cardTitle.textContent = `Resultados (${data.length})`;
    }

    const headRow = document.createElement('tr');

    const thCheckbox = document.createElement('th');
    thCheckbox.textContent = 'Seleccionar';
    headRow.appendChild(thCheckbox);

    camposMostrarGlobal.forEach(h => {
        const th = document.createElement('th');
        th.textContent = h;
        headRow.appendChild(th);
    });

    const thAccion1 = document.createElement('th');
    thAccion1.textContent = "Acciones";
    headRow.appendChild(thAccion1);

    const thAccion2 = document.createElement('th');
    thAccion2.textContent = "Documentos";
    headRow.appendChild(thAccion2);

    thead.appendChild(headRow);

    renderTablaPagina(1);

    showToast("Éxito", `Se encontraron ${data.length} registros.`, "success", 4000);

    })

    .catch(error => {
        const errorMsg = document.getElementById('errorMsg');
        errorMsg.textContent = error.message;  
        console.error("Error en la petición:", error);  

        clearInterval(intervalo);
        actualizarToastProgreso(toast, 100);
        setTimeout(() => toast.remove(), 400);
    })
    .finally(() => { 

        if (submitBtn){
            submitBtn.disabled = false;
            submitBtn.innerHTML = originalHTML;
            submitBtn.style.opacity = '1';
            submitBtn.style.cursor = "pointer"
        }
    });
});


// ============================================
// VISOR DE PDFS INSERTADOS
//  (BOTON VER DOCUMENTOS)
// ============================================

/**
 * Renderiza pdfs insertados
 */
function abrirVisorPDFs(idAdmision, idAtencion) {
    const w = window.open('', '_blank');

    const html = `<!DOCTYPE html>
    <html lang="es">
    <head>
    <meta charset="utf-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1"/>
    <title>Documentos de Atencion ${idAtencion || ''}</title>
    <style>
        :root{
        --primary:#9b87f5; --primary-dark:#7a68c3; --secondary:#33C3F0;
        --dark:#1A1F2C; --gray:#8E9196; --light:#f8f9fa; --border:#e2e8f0;
        --success:#10b981; --danger:#ef4444;
        }
        *{box-sizing:border-box}
        body{
        font-family:'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
        background:#f5f5f9; color:var(--dark); margin:0;
        }
        header{background:#fff; box-shadow:0 1px 3px rgba(0,0,0,.08); padding:16px 20px}
        h1{margin:0; font-size:18px}
        .container{display:grid; grid-template-columns: 320px 1fr; gap:12px; padding:16px}
        .card{background:#fff; border-radius:10px; box-shadow:0 2px 6px rgba(0,0,0,.06); overflow:hidden}
        .list{padding:10px}
        .list h3{font-size:14px; margin:10px}
        .item{
        width:100%; display:flex; align-items:center; justify-content:space-between;
        gap:8px; background:#fff; border:1px solid var(--border);
        border-radius:8px; padding:10px 12px; margin:8px 0; cursor:pointer;
        transition:background .2s, border-color .2s, box-shadow .2s;
        }
        .item:hover{ background:var(--light); border-color:var(--primary) }
        .item.active{ border-color:var(--primary); box-shadow:0 0 0 3px rgba(155,135,245,.12) }
        .name{ flex:1; text-align:left; white-space:nowrap; overflow:hidden; text-overflow:ellipsis }
        .btn-del {
        border: 1px solid var(--border);
        background: #fff;
        color: #555; /* gris neutro del cesto */
        border-radius: 6px;
        padding: 2px 8px;
        line-height: 1.4;
        font-weight: 700;
        cursor: pointer;
        display: flex;
        align-items: center;
        justify-content: center;
        transition: background 0.2s, border-color 0.2s, transform 0.1s;
        }

        .btn-del:hover {
        background: #f0f0f0;      /* gris claro al pasar el mouse */
        border-color: #d0d0d0;    /* bordes un poco más oscuros */
        transform: scale(1.05);   /* leve aumento */
        }

        .viewer {
        height: calc(100vh - 120px);
        }

        .viewer iframe,
        .viewer img {
        width: 100%;
        height: 100%;
        border: 0;
        display: block;
        background: #fff;
        }

        .meta{ padding:10px 14px; border-bottom:1px solid var(--border); font-size:14px; color:var(--gray) }
        .empty{ padding:24px; color:var(--gray); }
    </style>
    </head>
    <body>
    <header>
        <h1>Documentos de IdAtencion: ${idAtencion || 'N/D'}</h1>
    </header>

    <main class="container">
        <section class="card">
        <div class="list">
            <h3>Archivos</h3>
            <div id="lista"></div>
        </div>
        </section>

        <section class="card">
        <div class="meta">Selecciona un documento para visualizarlo</div>
        <div id="viewer" class="viewer"><div class="empty">Sin documento seleccionado</div></div>
        </section>
    </main>

        <script>
            const LISTA_URL = '${ENDPOINT_LISTA_PDFS}?idAdmision=${encodeURIComponent(idAdmision)}';
            const VER_URL   = '${ENDPOINT_VER_PDF}';
            const DEL_URL   = '${ENDPOINT_ELIMINAR_PDF}';
            

            let listaActual = [];

            async function cargarLista() {
                try {
                    const r = await fetch(LISTA_URL);
                    if (!r.ok) throw new Error(await r.text());
                    
                    const arr = await r.json(); // [{idSoporteKey, nombre}]
                    listaActual = Array.isArray(arr) ? arr : [];
                    renderLista(listaActual);
                    
                    if (listaActual.length) {
                        seleccionar(listaActual[0].idSoporteKey, listaActual[0].nombre);
                    }
                } catch (err) {
                    document.getElementById('lista').innerHTML = 
                        '<div class="empty">No se pudo cargar la lista: ' + (err.message || '') + '</div>';
                }
            }

            function renderLista(arr) {
                const cont = document.getElementById('lista');
                if (!arr || !arr.length) {
                    cont.innerHTML = '<div class="empty">No hay PDFs para esta admisión.</div>';
                    return;
                }
                cont.innerHTML = arr.map(it => {
                    const nombre = escapeHtml(it.nombre || ('Documento ' + it.idSoporteKey));
                    return (
                    '<div class="item" data-soporte="' + it.idSoporteKey + '" data-nombre="' + nombre + '">' +
                        '<span class="name">' + nombre + '</span>' +
                        '<button class="btn-del" title="Eliminar" data-del="' + it.idSoporteKey + '">' +
                        // Ícono SVG de cesto de basura
                        '<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" ' +
                        'viewBox="0 0 16 16">' +
                            '<path d="M5.5 5.5A.5.5 0 0 1 6 5h4a.5.5 0 0 1 .5.5v8a.5.5 0 0 1-.5.5H6a.5.5 0 0 1-.5-.5v-8z"/>' +
                            '<path fill-rule="evenodd" d="M14.5 3a1 1 0 0 1-1 1H13v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V4h-.5a1 1 0 0 1 0-2h3a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1h3a1 1 0 0 1 1 1zM4.118 4 4 4.059V13a1 1 0 0 0 1 1h6a1 1 0 0 0 1-1V4.059L11.882 4H4.118z"/>' +
                        '</svg>' +
                        '</button>' +
                    '</div>'
                    );
                }).join('');
            }

            document.addEventListener('click', async (e) => {
                const del = e.target.closest('.btn-del');
                if (del) {
                    e.stopPropagation();
                    const idSoporteKey = del.getAttribute('data-del');
                    const item = del.closest('.item');
                    const nombre = item?.dataset?.nombre || 'Documento';
                    await eliminarSoporte(idSoporteKey, nombre, item);
                    return;
                }

                const b = e.target.closest('.item');
                if (!b) return;
                
                document.querySelectorAll('.item').forEach(x => x.classList.remove('active'));
                b.classList.add('active');
                seleccionar(b.dataset.soporte, b.dataset.nombre);
            });

            async function seleccionar(idSoporteKey, nombre) {
                const url = VER_URL + '?idAdmision=${encodeURIComponent(idAdmision)}&idSoporteKey=' + encodeURIComponent(idSoporteKey);
                const viewer = document.getElementById('viewer');
                viewer.innerHTML = '<div class="empty">Cargando...</div>';
                
                try {
                    const resp = await fetch(url);
                    if (!resp.ok) throw new Error(await resp.text());
                    
                    const ct = (resp.headers.get('Content-Type') || 'application/pdf').toLowerCase();
                    const blob = await resp.blob();
                    const objUrl = URL.createObjectURL(blob);
                    
                    if (ct.startsWith('image/')) {
                        viewer.innerHTML = '<img alt="' + escapeHtml(nombre || 'Documento') + '">';
                        viewer.querySelector('img').src = objUrl;
                    } else {
                        viewer.innerHTML = '<iframe title="' + escapeHtml(nombre || 'Documento') + '"></iframe>';
                        viewer.querySelector('iframe').src = objUrl;
                    }
                    
                    document.querySelector('.meta').textContent = nombre || ('Documento ' + idSoporteKey);
                } catch (err) {
                    viewer.innerHTML = '<div class="empty">No se pudo cargar el documento: ' + 
                                    (err.message || '') + '</div>';
                }
            }

            // Eliminar documento en backend y refrescar lista
            async function eliminarSoporte(idSoporteKey, nombre, itemEl) {

                try {
                    const url = DEL_URL + '?idAdmision=${encodeURIComponent(idAdmision)}&idSoporteKey=' + encodeURIComponent(idSoporteKey);
                    const r = await fetch(url, { method: 'GET' }); // tu endpoint usa GET
                    
                    if (!r.ok) throw new Error(await r.text());

                    // Mensaje de éxito
                    alert('Documento eliminado correctamente.');

                    // Si era el activo, limpia el visor
                    const eraActivo = itemEl?.classList.contains('active');
                    if (eraActivo) {
                        document.querySelector('.meta').textContent = 'Selecciona un documento para visualizarlo';
                        document.getElementById('viewer').innerHTML = '<div class="empty">Sin documento seleccionado</div>';
                    }

                    // Actualizar lista local
                    listaActual = listaActual.filter(x => String(x.idSoporteKey) !== String(idSoporteKey));
                    renderLista(listaActual);
                    if (listaActual.length) seleccionar(listaActual[0].idSoporteKey, listaActual[0].nombre);

                } catch (err) {
                    alert('Error al eliminar: ' + (err.message || 'Desconocido'));
                }
            }

            function escapeHtml(text) {
                const map = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' };
                return String(text || '').replace(/[&<>"']/g, m => map[m]);
            }

            cargarLista();
        <\/script>
    </body>
    </html>`;

    w.document.open();
    w.document.write(html);
    w.document.close();
}

// =====================================================================
// EVENTO PARA LIMPIAR LOS RESULTADOS Y LOS DATOS QUE INGRESO EL USUARIO
// =====================================================================

document.getElementById('btnLimpiar').addEventListener('click', () => {
    const form = document.getElementById('filtrosForm');
    form.reset();

    const today = new Date().toISOString().split('T')[0];
    document.getElementById('fechaDesde').value = today;
    document.getElementById('fechaHasta').value = today;

    document.getElementById('tablaHead').innerHTML = '';
    document.getElementById('tablaBody').innerHTML = '';
    document.getElementById('resultadosTabla').style.display = 'none';
    document.getElementById('emptyState').style.display = 'none';
    document.getElementById('errorMsg').textContent = '';

    document.querySelector('.card-title-table').textContent = 'Resultados';

    const checkboxFacturadas = document.getElementById('mostrarFacturadas');
    delete checkboxFacturadas.dataset.touched;  

    archivosPorFila.clear();

    if (currentController) {
        currentController.abort();
        currentController = null;
    }

    resultadosGlobal = [];
    paginaActual = 1;

    const pagDiv = document.getElementById('pagination');
    if (pagDiv) {
        pagDiv.innerHTML = '';
    }
});


// ============================================
// EVENTOS PARA EXPORTAR POR LOTE
// ============================================

/**
 * Evento para desactivar o activar campos y switches  
 * al activar la exportacion por lote 
 */


exportarCuentaCobroSwitch.addEventListener('change', function() {
    if (exportarCuentaCobroSwitch.checked) {
    mostrarFacturadasSwitch.disabled = true; 
    mostrarFacturadasSwitch.checked = false;  

    numeroFacturaInput.disabled = true;  
    numeroFacturaInput.value = '';  

    cuentaCobroInput.required = true;
    } else {
    mostrarFacturadasSwitch.disabled = false;  
    numeroFacturaInput.disabled = false;

    cuentaCobroInput.required = false;
    }
});

/**
 * Evento para exportar
 */
document.getElementById('btnExportar').addEventListener('click', async (event) => {
    const exportarPorCuenta = document.getElementById('exportarCuentaCobro').checked;

    const botonExp = event.target;
    const expOriginal = botonExp.innerHTML;

    if (botonExp){
        botonExp.style.cursor = "not-allowed";
        botonExp.disabled = true;
        botonExp.innerHTML = "Exportando..."
        botonExp.style.opacity = "0.6";
    }

    try {
        if (!exportarPorCuenta) {
        // Modo: Exportar solo filas seleccionadas
        const checkboxes = document.querySelectorAll('.checkbox-row:checked');

        if (checkboxes.length === 0) {
            showToast("Sin selección", "Selecciona al menos una fila para exportar", "warning", 4000);
            return;
        }

        // Desmarcar de una todas las casillas seleccionadas al exportar
        const uncheck = (cb) => {
            cb.checked = false;
            cb.dispatchEvent(new Event('change', { bubbles: true }));
        };
        checkboxes.forEach(uncheck);

        try {
            const dirHandle = await window.showDirectoryPicker();
            const toast = showToast("Exportando", `Procesando ${checkboxes.length} admisión(es)...`, "info", 0, true);

            let admisionesProcesadas = 0;
            const totalAdmisiones = checkboxes.length;

            for (const checkbox of checkboxes) {
            const rowKey = checkbox.dataset.rowkey;
            const tr = document.querySelector(`tr[data-rowkey="${rowKey}"]`);
            if (!tr) continue;

            const idAdmision = tr.dataset.idadmision;
            const idAtencion = tr.dataset.idatencion;
            const btnExportar = tr.querySelector('.btn-exportar');
            const nFact = btnExportar ? btnExportar.dataset.nfact : null;

            console.log(`Exportando admisión: ${idAdmision}, nFact: ${nFact}`);

            try {
                const form = new FormData();

                try {
                const respSoporte = await fetch(`https://${host}:9876/api/soportes-por-anexos?idAdmision=${idAdmision}`);

                if (respSoporte.status === 204) {
                    showToast("Sin PDFs", `IdAtencion ${idAtencion} no tiene PDFs`, "warning", 3000);
                } else if (!respSoporte.ok) {
                    console.warn(`Error obteniendo soportes:`, await respSoporte.text());
                } else {
                    const soportes = await respSoporte.json(); 
                    for (const idSoporteKey of soportes) {
                    try {
                        const url = new URL(`https://${host}:9876/api/exportar-pdf`);
                        url.searchParams.set("idAdmision", idAdmision);
                        url.searchParams.set("idSoporteKey", idSoporteKey);

                        const resp = await fetch(url);
                        if (resp.status === 204 || !resp.ok) continue;

                        const blob = await resp.blob();
                        const header = resp.headers.get("Content-Disposition");
                        const nombre = header?.split("filename=")[1]?.replace(/"/g, "") || `Documento_${idSoporteKey}.pdf`;

                        form.append('pdfs', new File([blob], nombre, { type: blob.type || 'application/pdf' }));
                        await new Promise(r => setTimeout(r, 120));
                    } catch (err) {
                        console.error(`Error descargando PDF ${idSoporteKey}:`, err);
                    }
                    }
                }
                } catch (err) {
                console.error(`Error listando soportes:`, err);
                }

                // 2) XML 
                let idMovDoc = null;
                if (nFact) {
                try {
                    const respXml = await fetch(`https://${host}:9876/api/generarxml/${nFact}`);
                    if (respXml.ok) {
                    const blobXml = await respXml.blob();
                    const headerXml = respXml.headers.get("Content-Disposition");
                    const filenameXml = headerXml?.split("filename=")[1]?.replace(/"/g, "") || `Factura_${nFact}.xml`;
                    idMovDoc = respXml.headers.get("X-IdMovDoc") || null;

                    form.append('xml', new File([blobXml], filenameXml, { type: 'application/xml' }));
                    } else {
                    showToast("Sin XML", `No hay XML para factura ${nFact}`, "warning", 3000);
                    admisionesProcesadas++;
                    const porcentaje = Math.round((admisionesProcesadas / totalAdmisiones) * 100);
                    actualizarToastProgreso(toast, porcentaje);
                    continue;
                    }
                } catch (err) {
                    console.error(`Error descargando XML:`, err);
                    showToast("Error", `Error descargando XML: ${err.message}`, "error", 4000);
                    admisionesProcesadas++;
                    const porcentaje = Math.round((admisionesProcesadas / totalAdmisiones) * 100);
                    actualizarToastProgreso(toast, porcentaje);
                    continue;
                }
                } else {
                showToast("Sin NFact", `La fila ${idAdmision} no tiene NFact`, "warning", 3000);
                admisionesProcesadas++;
                const porcentaje = Math.round((admisionesProcesadas / totalAdmisiones) * 100);
                actualizarToastProgreso(toast, porcentaje);
                continue;
                }

                // 3) JSON de la factura
                if (idMovDoc) {
                try {
                    const respJson = await fetch(`https://${host}:9876/facturas/generarjson/${idMovDoc}`);
                    if (respJson.ok) {
                    const blobJson = await respJson.blob();
                    const headerJson = respJson.headers.get("Content-Disposition");
                    const filenameJson = headerJson?.split("filename=")[1]?.replace(/"/g, "") || `Factura_${nFact}.json`;

                    form.append('jsonFactura', new File([blobJson], filenameJson, { type: 'application/json' }));
                    } else {
                    showToast("Sin JSON", `No hay JSON para factura ${nFact}`, "warning", 3000);
                    }
                } catch (err) {
                    console.error(`Error descargando JSON:`, err);
                    showToast("Error", `Error descargando JSON: ${err.message}`, "error", 4000);
                }
                }

                // 4) Enviar todo al backend para que arme el ZIP (allí se añade el TXT MSPS)
                const respZip = await fetch(`https://${host}:9876/api/armar-zip/${encodeURIComponent(nFact)}`, {
                method: 'POST',
                body: form
                });
                if (!respZip.ok) {
                const txt = await respZip.text();
                throw new Error(`Error al armar ZIP: ${txt}`);
                }

                // 5) Guardar el ZIP en la carpeta seleccionada
                const blobZip = await respZip.blob();
                const zipName = `${nFact}.zip`;
                const fileHandleZip = await dirHandle.getFileHandle(zipName, { create: true });
                const writableZip = await fileHandleZip.createWritable();
                await writableZip.write(blobZip);
                await writableZip.close();

                console.log(`ZIP guardado: ${zipName}`);
                showToast("Éxito", `ZIP descargado: ${zipName}`, "success", 2500);

            } catch (err) {
                console.error(`Error procesando admisión ${idAdmision}:`, err);
                showToast("Error", err.message, "error", 4500);
            }

            admisionesProcesadas++;
            const porcentaje = Math.round((admisionesProcesadas / totalAdmisiones) * 100);
            actualizarToastProgreso(toast, porcentaje);
            }

            toast.querySelector("p").textContent = `Exportación completada (${admisionesProcesadas} admisiones)`;
            toast.classList.remove("info");
            toast.classList.add("success");

            setTimeout(() => {
            if (toast.parentElement) {
                toast.classList.add('fadeOut');
                setTimeout(() => toast.remove(), 300);
            }
            }, 3000);

        } catch (err) {
            console.error("Error general exportando:", err);
            showToast("Error", err.message, "error", 5000);
        }

        } else {

            // Modo: Exportar por Cuenta de Cobro 

            const numeroCuentaCobro = document.getElementById('cuentaCobro').value.trim();

            if (!numeroCuentaCobro) {
                showToast("Campo requerido", "Ingresa un número de cuenta de cobro", "warning", 4000);
                return;
            }

            const claveValidacion = numeroCuentaCobro;
            const yaValidado = sessionStorage.getItem(claveValidacion);

            if (!yaValidado) {
 
                const toastValidacion = showToast("Validando", "Verificando cuenta de cobro...", "info", 0, true);
                console.log(`Validando cuenta de cobro: ${numeroCuentaCobro}`);

                const urlValidacion = new URL(`https://${host}:9876/api/validar-cuenta`);
                urlValidacion.searchParams.set('cuentaCobro', numeroCuentaCobro);

                try {
                    const respValidacion = await fetch(urlValidacion);

                    if (!respValidacion.ok) {
                        throw new Error(await respValidacion.text());
                    }

                    const resultadosValidacion = await respValidacion.text();

                    if (toastValidacion.parentElement) {
                        toastValidacion.classList.add('fadeOut');
                        setTimeout(() => toastValidacion.remove(), 300);
                    }

                    // Si hay resultados
                    if (resultadosValidacion && resultadosValidacion.trim().length > 0) {
                        console.warn("Se encontraron facturas con documentos faltantes");
                        showToast("Advertencia", "Se encontraron facturas con documentos faltantes", "error", 4000);

                        const blob = new Blob([resultadosValidacion], { type: 'text/plain;charset=utf-8' });
                        const link = document.createElement('a');
                        link.href = URL.createObjectURL(blob);
                        link.download = `Validacion_Cuenta_${numeroCuentaCobro}.txt`;
                        link.click();
                        URL.revokeObjectURL(link.href);
                    }

                    sessionStorage.setItem(claveValidacion, 'true');

                } catch (error) {
                    console.error("Error al validar la cuenta de cobro:", error);
                    showToast("Error", `Error al validar la cuenta de cobro`, "error", 5000);
                    return;
                } finally {
                    if (toastValidacion.parentElement) {
                        toastValidacion.classList.add('fadeOut');
                        setTimeout(() => toastValidacion.remove(), 300);
                    }
                }
            } else {
                console.log('Cuenta ya validada previamente, omitiendo validación');
            }
            
            const todasLasFilas = document.querySelectorAll('tbody tr[data-idadmision]');
            if (todasLasFilas.length === 0) {
                showToast("Sin datos", "No hay filas para exportar", "warning", 4000);
                return;
            }

            let incluirArchivos = false;

            abrirModalTipos();

            const respuestaUsuario = new Promise((resolve) => {
                btnModalTiposNo.addEventListener('click', () => {
                    cerrarModalTipos();
                    console.log('No se incluirán JSON, XML ni CUV.');
                    resolve(false);
                }, { once: true });

                btnModalTiposSi.addEventListener('click', () => {
                    cerrarModalTipos();
                    console.log('Se incluirán JSON, XML y CUV.');
                    resolve(true);
                }, { once: true });

                btnModalTiposCancelar.addEventListener('click', () => {
                    cerrarModalTipos();
                    console.log('Acción cancelada.');
                    resolve(null); 
                }, { once: true });
            });

            incluirArchivos = await respuestaUsuario;

            if (incluirArchivos === null) {
                console.log('Exportación cancelada por el usuario');
                showToast("Cancelado", "Exportación cancelada", "info", 3000);
                return; 
            }

            try {
                const toast = showToast("Exportando", `Procesando ${todasLasFilas.length} fila(s)...`, "info", 0, true);
                
                const TAMAÑO_LOTE = 10;
                const filasArray = Array.from(todasLasFilas);
                const totalFilas = filasArray.length;
                let filasProces = 0;
                
                const formFinal = new FormData();

                // Procesar en lotes de 10
                for (let i = 0; i < filasArray.length; i += TAMAÑO_LOTE) {
                    const lote = filasArray.slice(i, i + TAMAÑO_LOTE);
                    console.log(`Procesando lote ${Math.floor(i / TAMAÑO_LOTE) + 1} de ${Math.ceil(filasArray.length / TAMAÑO_LOTE)} (${lote.length} filas)`);

                    await Promise.all(lote.map(async (tr) => {
                        const idAdmision = tr.dataset.idadmision;
                        const idAtencion = tr.dataset.idatencion;
                        const btnExportar = tr.querySelector('.btn-exportar');
                        const nFact = btnExportar ? btnExportar.dataset.nfact : null;

                        console.log(`Recopilando admisión: ${idAdmision}, nFact: ${nFact}`);

                        if (!nFact) {
                            console.warn(`❌ La fila ${idAdmision} no tiene NFact; se omite.`);
                            return;
                        }

                        try {

                            let idMovDoc = null;

                            // 1) XML (OBLIGATORIO si incluirArchivos = true)
                            if (incluirArchivos) {
                                try {
                                    const respXml = await fetch(`https://${host}:9876/api/generarxml/${nFact}`);
                                    if (respXml.ok) {
                                        const blobXml = await respXml.blob();
                                        const headerXml = respXml.headers.get("Content-Disposition");
                                        const filenameXml = headerXml?.split("filename=")[1]?.replace(/"/g, "") || `Factura_${nFact}.xml`;
                                        idMovDoc = respXml.headers.get("X-IdMovDoc") || null;

                                        formFinal.append(`${nFact}_xml`, new File([blobXml], filenameXml, { type: 'application/xml' }));
                                    } else {
                                        console.warn(`❌ Sin XML para factura ${nFact} - SE OMITE FACTURA COMPLETA`);
                                        return; 
                                    }
                                } catch (err) {
                                    console.error(`❌ Error descargando XML para ${nFact} - SE OMITE FACTURA:`, err);
                                    return;
                                }

                                // 2) JSON (OBLIGATORIO si incluirArchivos = true y se obtuvo idMovDoc)
                                if (idMovDoc) {
                                    try {
                                        const respJson = await fetch(`https://${host}:9876/facturas/generarjson/${idMovDoc}`);
                                        if (respJson.ok) {
                                            const blobJson = await respJson.blob();
                                            const headerJson = respJson.headers.get("Content-Disposition");
                                            const filenameJson = headerJson?.split("filename=")[1]?.replace(/"/g, "") || `Factura_${nFact}.json`;

                                            formFinal.append(`${nFact}_jsonFactura`, new File([blobJson], filenameJson, { type: 'application/json' }));

                                        } else {
                                            console.warn(`❌ Sin JSON para factura ${nFact} - SE OMITE FACTURA COMPLETA`);
                                            return; 
                                        }
                                    } catch (err) {
                                        console.error(`❌ Error descargando JSON para ${nFact} - SE OMITE FACTURA:`, err);
                                        return;
                                    }
                                } else {
                                    console.warn(`❌ Sin idMovDoc para factura ${nFact} - SE OMITE FACTURA COMPLETA`);
                                    return;
                                }
                            }

                            // 3) PDFs 
                            try {
                                const respSoporte = await fetch(`https://${host}:9876/api/soportes-por-anexos?idAdmision=${idAdmision}`);

                                if (respSoporte.status === 204) {
                                    console.warn(`⚠️ Sin PDFs para IdAtencion ${idAtencion}`);
                                } else if (!respSoporte.ok) {
                                    console.warn(`⚠️ Error obteniendo soportes:`, await respSoporte.text());
                                } else {
                                    const soportes = await respSoporte.json();
                                    
                                    // Descargar PDFs en paralelo
                                    const pdfPromises = soportes.map(async (idSoporteKey) => {
                                        try {
                                            const url = new URL(`https://${host}:9876/api/exportar-pdf`);
                                            url.searchParams.set("idAdmision", idAdmision);
                                            url.searchParams.set("idSoporteKey", idSoporteKey);

                                            const resp = await fetch(url);
                                            if (resp.status === 204 || !resp.ok) return null;

                                            const blob = await resp.blob();
                                            const header = resp.headers.get("Content-Disposition");
                                            const nombre = header?.split("filename=")[1]?.replace(/"/g, "") || `Documento_${idSoporteKey}.pdf`;

                                            return { blob, nombre, nFact };
                                        } catch (err) {
                                            console.error(`⚠️ Error descargando PDF ${idSoporteKey}:`, err);
                                            return null;
                                        }
                                    });

                                    const pdfs = await Promise.all(pdfPromises);
                                    pdfs.filter(p => p !== null).forEach(({ blob, nombre, nFact }) => {
                                        formFinal.append(`${nFact}_pdfs`, new File([blob], nombre, { type: blob.type || 'application/pdf' }));
                                    });
                                }
                            } catch (err) {
                                console.error(`⚠️ Error listando soportes:`, err);
                            }

                            console.log(`✅ Factura ${nFact} procesada correctamente`);

                        } catch (err) {
                            console.error(`❌ Error procesando admisión ${idAdmision}:`, err);
                        }
                    }));

                    // Actualizar progreso después de cada lote (máximo 90%)
                    filasProces += lote.length;
                    const porcentaje = Math.round((filasProces / totalFilas) * 90);
                    actualizarToastProgreso(toast, porcentaje);
                    
                    console.log(`✅ Lote completado. Progreso: ${filasProces}/${totalFilas} (${porcentaje}%)`);
                }

                toast.querySelector("p").textContent = "Generando archivo ZIP...";
                actualizarToastProgreso(toast, 95);
                console.log("Enviando todos los archivos al backend...");

                // 4) Exportar por cuenta cobro
                const url = new URL(`https://${host}:9876/api/exportar-cuenta-cobro`);
                url.searchParams.set('numeroCuentaCobro', numeroCuentaCobro);
                url.searchParams.set('incluirArchivos', incluirArchivos);
                
                const response = await fetch(url, {
                    method: 'POST',
                    body: formFinal 
                });

                if (!response.ok) {
                    throw new Error(await response.text());
                }

                toast.querySelector("p").textContent = "Descargando archivo...";
                actualizarToastProgreso(toast, 98);

                // 5) Descargar el ZIP final 
                const blob = await response.blob();
                const link = document.createElement('a');
                link.href = URL.createObjectURL(blob);
                link.download = `${numeroCuentaCobro}.zip`;
                link.click();
                URL.revokeObjectURL(link.href);

                toast.querySelector("p").textContent = "¡Exportación completada!";
                toast.classList.remove("info");
                toast.classList.add("success");
                actualizarToastProgreso(toast, 100);

                setTimeout(() => {
                    if (toast.parentElement) {
                        toast.classList.add('fadeOut');
                        setTimeout(() => toast.remove(), 300);
                    }
                }, 3000);

            } catch (err) {
                console.error("Error exportando por cuenta de cobro:", err);
                showToast("Error", err.message, "error", 5000);
            }
        }
    }catch (err){
        console.error("Error general:", err);
        showToast("Error", err.message, "error", 5000);
    }finally{
        if (botonExp){
            botonExp.style.cursor = "pointer";
            botonExp.disabled = false;
            botonExp.innerHTML = expOriginal;
            botonExp.style.opacity = "1";
        }
    }
});


// ===============================
// EVENTO PARA EXPORTAR INDIVIDUAL
// ===============================

tabla.addEventListener('click', async (e) => {
    const btn = e.target.closest('.btn-exportar');
    if (!btn) return;

    const idAdmision = btn.dataset.idadmision;
    const nFact = btn.dataset.nfact;
    
    console.log("nFact obtenido:", nFact);

    try {
    const dirHandle = await window.showDirectoryPicker();

    // -------------------------------
    // Descargar PDFs
    // -------------------------------
    const respSoporte = await fetch(`https://${host}:9876/api/soportes-por-anexos?idAdmision=${idAdmision}`);
    
    if (respSoporte.status === 204) {
        showToast("Sin PDFs", "No hay PDFs asociados a esta admisión", "warning", 3000);
    } else if (!respSoporte.ok) {
        console.warn("Error obteniendo soportes:", await respSoporte.text());
    } else {
        const soportes = await respSoporte.json();
        console.log("Total soportes encontrados:", soportes.length);

        const toast = showToast("Exportando", "Exportando PDFs, por favor espera...", "info", 0, true);

        let procesados = 0;
        for (const idSoporteKey of soportes) {
        try {
            const url = new URL(`https://${host}:9876/api/exportar-pdf`);
            url.searchParams.set("idAdmision", idAdmision);
            url.searchParams.set("idSoporteKey", idSoporteKey);

            const resp = await fetch(url);
            if (resp.status === 204) continue;
            if (!resp.ok) {
            console.warn(`Soporte ${idSoporteKey} error:`, await resp.text());
            continue;
            }

            const blob = await resp.blob();
            const header = resp.headers.get("Content-Disposition");
            const nombre = header?.split("filename=")[1]?.replace(/"/g, "") || `Documento_${idSoporteKey}.pdf`;

            const fileHandle = await dirHandle.getFileHandle(nombre, { create: true });
            const writable = await fileHandle.createWritable();
            await writable.write(blob);
            await writable.close();

            console.log(`Guardado: ${nombre}`);
            await new Promise(r => setTimeout(r, 300));

        } catch (err) {
            console.error(`Error descargando soporte ${idSoporteKey}:`, err);
        }

        procesados++;
        const porcentaje = Math.round((procesados / soportes.length) * 100);
        actualizarToastProgreso(toast, porcentaje);
        }

        toast.querySelector("p").textContent = "Exportación PDFs completada";
        toast.classList.remove("info");
        toast.classList.add("success");
        setTimeout(() => {
        if (toast.parentElement) {
            toast.classList.add('fadeOut');
            setTimeout(() => toast.remove(), 300);
        }
        }, 3000);
    }

    // -----------------
    // Descargar XML 
    // -----------------
    if (nFact) {
        const respXml = await fetch(`https://${host}:9876/api/generarxml/${nFact}`);
        if (respXml.status === 204) {
        showToast("Sin XML", "No hay XML asociado a esta factura", "warning", 3000);
        } else if (!respXml.ok) {
        console.warn("Error obteniendo XML:", await respXml.text());
        } else {
        const blobXml = await respXml.blob();
        const headerXml = respXml.headers.get("Content-Disposition");
        const filenameXml = headerXml?.split("filename=")[1]?.replace(/"/g, "") || `Factura_${nFact}.xml`;

        const idMovDoc = respXml.headers.get("X-IdMovDoc");

        const fileHandleXml = await dirHandle.getFileHandle(filenameXml, { create: true });
        const writableXml = await fileHandleXml.createWritable();
        await writableXml.write(blobXml);
        await writableXml.close();

        console.log(`XML guardado: ${filenameXml}`);
        showToast("Éxito", "Archivo XML descargado correctamente", "success", 3000);

        // -------------------------------
        // Descargar JSON usando el IdMovDoc
        // -------------------------------
        if (idMovDoc) {
            const respJson = await fetch(`https://${host}:9876/facturas/generarjson/${idMovDoc}`);
            if (respJson.status === 204) {
            showToast("Sin JSON", "No hay JSON asociado a esta factura", "warning", 3000);
            } else if (!respJson.ok) {
            console.warn("Error obteniendo JSON:", await respJson.text());
            } else {
            const blobJson = await respJson.blob();
            const headerJson = respJson.headers.get("Content-Disposition");
            const filenameJson = headerJson?.split("filename=")[1]?.replace(/"/g, "") || `Fac_${nFact}.json`;

            const fileHandleJson = await dirHandle.getFileHandle(filenameJson, { create: true });
            const writableJson = await fileHandleJson.createWritable();
            await writableJson.write(blobJson);
            await writableJson.close();

            console.log(`JSON guardado: ${filenameJson}`);
            showToast("Éxito", "Archivo JSON descargado correctamente", "success", 3000);
            }
        }
        }

        // -------------------------------
        // Descargar TXT RIPS
        // -------------------------------
        try {
        const urlTxt = new URL(`https://${host}:9876/api/rips/respuesta`);
        urlTxt.searchParams.set("nFact", encodeURIComponent(nFact));

        const respTxt = await fetch(urlTxt);
        if (respTxt.status === 204) {
            console.log("Sin TXT de RIPS para", nFact);
        } else if (!respTxt.ok) {
            console.warn("Error obteniendo TXT RIPS:", await respTxt.text());
        } else {
            const contenidoTxt = await respTxt.text();

            let procesoId = "";
            try {
            const js = JSON.parse(contenidoTxt);
            if (js && typeof js === "object" && "ProcesoId" in js) {
                procesoId = String(js.ProcesoId ?? "").trim();
            }
            } catch (_) {
            }

            const nombreTxt = `ResultadosMSPS_${nFact}${procesoId ? `_ID${procesoId}` : ""}_A_CUV.txt`;

            const fileHandleTxt = await dirHandle.getFileHandle(nombreTxt, { create: true });
            const writableTxt = await fileHandleTxt.createWritable();
            await writableTxt.write(contenidoTxt);
            await writableTxt.close();

            console.log(`TXT guardado: ${nombreTxt}`);
            showToast("Éxito", "Archivo TXT (RIPS) descargado correctamente", "success", 3000);
        }
        } catch (err) {
        console.error("Error descargando TXT RIPS:", err);
        showToast("Error", `Error descargando TXT RIPS: ${err.message}`, "error", 4000);
        }
    } else {
        console.log("No se pudo obtener nFact, XML/JSON no descargados");
    }

    } catch (err) {
    console.error("Error exportando PDFs/XML/JSON:", err);
    showToast("Error", err.message, "error", 5000);
    }
});

// ==========================================================
// EVENTO PARA GENERAR DOCUMENTOS DISPONIBLES DE UNA ATENCION
// ==========================================================

tabla.addEventListener('click', async (e) => {
    const btn = e.target.closest('.btn-generar');
    if (!btn) return;

    const idAdmision = btn.dataset.idadmision;
    const idPacienteKey = btn.dataset.idpacientekey;
    const idAtencion = btn.dataset.idatencion;

    let toastProceso;

    try {
    // Verificar si ya existe documentación
    const [respCheck1, respCheck2] = await Promise.all([
        fetch(`https://${host}:9876/api/soportes-disponibles?idAdmision=${idAdmision}`),
        fetch(`https://${host}:9876/api/verificar-factura-venta?idAdmision=${idAdmision}`)
    ]);

    if (respCheck1.ok && respCheck2.ok) {
        const [soportes1, soportes2] = await Promise.all([
        respCheck1.json(), 
        respCheck2.json()
        ]);

        const tieneSoportes1 = Array.isArray(soportes1) && soportes1.length == 0;
        const tieneSoportes2 = soportes2?.cantidad == 1;

        if (tieneSoportes1 && tieneSoportes2) {
        const confirmar = await showModalConfirm(
            "Este Documento Soporte ya se encuentra generado. ¿Desea eliminarlo?"
        );
        if (!confirmar) return;

        abrirVisorPDFs(idAdmision, idAtencion);
        return;
        }
    }

    // Toast general
    toastProceso = showToast("Proceso", "Generación de documentos en curso...", "info", 0, true);

    // ===== Paso 1: Documento de apoyo diagnóstico =====
    const toastDiag = showToast("Diagnóstico", "Generando documento de apoyo diagnóstico...", "info", 0, true);

    try {
        const urlPrint = `https://${host}:9876/api/generar-apoyo-diagnostico?idPacienteKey=${idPacienteKey}&idAdmision=${idAdmision}`;
        const respPrint = await fetch(urlPrint);

        if (!respPrint.ok) throw new Error(await respPrint.text());

        const pdfBlob = await respPrint.blob();

        const fd = new FormData();
        fd.append("idAdmision",    idAdmision);
        fd.append("idPacienteKey", idPacienteKey);
        fd.append("idSoporteKey",  3);
        fd.append("tipoDocumento", 1);
        fd.append("nameFilePdf",   pdfBlob, "anexos.pdf");
        fd.append("automatico", "true");

        const insertResp = await fetch(`https://${host}:9876/api/insertar-pdf`, {
        method: "POST",
        body: fd
        });
        if (!insertResp.ok) throw new Error(await insertResp.text());

        actualizarToastProgreso(toastDiag, 100);
        toastDiag.querySelector("p").textContent = "Documento diagnóstico generado ✅";
        toastDiag.classList.replace("info", "success");
    } catch (err1) {
        console.error("Error en apoyo diagnóstico:", err1);
        actualizarToastProgreso(toastDiag, 100);
        toastDiag.querySelector("p").textContent = "❌ Error en apoyo diagnóstico";
        toastDiag.classList.replace("info", "error");
    } finally {
        setTimeout(() => {
        if (toastDiag.parentElement) {
            toastDiag.classList.add('fadeOut');
            setTimeout(() => toastDiag.remove(), 300);
        }
        }, 2500);
    }

    // ===== Paso 2: Factura (SIEMPRE SE EJECUTA) =====
    const toastFactura = showToast("Factura", "Generando factura de venta...", "info", 0, true);

    try {
        const urlFactura = new URL(`https://${host}:9876/api/descargar-factura-venta`);
        urlFactura.searchParams.set("idAdmision", idAdmision);
        urlFactura.searchParams.set("idPacienteKey", idPacienteKey);
        urlFactura.searchParams.set("idSoporteKey", "18");
        urlFactura.searchParams.set("tipoDocumento", "1");

        console.log("Llamando a API factura:", urlFactura.toString());



        const respFactura = await fetch(urlFactura);
        console.log("Respuesta factura status:", respFactura.status);

        if (!respFactura.ok) {
        const errorText = await respFactura.text();
        console.error("Error en factura:", errorText);
        throw new Error(errorText);
        }

        console.log("✅ Factura generada correctamente");
        actualizarToastProgreso(toastFactura, 100);
        toastFactura.querySelector("p").textContent = "Factura generada ✅";
        toastFactura.classList.replace("info", "success");
    } catch (errFactura) {
        console.error("Error al generar factura:", errFactura);
        actualizarToastProgreso(toastFactura, 100);
        toastFactura.querySelector("p").textContent = "❌ Error en factura";
        toastFactura.classList.replace("info", "error");
    } finally {
        setTimeout(() => {
        if (toastFactura.parentElement) {
            toastFactura.classList.add('fadeOut');
            setTimeout(() => toastFactura.remove(), 300);
        }
        }, 2500);
    }

    // ===== Paso 3: Soportes adicionales (SOLO SI EXISTEN) =====
    try {
        const respSoporte = await fetch(`https://${host}:9876/api/soportes-disponibles?idAdmision=${idAdmision}`);
        
        if (!respSoporte.ok) {
        const errorText = await respSoporte.text();
        console.error("Error en soporte-automatico:", errorText);
        throw new Error(errorText);
        }
        
        const soportes = await respSoporte.json();
        console.log("Soportes adicionales obtenidos:", soportes.length);

        if (soportes.length === 0) {
        console.log("No hay soportes adicionales para procesar");
        }

        let total = soportes.length;
        let procesados = 0;

        for (const soporte of soportes) {
        const { Id: idSoporteKey, nombreRptService: nombreSoporte, TipoDocumento: tipoDocumento } = soporte;
        
        if (!nombreSoporte || nombreSoporte.trim() === "") {
            console.warn("⚠️ Soporte omitido: nombreSoporte vacío");
            procesados++;
            continue;
        }

        const toastSoporte = showToast(
            "Soporte",
            `Procesando documento ${idSoporteKey}...`,
            "info",
            0,
            true
        );

        try {
            const urlDescargar = new URL(`https://${host}:9876/api/insertar-soportes`);
            urlDescargar.searchParams.set("idAdmision",     idAdmision);
            urlDescargar.searchParams.set("idPacienteKey",  idPacienteKey);
            urlDescargar.searchParams.set("idSoporteKey",   idSoporteKey);
            urlDescargar.searchParams.set("tipoDocumento",  tipoDocumento);
            urlDescargar.searchParams.set("nombreSoporte",  nombreSoporte);

            const resp = await fetch(urlDescargar);

            if (!resp.ok) {
            const errorText = await resp.text();
            throw new Error(errorText);
            }

            actualizarToastProgreso(toastSoporte, 100);
            toastSoporte.querySelector("p").textContent = `Soporte ${idSoporteKey} completado ✅`;
            toastSoporte.classList.replace("info", "success");
        } catch (errIter) {
            console.error("Error en soporte:", errIter);
            actualizarToastProgreso(toastSoporte, 100);
            toastSoporte.querySelector("p").textContent = `Error en soporte ${idSoporteKey}`;
            toastSoporte.classList.replace("info", "error");
        } finally {
            setTimeout(() => {
            if (toastSoporte.parentElement) {
                toastSoporte.classList.add('fadeOut');
                setTimeout(() => toastSoporte.remove(), 300);
            }
            }, 2500);

            procesados++;
            const porcentaje = Math.round((procesados / total) * 100);
            actualizarToastProgreso(toastProceso, porcentaje);
        }
        }
    } catch (errSoportes) {
        console.error("Error en soportes adicionales:", errSoportes);
    }

    // ===== Final =====
    actualizarToastProgreso(toastProceso, 100);
    toastProceso.querySelector("p").textContent = "Proceso completo ✅";
    toastProceso.classList.replace("info", "success");

    setTimeout(() => {
        if (toastProceso.parentElement) {
        toastProceso.classList.add('fadeOut');
        setTimeout(() => toastProceso.remove(), 300);
        }
    }, 3000);

    } catch (err) {
    console.error("Error general:", err);
    showToast("Error", "Error en Generación Auto: " + err.message, "error");
    
    if (toastProceso && toastProceso.parentElement) {
        actualizarToastProgreso(toastProceso, 100);
        toastProceso.querySelector("p").textContent = "Proceso con errores";
        toastProceso.classList.replace("info", "error");
        setTimeout(() => {
        toastProceso.classList.add('fadeOut');
        setTimeout(() => toastProceso.remove(), 300);
        }, 3000);
    }
    }
});

// ==========================================
// EVENTO PARA ABRIR VENTANA Y VER DOCUMENTOS
// ==========================================

tabla.addEventListener('click', (e) => {
    const btn = e.target.closest('.btn-verpdfs');
    if (!btn) return;

    const idAdmision = btn.dataset.idadmision ||
    tabla.querySelector(`tr[data-rowkey="${btn.dataset.rowkey}"]:not(.anexar-row)`)?.dataset.idadmision;

    const idAtencion = btn.dataset.idatencion ||
    tabla.querySelector(`tr[data-rowkey="${btn.dataset.rowkey}"]:not(.anexar-row)`)?.dataset.idatencion;

    if (!idAdmision) {
    showToast('Falta IdAdmision', 'No se pudo obtener IdAdmision.', 'error', 6000);
    return;
    } 

    console.log("ID ADMISION:", idAdmision);
    
    abrirVisorPDFs(idAdmision, idAtencion);
});

// ============================================
// EVENTO PARA TOGGLE DE ANEXAR FILAS EN LA TABLA
// ============================================

tabla.addEventListener('click', (e) => {
    const btn = e.target.closest('.btn-anexar');
    if (!btn) return;

    const rowKey = btn.dataset.rowkey;
    const panelRow = tabla.querySelector(`tr.anexar-row[data-rowkey="${rowKey}"]`);
    if (!panelRow) return;

    panelRow.style.display = panelRow.style.display === 'none' ? '' : 'none';

    if (panelRow.style.display !== 'none') {
        const selectSoporte = panelRow.querySelector(`select[name="idSoporte"]`);
        if (selectSoporte && selectSoporte.dataset.loaded !== '1') {
            poblarSelectSoporte(selectSoporte);
        }
    }
});

// =====================================================
// EVENTO PARA GUARDAR ARCHIVOS ASOCIADOS A UNA FILA (ANEXAR)
// =====================================================

tabla.addEventListener('click', async (e) => {
    const btn = e.target.closest('.btn-guardar');
    if (!btn) return;

    const rowKey = btn.dataset.rowkey;

    const mainRow = tabla.querySelector(`tr[data-rowkey="${rowKey}"]:not(.anexar-row)`);
    let idAdmision    = btn.dataset.idadmision    || mainRow?.dataset.idadmision    || '';
    let idPacienteKey = btn.dataset.idpacientekey || mainRow?.dataset.idpacientekey || '';

    const archivos = archivosPorFila.get(rowKey) || [];
    const errorEl = document.getElementById(`error-${rowKey}`);
    const selectSoporte = document.getElementById(`idSoporte-${rowKey}`);
    const idSoporte = selectSoporte ? (selectSoporte.value || '') : '';
    const tipoDocumento = selectSoporte 
    ? selectSoporte.options[selectSoporte.selectedIndex].dataset.tipodoc || '' 
    : '';

    if (!idAdmision) {
    showToast('Falta IdAdmision', 'No se pudo obtener IdAdmision de la fila.', 'error', 6000);
    return;
    }
    if (!idPacienteKey) {
    showToast('Falta IdPacienteKey', 'No se pudo obtener IdPacienteKey de la fila.', 'error', 6000);
    return;
    }
    if (!idSoporte || isNaN(Number(idSoporte))) {
    errorEl.textContent = 'Debes seleccionar un Documento Soporte válido.';
    errorEl.style.display = 'block';
    showToast('Documento soporte requerido', 'Selecciona un Documento Soporte.', 'error', 6000);
    return;
    }
    if (archivos.length === 0) {
    errorEl.textContent = 'Selecciona al menos un archivo antes de guardar.';
    errorEl.style.display = 'block';
    showToast('Sin archivos', 'Agrega archivos para continuar.', 'error', 5000);
    return;
    }

    let eliminarSiNo = true; 

    try {
    const respCheck = await fetch(`https://${host}:9876/api/soportes-por-anexos?idAdmision=${idAdmision}`);
    if (respCheck.ok) {
        const anexos = await respCheck.json();
        if (Array.isArray(anexos) && anexos.includes(Number(idSoporte))) {

        const confirmar = await showModalGuardar("Ya existe un registro para este soporte, ¿Qué deseas hacer?");
        if (!confirmar) {
            return; 
        }
        if (confirmar === "anexar") {
            eliminarSiNo = false; 
        } else if (confirmar === "reemplazar") {
            eliminarSiNo = true;  
        }
        }
    }
    } catch (err) {
    console.error("Error verificando anexos:", err);

    }
        
    const originalText = btn.textContent;
    btn.disabled = true;
    btn.textContent = 'Guardando...';

    const toast = showToast('Insertando', 'Procesando archivos...', 'info', 120000, true);

    try {
    // Enviar TODOS los archivos en un solo request
    const fd = new FormData();
    fd.append('idAdmision', idAdmision);
    fd.append('idPacienteKey', idPacienteKey);
    fd.append('idSoporteKey', idSoporte); 
    fd.append('tipoDocumento', tipoDocumento);

    for (const f of archivos) {
        fd.append('nameFilePdf', f, f.name);  
    }

    fd.append("automatico", "false");
    fd.append("eliminarSiNo", eliminarSiNo);

    if (DEBUG_ENVIO) {
        console.group('Enviando a backend:', ENDPOINT_ANEXOS);
        for (const [k, v] of fd.entries()) console.log(k, v instanceof File ? v.name : v);
        console.groupEnd();
    }

    const resp = await fetch(ENDPOINT_ANEXOS, { method: 'POST', body: fd });
    const text = await resp.text().catch(() => '');
    if (DEBUG_ENVIO) console.log('⬅️ Respuesta:', resp.status, resp.statusText, text);
    if (!resp.ok) throw new Error(text || 'Fallo insertando archivos');

    actualizarToastProgreso(toast, 100);

    archivosPorFila.set(rowKey, []);
    renderLista(rowKey);
    errorEl.style.color = 'var(--success)';
    errorEl.textContent = `Insert OK (${archivos.length}/${archivos.length}).`;
    errorEl.style.display = 'block';
    setTimeout(() => { errorEl.style.display = 'none'; errorEl.style.color = 'var(--danger)'; }, 3000);
    showToast('Guardado', `Se insertaron ${archivos.length} archivo(s).`, 'success', 4500);

    toast.classList.add('fadeOut'); setTimeout(() => toast.remove(), 300);

    } catch (err) {
    toast.classList.add('fadeOut'); setTimeout(() => toast.remove(), 300);
    errorEl.style.color = 'var(--danger)';
    errorEl.textContent = 'Error al guardar: ' + err.message;
    errorEl.style.display = 'block';
    showToast('Error al guardar', err.message || 'No se pudo completar la operación.', 'error', 7000);
    } finally {
    btn.disabled = false;
    btn.textContent = originalText;
    }
});

// ============================================
// EVENTO PARA ABRIR EL PICKER DE ARCHIVOS
// ============================================
tabla.addEventListener('click', (e) => {
    const picker = e.target.closest('.btn-picker');
    if (!picker) return;
    const inputId = picker.dataset.input;
    const input = document.getElementById(inputId);
    if (input) input.click();
});

// ============================================
// EVENTO PARA MANEJAR CAMBIO EN INPUT DE ARCHIVOS
// ============================================

tabla.addEventListener('change', (e) => {
    const input = e.target.closest('.file-input');
    if (!input) return;
    const rowKey = input.id.replace('fileInput-','');
    agregarArchivos(rowKey, input.files);
    input.value = '';
});

// ============================================
// DRAG & DROP PARA CARGAR ARCHIVOS (ANEXAR)
// ============================================
tabla.addEventListener('dragover', (e) => {
    const dz = e.target.closest('.dropzone');
    if (!dz) return;
    e.preventDefault();
    dz.classList.add('dragover');
});
tabla.addEventListener('dragleave', (e) => {
    const dz = e.target.closest('.dropzone');
    if (!dz) return;
    dz.classList.remove('dragover');
});
tabla.addEventListener('drop', (e) => {
    const dz = e.target.closest('.dropzone');
    if (!dz) return;
    e.preventDefault();
    dz.classList.remove('dragover');
    const rowKey = dz.dataset.rowkey;
    const files = e.dataTransfer.files;
    agregarArchivos(rowKey, files);
});

// ============================================
// QUITAR ARCHIVO DE LA FILA
// ============================================
tabla.addEventListener('click', (e) => {
    if (!e.target.classList.contains('file-remove')) return;
    const li = e.target.closest('.file-item');
    const rowKey = li.dataset.rowkey;
    const idx = Number(li.dataset.index);
    const arr = archivosPorFila.get(rowKey) || [];
    arr.splice(idx, 1);
    archivosPorFila.set(rowKey, arr);
    renderLista(rowKey);
});

// ============================================
// AGREGAR ARCHIVOS PARA UNA FILA (ANEXAR)
// ============================================
function agregarArchivos(rowKey, fileList) {
    const arr = archivosPorFila.get(rowKey) || [];
    const nuevos = Array.from(fileList);

    const errorEl = document.getElementById(`error-${rowKey}`);
    const showError = (msg) => {
    errorEl.textContent = msg;
    errorEl.style.display = 'block';
    setTimeout(() => (errorEl.style.display = 'none'), 4000);
    };

    for (const f of nuevos) {
    if (!TIPOS_PERMITIDOS.includes(f.type)) {
        showError(`Tipo no permitido: ${f.name}`);
        continue;
    }
    if (f.size > MAX_TAM_MB * 1024 * 1024) {
        showError(`Archivo supera ${MAX_TAM_MB}MB: ${f.name}`);
        continue;
    }
    arr.push(f);
    }
    archivosPorFila.set(rowKey, arr);
    renderLista(rowKey);
}

// ============================================
// RENDERIZAR LA LISTA DE ARCHIVOS EN LA FILA (ANEXAR)
// ============================================

function renderLista(rowKey) {
    const ul = document.getElementById(`fileList-${rowKey}`);
    const arr = archivosPorFila.get(rowKey) || [];
    ul.innerHTML = '';
    arr.forEach((f, i) => {
    const li = document.createElement('li');
    li.className = 'file-item';
    li.dataset.rowkey = rowKey;
    li.dataset.index = i.toString();
    li.innerHTML = `
        <span class="file-meta">${escapeHtml(f.name)} • ${(f.size/1024/1024).toFixed(2)} MB</span>
        <button type="button" class="file-remove" title="Quitar">&times;</button>
    `;
    ul.appendChild(li);
    });
}


// ==========================
// CARGA INICIAL DE SELECTS
// =========================

document.addEventListener('DOMContentLoaded', () => {

    poblarSelect("eps", 2, -1, "IdTerceroKey", "NomTercero");

    poblarSelect("idAreaAtencion", 4, -1, "IdAreaAtencion", "NomAreaAtencion");

    inicializarPagina();
});

/**
 * Manejo de cambios en el select EPS o Cliente
 */
document.getElementById("eps").addEventListener("change", (e) => {
    const idCliente = e.target.value;
    poblarSelect("contrato", 3, idCliente, "NoContrato", "NomContrato");
});

/**
 * Manejo de cambios en el select de Area Atencion
 */
document.getElementById("idAreaAtencion").addEventListener("change", (e) => {
    const idArea = e.target.value;
    poblarSelect("idUnidadAtencionCur", 6, idArea, "IdEntorno", "NomEntorno");
});


/**
 * Manejo de cambios en el select de soportes de la ventana anexar
 */

tabla.addEventListener('change', (e) => {
    const switchEl = e.target.closest('input[type="checkbox"][id^="verTodos-"]');
    if (!switchEl) return;

    const rowKey = switchEl.dataset.rowkey;
    const selectEl = document.getElementById(`idSoporte-${rowKey}`);
    if (selectEl) {
    poblarSelectSoporte(selectEl);
    }
});








