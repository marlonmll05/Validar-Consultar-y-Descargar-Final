// URL del backend
const API_URL =
  window.location.hostname === "localhost"
    ? "http://localhost:8080/api/activos-fijos"
    : `http://${window.location.hostname}:8080/api/activos-fijos`;

// CARGAR DOM
document.addEventListener("DOMContentLoaded", function () {

  cargarGruposCategorias("idGrupoCat");
  cargarGAItems("idGAItem");
  cargarGAItems2();

  document.getElementById("idGrupoCat").addEventListener("change", function () {
    const idCategoria = this.value;
    if (idCategoria) {
      cargarSubCategorias(idCategoria, "idSubCatKey");
    } else {
      document.getElementById("idSubCatKey").innerHTML =
        '<option value="">Seleccione primero una categoría...</option>';
    }
  });

  // CONSULTAR
  cargarGruposCategorias("consIdCategoria");
  cargarGAItems("consIdGAItem");

  // REPORTE
  cargarGruposCategorias("repIdCategoria");
  cargarGAItems("repIdGAItem");

  document
    .getElementById("consIdCategoria")
    .addEventListener("change", function () {
      const idCategoria = this.value;
      if (idCategoria) {
        cargarSubCategorias(idCategoria, "consIdSubCategoria");
      } else {
        const sub = document.getElementById("consIdSubCategoria");
        sub.innerHTML =
          '<option value="">Seleccione primero una categoría...</option>';
        sub.disabled = true;
      }
    });

  document
    .getElementById("repIdCategoria")
    .addEventListener("change", function () {
      const idCategoria = this.value;
      if (idCategoria) {
        cargarSubCategorias(idCategoria, "repIdSubCategoria");
      } else {
        const sub = document.getElementById("repIdSubCategoria");
        sub.innerHTML =
          '<option value="">Seleccione primero una categoría...</option>';
        sub.disabled = true;
      }
    });
});

// CARGAR CATEGORÍAS (tipo = 4)
async function cargarGruposCategorias(selectId = "idGrupoCat") {
  try {
    const response = await fetch(`${API_URL}/tablas/4?id=0&id2=0`);
    const resultado = await response.json();

    const select = document.getElementById(selectId);
    if (!select) return;

    select.innerHTML = '<option value="">Seleccione...</option>';

    if (resultado.success && resultado.data.length > 0) {
      resultado.data.forEach((item) => {
        const option = document.createElement("option");
        option.value = item.IdCategoria;
        option.textContent = item.Categoria;
        select.appendChild(option);
      });
    }
  } catch (error) {
    console.error("Error al cargar categorías:", error);
  }
}

// CARGAR SUBCATEGORÍAS SEGÚN LA CATEGORÍA SELECCIONADA (tipo = 5)
async function cargarSubCategorias(idCategoria, selectId = "idSubCatKey") {
  try {
    const response = await fetch(`${API_URL}/tablas/5?id=${idCategoria}&id2=0`);
    const resultado = await response.json();

    const select = document.getElementById(selectId);
    if (!select) return;

    select.disabled = false;
    select.innerHTML = '<option value="">Seleccione...</option>';

    if (resultado.success && resultado.data.length > 0) {
      resultado.data.forEach((item) => {
        const option = document.createElement("option");
        option.value = item.idSubCatKey;
        option.textContent = item.NomSubCategoria;
        select.appendChild(option);
      });
    } else {
      select.innerHTML = '<option value="">No hay subcategorías</option>';
    }
  } catch (error) {
    console.error("Error al cargar subcategorías:", error);
  }
}

// CARGAR GA ITEMS (tipo = 1)
async function cargarGAItems(selectId = "idGAItem") {
  try {
    const response = await fetch(`${API_URL}/tablas/1?id=0&id2=0`);
    const resultado = await response.json();

    const select = document.getElementById(selectId);
    if (!select) return;

    select.innerHTML = '<option value="">Seleccione...</option>';

    if (resultado.success && resultado.data.length > 0) {
      resultado.data.forEach((item) => {
        const option = document.createElement("option");
        option.value = item.IdGAItem;
        option.textContent = item.NomGAItem;
        select.appendChild(option);
      });
    }
  } catch (error) {
    console.error("Error al cargar GA Items:", error);
  }
}

// CARGAR GA ITEMS 2 (tipo = 2)
async function cargarGAItems2() {
  try {
    const response = await fetch(`${API_URL}/tablas/2?id=0&id2=0`);
    const resultado = await response.json();

    if (resultado.success && resultado.data.length > 0) {
      const select = document.getElementById("idGAItem2");
      select.innerHTML = '<option value="">Seleccione...</option>';

      resultado.data.forEach((item) => {
        const option = document.createElement("option");
        option.value = item.IdGAItem;
        option.textContent = item.NomGAItem;
        select.appendChild(option);
      });

      console.log("GA Items 2 cargados:", resultado.data);
    }
  } catch (error) {
    console.error("Error al cargar GA Items 2:", error);
  }
}

function showTab(tabName) {
  document
    .querySelectorAll(".tab")
    .forEach((tab) => tab.classList.remove("active"));
  document
    .querySelectorAll(".tab-content")
    .forEach((content) => content.classList.remove("active"));

  event.target.classList.add("active");
  document.getElementById("tab-" + tabName).classList.add("active");
}

// Mapa de timeouts por contenedor
const alertTimeouts = {};

function mostrarAlerta(mensaje, tipo, contenedor = "alert-container") {
  const alertDiv = document.getElementById(contenedor);
  if (!alertDiv) return;

  alertDiv.innerHTML = `
                <div class="alert alert-${tipo}">
                    ${mensaje}
                </div>
            `;

  if (alertTimeouts[contenedor]) {
    clearTimeout(alertTimeouts[contenedor]);
  }

  alertTimeouts[contenedor] = setTimeout(() => {
    alertDiv.innerHTML = "";
    delete alertTimeouts[contenedor];
  }, 8000);
}

// REGISTRAR ACTIVO
document
  .getElementById("formRegistrar")
  .addEventListener("submit", async function (e) {
    e.preventDefault();

    const selectCategoria = document.getElementById("idGrupoCat");
    const opcionSeleccionada =
      selectCategoria.options[selectCategoria.selectedIndex];
    const grupoPUC = opcionSeleccionada
      ? opcionSeleccionada.getAttribute("data-grupo-puc")
      : null;
    console.log("Grupo Puc:", grupoPUC);

    const datos = {
      // 2. NombreActivo
      nombreActivo: document.getElementById("nombreActivo").value,

      // 3. Descripcion
      descripcion: document.getElementById("descripcion").value || null,

      // 4. Activo
      activo: document.getElementById("activo").checked,

      // 5. Estado
      estado: document.getElementById("estado").value,

      // 6. Fabricante
      fabricante: document.getElementById("fabricante").value || null,

      // 7. IdProveedor
      idProveedor: document.getElementById("idProveedor").value
        ? parseInt(document.getElementById("idProveedor").value)
        : null,

      // 8. Modelo
      modelo: document.getElementById("modelo").value || null,

      // 9. Serial
      serial: document.getElementById("serial").value || null,

      // 10. FecFabricacion
      fecFabricacion: document.getElementById("fecFabricacion").value || null,

      // 11. FecAdquisicion
      fecAdquisicion: document.getElementById("fecAdquisicion").value,

      // 12. CostoAdquisicion
      costoAdquisicion:
        parseFloat(document.getElementById("costoAdquisicion").value) || 0,

      // 17. Responsable
      responsable: document.getElementById("responsable").value,

      // 18. LugarUbicacion
      lugarUbicacion: document.getElementById("lugarUbicacion").value,

      // 19. IdGAItem
      idGAItem: document.getElementById("idGAItem").value
        ? parseInt(document.getElementById("idGAItem").value)
        : null,

      // 21. NoFactura
      noFactura: document.getElementById("noFactura").value || null,

      // 23. Asegurado
      asegurado: document.getElementById("asegurado").checked,

      // 26. idSubCatKey
      idSubCatKey:
        document.getElementById("idSubCatKey").value &&
        document.getElementById("idSubCatKey").value !== ""
          ? parseInt(document.getElementById("idSubCatKey").value)
          : null,

      idGAItem2: document.getElementById("idGAItem2").value
        ? parseInt(document.getElementById("idGAItem2").value)
        : null,
    };

    console.log("Datos a enviar:", datos);

    try {
      const response = await fetch(API_URL + "/insertar", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify(datos),
      });

      const resultado = await response.json();

      if (resultado.success) {
        mostrarAlerta(
          "✔ " +
            resultado.message +
            " (ID: " +
            resultado.idActivoFijoKey +
            ")",
          "success",
        );
        limpiarFormulario();
      } else {
        mostrarAlerta("⚠ " + resultado.message, "error");
      }
    } catch (error) {
      console.error("Error:", error);
      mostrarAlerta("⚠ Error al conectar con el servidor", "error");
    }
  });

function limpiarFormulario() {
  document.getElementById("formRegistrar").reset();
  document.getElementById("activo").checked = true;
  document.getElementById("idGAItem2").value = "0";
}

function getBadgeEstado(estado) {
  const badges = {
    Bueno: "badge-bueno",
    Regular: "badge-regular",
    Defectuoso: "badge-defectuoso",
    Malo: "badge-malo",
  };
  const clase = badges[estado] || "badge-activo";
  return `<span class="badge ${clase}">${estado}</span>`;
}

function formatearFecha(fecha) {
  if (!fecha) return "N/A";
  const d = new Date(fecha);
  return d.toLocaleDateString("es-CO");
}

function formatearNumero(numero) {
  return new Intl.NumberFormat("es-CO", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(numero);
}

function limpiarBusqueda() {
  document.getElementById("searchCodigo").value = "";
  document.getElementById("searchNombre").value = "";
  document.getElementById("searchEstado").value = "";
  document.getElementById("searchResponsable").value = "";
  document.getElementById("tablaActivosBody").innerHTML = `
                <tr>
                    <td colspan="9" style="text-align: center; padding: 30px;">
                        No hay registros para mostrar. Realice una búsqueda.
                    </td>
                </tr>
            `;
}

let campoDestinoActual = null; 

// ABRIR MODAL DE CATEGORÍA
document.querySelector(".btn-agregar").addEventListener("click", function () {
  const modal = document.getElementById("modalCategoria");
  modal.classList.add("show");
  limpiarFormularioCategoria();
  document.getElementById("nuevaCategoria").focus();
});

// CERRAR MODAL DE CATEGORÍA
function cerrarModalCategoria() {
  document.getElementById("modalCategoria").classList.remove("show");
}

// LIMPIAR FORMULARIO DE CATEGORÍA
function limpiarFormularioCategoria() {
  document.getElementById("nuevaCategoria").value = "";
  document.getElementById("idCuentaModal").value = "";
  document.getElementById("idCuentaDepre").value = "";
  document.getElementById("idCuentaDepreGasto").value = "";
  document.getElementById("idCuentaPerdidaRetiro").value = "";
  document.getElementById("idCuentaIngresoRetiro").value = "";
  document.getElementById("alert-modal-categoria").innerHTML = "";
}

// ABRIR BUSCADOR DE CUENTA
function abrirBuscadorCuenta(campoDestino) {
  campoDestinoActual = campoDestino;
  const modal = document.getElementById("modalBuscadorCuenta");
  modal.classList.add("show");
  document.getElementById("filtroCuentaBuscador").value = "";
  document.getElementById("filtroNombreBuscador").value = "";
  document.getElementById("tablaCuentasBody").innerHTML = "";
  document.getElementById("resultadosCuentasBuscador").classList.remove("show");
  document.getElementById("filtroCuentaBuscador").focus();
}

// CERRAR BUSCADOR DE CUENTA
function cerrarBuscadorCuenta() {
  document.getElementById("modalBuscadorCuenta").classList.remove("show");
  campoDestinoActual = null;
}

// BUSCAR CUENTAS PUC
async function buscarCuentasPUC() {
  const filtroCuenta = document
    .getElementById("filtroCuentaBuscador")
    .value.trim();
  const filtroNombre = document
    .getElementById("filtroNombreBuscador")
    .value.trim();
  const orden = document.getElementById("ordenBusqueda").value;
  const resultadosDiv = document.getElementById("resultadosCuentasBuscador");
  const tbody = document.getElementById("tablaCuentasBody");

  if (!filtroCuenta && !filtroNombre) {
    tbody.innerHTML =
      '<tr><td colspan="3" class="no-resultados-tabla"><p>Ingrese al menos un criterio de búsqueda</p></td></tr>';
    resultadosDiv.classList.add("show");
    return;
  }

  try {
    let url = `${API_URL}/buscar-cuentas?orden=${orden}`;
    if (filtroCuenta)
      url += `&filtroCuenta=${encodeURIComponent(filtroCuenta)}`;
    if (filtroNombre)
      url += `&filtroNombre=${encodeURIComponent(filtroNombre)}`;

    const response = await fetch(url);
    const resultado = await response.json();

    if (resultado.success && resultado.data.length > 0) {
      tbody.innerHTML = "";

      resultado.data.forEach((cuenta) => {
        const tr = document.createElement("tr");
        tr.innerHTML = `
                            <td>${cuenta.idCuenta}</td>
                            <td>${cuenta.nomCuenta}</td>
                            <td>${cuenta.idTipoDoc || "-"}</td>
                        `;
        tr.onclick = () =>
          seleccionarCuentaParaCampo(cuenta.idCuenta, cuenta.nomCuenta);
        tbody.appendChild(tr);
      });

      resultadosDiv.classList.add("show");
    } else {
      tbody.innerHTML =
        '<tr><td colspan="3" class="no-resultados-tabla"><p>No se encontraron cuentas</p></td></tr>';
      resultadosDiv.classList.add("show");
    }
  } catch (error) {
    console.error("Error al buscar cuentas:", error);
    tbody.innerHTML =
      '<tr><td colspan="3" class="no-resultados-tabla"><p>Error al buscar cuentas</p></td></tr>';
    resultadosDiv.classList.add("show");
  }
}

function seleccionarCuentaParaCampo(idCuenta) {
  if (campoDestinoActual) {
    document.getElementById(campoDestinoActual).value = idCuenta;
    console.log(`Cuenta ${idCuenta} asignada a ${campoDestinoActual}`);
    cerrarBuscadorCuenta();
  }
}

async function crearCategoria() {
  const alertContainer = document.getElementById("alert-modal-categoria");

  const nombreCategoria = document
    .getElementById("nuevaCategoria")
    .value.trim();
  const idCuenta = document.getElementById("idCuentaModal").value.trim();
  const idCuentaDepre = document.getElementById("idCuentaDepre").value.trim();
  const idCuentaDepreGasto = document
    .getElementById("idCuentaDepreGasto")
    .value.trim();
  const idCuentaPerdidaRetiro = document
    .getElementById("idCuentaPerdidaRetiro")
    .value.trim();
  const idCuentaIngresoRetiro = document
    .getElementById("idCuentaIngresoRetiro")
    .value.trim();

  if (!nombreCategoria) {
    alertContainer.innerHTML =
      '<div class="alert alert-error">Por favor ingrese el nombre de la categoría</div>';
    return;
  }

  if (!idCuenta) {
    alertContainer.innerHTML =
      '<div class="alert alert-error">Por favor ingrese o seleccione la cuenta principal</div>';
    return;
  }

  if (!idCuentaDepre) {
    alertContainer.innerHTML =
      '<div class="alert alert-error">Por favor ingrese o seleccione la cuenta de depreciación</div>';
    return;
  }

  if (!idCuentaDepreGasto) {
    alertContainer.innerHTML =
      '<div class="alert alert-error">Por favor ingrese o seleccione la cuenta de depreciación gasto</div>';
    return;
  }

  if (!idCuentaPerdidaRetiro) {
    alertContainer.innerHTML =
      '<div class="alert alert-error">Por favor ingrese o seleccione la cuenta de pérdida retiro</div>';
    return;
  }

  if (!idCuentaIngresoRetiro) {
    alertContainer.innerHTML =
      '<div class="alert alert-error">Por favor ingrese o seleccione la cuenta de ingreso retiro</div>';
    return;
  }

  try {
    const response = await fetch(`${API_URL}/categorias`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        idEmpresaKey: 1,
        categoria: nombreCategoria,
        idCuenta: idCuenta,
        grupoPuc: "03",
        idCuentaDepre: idCuentaDepre,
        idCuentaDepreGasto: idCuentaDepreGasto,
        idCuentaPerdidaRetiro: idCuentaPerdidaRetiro,
        idCuentaIngresoRetiro: idCuentaIngresoRetiro,
      }),
    });

    const resultado = await response.json();

    if (resultado.success) {
      alertContainer.innerHTML =
        '<div class="alert alert-success">Categoría creada exitosamente</div>';
      await cargarGruposCategorias();
      setTimeout(() => cerrarModalCategoria(), 1500);
    } else {
      alertContainer.innerHTML = `<div class="alert alert-error">Error: ${resultado.message}</div>`;
    }
  } catch (error) {
    alertContainer.innerHTML = `<div class="alert alert-error">Error: ${error.message}</div>`;
  }
}

// BUSCAR CON ENTER en el buscador
document.addEventListener("DOMContentLoaded", function () {
  ["filtroCuentaBuscador", "filtroNombreBuscador"].forEach((id) => {
    const elem = document.getElementById(id);
    if (elem) {
      elem.addEventListener("keypress", function (e) {
        if (e.key === "Enter") {
          buscarCuentasPUC();
        }
      });
    }
  });

  // Cerrar modales con ESC
  document.addEventListener("keydown", function (e) {
    if (e.key === "Escape") {
      if (
        document
          .getElementById("modalBuscadorCuenta")
          .classList.contains("show")
      ) {
        cerrarBuscadorCuenta();
      } else if (
        document.getElementById("modalCategoria").classList.contains("show")
      ) {
        cerrarModalCategoria();
      }
    }
  });
});

// ABRIR MODAL SUBCATEGORÍA
document.querySelector(".btn-agregar2").addEventListener("click", function () {
  const modal = document.getElementById("modalSubCategoria");
  modal.classList.add("show");
  document.getElementById("nuevaSubCategoria").value = "";
  document.getElementById("alert-modal-subcategoria").innerHTML = "";

  // Cargar categorías en el modal
  cargarCategoriasEnModal();

  document.getElementById("nuevaSubCategoria").focus();
});

// CERRAR MODAL SUBCATEGORÍA
function cerrarModalSubCategoria() {
  document.getElementById("modalSubCategoria").classList.remove("show");
}

// CARGAR CATEGORÍAS EN EL SELECT DEL MODAL
async function cargarCategoriasEnModal() {
  try {
    const response = await fetch(`${API_URL}/tablas/4?id=0&id2=0`);
    const resultado = await response.json();

    if (resultado.success && resultado.data.length > 0) {
      const select = document.getElementById("categoriaModal");
      select.innerHTML =
        '<option value="">Seleccione una categoría...</option>';

      resultado.data.forEach((item) => {
        const option = document.createElement("option");
        option.value = item.IdCategoria;
        option.textContent = item.Categoria;
        select.appendChild(option);
      });

      console.log("Categorías cargadas en modal:", resultado.data);
    }
  } catch (error) {
    console.error("Error al cargar categorías en modal:", error);
  }
}

async function crearSubCategoria() {
  const idCategoria = document.getElementById("categoriaModal").value;
  const nombreSubCategoria = document
    .getElementById("nuevaSubCategoria")
    .value.trim();
  const vidaUtilEstimada = document
    .getElementById("vidaUtilEstimada")
    .value.trim();
  const depreciable = document.getElementById("depreciable").checked;
  const alertContainer = document.getElementById("alert-modal-subcategoria");

  if (!idCategoria) {
    alertContainer.innerHTML =
      '<div class="alert alert-error">Por favor seleccione una categoría</div>';
    return;
  }

  if (!nombreSubCategoria) {
    alertContainer.innerHTML =
      '<div class="alert alert-error">Por favor ingrese el nombre de la subcategoría</div>';
    return;
  }

  if (!vidaUtilEstimada) {
    alertContainer.innerHTML =
      '<div class="alert alert-error">Por favor ingrese la vida útil estimada</div>';
    return;
  }

  const vidaUtil = parseInt(vidaUtilEstimada);
  if (isNaN(vidaUtil) || vidaUtil <= 0) {
    alertContainer.innerHTML =
      '<div class="alert alert-error">La vida útil debe ser un número positivo</div>';
    return;
  }

  try {
    const response = await fetch(`${API_URL}/subcategorias`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        idCategoria: parseInt(idCategoria),
        nomSubCategoria: nombreSubCategoria,
        vidaUtilEstimada: vidaUtil,
        depreciable: depreciable,
        idProductoKey: null,
      }),
    });

    const resultado = await response.json();

    if (resultado.success) {
      alertContainer.innerHTML =
        '<div class="alert alert-success">Subcategoría creada exitosamente</div>';

      const categoriaSeleccionada = document.getElementById("idGrupoCat").value;
      if (categoriaSeleccionada) {
        await cargarSubCategorias(categoriaSeleccionada);
      }

      setTimeout(() => cerrarModalSubCategoria(), 1500);
    } else {
      alertContainer.innerHTML = `<div class="alert alert-error">Error: ${resultado.message}</div>`;
    }
  } catch (error) {
    console.error("Error al crear subcategoría:", error);
    alertContainer.innerHTML = `<div class="alert alert-error">Error de conexión: ${error.message}</div>`;
  }
}

// ============================================================================
// VARIABLES GLOBALES PARA REPORTE
// ============================================================================
let datosReporte = [];

// ============================================================================
// FUNCIÓN: CARGAR REPORTE
// ============================================================================
async function cargarReporte() {
  const alertContainer = document.getElementById("alert-container-reporte");
  const tablaBody = document.getElementById("tablaReporteBody");
  const btnDescargar = document.getElementById("btnDescargarExcel");
  const estadisticas = document.getElementById("estadisticas-reporte");
  const contenedorTabla = document.getElementById("contenedor-tabla-reporte");

  try {
    alertContainer.innerHTML = "";
    tablaBody.innerHTML =
      '<tr><td colspan="19" class="loading-reporte">Cargando datos</td></tr>';
    contenedorTabla.style.display = "block";
    estadisticas.style.display = "none";
    btnDescargar.disabled = true;

    const idCategoria = document.getElementById("repIdCategoria").value.trim();
    const idSubCategoria = document
      .getElementById("repIdSubCategoria")
      .value.trim();
    const idGAItem = document.getElementById("repIdGAItem").value.trim();
    const nomActivo = document.getElementById("nomActivo").value.trim();
    const porcCostoIni = document.getElementById("porcCostoIni").value.trim();
    const porcCostoFin = document.getElementById("porcCostoFin").value.trim();

    const params = new URLSearchParams();

    if (idCategoria) params.append("idCategoria", idCategoria);
    if (idSubCategoria) params.append("idSubCategoria", idSubCategoria);
    if (idGAItem) params.append("idGAItem", idGAItem);
    if (nomActivo) params.append("nomActivo", nomActivo);
    if (porcCostoIni) params.append("porcCostoIni", porcCostoIni);
    if (porcCostoFin) params.append("porcCostoFin", porcCostoFin);

    const response = await fetch(
      `/api/activos-fijos/reporte?${params.toString()}`,
    );
    console.log("URL reporte:", response);

    const resultado = await response.json();

    if (!response.ok) {
      throw new Error(resultado.message || "Error al cargar el reporte");
    }

    if (!resultado.success) {
      throw new Error(resultado.message || "Error al cargar el reporte");
    }

    // Guardar datos globalmente
    datosReporte = resultado.data || [];

    // Validar si hay datos
    if (datosReporte.length === 0) {
      tablaBody.innerHTML =
        '<tr><td colspan="19" style="text-align: center; padding: 30px; color: #666;">No hay activos fijos registrados en el sistema.</td></tr>';
      mostrarAlerta(
        "No se encontraron registros",
        "warning",
        "alert-container-reporte",
      );
      return;
    }

    // Renderizar tabla
    renderizarTablaReporte(datosReporte);

    // Calcular y mostrar estadísticas
    calcularEstadisticas(datosReporte);

    // Habilitar botón de descarga
    btnDescargar.disabled = false;

    // Mostrar éxito
    mostrarAlerta(
      `Reporte cargado exitosamente: ${datosReporte.length} registros`,
      "success",
      "alert-container-reporte",
    );
  } catch (error) {
    console.error("Error al cargar reporte:", error);
    tablaBody.innerHTML =
      '<tr><td colspan="19" style="text-align: center; padding: 30px; color: #e74c3c;">Error al cargar los datos. Intente nuevamente.</td></tr>';
    mostrarAlerta(
      "Error: " + error.message,
      "error",
      "alert-container-reporte",
    );
    btnDescargar.disabled = true;
  }
}

// ============================================================================
// FUNCIÓN: RENDERIZAR TABLA DE REPORTE
// ============================================================================

function renderizarTablaReporte(datos) {
  const tbody = document.getElementById("tablaReporteBody");
  const contadorRegistros = document.getElementById("contador-registros");
  const contenedorTabla = document.getElementById("contenedor-tabla-reporte");

  tbody.innerHTML = "";

  datos.forEach((activo, index) => {
    const fila = document.createElement("tr");

    if (index % 2 === 0) {
      fila.style.backgroundColor = "#f8f9fa";
    }

    fila.innerHTML = `
                    <td>${index + 1 || ""}</td>
                    <td>${activo.Categoria || ""}</td>
                    <td>${activo.NomSubCategoria || ""}</td>
                    <td>${activo.CodActivoFijo || ""}</td>
                    <td>${activo.NombreActivo || ""}</td>
                    <td>${formatearFecha(activo.FecAdquisicion)}</td>
                    <td style="text-align: right;">${formatearMoneda(activo.CostoAdquisicion)}</td>
                    <td>${activo.NomGAItem || ""}</td>
                    <td>${activo.IdCuenta || ""}</td>
                `;

    tbody.appendChild(fila);
  });

  contadorRegistros.textContent = `Mostrando ${datos.length} registro${datos.length !== 1 ? "s" : ""}`;
  contenedorTabla.style.display = "block";
}

// ============================================================================
// FUNCIÓN: CALCULAR ESTADÍSTICAS
// ============================================================================
function calcularEstadisticas(datos) {
  const estadisticas = document.getElementById("estadisticas-reporte");

  // Total de activos
  const total = datos.length;
  document.getElementById("totalActivos").textContent = total;

  // Valor total
  const valorTotal = datos.reduce(
    (sum, activo) => sum + (activo.CostoAdquisicion || 0),
    0,
  );
  document.getElementById("valorTotal").textContent =
    formatearMoneda(valorTotal);

  estadisticas.style.display = "block";
}

// ============================================================================
// FUNCIÓN: DESCARGAR EXCEL
// ============================================================================
async function descargarExcel() {
  if (datosReporte.length === 0) {
    mostrarAlerta(
      "No hay datos para exportar",
      "warning",
      "alert-container-reporte",
    );
    return;
  }

  try {
    // Preparar datos para Excel
    const datosExcel = datosReporte.map((activo, index) => ({
      ID: index + 1 || "",
      Categoria: activo.Categoria || "",
      NomSubCategoria: activo.NomSubCategoria || "",
      CodActivoFijo: activo.CodActivoFijo || "",
      NombreActivo: activo.NombreActivo || "",
      "Fecha Adquisición": formatearFecha(activo.FecAdquisicion),
      "Costo Adquisición": formatearMoneda(activo.CostoAdquisicion) || 0,
      NomGAItem: activo.NomGAItem || "",
      idCuenta: activo.IdCuenta || "",
    }));

    // Crear libro de Excel
    const worksheet = XLSX.utils.json_to_sheet(datosExcel);
    const workbook = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(workbook, worksheet, "Activos Fijos");

    // Ajustar anchos de columna
    const columnWidths = [
      { wch: 8 }, // ID
      { wch: 15 }, // Categoria
      { wch: 30 }, // NomSubCategoria
      { wch: 20 }, // CodActivoFijo
      { wch: 12 }, //NombreActivo
      { wch: 20 }, // Fecha Adquisicion
      { wch: 20 }, // Costo Adquisicion
      { wch: 12 }, // NomGAItem
      { wch: 12 }, //idCuenta
    ];
    worksheet["!cols"] = columnWidths;

    // Generar nombre de archivo con fecha
    const fecha = new Date().toISOString().split("T")[0];
    const nombreArchivo = `Reporte_Activos_Fijos_${fecha}.xlsx`;

    // Descargar
    XLSX.writeFile(workbook, nombreArchivo);

    mostrarAlerta(
      "Excel descargado exitosamente",
      "success",
      "alert-container-reporte",
    );
  } catch (error) {
    console.error("Error al generar Excel:", error);
    mostrarAlerta(
      "Error al generar el archivo Excel: " + error.message,
      "error",
      "alert-container-reporte",
    );
  }
}

// ============================================================================
// FUNCIONES AUXILIARES
// ============================================================================

function obtenerColorEstado(estado) {
  const colores = {
    Bueno: "success",
    Regular: "warning",
    Defectuoso: "danger",
    Malo: "danger",
  };
  return colores[estado] || "secondary";
}

function formatearFecha(fecha) {
  if (!fecha) return "N/A";
  const date = new Date(fecha);
  if (isNaN(date.getTime())) return "N/A";
  return date.toLocaleDateString("es-CO", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  });
}

function formatearMoneda(valor) {
  if (valor === null || valor === undefined) return "$0";
  return new Intl.NumberFormat("es-CO", {
    style: "currency",
    currency: "COP",
    minimumFractionDigits: 0,
    maximumFractionDigits: 0,
  }).format(valor);
}

// ============================================
// BUSCAR ACTIVOS
// ============================================
async function buscarActivos() {
  const codigo = document.getElementById("searchCodigo").value;
  const nombre = document.getElementById("searchNombre").value;
  const estado = document.getElementById("searchEstado").value;
  const responsable = document.getElementById("searchResponsable").value;
  const idCategoria = document.getElementById("consIdCategoria").value;
  const idSubCategoria = document.getElementById("consIdSubCategoria").value;
  const idGAItem = document.getElementById("consIdGAItem").value;

  let url = API_URL + "/consultar?";
  if (codigo) url += `codActivoFijo=${encodeURIComponent(codigo)}&`;
  if (nombre) url += `nombreActivo=${encodeURIComponent(nombre)}&`;
  if (estado) url += `estado=${encodeURIComponent(estado)}&`;
  if (responsable) url += `responsable=${encodeURIComponent(responsable)}&`;
  if (idCategoria) url += `idCategoria=${encodeURIComponent(idCategoria)}&`;
  if (idSubCategoria)
    url += `idSubCategoria=${encodeURIComponent(idSubCategoria)}&`;
  if (idGAItem) url += `IdGAItem=${encodeURIComponent(idGAItem)}`;

  try {
    const response = await fetch(url);
    const resultado = await response.json();

    if (resultado.success) {
      mostrarResultados(resultado.data);
    } else {
      mostrarAlerta(
        "⚠ " + resultado.message,
        "error",
        "alert-container-consulta",
      );
    }
  } catch (error) {
    console.error("Error:", error);
    mostrarAlerta(
      "⚠ Error al conectar con el servidor",
      "error",
      "alert-container-consulta",
    );
  }
}

// ============================================
// MOSTRAR RESULTADOS 
// ============================================
async function mostrarResultados(activos) {
  const tbody = document.getElementById("tablaActivosBody");

  if (activos.length === 0) {
    tbody.innerHTML = `
            <tr>
                <td colspan="9" style="text-align: center; padding: 30px;">
                    No se encontraron registros con los criterios de búsqueda.
                </td>
            </tr>
        `;
    return;
  }

  tbody.innerHTML =
    '<tr><td colspan="9" style="text-align: center; padding: 20px;">Cargando resultados...</td></tr>';

  const verificaciones = await Promise.all(
    activos.map((activo) => verificarSiTieneImagen(activo.IdActivoFijoKey)),
  );

  tbody.innerHTML = "";

  activos.forEach((activo, index) => {
    const tieneImagen = verificaciones[index];
    const tr = document.createElement("tr");

    tr.innerHTML = `
            <td>${activo.IdActivoFijoKey}</td>
            <td><strong>${activo.CodActivoFijo}</strong></td>
            <td>${activo.NombreActivo}</td>
            <td>${getBadgeEstado(activo.Estado)}</td>
            <td>${activo.Responsable}</td>
            <td>${activo.LugarUbicacion}</td>
            <td>${formatearFecha(activo.FecAdquisicion)}</td>
            <td><strong>$${formatearNumero(activo.CostoAdquisicion)}</strong></td>
            <td>
                <div class="action-buttons">
                    <button 
                    onclick="${
                      tieneImagen
                        ? `verImagen(${activo.IdActivoFijoKey}, this)`
                        : `subirImagen(${activo.IdActivoFijoKey})`
                    }"
                    class="btn btn-sm ${tieneImagen ? "btn-primary" : "btn-success"}"
                    id="btnImg-${activo.IdActivoFijoKey}">
                    ${tieneImagen ? "Ver Imágenes" : "Subir Imágenes"}
                    </button>

                    <button class="btn btn-sm btn-edit" onclick="editarActivo(${activo.IdActivoFijoKey})">
                        Editar ✏️
                    </button>

                    <button class="btn btn-sm btn-danger" onclick="eliminarActivo(${activo.IdActivoFijoKey})">
                        Eliminar 🗑️
                    </button>
                </div>
            </td>
        `;

    tbody.appendChild(tr);
  });
}

// ============================================
// FUNCIÓN 2: VERIFICAR SI TIENE IMAGEN
// ============================================
async function verificarSiTieneImagen(idActivo) {
  try {
    const res = await fetch(`${API_URL}/imagen/${idActivo}/existe`);
    const r = await res.json();
    return r.success ? r.tieneImagen : false;
  } catch (e) {
    console.error("Error al verificar imagen:", e);
    return false;
  }
}

// ============================================
// FUNCIÓN 3: SUBIR IMAGEN
// ============================================
async function subirImagen(id) {
  const modal = document.createElement("div");
  modal.className = "modal show";
  modal.innerHTML = `
    <div class="modal-content">
      <div class="modal-header">
        <h3>Subir Imagen del Activo #${id}</h3>
        <span class="close" onclick="this.closest('.modal').remove()">&times;</span>
      </div>

      <div class="modal-body" style="padding: 20px;">
        <div style="text-align: center; margin-bottom: 20px;">
          <label for="inputImagen-${id}" style="cursor: pointer; display: inline-block; padding: 15px 30px; background: linear-gradient(135deg, var(--primary) 0%, var(--primary-dark) 100%); color: white; border-radius: 8px; font-weight: 600;">
            Seleccionar Imágenes
          </label>
          <input type="file" id="inputImagen-${id}" accept="image/*" multiple style="display:none;">
        </div>

        <div id="preview-${id}" style="display:none; margin-top:20px;">
          <p id="nombreArchivo-${id}" style="color:#666; font-size:14px; text-align:center;"></p>
          <div id="gridPreview-${id}" style="display:grid; grid-template-columns:repeat(auto-fill, minmax(120px, 1fr)); gap:10px; margin-top:15px;"></div>
        </div>
      </div>

      <div class="modal-footer">
        <button class="btn btn-secondary" onclick="this.closest('.modal').remove()">Cancelar</button>
        <button class="btn btn-success" id="btnSubir-${id}" disabled>Subir Imagenes</button>
      </div>
    </div>
  `;
  document.body.appendChild(modal);

  const input = document.getElementById(`inputImagen-${id}`);
  const preview = document.getElementById(`preview-${id}`);
  const nombreArchivo = document.getElementById(`nombreArchivo-${id}`);
  const gridPreview = document.getElementById(`gridPreview-${id}`);
  const btnSubir = document.getElementById(`btnSubir-${id}`);

  let archivosSeleccionados = [];

  input.addEventListener("change", function (e) {
    archivosSeleccionados = Array.from(e.target.files || []);

    if (archivosSeleccionados.length === 0) {
      btnSubir.disabled = true;
      preview.style.display = "none";
      return;
    }

    // Validar todos
    for (const file of archivosSeleccionados) {
      if (!file.type.startsWith("image/")) {
        mostrarAlerta(
          "⚠ Todos los archivos deben ser imágenes",
          "error",
          "alert-container-consulta",
        );
        btnSubir.disabled = true;
        return;
      }
      if (file.size > 5 * 1024 * 1024) {
        mostrarAlerta(
          "⚠ Cada imagen no debe superar 5MB",
          "error",
          "alert-container-consulta",
        );
        btnSubir.disabled = true;
        return;
      }
    }

    // Mostrar nombres + previews
    nombreArchivo.textContent = `${archivosSeleccionados.length} imagen(es) seleccionada(s)`;
    gridPreview.innerHTML = "";
    preview.style.display = "block";
    btnSubir.disabled = false;

    // Mini previews 
    archivosSeleccionados.forEach((file) => {
      const reader = new FileReader();
      reader.onload = (ev) => {
        const img = document.createElement("img");
        img.src = ev.target.result;
        img.style.width = "100%";
        img.style.height = "90px";
        img.style.objectFit = "cover";
        img.style.borderRadius = "8px";
        img.style.border = "1px solid #ddd";
        gridPreview.appendChild(img);
      };
      reader.readAsDataURL(file);
    });
  });

  btnSubir.addEventListener("click", async function () {
    if (archivosSeleccionados.length === 0) return;

    btnSubir.disabled = true;
    btnSubir.textContent = "Subiendo...";

    try {
      const formData = new FormData();
      formData.append("IdActivoFijoKey", id);

      archivosSeleccionados.forEach((file) =>
        formData.append("imagenes", file),
      );

      const response = await fetch(`${API_URL}/imagen`, {
        method: "POST",
        body: formData,
      });

      const resultado = await response.json();

      if (resultado.success) {
        mostrarAlerta(
          "✔ Imágenes subidas correctamente",
          "success",
          "alert-container-consulta",
        );
        modal.remove();
        actualizarBotonImagen(id, true);
      } else {
        mostrarAlerta(
          "⚠ " + (resultado.message || "Error"),
          "error",
          "alert-container-consulta",
        );
        btnSubir.disabled = false;
        btnSubir.textContent = "Subir Imagenes";
      }
    } catch (error) {
      console.error("Error:", error);
      mostrarAlerta(
        "⚠ Error al subir las imágenes",
        "error",
        "alert-container-consulta",
      );
      btnSubir.disabled = false;
      btnSubir.textContent = "Subir Imagenes";
    }
  });
}

// ============================================
// FUNCIÓN 4: VER IMAGEN 
// ============================================
async function verImagen(idActivo, btn) {

  let textoOriginal;
  if (btn) {
    btn.disabled = true;
    textoOriginal = btn.innerHTML;
    btn.innerHTML = "Cargando...";
  }

  try {
    const res = await fetch(`${API_URL}/imagen/${idActivo}`);
    const r = await res.json();

    if (!r.success || !r.tieneImagen) {
      mostrarAlerta("⚠ No hay imágenes", "error", "alert-container-consulta");
      return;
    }

    const items = r.imagenes
      .map(
        (img) => `
      <div style="display:flex; gap:15px; align-items:center; margin-bottom:15px; border:1px solid #e0e0e0; padding:15px; border-radius:10px; background:#fafafa; transition: all 0.3s;">
        <div style="position:relative;">
          <img src="${img.src}" 
               style="width:250px; height:180px; object-fit:cover; border-radius:8px; cursor:pointer; transition: transform 0.2s; box-shadow: 0 2px 8px rgba(0,0,0,0.1);"
               onclick="ampliarImagen('${img.src}')"
               onmouseover="this.style.transform='scale(1.05)'; this.style.boxShadow='0 4px 16px rgba(0,0,0,0.2)'"
               onmouseout="this.style.transform='scale(1)'; this.style.boxShadow='0 2px 8px rgba(0,0,0,0.1)'">
        </div>
        <div style="flex:1;">
          <p style="margin:0; color:#666; font-size:14px;">Imagen #${img.idImagen}</p>
          <p style="margin:5px 0 0 0; color:#999; font-size:12px;">Click para ampliar</p>
        </div>
        <button class="btn btn-sm btn-danger"
                onclick="eliminarImagenPorId(${img.idImagen}, ${idActivo})"
                style="height:fit-content;">
          Eliminar
        </button>
      </div>
    `,
      )
      .join("");

    const modal = document.createElement("div");
    modal.className = "modal show";
    modal.innerHTML = `
      <div class="modal-content" style="max-width:950px;">
        <div class="modal-header" style="background:linear-gradient(135deg, #667eea 0%, #764ba2 100%); color:white; border-radius:10px 10px 0 0;">
          <h3 style="margin:0;">Imágenes del Activo #${idActivo} (${r.total})</h3>
          <span class="close" onclick="this.closest('.modal').remove()" style="color:white; font-size:28px; cursor:pointer;">&times;</span>
        </div>
        <div class="modal-body" style="padding:25px; max-height:70vh; overflow:auto;">
          ${
            r.total === 0
              ? '<p style="text-align:center; color:#999; padding:40px 0;">No hay imágenes todavía</p>'
              : items
          }
        </div>
        <div class="modal-footer" style="padding:15px; background:#f8f9fa; border-radius:0 0 10px 10px;">
          <button class="btn btn-success"
            onclick="this.closest('.modal').remove(); subirImagen(${idActivo});">
            + Agregar más
          </button>
          <button class="btn btn-secondary"
            onclick="this.closest('.modal').remove()">
            Cerrar
          </button>
        </div>
      </div>
    `;
    document.body.appendChild(modal);
  } catch (error) {
    console.error(error);
    mostrarAlerta(
      "⚠ Error al cargar las imágenes",
      "error",
      "alert-container-consulta",
    );
  } finally {
  
    if (btn) {
      btn.disabled = false;
      btn.innerHTML = textoOriginal || "Ver Imágenes";
    }
  }
}

// ============================================
// FUNCIÓN AUXILIAR: AMPLIAR IMAGEN
// ============================================
function ampliarImagen(src) {
  const modalAmpliar = document.createElement("div");
  modalAmpliar.className = "modal show";
  modalAmpliar.style.zIndex = "10000";
  modalAmpliar.style.background = "rgba(0,0,0,0.9)";
  modalAmpliar.innerHTML = `
    <div class="modal-content" style="max-width:95vw; max-height:95vh; background:transparent; border:none; box-shadow:none; position:relative;">
      <span class="close" 
            onclick="this.closest('.modal').remove()" 
            style="position:absolute; top:20px; right:30px; color:white; font-size:40px; cursor:pointer; z-index:10001; background:rgba(0,0,0,0.5); width:50px; height:50px; border-radius:50%; display:flex; align-items:center; justify-content:center; transition: all 0.2s;"
            onmouseover="this.style.background='rgba(255,255,255,0.2)'; this.style.transform='rotate(90deg)'"
            onmouseout="this.style.background='rgba(0,0,0,0.5)'; this.style.transform='rotate(0deg)'">&times;</span>
      
      <div style="display:flex; justify-content:center; align-items:center; min-height:90vh; padding:60px 20px 20px 20px;">
        <img src="${src}" 
             style="max-width:100%; max-height:85vh; object-fit:contain; border-radius:8px; box-shadow:0 10px 40px rgba(0,0,0,0.8); cursor:pointer;"
             onclick="this.closest('.modal').remove()"
             title="Click para cerrar">
      </div>
      
      <div style="position:absolute; bottom:20px; left:50%; transform:translateX(-50%); color:white; background:rgba(0,0,0,0.6); padding:10px 20px; border-radius:20px; font-size:14px;">
        Click en la imagen o presiona ESC para cerrar
      </div>
    </div>
  `;

  // Cerrar con click fuera de la imagen
  modalAmpliar.addEventListener("click", (e) => {
    if (e.target === modalAmpliar) {
      modalAmpliar.remove();
    }
  });

  // Cerrar con ESC
  const cerrarConEsc = (e) => {
    if (e.key === "Escape") {
      modalAmpliar.remove();
      document.removeEventListener("keydown", cerrarConEsc);
    }
  };
  document.addEventListener("keydown", cerrarConEsc);

  document.body.appendChild(modalAmpliar);
}

async function eliminarImagenPorId(idImagen, idActivo) {
  if (!confirm("¿Eliminar esta imagen?")) return;

  try {
    const res = await fetch(`${API_URL}/imagen/${idImagen}`, {
      method: "DELETE",
    });
    const r = await res.json();

    if (r.success) {
      mostrarAlerta(
        "✔ Imagen eliminada",
        "success",
        "alert-container-consulta",
      );

      const modalActual = document.querySelector(".modal.show");
      if (modalActual) {
        modalActual.remove();
      }

      setTimeout(() => {
        verImagen(idActivo); 
      }, 100);

      try {
        const check = await fetch(`${API_URL}/imagen/${idActivo}/existe`);
        const checkData = await check.json();
        actualizarBotonImagen(
          idActivo,
          checkData.success ? checkData.tieneImagen : false,
        );
      } catch (err) {
        console.error("Error al verificar imágenes:", err);
      }
    } else {
      mostrarAlerta("⚠ " + r.message, "error", "alert-container-consulta");
    }
  } catch (error) {
    console.error(error);
    mostrarAlerta(
      "⚠ Error al eliminar la imagen",
      "error",
      "alert-container-consulta",
    );
  }
}

// ============================================
// FUNCIÓN 6: ACTUALIZAR BOTÓN IMAGEN 
// ============================================
function actualizarBotonImagen(id, tieneImagen) {
  const boton = document.getElementById(`btnImg-${id}`);

  if (boton) {
    if (tieneImagen) {

      boton.className = "btn btn-sm btn-primary";
      boton.innerHTML = "Ver Imagenes";
      boton.setAttribute("onclick", `verImagen(${id})`);
    } else {
      boton.className = "btn btn-sm btn-success";
      boton.innerHTML = "Subir Imagen";
      boton.setAttribute("onclick", `subirImagen(${id})`);
    }
  }
}

// ============================================
// FUNCIÓN 7: ELIMINAR ACTIVO
// ============================================
let idActivoAEliminar = null;

function eliminarActivo(id) {
  idActivoAEliminar = id;
  document.getElementById("modalConfirmDelete").classList.add("show");
}

function cerrarModalEliminar() {
  idActivoAEliminar = null;
  document.getElementById("modalConfirmDelete").classList.remove("show");
}

async function confirmarEliminar() {
  if (!idActivoAEliminar) return;

  try {
    const response = await fetch(`${API_URL}/${idActivoAEliminar}`, {
      method: "DELETE",
    });

    const resultado = await response.json();

    if (resultado.success) {
      mostrarAlerta(
        "✔ " + resultado.message,
        "success",
        "alert-container-consulta",
      );
      buscarActivos(); 
    } else {
      mostrarAlerta(
        "⚠ " + resultado.message,
        "error",
        "alert-container-consulta",
      );
    }
  } catch (error) {
    console.error("Error:", error);
    mostrarAlerta(
      "⚠ Error al conectar con el servidor",
      "error",
      "alert-container-consulta",
    );
  } finally {
    cerrarModalEliminar();
  }
}

async function editarActivo(idActivo) {
  try {

    const response = await fetch(`${API_URL}/tablas/6?id=${idActivo}&id2=0`);

    if (!response.ok) {
      throw new Error("Error al obtener el activo");
    }

    const responseData = await response.json();

    let activo;
    if (Array.isArray(responseData.data)) {
      activo = responseData.data[0];
    } else {
      activo = responseData.data;
    }

    if (!activo) {
      throw new Error("No se encontró el activo");
    }

    console.log("ACTIVO OBTENIDO:", activo);

    // ========================================
    // 2. CARGAR LOS SELECTS PRIMERO
    // ========================================
    console.log("Cargando categorías...");
    await cargarCategoriasModal();

    console.log("Cargando centros de costo...");
    await cargarCentrosCostoModal();

    console.log("Cargando sucursales...");
    await cargarSucursalesModal();

    await new Promise((resolve) => setTimeout(resolve, 200));

    // ========================================
    // 3. LLENAR CAMPOS NORMALES
    // ========================================
    document.getElementById("codActivoFijoModal").value =
      activo.CodActivoFijo || "";
    document.getElementById("nombreActivoModal").value =
      activo.NombreActivo || "";
    document.getElementById("descripcionModal").value =
      activo.Descripcion || "";
    document.getElementById("responsableModal").value =
      activo.Responsable || "";
    document.getElementById("lugarUbicacionModal").value =
      activo.LugarUbicacion || "";
    document.getElementById("estadoModal").value = activo.Estado || "";

    if (activo.FecAdquisicion) {
      const fecha = String(activo.FecAdquisicion).split("T")[0];
      document.getElementById("fecAdquisicionModal").value = fecha;
    }

    document.getElementById("costoAdquisicionModal").value =
      activo.CostoAdquisicion || 0;
    document.getElementById("idCuentaModal").value = activo.IdCuenta || "";
    document.getElementById("aseguradoModal").checked =
      activo.Asegurado || false;

    // COMPRA
    document.getElementById("idProveedorModal").value =
      activo.IdProveedor || "";
    document.getElementById("noFacturaModal").value = activo.NoFactura || "";
    document.getElementById("fabricanteModal").value = activo.Fabricante || "";

    if (activo.FecFabricacion) {
      const fechaFab = String(activo.FecFabricacion).split("T")[0];
      document.getElementById("fecFabricacionModal").value = fechaFab;
    }

    document.getElementById("modeloModal").value = activo.Modelo || "";
    document.getElementById("serialModal").value = activo.Serial || "";

    document.getElementById("activoModal").checked = activo.Activo !== false;

    // ========================================
    // 4. ASIGNAR CATEGORÍA
    // ========================================
    const categoriaId = activo.IdCategoria;
    const subcategoriaId = activo.idSubCatKey;

    console.log("Asignando categoría ID:", categoriaId);

    if (categoriaId) {
      const selectCategoria = document.getElementById("idGrupoCatModal");
      console.log(
        "Opciones disponibles en categoría:",
        selectCategoria.options.length,
      );

      selectCategoria.value = String(categoriaId);
      console.log(
        "Categoría value después de asignar:",
        selectCategoria.value,
      );

      if (selectCategoria.value) {

        console.log("Cargando subcategorías...");
        await cargarSubCategoriasModal(categoriaId);
        await new Promise((resolve) => setTimeout(resolve, 100));

        if (subcategoriaId) {
          const selectSubcategoria =
            document.getElementById("idSubCatKeyModal");
          console.log(
            "Opciones disponibles en subcategoría:",
            selectSubcategoria.options.length,
          );

          selectSubcategoria.value = String(subcategoriaId);
          console.log(
            "Subcategoría value después de asignar:",
            selectSubcategoria.value,
          );
        }
      } else {
        console.warn("La categoría no se pudo asignar");
      }
    }

    // ========================================
    // 5. ASIGNAR CENTRO DE COSTO Y SUCURSAL
    // ========================================
    const centroCostoId = activo.IdGAItem;
    const sucursalId = activo.IdGAItem2;

    console.log("Valores del activo:");
    console.log(
      "   Centro de Costo ID:",
      centroCostoId,
      "Tipo:",
      typeof centroCostoId,
    );
    console.log("   Sucursal ID:", sucursalId, "Tipo:", typeof sucursalId);

    if (centroCostoId) {
      const selectCentro = document.getElementById("idGAItemModal");
      console.log(" Select Centro de Costo:");
      console.log("   Total opciones:", selectCentro.options.length);

      for (let i = 0; i < selectCentro.options.length; i++) {
        console.log(
          `   Opción ${i}: value="${selectCentro.options[i].value}" (tipo: ${typeof selectCentro.options[i].value})`,
        );
      }

      selectCentro.value = String(centroCostoId);
      console.log(
        "Centro de Costo value después de asignar:",
        selectCentro.value,
      );

      if (!selectCentro.value) {
        console.warn(
          "El Centro de Costo no se asignó. Intentando forzar...",
        );

        for (let i = 0; i < selectCentro.options.length; i++) {
          if (selectCentro.options[i].value == centroCostoId) {
            selectCentro.selectedIndex = i;
            console.log("Centro de Costo asignado por índice:", i);
            break;
          }
        }
      }
    }

    if (sucursalId) {
      const selectSucursal = document.getElementById("idGAItem2Modal");
      console.log("   Select Sucursal:");
      console.log("   Total opciones:", selectSucursal.options.length);

      for (let i = 0; i < selectSucursal.options.length; i++) {
        console.log(
          `   Opción ${i}: value="${selectSucursal.options[i].value}" (tipo: ${typeof selectSucursal.options[i].value})`,
        );
      }

      selectSucursal.value = String(sucursalId);
      console.log(
        "Sucursal value después de asignar:",
        selectSucursal.value,
      );

      if (!selectSucursal.value) {
        console.warn("La Sucursal no se asignó. Intentando forzar...");

        for (let i = 0; i < selectSucursal.options.length; i++) {
          if (selectSucursal.options[i].value == sucursalId) {
            selectSucursal.selectedIndex = i;
            console.log("✔ Sucursal asignada por índice:", i);
            break;
          }
        }
      }
    }

    // ========================================
    // 6. ABRIR MODAL
    // ========================================
    document.getElementById("idActivoEdit").value = idActivo;
    document.getElementById("modalTitle").textContent =
      `Editar Activo #${idActivo}`;
    document.getElementById("btnGuardar").textContent = "Actualizar";

    const modalElement = document.getElementById("modalActivo");
    modalElement.classList.add("show");
  } catch (error) {
    console.error("⚠ Error al editar activo:", error);
    alert("Error al cargar los datos del activo: " + error.message);
  }
}

// ============================================
// FUNCIONES AUXILIARES PARA CARGAR SELECTS DEL MODAL
// ============================================
async function cargarCategoriasModal() {
  try {
    const response = await fetch(`${API_URL}/tablas/4?id=0&id2=0`);
    const result = await response.json(); 
    console.log("Categorías:", result);
    const select = document.getElementById("idGrupoCatModal");
    select.innerHTML = '<option value="">Seleccione...</option>';

    if (result.success && Array.isArray(result.data)) {
      result.data.forEach((item) => {
        const option = document.createElement("option");
        option.value = item.IdCategoria;
        option.textContent = item.Categoria;
        select.appendChild(option);
      });
    } else {
      console.error("La respuesta no contiene un array de categorías:", result);
      alert(
        "No se pudieron cargar las categorías. Por favor, verifica la conexión.",
      );
    }
  } catch (error) {
    console.error("Error al cargar categorías:", error);
    alert("Ocurrió un error al cargar las categorías.");
  }
}

async function cargarSubCategoriasModal(idCategoria) {
  try {
    const response = await fetch(`${API_URL}/tablas/5?id=${idCategoria}&id2=0`);
    const result = await response.json();
    console.log("Subcategorías:", result);
    const select = document.getElementById("idSubCatKeyModal");
    select.innerHTML = '<option value="">Seleccione...</option>'; 

    if (result.success && Array.isArray(result.data)) {
      result.data.forEach((item) => {
        const option = document.createElement("option"); 
        option.value = item.idSubCatKey; 
        option.textContent = item.NomSubCategoria;
        select.appendChild(option); 
      });
    } else {
      console.error(
        "La respuesta no contiene un array de subcategorías:",
        result,
      );
      alert("No se pudieron cargar las subcategorías.");
    }
  } catch (error) {
    console.error("Error al cargar subcategorías:", error);
    alert("Ocurrió un error al cargar las subcategorías.");
  }
}

async function cargarCentrosCostoModal() {
  try {
    const response = await fetch(`${API_URL}/tablas/1?id=0&id2=0`);
    const result = await response.json();
    console.log("Centros de Costo:", result); 
    const select = document.getElementById("idGAItemModal");
    select.innerHTML = '<option value="">Seleccione...</option>';

    if (result.success && Array.isArray(result.data)) {
      result.data.forEach((item) => {
        const option = document.createElement("option");
        option.value = item.IdGAItem;
        option.textContent = item.NomGAItem;
        select.appendChild(option);
      });
    } else {
      console.error(
        "La respuesta no contiene un array de centros de costo:",
        result,
      );
      alert("No se pudieron cargar los centros de costo.");
    }
  } catch (error) {
    console.error("Error al cargar centros de costo:", error);
    alert("Ocurrió un error al cargar los centros de costo.");
  }
}

async function cargarSucursalesModal() {
  try {
    const response = await fetch(`${API_URL}/tablas/2?id=0&id2=0`);
    const result = await response.json();
    console.log("Sucursales:", result); 
    const select = document.getElementById("idGAItem2Modal");
    select.innerHTML = '<option value="">Seleccione...</option>';

    if (result.success && Array.isArray(result.data)) {
      result.data.forEach((item) => {
        const option = document.createElement("option");
        option.value = item.IdGAItem;
        option.textContent = item.NomGAItem;
        select.appendChild(option);
      });
    } else {
      console.error("La respuesta no contiene un array de sucursales:", result);
      alert("No se pudieron cargar las sucursales.");
    }
  } catch (error) {
    console.error("Error al cargar sucursales:", error);
    alert("Ocurrió un error al cargar las sucursales.");
  }
}

// ============================================
// FUNCIÓN PARA GUARDAR
// ============================================

async function guardarActivo() {
  const idActivoEdit = document.getElementById("idActivoEdit");
  const codActivoFijoModal = document.getElementById("codActivoFijoModal");
  const nombreActivoModal = document.getElementById("nombreActivoModal");
  const descripcionModal = document.getElementById("descripcionModal");
  const responsableModal = document.getElementById("responsableModal");
  const lugarUbicacionModal = document.getElementById("lugarUbicacionModal");
  const estadoModal = document.getElementById("estadoModal");
  const fecAdquisicionModal = document.getElementById("fecAdquisicionModal");
  const costoAdquisicionModal = document.getElementById(
    "costoAdquisicionModal",
  );
  const aseguradoModal = document.getElementById("aseguradoModal");
  const activoModal = document.getElementById("activoModal");
  const idProveedorModal = document.getElementById("idProveedorModal");
  const noFacturaModal = document.getElementById("noFacturaModal");
  const fabricanteModal = document.getElementById("fabricanteModal");
  const fecFabricacionModal = document.getElementById("fecFabricacionModal");
  const modeloModal = document.getElementById("modeloModal");
  const serialModal = document.getElementById("serialModal");

  const idGrupoCatModal = document.getElementById("idGrupoCatModal");
  const idSubCatKeyModal = document.getElementById("idSubCatKeyModal");
  const idGAItemModal = document.getElementById("idGAItemModal");
  const idGAItem2Modal = document.getElementById("idGAItem2Modal");
  const idCuentaModal = document.getElementById("idCuentaModal");

  const data = {
    IdActivoFijoKey: parseInt(idActivoEdit.value),
    NombreActivo: nombreActivoModal.value,
    Descripcion: descripcionModal.value,
    Activo: activoModal.checked,
    Estado: estadoModal.value,
    Fabricante: fabricanteModal.value,
    IdProveedor: idProveedorModal.value
      ? parseInt(idProveedorModal.value)
      : null,
    Modelo: modeloModal.value,
    Serial: serialModal.value,
    FecFabricacion: fecFabricacionModal.value || null,
    FecAdquisicion: fecAdquisicionModal.value,
    CostoAdquisicion: parseFloat(costoAdquisicionModal.value) || 0,
    Responsable: responsableModal.value,
    LugarUbicacion: lugarUbicacionModal.value,
    IdGAItem: parseInt(idGAItemModal.value),
    NoFactura: noFacturaModal.value,
    Asegurado: aseguradoModal.checked,
    idSubCatKey: idSubCatKeyModal.value
      ? parseInt(idSubCatKeyModal.value)
      : null, 
    IdGAItem2: parseInt(idGAItem2Modal.value), 
  };

  console.log("DATOS A ENVIAR:", data);

  try {
    const response = await fetch(`${API_URL}/actualizar`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(data),
    });

    const resultado = await response.json();

    console.log("RESPUESTA DEL SERVIDOR:", resultado);

    if (resultado.success) {
      alert("Activo actualizado correctamente");
      cerrarModalActivo();
      buscarActivos();
    } else {
      alert("Error: " + resultado.message);
    }
  } catch (error) {
    console.error("Error al guardar:", error);
    alert("Error al actualizar el activo: " + error.message);
  }
}

function abrirModal() {
  document.getElementById("modalActivo").classList.add("show");
}

function cerrarModalActivo() {
  document.getElementById("modalActivo").classList.remove("show");
}
