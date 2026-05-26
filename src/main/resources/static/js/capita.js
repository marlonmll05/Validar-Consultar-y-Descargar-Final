// ============================================
// SEGURIDAD Y VALIDACIÓN DE ACCESO
// ============================================

// Validar token en localStorage
if (!localStorage.getItem("tokenSQL")) {
  window.location.href = "loginsql.html";
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
      window.location.href = "inicio.html";
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
const tabla = document.getElementById("resultadosTabla");

// Constantes de validación de archivos
const archivosPorFila = new Map();
const MAX_TAM_MB = 20;
const TIPOS_PERMITIDOS = [
  "application/pdf",
  "image/png",
  "image/jpeg",
  "image/gif",
  "image/webp",
];

// Endpoints del API
const ENDPOINT_LISTA_PDFS = `https://${host}:9876/api/admisiones/lista-pdfs`;
const ENDPOINT_VER_PDF = `https://${host}:9876/api/admisiones/ver-pdf`;
const ENDPOINT_ELIMINAR_PDF = `https://${host}:9876/api/eliminar-pdf`;
const ENDPOINT_SOPORTE = `https://${host}:9876/api/soportes-anexos`;

//Variables de Paginacion
const PAGE_SIZE = 1000;
let resultadosGlobal = [];
let paginaActual = 1;
let camposMostrarGlobal = [
  "tipoiden",
  "nroiden",
  "1ernombre",
  "2donombre",
  "1erapellido",
  "2doapellido",
  "ultimaadmision",
  "Nfact",
  "IdTerceroKey",
  "sexo",
  "CantidadAdmisiones",
  "IdAdmision",
];

// Variables de busqueda
let currentController = null;
let listaSoporte = [];
let multiConsultante = false;
let mostrarGeneradas = null;

// Configuracion de debugging
const DEBUG_ENVIO = false;

// Referencias a modales
const btnModalTiposSi = document.getElementById("btnModalTiposSi");
const btnModalTiposNo = document.getElementById("btnModalTiposNo");
const btnModalTiposCancelar = document.getElementById("btnModalTiposCancel");

// ============================================
// FUNCIONES UTILITARIAS
// ============================================

/**
 * Formatea una fecha de yyyy-MM-dd a yyyyMMdd
 */
function escapeHtml(text) {
  const map = {
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#039;",
  };
  return String(text).replace(/[&<>"']/g, (m) => map[m]);
}

/**
 * Escapa caracteres HTML para prevenir inyección XSS
 */
function formatDate(date) {
  const [year, month, day] = date.split("-");
  return `${year}${month}${day}`;
}

// ============================================
// SISTEMA DE NOTIFICACIONES (TOAST)
// ============================================

/**
 * Muestra un mensaje toast en la interfaz
 */
function showToast(
  title,
  message,
  type = "success",
  duration = 6000,
  showProgress = false,
) {
  const container = document.getElementById("toastContainer");
  const toast = document.createElement("div");
  toast.className = `toast ${type}`;

  toast.innerHTML = `
        <div class="toast-content">
            <strong>${title}</strong>
            <button class="close-toast" onclick="this.parentElement.parentElement.remove()">✖</button>
            <p>${message}</p>
            ${
              showProgress
                ? `
            <div class="toast-progress-container">
                <div class="toast-progress-bar" style="width: 0%;"></div>
            </div>`
                : ""
            }
        </div>
    `;

  container.appendChild(toast);

  if (!showProgress) {
    setTimeout(() => {
      if (toast.parentElement) {
        toast.classList.add("fadeOut");
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
  const progressBar = toast.querySelector(".toast-progress-bar");
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
  modalTipos.style.display = "flex";
}

/**
 * Cierra el modal de tipos de archivo
 */
function cerrarModalTipos() {
  modalTipos.style.display = "none";
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

    function okHandler() {
      close(true);
    }
    function cancelHandler() {
      close(false);
    }

    btnOk.addEventListener("click", okHandler);
    btnCancel.addEventListener("click", cancelHandler);
  });
}

function showModalEncriptar(message) {
  return new Promise((resolve) => {
    const modal = document.getElementById("modalEncriptar");
    const msg = document.getElementById("modalEncriptarMessage");
    const btnCancel = document.getElementById("btnModalEncriptarCancel");
    const btnEncriptar = document.getElementById("btnModalEncriptar");
    const btnExportar = document.getElementById("btnModalEncriptarExportar");

    msg.textContent = message;
    modal.style.display = "flex";

    function close(result) {
      modal.style.display = "none";
      btnCancel.removeEventListener("click", cancelHandler);
      btnEncriptar.removeEventListener("click", encriptarHandler);
      btnExportar.removeEventListener("click", exportarHandler);
      resolve(result);
    }

    function cancelHandler() {
      close(false);
    }
    function encriptarHandler() {
      close("encriptar");
    }
    function exportarHandler() {
      close("exportar");
    }

    btnCancel.addEventListener("click", cancelHandler);
    btnEncriptar.addEventListener("click", encriptarHandler);
    btnExportar.addEventListener("click", exportarHandler);
  });
}

// ============================================
// GESTIÓN DE SELECTS Y FORMULARIOS
// ============================================

/**
 * Llena los campos de fecha con la actual y hace una peticion para obtener la lista de soportes
 */
async function inicializarPagina() {
  const today = new Date().toISOString().split("T")[0]; // Obtener la fecha actual
  document.getElementById("fechaDesde").value = today;
  document.getElementById("fechaHasta").value = today;

  try {
    const r = await fetch(ENDPOINT_SOPORTE); // Solicitud para obtener datos de soporte
    if (!r.ok) throw new Error(await r.text());
    const data = await r.json();
    listaSoporte = Array.isArray(data) ? data : [];
  } catch (err) {
    console.error("Error cargando soporte:", err);
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

    select
      .querySelectorAll("option:not([value=''])")
      .forEach((opt) => opt.remove());

    data.forEach((item) => {
      const opt = document.createElement("option");
      opt.value = item[valueField];
      opt.textContent = item[textField];
      select.appendChild(opt);
    });
  } catch (err) {
    console.error(`Error cargando ${selectId}:`, err);

    select
      .querySelectorAll("option:not([value=''])")
      .forEach((opt) => opt.remove());
    const errorOpt = document.createElement("option");
    errorOpt.value = "";
    errorOpt.textContent = "⚠ Error al cargar";
    select.appendChild(errorOpt);
  }
}

/**
 * Puebla el select de soportes (con opción de ver todos)
 */
async function poblarSelectTipoSoporte() {
  const selectEl = document.getElementById("idDocSoporte");
  if (!selectEl) return;

  selectEl.innerHTML = `<option value="" selected>-- Selecciona un soporte --</option>`;

  try {
    const resp = await fetch(
      `https://${host}:9876/api/soportes-anexos-completo`,
    );
    if (!resp.ok) throw new Error(await resp.text());
    const data = await resp.json();

    if (Array.isArray(data)) {
      const opts = data
        .map(
          (s) =>
            `<option value="${s.Id}" data-tipodoc="${s.TipoDocumento}">
                    ${escapeHtml(s.nombreDocSoporte ?? "")}
                </option>`,
        )
        .join("");
      selectEl.insertAdjacentHTML("beforeend", opts);
    }
  } catch (err) {
    console.error("Error poblando unidad atención:", err);
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
  const table = document.getElementById("resultadosTabla");
  const tbody = document.getElementById("tablaBody");
  const cardTitle = document.querySelector(".card-title-table");

  if (!Array.isArray(resultadosGlobal) || resultadosGlobal.length === 0) {
    table.style.display = "none";
    return;
  }

  const totalRegistros = resultadosGlobal.length;
  const totalPaginas = Math.ceil(totalRegistros / PAGE_SIZE);

  if (pagina < 1) pagina = 1;
  if (pagina > totalPaginas) pagina = totalPaginas;
  paginaActual = pagina;

  tbody.innerHTML = "";

  const inicio = (pagina - 1) * PAGE_SIZE;
  const fin = Math.min(inicio + PAGE_SIZE, totalRegistros);
  const slice = resultadosGlobal.slice(inicio, fin);

  if (cardTitle) {
    cardTitle.textContent = `Resultados (${totalRegistros}) - Página ${pagina}/${totalPaginas}`;
  }

  slice.forEach((row, idx) => {  
    const rowKey = row["IdAtencion"] ?? `fila-${inicio + idx}`;
    const idAdmision = row["IdAdmision"] ?? "";
    const idPacienteKey = row["IdPacienteKey"] ?? "";
    const idTerceroKey = row["IdTerceroKey"] ?? "";
    const nombreArchivo = row["nombrearchivo"] ?? "";

    const tr = document.createElement("tr");
    tr.dataset.rowkey = rowKey;
    tr.dataset.idadmision = idAdmision;
    tr.dataset.idpacientekey = idPacienteKey;
    tr.dataset.idtercerokey = idTerceroKey;
    tr.dataset.nombrearchivo = nombreArchivo;

    // Checkbox selección
    const tdCheckbox = document.createElement("td");
    const checkbox = document.createElement("input");
    checkbox.type = "checkbox";
    checkbox.className = "checkbox-row";
    checkbox.dataset.rowkey = rowKey;
    tdCheckbox.appendChild(checkbox);
    tr.appendChild(tdCheckbox);

    camposMostrarGlobal.forEach((h) => {
      const td = document.createElement("td");
      td.textContent = row[h] !== null && row[h] !== undefined ? row[h] : "";

      if (h === "CantSoporte") {
        td.classList.add("cant-soportes");
        td.dataset.idadmision = idAdmision;
      }

        if (h === "IdAdmision") {
            td.style.maxWidth = "80px";
            td.style.overflow = "hidden";
            td.style.textOverflow = "ellipsis";
            td.style.whiteSpace = "nowrap";
            td.title = td.textContent; // tooltip con el valor completo al hacer hover
        }

      tr.appendChild(td);
    });

    // Columna 1: acciones
    const tdAccion1 = document.createElement("td");
    tdAccion1.classList.add("anexar-col");

    const btnGenerar = document.createElement("button");
    btnGenerar.type = "button";
    btnGenerar.textContent = "Generación Automática";
    btnGenerar.className = "btn-primary btn-generar";
    btnGenerar.dataset.rowkey = rowKey;
    btnGenerar.dataset.idadmision = idAdmision;
    btnGenerar.dataset.idpacientekey = idPacienteKey;
    tdAccion1.appendChild(btnGenerar);

    // Columna 2: docs
    const tdAccion2 = document.createElement("td");
    tdAccion2.classList.add("doc-col");

    const btnExportar = document.createElement("button");
    btnExportar.type = "button";
    btnExportar.className = "btn-warning btn-exportar";
    btnExportar.innerHTML = '<i class="fa-solid fa-file-export"></i> Exportar';
    btnExportar.dataset.rowkey = rowKey;
    btnExportar.dataset.idadmision = idAdmision;
    btnExportar.dataset.nombrearchivo = nombreArchivo;
    btnExportar.dataset.idpacientekey = idPacienteKey;
    btnExportar.dataset.idtercerokey = idTerceroKey;
    btnExportar.dataset.nfact = row["Nfact"] || "";
    tdAccion2.appendChild(btnExportar);

    const btnVerPdfs = document.createElement("button");
    btnVerPdfs.type = "button";
    btnVerPdfs.textContent = "Ver Documentos";
    btnVerPdfs.className = "btn-secondary btn-verpdfs";
    btnVerPdfs.dataset.rowkey = rowKey;
    btnVerPdfs.dataset.idadmision = idAdmision;
    tdAccion2.appendChild(btnVerPdfs);

    tr.appendChild(tdAccion1);
    tr.appendChild(tdAccion2);
    tbody.appendChild(tr);
  });

  table.style.display = "table";
  renderPaginacion();
}

/**
 * Renderiza los controles de paginación
 */
function renderPaginacion() {
  const pagDiv = document.getElementById("pagination");
  if (!pagDiv) return;

  const totalRegistros = resultadosGlobal.length;
  const totalPaginas = Math.ceil(totalRegistros / PAGE_SIZE);

  pagDiv.innerHTML = "";

  if (totalPaginas <= 1) {
    return;
  }

  const btnPrev = document.createElement("button");
  btnPrev.textContent = "Anterior";
  btnPrev.disabled = paginaActual === 1;
  btnPrev.addEventListener("click", () => renderTablaPagina(paginaActual - 1));
  pagDiv.appendChild(btnPrev);

  for (let p = 1; p <= totalPaginas; p++) {
    if (p === 1 || p === totalPaginas || Math.abs(p - paginaActual) <= 2) {
      const btn = document.createElement("button");
      btn.textContent = p;
      if (p === paginaActual) {
        btn.disabled = true;
        btn.classList.add("active-page");
      }
      btn.addEventListener("click", () => renderTablaPagina(p));
      pagDiv.appendChild(btn);
    } else if (
      (p === 2 && paginaActual > 4) ||
      (p === totalPaginas - 1 && paginaActual < totalPaginas - 3)
    ) {
      const span = document.createElement("span");
      span.textContent = "...";
      pagDiv.appendChild(span);
    }
  }

  const btnNext = document.createElement("button");
  btnNext.textContent = "Siguiente";
  btnNext.disabled = paginaActual === totalPaginas;
  btnNext.addEventListener("click", () => renderTablaPagina(paginaActual + 1));
  pagDiv.appendChild(btnNext);
}

// ============================================
// BÚSQUEDA Y FILTROS
// ============================================

/**
 * Maneja el formulario de búsqueda de atenciones
 */

document.getElementById("filtrosForm").addEventListener("submit", function (e) {
  e.preventDefault();

  const submitBtn = e.submitter;
  const originalHTML = submitBtn ? submitBtn.innerHTML : "";

  if (submitBtn) {
    submitBtn.disabled = true;
    submitBtn.innerHTML = "Buscando...";
    submitBtn.style.opacity = "0.6";
    submitBtn.style.cursor = "not-allowed";
  }

  const errorMsg = document.getElementById("errorMsg");
  const emptyState = document.getElementById("emptyState");
  const table = document.getElementById("resultadosTabla");
  const thead = document.getElementById("tablaHead");
  const tbody = document.getElementById("tablaBody");

  errorMsg.textContent = "";
  emptyState.style.display = "none";
  table.style.display = "none";
  thead.innerHTML = "";
  tbody.innerHTML = "";

  if (currentController) {
    currentController.abort();
  }
  currentController = new AbortController();

  const toast = showToast(
    "Buscando",
    "Obteniendo resultados...",
    "info",
    0,
    true,
  );

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
    idDocSoporte: "idDocSoporte",
    cantSoportes: "cantSoportes",

  };

  const fechaDesde = document.getElementById("fechaDesde").value; // "yyyy-MM-dd"
  const fechaHasta = document.getElementById("fechaHasta").value; // "yyyy-MM-dd"
  const formattedFechaDesde = formatDate(fechaDesde); // "yyyyMMdd"
  const formattedFechaHasta = formatDate(fechaHasta); // "yyyyMMdd"

  params.append("FechaDesde", formattedFechaDesde);
  params.append("FechaHasta", formattedFechaHasta);
  params.append("multiConsultante", multiConsultante);
    if (mostrarGeneradas !== null) {
    params.append("mostrarGeneradas", mostrarGeneradas);
    }

  Object.entries(campos).forEach(([paramName, inputId]) => {
    const el = document.getElementById(inputId);
    if (el && el.value.trim() !== "") {
      params.append(paramName, el.value.trim());
    }
  });

  const url = `https://${host}:9876/filtros/capita?${params.toString()}`;
  console.log("URL generada:", url);

  fetch(url, { signal: currentController.signal })
    .then((response) => {
      if (!response.ok) {
        return response.text().then((text) => {
          throw new Error(text);
        });
      }
      return response.json();
    })
    .then((data) => {
      clearInterval(intervalo);
      actualizarToastProgreso(toast, 100);
      setTimeout(() => toast.remove(), 400);

      const emptyState = document.getElementById("emptyState");
      const table = document.getElementById("resultadosTabla");
      const thead = document.getElementById("tablaHead");
      const tbody = document.getElementById("tablaBody");
      const errorMsg = document.getElementById("errorMsg");
      const cardTitle = document.querySelector(".card-title-table");

      errorMsg.textContent = "";
      emptyState.style.display = "none";
      table.style.display = "none";
      thead.innerHTML = "";
      tbody.innerHTML = "";

      if (!Array.isArray(data) || data.length === 0) {
        emptyState.style.display = "block";
        if (cardTitle) cardTitle.textContent = "Resultados (0)";
        showToast(
          "Sin resultados",
          "No se encontraron registros.",
          "warning",
          4000,
        );
        resultadosGlobal = [];
        renderPaginacion();
        return;
      }

      resultadosGlobal = data;

      if (cardTitle) {
        cardTitle.textContent = `Resultados (${data.length})`;
      }

      const headRow = document.createElement("tr");

      const thCheckbox = document.createElement("th");
      const selectAllInput = document.createElement("input");
      selectAllInput.type = "checkbox";
      selectAllInput.id = "selectAll";

      thCheckbox.appendChild(selectAllInput);
      headRow.appendChild(thCheckbox);

      selectAllInput.addEventListener("change", function () {
        const checkboxes = document.querySelectorAll(".checkbox-row");
        checkboxes.forEach((cb) => (cb.checked = this.checked));
      });

      camposMostrarGlobal.forEach((h) => {
        const th = document.createElement("th");
        th.textContent = h;
        headRow.appendChild(th);
      });

      const thAccion1 = document.createElement("th");
      thAccion1.textContent = "Acciones";
      headRow.appendChild(thAccion1);

      const thAccion2 = document.createElement("th");
      thAccion2.textContent = "Documentos";
      headRow.appendChild(thAccion2);

      thead.appendChild(headRow);

      renderTablaPagina(1);

      showToast(
        "Éxito",
        `Se encontraron ${data.length} registros.`,
        "success",
        4000,
      );

      actualizarBotonesExportar();
    })

    

    .catch((error) => {
      const errorMsg = document.getElementById("errorMsg");
      errorMsg.textContent = error.message;
      console.error("Error en la petición:", error);

      clearInterval(intervalo);
      actualizarToastProgreso(toast, 100);
      setTimeout(() => toast.remove(), 400);
    })
    .finally(() => {
      if (submitBtn) {
        submitBtn.disabled = false;
        submitBtn.innerHTML = originalHTML;
        submitBtn.style.opacity = "1";
        submitBtn.style.cursor = "pointer";
      }
    });
});

function abrirVisorPDFs(idAdmision) {
  const w = window.open("", "_blank");

  const html = `<!DOCTYPE html>
    <html lang="es">
    <head>
    <meta charset="utf-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1"/>
    <title>Documentos de Admision ${idAdmision}</title>
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
        color: #555;
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
        background: #f0f0f0;
        border-color: #d0d0d0;
        transform: scale(1.05);
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
        <h1>Documentos de IdAdmision: ${idAdmision}</h1>
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
                    const arr = await r.json();
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
                    viewer.innerHTML = '<div class="empty">No se pudo cargar el documento: ' + (err.message || '') + '</div>';
                }
            }

            async function eliminarSoporte(idSoporteKey, nombre, itemEl) {
                try {
                    const url = DEL_URL + '?idAdmision=${encodeURIComponent(idAdmision)}&idSoporteKey=' + encodeURIComponent(idSoporteKey);
                    const r = await fetch(url, { method: 'GET' });
                    if (!r.ok) throw new Error(await r.text());
                    alert('Documento eliminado correctamente.');
                    const eraActivo = itemEl?.classList.contains('active');
                    if (eraActivo) {
                        document.querySelector('.meta').textContent = 'Selecciona un documento para visualizarlo';
                        document.getElementById('viewer').innerHTML = '<div class="empty">Sin documento seleccionado</div>';
                    }
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

document.getElementById("btnLimpiar").addEventListener("click", () => {
  const form = document.getElementById("filtrosForm");
  form.reset();

  const today = new Date().toISOString().split("T")[0];
  document.getElementById("fechaDesde").value = today;
  document.getElementById("fechaHasta").value = today;

  document.getElementById("tablaHead").innerHTML = "";
  document.getElementById("tablaBody").innerHTML = "";
  document.getElementById("resultadosTabla").style.display = "none";
  document.getElementById("emptyState").style.display = "none";
  document.getElementById("errorMsg").textContent = "";

  document.querySelector(".card-title-table").textContent = "Resultados";

  archivosPorFila.clear();

  if (currentController) {
    currentController.abort();
    currentController = null;
  }

  resultadosGlobal = [];
  paginaActual = 1;

  const pagDiv = document.getElementById("pagination");
  if (pagDiv) {
    pagDiv.innerHTML = "";
  }
});

// ============================================
// EVENTOS PARA EXPORTAR POR LOTE
// ============================================
document
  .getElementById("btnExportar")
  .addEventListener("click", async (event) => {
    const botonExp = event.target;
    const expOriginal = botonExp.innerHTML;

    if (botonExp) {
      botonExp.style.cursor = "not-allowed";
      botonExp.disabled = true;
      botonExp.innerHTML = "Exportando...";
      botonExp.style.opacity = "0.6";
    }

    try {
      const checkboxes = document.querySelectorAll(".checkbox-row:checked");
      const selectAll = document.getElementById("selectAll");

      if (checkboxes.length === 0) {
        showToast(
          "Sin selección",
          "Selecciona al menos una fila para exportar",
          "warning",
          4000,
        );
        return;
      }

      const uncheck = (cb) => {
        cb.checked = false;
        cb.dispatchEvent(new Event("change", { bubbles: true }));
      };
      checkboxes.forEach(uncheck);

      if (selectAll) selectAll.checked = false;

      try {

        const dirHandle = await window.showDirectoryPicker();
        const toast = showToast(
          "Exportando",
          `Procesando ${checkboxes.length} admisión(es)...`,
          "info",
          0,
          true,
        );

        let admisionesProcesadas = 0;
        const totalAdmisiones = checkboxes.length;

        for (const checkbox of checkboxes) {
          const rowKey = checkbox.dataset.rowkey;
          const tr = document.querySelector(`tr[data-rowkey="${rowKey}"]`);
          if (!tr) continue;
            

          const idAdmisionRaw = tr.dataset.idadmision;
          const nombreArchivo = tr.dataset.nombrearchivo;
          const idAdmisiones = idAdmisionRaw
            .split(",")
            .map((id) => id.trim())
            .filter(Boolean);

          try {
            const url = new URL(`https://${host}:9876/api/unificar-pdfs`);
            idAdmisiones.forEach((id) =>
              url.searchParams.append("idAdmisiones", id),
            );
            url.searchParams.append("nombreCapita", nombreArchivo);


            const resp = await fetch(url);

            if (resp.status === 204) {
              showToast(
                "Sin PDFs",
                `Admisión(es) ${idAdmisionRaw} no tienen PDFs`,
                "warning",
                3000,
              );
            } else if (!resp.ok) {
              throw new Error(await resp.text());
            } else {
              const blob = await resp.blob();
              const header = resp.headers.get("Content-Disposition");
              const nombre =
                header?.split("filename=")[1]?.replace(/"/g, "") ||
                `Documentos_${idAdmisionRaw}.pdf`;

              const fileHandle = await dirHandle.getFileHandle(nombre, {
                create: true,
              });
              const writable = await fileHandle.createWritable();
              await writable.write(blob);
              await writable.close();

              console.log(`PDF unificado guardado: ${nombre}`);
              showToast("Éxito", `PDF guardado: ${nombre}`, "success", 2500);
            }
          } catch (err) {
            console.error(`Error procesando admisión ${idAdmisionRaw}:`, err);
            showToast("Error", err.message, "error", 4500);
          }

          admisionesProcesadas++;
          const porcentaje = Math.round(
            (admisionesProcesadas / totalAdmisiones) * 100,
          );
          actualizarToastProgreso(toast, porcentaje);
        }

        toast.querySelector("p").textContent =
          `Exportación completada (${admisionesProcesadas} admisiones)`;
        toast.classList.remove("info");
        toast.classList.add("success");

        setTimeout(() => {
          if (toast.parentElement) {
            toast.classList.add("fadeOut");
            setTimeout(() => toast.remove(), 300);
          }
        }, 3000);
      } catch (err) {
        console.error("Error general exportando:", err);
        showToast("Error", err.message, "error", 5000);
      }
    } catch (err) {
      console.error("Error general:", err);
      showToast("Error", err.message, "error", 5000);
    } finally {
      if (botonExp) {
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
tabla.addEventListener("click", async (e) => {
  const btn = e.target.closest(".btn-exportar");
  if (!btn) return;

  const expOriginal = btn.innerHTML;

  btn.disabled = true;
  btn.style.opacity = "0.6";
  btn.style.cursor = "not-allowed";
  btn.innerHTML = "Exportando...";

  let toast;

  try {
    const idAdmisionRaw = btn.dataset.idadmision;
    const nombreArchivo = btn.dataset.nombrearchivo;
    const idTerceroKey = btn.dataset.idtercerokey;

    const idAdmisiones = idAdmisionRaw
      .split(",")
      .map((id) => id.trim())
      .filter(Boolean);

    const confirmar = await showModalEncriptar(
      "Deseas solo exportar o exportar y encriptar este soporte"
    );

    if (confirmar == false) return;

    const dirHandle = await window.showDirectoryPicker();

    toast = showToast("Exportando", "Generando PDF unificado...", "info", 0, true);

    const url = new URL(`https://${host}:9876/api/unificar-pdfs`);
    idAdmisiones.forEach((id) => url.searchParams.append("idAdmisiones", id));
    url.searchParams.append("nombreCapita", nombreArchivo);

    const resp = await fetch(url);

    if (resp.status === 204) {
      actualizarToastProgreso(toast, 100);
      toast.querySelector("p").textContent = "No hay PDFs asociados";
      toast.classList.replace("info", "warning");
      setTimeout(() => {
        if (toast.parentElement) {
          toast.classList.add("fadeOut");
          setTimeout(() => toast.remove(), 300);
        }
      }, 2500);
      return;
    }

    if (!resp.ok) {
      throw new Error(await resp.text());
    }

    const blob = await resp.blob();
    actualizarToastProgreso(toast, 50);

    const header = resp.headers.get("Content-Disposition");
    const nombre =
      header?.split("filename=")[1]?.replace(/"/g, "") ||
      `${nombreArchivo || "Documentos"}.pdf`;

    if (confirmar === "encriptar") {
      toast.querySelector("p").textContent = "Cifrando archivo...";

      const formData = new FormData();
      formData.append("archivo", new File([blob], nombre));
      formData.append("IdTerceroKey", idTerceroKey);

      const respCifrado = await fetch(`https://${host}:9876/cifrado/cifrar`, {
        method: "POST",
        body: formData,
      });

      if (respCifrado.ok) {
        const zipBlob = await respCifrado.blob();
        const fileHandle = await dirHandle.getFileHandle(nombre + ".zip", { create: true });
        const writable = await fileHandle.createWritable();
        await writable.write(zipBlob);
        await writable.close();
      }
    } else {
      const fileHandle = await dirHandle.getFileHandle(nombre, { create: true });
      const writable = await fileHandle.createWritable();
      await writable.write(blob);
      await writable.close();
    }

    actualizarToastProgreso(toast, 100);
    toast.querySelector("p").textContent = "PDF exportado correctamente ✔";
    toast.classList.replace("info", "success");
    setTimeout(() => {
      if (toast.parentElement) {
        toast.classList.add("fadeOut");
        setTimeout(() => toast.remove(), 300);
      }
    }, 2500);

    console.log(`Guardado: ${nombre}`);

  } catch (err) {
    console.error("Error exportando:", err);
    if (toast && toast.parentElement) {
      actualizarToastProgreso(toast, 100);
      toast.querySelector("p").textContent = "⚠ Error al exportar";
      toast.classList.replace("info", "error");
      setTimeout(() => {
        if (toast.parentElement) {
          toast.classList.add("fadeOut");
          setTimeout(() => toast.remove(), 300);
        }
      }, 2500);
    }
    showToast("Error", err.message, "error", 5000);
  } finally {
    btn.disabled = false;
    btn.style.opacity = "1";
    btn.style.cursor = "pointer";
    btn.innerHTML = expOriginal;
  }
});


// ==========================================================
// EVENTO PARA GENERAR DOCUMENTOS DISPONIBLES DE UNA ATENCION
// ==========================================================

tabla.addEventListener("click", async (e) => {
  const btn = e.target.closest(".btn-generar");
  if (!btn) return;

  const idAdmisionRaw = btn.dataset.idadmision;
  const idAdmisiones = idAdmisionRaw
    .split(",")
    .map((id) => id.trim())
    .filter(Boolean);

  const idPacienteKey = btn.dataset.idpacientekey;

  let toastProceso;
  let soportesExitosos = 0;

  for (const idAdmision of idAdmisiones) {
    try {
        // Verificar si ya existe documentación
        const [respCheck1, respCheck2] = await Promise.all([
        fetch(
            `https://${host}:9876/api/soportes-disponibles?idAdmision=${idAdmision}`,
        ),
        fetch(
            `https://${host}:9876/api/verificar-factura-venta?idAdmision=${idAdmision}`,
        ),
        ]);

        if (respCheck1.ok && respCheck2.ok) {
        const [soportes1, soportes2] = await Promise.all([
            respCheck1.json(),
            respCheck2.json(),
        ]);

        const tieneSoportes1 = Array.isArray(soportes1) && soportes1.length == 0;
        const tieneSoportes2 = soportes2?.cantidad == 1;

        if (tieneSoportes1 && tieneSoportes2) {
            const confirmar = await showModalConfirm(
            "Este Documento Soporte ya se encuentra generado. ¿Desea eliminarlo?",
            );
            if (!confirmar) return;

            abrirVisorPDFs(idAdmision);
            return;
        }
        }

        // Toast general
        toastProceso = showToast(
        "Proceso",
        "Generación de documentos en curso...",
        "info",
        0,
        true,
        );

        // ===== Paso 1: Documento de apoyo diagnóstico =====
        const toastDiag = showToast(
        "Diagnóstico",
        "Generando documento de apoyo diagnóstico...",
        "info",
        0,
        true,
        );

        try {
        const urlPrint = `https://${host}:9876/api/generar-apoyo-diagnostico?idPacienteKey=${idPacienteKey}&idAdmision=${idAdmision}`;
        const respPrint = await fetch(urlPrint);

        if (!respPrint.ok) throw new Error(await respPrint.text());

        const pdfBlob = await respPrint.blob();

        const fd = new FormData();
        fd.append("idAdmision", idAdmision);
        fd.append("idPacienteKey", idPacienteKey);
        fd.append("idSoporteKey", 3);
        fd.append("tipoDocumento", 1);
        fd.append("nameFilePdf", pdfBlob, "anexos.pdf");
        fd.append("automatico", "true");

        const insertResp = await fetch(`https://${host}:9876/api/insertar-pdf`, {
            method: "POST",
            body: fd,
        });
        if (!insertResp.ok) throw new Error(await insertResp.text());

        actualizarToastProgreso(toastDiag, 100);
        toastDiag.querySelector("p").textContent =
            "Documento diagnóstico generado ✔";
        toastDiag.classList.replace("info", "success");
        soportesExitosos++;
        } catch (err1) {
        console.error("Error en apoyo diagnóstico:", err1);
        actualizarToastProgreso(toastDiag, 100);
        toastDiag.querySelector("p").textContent = "⚠ Error en apoyo diagnóstico";
        toastDiag.classList.replace("info", "error");
        } finally {
        setTimeout(() => {
            if (toastDiag.parentElement) {
            toastDiag.classList.add("fadeOut");
            setTimeout(() => toastDiag.remove(), 300);
            }
        }, 2500);
        }

        // ===== Paso 2: Factura (SIEMPRE SE EJECUTA) =====
        const toastFactura = showToast(
        "Factura",
        "Generando factura de venta...",
        "info",
        0,
        true,
        );

        try {
        const urlFactura = new URL(
            `https://${host}:9876/api/descargar-factura-venta`,
        );
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

        console.log("✔ Factura generada correctamente");
        actualizarToastProgreso(toastFactura, 100);
        toastFactura.querySelector("p").textContent = "Factura generada ✔";
        toastFactura.classList.replace("info", "success");
        soportesExitosos++;
        } catch (errFactura) {
        console.error("Error al generar factura:", errFactura);
        actualizarToastProgreso(toastFactura, 100);
        toastFactura.querySelector("p").textContent = "⚠ Error en factura";
        toastFactura.classList.replace("info", "error");
        } finally {
        setTimeout(() => {
            if (toastFactura.parentElement) {
            toastFactura.classList.add("fadeOut");
            setTimeout(() => toastFactura.remove(), 300);
            }
        }, 2500);
        }

        // ===== Paso 3: Soportes adicionales (SOLO SI EXISTEN) =====
        try {
        const respSoporte = await fetch(
            `https://${host}:9876/api/soportes-disponibles?idAdmision=${idAdmision}`,
        );

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
            const {
            Id: idSoporteKey,
            nombreRptService: nombreSoporte,
            TipoDocumento: tipoDocumento,
            } = soporte;

            if (!nombreSoporte || nombreSoporte.trim() === "") {
            console.warn("⚠ Soporte omitido: nombreSoporte vacío");
            procesados++;
            continue;
            }

            const toastSoporte = showToast(
            "Soporte",
            `Procesando documento ${idSoporteKey}...`,
            "info",
            0,
            true,
            );

            try {
            const urlDescargar = new URL(
                `https://${host}:9876/api/insertar-soportes`,
            );
            urlDescargar.searchParams.set("idAdmision", idAdmision);
            urlDescargar.searchParams.set("idPacienteKey", idPacienteKey);
            urlDescargar.searchParams.set("idSoporteKey", idSoporteKey);
            urlDescargar.searchParams.set("tipoDocumento", tipoDocumento);
            urlDescargar.searchParams.set("nombreSoporte", nombreSoporte);

            const resp = await fetch(urlDescargar);

            if (!resp.ok) {
                const errorText = await resp.text();
                throw new Error(errorText);
            }

            actualizarToastProgreso(toastSoporte, 100);
            toastSoporte.querySelector("p").textContent =
                `Soporte ${idSoporteKey} completado ✔`;
            toastSoporte.classList.replace("info", "success");
            soportesExitosos++;
            } catch (errIter) {
            console.error("Error en soporte:", errIter);
            actualizarToastProgreso(toastSoporte, 100);
            toastSoporte.querySelector("p").textContent =
                `Error en soporte ${idSoporteKey}`;
            toastSoporte.classList.replace("info", "error");
            } finally {
            setTimeout(() => {
                if (toastSoporte.parentElement) {
                toastSoporte.classList.add("fadeOut");
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
        toastProceso.querySelector("p").textContent = "Proceso completo ✔";
        toastProceso.classList.replace("info", "success");

        const tr = btn.closest("tr");
        const celdaCant = tr?.querySelector(".cant-soportes");

        if (celdaCant) {
        celdaCant.textContent = soportesExitosos;
        } else {
        console.warn("No se encontró la celda cant-soportes en esta fila");
        }

        setTimeout(() => {
        if (toastProceso.parentElement) {
            toastProceso.classList.add("fadeOut");
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
        toastProceso.classList.add("fadeOut");
        setTimeout(() => toastProceso.remove(), 300);
      }, 3000);
    }
  }
}
});

async function generarPorLote() {
  const checkboxes = document.querySelectorAll(".checkbox-row:checked");

  if (checkboxes.length === 0) {
    showToast(
      "Sin selección",
      "Selecciona al menos una fila para generar",
      "warning",
      4000,
    );
    return;
  }

  const btn = document.getElementById("btnGenerarLote");
  btn.disabled = true;
  btn.style.opacity = "0.6";
  btn.style.cursor = "not-allowed";
  btn.innerHTML = "Generando...";

  let procesados = 0;
  let errores = 0;
  const total = checkboxes.length;
  const fallos = [];

  for (const checkbox of checkboxes) {
    const row = checkbox.closest("tr");
    const idAdmisionRaw = row.dataset.idadmision;

    const idAdmision = idAdmisionRaw
    .split(",")
    .map((id) => id.trim())
    .filter(Boolean);

    
    const idPacienteKey = row.dataset.idpacientekey;
    const erroresPaso = [];

    // Toast general por admisión
    const toastProceso = showToast(
      "Proceso",
      `Generando admisión ${idAdmision}...`,
      "info",
      0,
      true,
    );

    // ===== HEV =====
    const toastHev = showToast("HEV", "Generando HEV...", "info", 0, true);
    try {
        for (const id of idAdmision) {
            const urlHev = new URL(`https://${host}:9876/api/descargar-hev`);

            urlHev.searchParams.set("idAdmision", id);
            urlHev.searchParams.set("idPacienteKey", idPacienteKey);
            urlHev.searchParams.set("idSoporteKey", "1");
            urlHev.searchParams.set("tipoDocumento", "1");

            const respHev = await fetch(urlHev);

            if (!respHev.ok) {
                throw new Error(`HEV ${id}: ` + (await respHev.text()));
            }
        }

        actualizarToastProgreso(toastHev, 100);
        toastHev.querySelector("p").textContent = "HEV generado ✔";
        toastHev.classList.replace("info", "success");
        } catch (err) {
        console.error(`[${idAdmision}] Error HEV:`, err.message);
        actualizarToastProgreso(toastHev, 100);
        toastHev.querySelector("p").textContent = "⚠ Error en HEV";
        toastHev.classList.replace("info", "error");
        erroresPaso.push(err.message);
        } finally {
        setTimeout(() => {
            if (toastHev.parentElement) {
            toastHev.classList.add("fadeOut");
            setTimeout(() => toastHev.remove(), 300);
            }
        }, 2500);
    }

    // ===== Cierre toast proceso =====
    actualizarToastProgreso(toastProceso, 100);
    if (erroresPaso.length === 0) {
      toastProceso.querySelector("p").textContent =
        `Admisión ${idAdmision} completada ✔`;
      toastProceso.classList.replace("info", "success");
      procesados++;
    } else {
      toastProceso.querySelector("p").textContent =
        `Admisión ${idAdmision} con errores`;
      toastProceso.classList.replace("info", "error");
      errores++;
      fallos.push({ idAdmision, errores: erroresPaso });
    }

    setTimeout(() => {
      if (toastProceso.parentElement) {
        toastProceso.classList.add("fadeOut");
        setTimeout(() => toastProceso.remove(), 300);
      }
    }, 3000);

    checkbox.checked = false;

    const porcentaje = Math.round(((procesados + errores) / total) * 100);
    btn.innerHTML = `Generando... ${porcentaje}%`;
  }

  // Final
  btn.disabled = false;
  btn.style.opacity = "1";
  btn.style.cursor = "pointer";
  btn.innerHTML = "Generar por Lote";

  if (errores === 0) {
    showToast(
      "Listo",
      `${procesados} admisiones generadas correctamente ✔`,
      "success",
      5000,
    );
  } else if (procesados === 0) {
    showToast(
      "Error",
      `Ninguna admisión se generó correctamente`,
      "error",
      5000,
    );
  } else {
    showToast(
      "Completado con errores",
      `${procesados} exitosas — fallaron: ${fallos.map((f) => f.idAdmision).join(", ")}`,
      "warning",
      8000,
    );
  }

  console.table(fallos);
}

// ==========================================
// EVENTO PARA ABRIR VENTANA Y VER DOCUMENTOS
// ==========================================

tabla.addEventListener("click", (e) => {
  const btn = e.target.closest(".btn-verpdfs");
  if (!btn) return;

  const idAdmisionRaw = btn.dataset.idadmision;


  if (!idAdmisionRaw) {
    showToast(
      "Falta IdAdmision",
      "No se pudo obtener IdAdmision.",
      "error",
      6000,
    );
    return;
  }

  console.log("ID ADMISION:", idAdmisionRaw);
  const idAdmisiones = idAdmisionRaw
    .split(",")
    .map((id) => id.trim())
    .filter(Boolean);

  for (const idAdmision of idAdmisiones) {
    abrirVisorPDFs(idAdmision); // abre una pestaña por cada admisión
  }
});


// ==========================
// CARGA INICIAL DE SELECTS
// =========================
document.addEventListener("DOMContentLoaded", () => {
  poblarSelect("eps", 2, -1, "IdTerceroKey", "NomTercero");

  poblarSelect("idAreaAtencion", 4, -1, "IdAreaAtencion", "NomAreaAtencion");

  poblarSelectTipoSoporte();
  

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


document.getElementById("multiconsultante").addEventListener("change", (e) => {
  multiConsultante = e.target.checked;
  console.log("multiConsultante:", multiConsultante);
});


document.getElementById("verGeneradas").addEventListener("change", (e) => {
  mostrarGeneradas = e.target.checked;
});


 
function actualizarBotonesExportar() {
const habilitado = mostrarGeneradas === true;
const btnExportar = document.getElementById("btnExportar");
if (btnExportar) {
    btnExportar.disabled = !habilitado;
    btnExportar.style.opacity = habilitado ? "1" : "0.4";
    btnExportar.style.cursor = habilitado ? "pointer" : "not-allowed";
}
}











//CODIGO DE REPUESOTOOOO


function showModalGen(message) {
  return new Promise((resolve) => {
    const modal = document.getElementById("modalGen");
    const msg = document.getElementById("modalGenMessage");
    const btnCancel = document.getElementById("btnModalGenCancel");
    const btnGenTodo = document.getElementById("btnModalGenTodo");
    const btnGenResumen = document.getElementById("btnModalGenResumen");

    msg.textContent = message;
    modal.style.display = "flex";

    function close(result) {
      modal.style.display = "none";
      btnCancel.removeEventListener("click", cancelHandler);
      btnGenResumen.removeEventListener("click", genResumenHandler);
      btnGenTodo.removeEventListener("click", genTodoHandler);
      resolve(result);
    }

    function cancelHandler() {
      close(false);
    }
    function genResumenHandler() {
      close("resumen");
    }
    function genTodoHandler() {
      close("todo");
    }

    btnCancel.addEventListener("click", cancelHandler);
    btnGenResumen.addEventListener("click", genResumenHandler);
    btnGenTodo.addEventListener("click", genTodoHandler);
  });
}










async function generarPorLoteEVENTO() {
  const checkboxes = document.querySelectorAll(".checkbox-row:checked");

  if (checkboxes.length === 0) {
    showToast(
      "Sin selección",
      "Selecciona al menos una fila para generar",
      "warning",
      4000,
    );
    return;
  }

  const btn = document.getElementById("btnGenerarLote");
  btn.disabled = true;
  btn.style.opacity = "0.6";
  btn.style.cursor = "not-allowed";
  btn.innerHTML = "Generando...";

  let procesados = 0;
  let errores = 0;
  const total = checkboxes.length;
  const fallos = [];

  for (const checkbox of checkboxes) {
    const row = checkbox.closest("tr");
    const idAdmision = row.dataset.idadmision;
    const idPacienteKey = row.dataset.idpacientekey;
    const idAtencion = row.dataset.idatencion;
    const erroresPaso = [];

    // Toast general por admisión
    const toastProceso = showToast(
      "Proceso",
      `Generando admisión ${idAdmision}...`,
      "info",
      0,
      true,
    );

    if (tipoGen === "resumen") {
      // ===== HEV =====
      const toastHev = showToast("HEV", "Generando HEV...", "info", 0, true);
      try {
        const urlHev = new URL(`https://${host}:9876/api/descargar-hev`);
        urlHev.searchParams.set("idAdmision", idAdmision);
        urlHev.searchParams.set("idPacienteKey", idPacienteKey);
        urlHev.searchParams.set("idSoporteKey", "1");
        urlHev.searchParams.set("tipoDocumento", "1");

        const respHev = await fetch(urlHev);
        if (!respHev.ok) throw new Error("HEV: " + (await respHev.text()));

        actualizarToastProgreso(toastHev, 100);
        toastHev.querySelector("p").textContent = "HEV generado ✔";
        toastHev.classList.replace("info", "success");
      } catch (err) {
        console.error(`[${idAdmision}] Error HEV:`, err.message);
        actualizarToastProgreso(toastHev, 100);
        toastHev.querySelector("p").textContent = "⚠ Error en HEV";
        toastHev.classList.replace("info", "error");
        erroresPaso.push(err.message);
      } finally {
        setTimeout(() => {
          if (toastHev.parentElement) {
            toastHev.classList.add("fadeOut");
            setTimeout(() => toastHev.remove(), 300);
          }
        }, 2500);
      }
    } else if (tipoGen === "todo") {
      // ===== Paso 1: Apoyo diagnóstico =====
      const toastDiag = showToast(
        "Diagnóstico",
        "Generando apoyo diagnóstico...",
        "info",
        0,
        true,
      );
      try {
        const respPrint = await fetch(
          `https://${host}:9876/api/generar-apoyo-diagnostico?idPacienteKey=${idPacienteKey}&idAdmision=${idAdmision}`,
        );
        if (respPrint.ok) {
          const contentType = respPrint.headers.get("content-type");
          if (contentType && contentType.includes("application/pdf")) {
            const pdfBlob = await respPrint.blob();
            const fd = new FormData();
            fd.append("idAdmision", idAdmision);
            fd.append("idPacienteKey", idPacienteKey);
            fd.append("idSoporteKey", 3);
            fd.append("tipoDocumento", 1);
            fd.append("nameFilePdf", pdfBlob, "anexos.pdf");
            fd.append("automatico", "true");
            await fetch(`https://${host}:9876/api/insertar-pdf`, {
              method: "POST",
              body: fd,
            });
          }
        }
        actualizarToastProgreso(toastDiag, 100);
        toastDiag.querySelector("p").textContent =
          "Apoyo diagnóstico generado ✔";
        toastDiag.classList.replace("info", "success");
      } catch (err) {
        console.warn(`[${idAdmision}] Apoyo diagnóstico omitido:`, err.message);
        actualizarToastProgreso(toastDiag, 100);
        toastDiag.querySelector("p").textContent =
          "⚠ Error en apoyo diagnóstico";
        toastDiag.classList.replace("info", "error");
      } finally {
        setTimeout(() => {
          if (toastDiag.parentElement) {
            toastDiag.classList.add("fadeOut");
            setTimeout(() => toastDiag.remove(), 300);
          }
        }, 2500);
      }

      // ===== Paso 2: Factura =====
      const toastFactura = showToast(
        "Factura",
        "Generando factura de venta...",
        "info",
        0,
        true,
      );
      try {
        const urlFactura = new URL(
          `https://${host}:9876/api/descargar-factura-venta`,
        );
        urlFactura.searchParams.set("idAdmision", idAdmision);
        urlFactura.searchParams.set("idPacienteKey", idPacienteKey);
        urlFactura.searchParams.set("idSoporteKey", "18");
        urlFactura.searchParams.set("tipoDocumento", "1");

        const respFactura = await fetch(urlFactura);
        if (!respFactura.ok)
          throw new Error("Factura: " + (await respFactura.text()));

        actualizarToastProgreso(toastFactura, 100);
        toastFactura.querySelector("p").textContent = "Factura generada ✔";
        toastFactura.classList.replace("info", "success");
      } catch (err) {
        console.error(`[${idAdmision}] Error factura:`, err.message);
        actualizarToastProgreso(toastFactura, 100);
        toastFactura.querySelector("p").textContent = "⚠ Error en factura";
        toastFactura.classList.replace("info", "error");
        erroresPaso.push(err.message);
      } finally {
        setTimeout(() => {
          if (toastFactura.parentElement) {
            toastFactura.classList.add("fadeOut");
            setTimeout(() => toastFactura.remove(), 300);
          }
        }, 2500);
      }

      // ===== Paso 3: Soportes adicionales =====
      try {
        const respSoporte = await fetch(
          `https://${host}:9876/api/soportes-disponibles?idAdmision=${idAdmision}`,
        );
        if (!respSoporte.ok)
          throw new Error("Obtener soportes: " + (await respSoporte.text()));

        const soportes = await respSoporte.json();
        let totalSoportes = soportes.length;
        let procesadosSoportes = 0;

        for (const soporte of soportes) {
          const {
            Id: idSoporteKey,
            nombreRptService: nombreSoporte,
            TipoDocumento: tipoDocumento,
          } = soporte;
          if (!nombreSoporte || nombreSoporte.trim() === "") {
            procesadosSoportes++;
            continue;
          }

          const toastSoporte = showToast(
            "Soporte",
            `Procesando soporte ${idSoporteKey}...`,
            "info",
            0,
            true,
          );
          try {
            const urlSoporte = new URL(
              `https://${host}:9876/api/insertar-soportes`,
            );
            urlSoporte.searchParams.set("idAdmision", idAdmision);
            urlSoporte.searchParams.set("idPacienteKey", idPacienteKey);
            urlSoporte.searchParams.set("idSoporteKey", idSoporteKey);
            urlSoporte.searchParams.set("tipoDocumento", tipoDocumento);
            urlSoporte.searchParams.set("nombreSoporte", nombreSoporte);

            const respS = await fetch(urlSoporte);
            if (!respS.ok)
              throw new Error(
                `Soporte ${idSoporteKey}: ` + (await respS.text()),
              );

            actualizarToastProgreso(toastSoporte, 100);
            toastSoporte.querySelector("p").textContent =
              `Soporte ${idSoporteKey} completado ✔`;
            toastSoporte.classList.replace("info", "success");
          } catch (errS) {
            console.error(
              `[${idAdmision}] Error soporte ${idSoporteKey}:`,
              errS.message,
            );
            actualizarToastProgreso(toastSoporte, 100);
            toastSoporte.querySelector("p").textContent =
              `⚠ Error en soporte ${idSoporteKey}`;
            toastSoporte.classList.replace("info", "error");
            erroresPaso.push(errS.message);
          } finally {
            setTimeout(() => {
              if (toastSoporte.parentElement) {
                toastSoporte.classList.add("fadeOut");
                setTimeout(() => toastSoporte.remove(), 300);
              }
            }, 2500);

            procesadosSoportes++;
            const porcentajeSoportes = Math.round(
              (procesadosSoportes / totalSoportes) * 100,
            );
            actualizarToastProgreso(toastProceso, porcentajeSoportes);
          }
        }
      } catch (err) {
        console.error(`[${idAdmision}] Error soportes:`, err.message);
        erroresPaso.push(err.message);
      }
    }

    // ===== Cierre toast proceso =====
    actualizarToastProgreso(toastProceso, 100);
    if (erroresPaso.length === 0) {
      toastProceso.querySelector("p").textContent =
        `Admisión ${idAdmision} completada ✔`;
      toastProceso.classList.replace("info", "success");
      procesados++;
    } else {
      toastProceso.querySelector("p").textContent =
        `Admisión ${idAdmision} con errores`;
      toastProceso.classList.replace("info", "error");
      errores++;
      fallos.push({ idAdmision, errores: erroresPaso });
    }

    setTimeout(() => {
      if (toastProceso.parentElement) {
        toastProceso.classList.add("fadeOut");
        setTimeout(() => toastProceso.remove(), 300);
      }
    }, 3000);

    const porcentaje = Math.round(((procesados + errores) / total) * 100);
    btn.innerHTML = `Generando... ${porcentaje}%`;
  }

  // Final
  btn.disabled = false;
  btn.style.opacity = "1";
  btn.style.cursor = "pointer";
  btn.innerHTML = "Generar por Lote";

  if (errores === 0) {
    showToast(
      "Listo",
      `${procesados} admisiones generadas correctamente ✔`,
      "success",
      5000,
    );
  } else if (procesados === 0) {
    showToast(
      "Error",
      `Ninguna admisión se generó correctamente`,
      "error",
      5000,
    );
  } else {
    showToast(
      "Completado con errores",
      `${procesados} exitosas — fallaron: ${fallos.map((f) => f.idAdmision).join(", ")}`,
      "warning",
      8000,
    );
  }

  console.table(fallos);
}
