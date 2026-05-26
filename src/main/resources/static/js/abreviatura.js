const host = window.location.hostname;
let _idTerceroKeyInsertar = null;


/**
 * Muestra un toast dinámico en pantalla.
 * 
 * @param {string} title Título del toast.
 * @param {string} message Mensaje descriptivo.
 * @param {string} [type="success"] Tipo de toast (success, error, warning, info).
 * @param {number} [duration=4000] Tiempo de duración en milisegundos.
 * @param {boolean} [showProgress=false] Indica si se muestra barra de progreso.
 * @returns {HTMLElement} Elemento toast creado.
 */

  function showToast(title, message, type = "success", duration = 4000, showProgress = false) {
    const container = document.getElementById("toastContainer");
    const toast = document.createElement("div");
    toast.className = `toast ${type}`;
    toast.innerHTML = `
      <div class="toast-content">
        <strong>${title}</strong>
        <p>${message}</p>
        ${showProgress ? `<div class="toast-progress-container"><div class="toast-progress-bar" style="width:0%"></div></div>` : ""}
      </div>`;
    container.appendChild(toast);
    if (!showProgress) {
      setTimeout(() => {
        toast.classList.add("fadeOut");
        setTimeout(() => toast.remove(), 300);
      }, duration);
    }
    return toast;
  }


/**
 * Actualiza visualmente el porcentaje de progreso de un toast.
 * 
 * @param {HTMLElement} toast Elemento toast a actualizar.
 * @param {number} pct Porcentaje de progreso.
 */
  function actualizarToastProgreso(toast, pct) {
    const bar = toast.querySelector(".toast-progress-bar");
    if (bar) bar.style.width = pct + 
    "%";
  }


/**
 * Evento submit del formulario de filtros.
 * Ejecuta automáticamente la búsqueda evitando el refresco de página.
 */
  document.getElementById("filtrosForm").addEventListener("submit", async function (e) {
    e.preventDefault();
    await buscar();
  });

/**
 * Consulta los documentos soporte aplicando filtros y renderiza la tabla.
 * También controla loaders, mensajes y estados visuales.
 * 
 * @returns {Promise<void>}
 */
  async function buscar() {
    const submitBtn = document.querySelector("#filtrosForm button[type='submit']");
    const originalHTML = submitBtn.innerHTML;
    submitBtn.disabled = true;
    submitBtn.innerHTML = "Buscando...";
    submitBtn.style.opacity = "0.6";

    document.getElementById("errorMsg").textContent = "";
    document.getElementById("emptyState").style.display = "none";
    document.getElementById("resultadosTabla").style.display = "none";
    document.getElementById("tablaBody").innerHTML = "";

    const nombre  = document.getElementById("nombre").value.trim();
    const inactivo = document.getElementById("inactivo").checked ? 1 : 0;

    const toast = showToast("Buscando", "Obteniendo resultados...", "info", 0, true);
    let progreso = 0;
    const intervalo = setInterval(() => {
      progreso = Math.min(progreso + 15, 95);
      actualizarToastProgreso(toast, progreso);
    }, 100);

    try {
      const params = new URLSearchParams();
      if (nombre) params.append("nombre", nombre);
      params.append("inactivo", inactivo);

      const resp = await fetch(`https://${host}:9876/abreviatura/doc-soporte?${params.toString()}`);

      clearInterval(intervalo);
      actualizarToastProgreso(toast, 100);
      setTimeout(() => { toast.classList.add("fadeOut"); setTimeout(() => toast.remove(), 300); }, 400);

      if (!resp.ok) throw new Error(await resp.text());

      const data = await resp.json();

      if (!Array.isArray(data) || data.length === 0) {
        document.getElementById("emptyState").style.display = "block";
        document.getElementById("cardTitle").textContent = "Resultados (0)";
        showToast("Sin resultados", "No se encontraron registros", "warning", 4000);
        return;
      }

      document.getElementById("cardTitle").textContent = `Resultados (${data.length})`;
      renderTabla(data);
      showToast("Éxito", `Se encontraron ${data.length} registros`, "success", 4000);

    } catch (err) {
      clearInterval(intervalo);
      toast.classList.add("fadeOut");
      setTimeout(() => toast.remove(), 300);
      document.getElementById("errorMsg").textContent = err.message;
      showToast("Error", err.message, "error", 5000);
    } finally {
      submitBtn.disabled = false;
      submitBtn.innerHTML = originalHTML;
      submitBtn.style.opacity = "1";
    }
  }


/**
 * Renderiza dinámicamente la tabla principal de documentos soporte.
 * 
 * @param {Array<Object>} data Lista de registros obtenidos desde el backend.
 */
  function renderTabla(data) {
    const tbody = document.getElementById("tablaBody");
    tbody.innerHTML = "";

    data.forEach((row, idx) => {
      const activo = !row.Inactivo || row.Inactivo == 0;
      const tr = document.createElement("tr");
      tr.innerHTML = `
        <td>${idx + 1}</td>
        <td>${row.Id ?? ""}</td>
        <td>${row.NombreDocSoporte ?? ""}</td>
        <td>${row.Prefijo ?? ""}</td>
        <td>${row.NombreRptService ?? ""}</td>
        <td>${row.Descripcion ?? ""}</td>
        <td><span class="badge ${activo ? "badge-success" : "badge-danger"}">${activo ? "Activo" : "Inactivo"}</span></td>`;

      tr.style.cursor = "pointer";
      tr.addEventListener("click", () => abrirModalTerceros(row));
      tbody.appendChild(tr);
    });

    document.getElementById("resultadosTabla").style.display = "table";
  }


  // ===================== MODAL CONFIRM =====================

/**
 * Muestra un modal de confirmación reutilizable.
 * 
 * @param {string} message Mensaje mostrado al usuario.
 * @returns {Promise<boolean>} Retorna true si confirma o false si cancela.
 */
  function showModalConfirm(message) {
    return new Promise((resolve) => {
      const modal  = document.getElementById("modalConfirm");
      const msg    = document.getElementById("modalConfirmMsg");
      const btnOk  = document.getElementById("btnConfirmOk");
      const btnCan = document.getElementById("btnConfirmCancel");

      msg.textContent = message;
      modal.style.display = "flex";

      function close(result) {
        modal.style.display = "none";
        btnOk.removeEventListener("click", okHandler);
        btnCan.removeEventListener("click", cancelHandler);
        resolve(result);
      }

      function okHandler()     { close(true);  }
      function cancelHandler() { close(false); }

      btnOk.addEventListener("click", okHandler);
      btnCan.addEventListener("click", cancelHandler);
    });
  }

  // ===================== MODAL TERCEROS =====================

/**
 * Cierra el modal de terceros asociados.
 */
  function cerrarModalTerceros() {
    document.getElementById("modalTerceros").style.display = "none";
  }


/**
 * Abre el modal de terceros asociados al documento soporte seleccionado.
 * También carga dinámicamente la información desde el backend.
 * 
 * @param {Object} row Registro seleccionado de la tabla principal.
 * @returns {Promise<void>}
 */

  let _rowPadreActual = null;

  async function abrirModalTerceros(row) {
    _rowPadreActual = row;
    
    const modal    = document.getElementById("modalTerceros");
    const loading  = document.getElementById("modalTercerosLoading");
    const empty    = document.getElementById("modalTercerosEmpty");
    const tabla    = document.getElementById("modalTercerosTabla");
    const tbody    = document.getElementById("modalTercerosTablaBody");
    const thead    = document.getElementById("modalTercerosHead");
    const subtitle = document.getElementById("modalTercerosSubtitle");

    loading.style.display = "block";
    empty.style.display = "none";
    tabla.style.display = "none";
    tbody.innerHTML = "";
    thead.innerHTML = "";
    subtitle.textContent = `Soporte: ${row.NombreDocSoporte ?? row.Id}`;

    modal.style.display = "flex";

    const btnAbrirInsertar = document.getElementById("btnAbrirInsertar");

    btnAbrirInsertar.onclick = () => {
    abrirModalInsertar(
        row.Id,
        row.NombreDocSoporte ?? row.Id
    );
    };

    try {
      const resp = await fetch(`https://${host}:9876/abreviatura/doc-soporte-tercero?idDocSoporte=${row.Id}`);
      if (!resp.ok) throw new Error(await resp.text());

      const data = await resp.json();

      _idTerceroKeyInsertar = data[0]?.IdTerceroKey ?? null;
      loading.style.display = "none";

      if (!Array.isArray(data) || data.length === 0) {
        empty.style.display = "block";
        return;
      }

      // Cabeceras dinámicas + columna acciones
      const keys = Object.keys(data[0]);
      keys.forEach(k => {
        const th = document.createElement("th");
        th.textContent = k;
        thead.appendChild(th);
      });
      const thAccion = document.createElement("th");
      thAccion.textContent = "Acciones";
      thead.appendChild(thAccion);

      // Filas dinámicas
      data.forEach(item => {
        const tr = document.createElement("tr");
        keys.forEach(k => {
          const td = document.createElement("td");
          td.textContent = item[k] ?? "";
          tr.appendChild(td);
        });


        // Botón Editar
        const btnEdit = document.createElement("button");
        btnEdit.className = "button button-secondary";
        btnEdit.style.cssText = "padding:5px 12px; font-size:12px; margin-left:6px;";
        btnEdit.innerHTML = '<i class="fa-solid fa-pen"></i> Editar';
        btnEdit.addEventListener("click", (e) => {
          e.stopPropagation();
          abrirModalEditar(item);
        });

        // Botón Eliminar
        const btnElim = document.createElement("button");
        btnElim.className = "button button-danger";
        btnElim.style.cssText = "padding:5px 12px; font-size:12px; margin-left:6px;";
        btnElim.innerHTML = '<i class="fa-solid fa-trash"></i>';
        btnElim.addEventListener("click", async (e) => {
          e.stopPropagation();
          const confirmar = await showModalConfirm("¿Seguro que deseas eliminar este registro?");
          if (!confirmar) return;

          try {
            const resp = await fetch(`https://${host}:9876/abreviatura/doc-soporte-tercero/${item.Id}`, {
              method: "DELETE"
            });
            if (!resp.ok) throw new Error(await resp.text());
            showToast("Éxito", "Registro eliminado correctamente", "success", 4000);
            await abrirModalTerceros(_rowPadreActual);
          } catch (err) {
            showToast("Error", err.message, "error", 5000);
          }
        });

        const tdBtn = document.createElement("td");

        tdBtn.appendChild(btnEdit);
        tdBtn.appendChild(btnElim);
        tr.appendChild(tdBtn);

        tbody.appendChild(tr);
      });

      tabla.style.display = "table";

    } catch (err) {
      loading.style.display = "none";
      empty.style.display = "block";
      showToast("Error", err.message, "error", 5000);
    }
  }

  // ===================== MODAL INSERTAR =====================

// Variables globales utilizadas durante la inserción.
  let _idDocSoporteActual = null;
  let _idTerceroKeyActual = null;

/**
 * Cierra el modal de inserción de terceros.
 */
  function cerrarModalInsertar() {
    document.getElementById("modalInsertar").style.display = "none";
  }

/**
 * Abre el modal de inserción y carga dinámicamente los terceros disponibles.
 * 
 * @param {number} idDocSoporte Id del documento soporte.
 * @param {string} nombreSoporte Nombre del documento soporte.
 * @returns {Promise<void>}
 */
    async function abrirModalInsertar(idDocSoporte, nombreSoporte) {
    _idDocSoporteActual = idDocSoporte;

    document.getElementById("modalInsertarSubtitle").textContent =
        `Soporte: ${nombreSoporte}`;

    document.getElementById("ins_prefijo").value = "";
    document.getElementById("modalInsertarError").textContent = "";

    const select = document.getElementById("ins_tercero");

    select.innerHTML =
        `<option value="">Cargando terceros...</option>`;

    try {
        const resp = await fetch(
        `https://${host}:9876/abreviatura/terceros`
        );

        if (!resp.ok) {
        throw new Error(await resp.text());
        }

        const terceros = await resp.json();

        select.innerHTML =
        `<option value="">Seleccione un tercero</option>`;

        terceros.forEach(t => {
        const option = document.createElement("option");

        option.value = t.idTerceroKey;

        option.textContent =
            `${t.nomTercero}`;

        select.appendChild(option);
        });

    } catch (err) {
        select.innerHTML =
        `<option value="">Error cargando terceros</option>`;

        showToast("Error", err.message, "error", 5000);
    }

    document.getElementById("modalInsertar").style.display = "flex";
    }


/**
 * Inserta una nueva relación entre documento soporte y tercero.
 * 
 * @returns {Promise<void>}
 */
  async function insertar() {
    const prefijo = document.getElementById("ins_prefijo").value.trim();

    if (!prefijo) {
      document.getElementById("modalInsertarError").textContent = "El prefijo es requerido";
      return;
    }

    const btn = document.getElementById("btnInsertar");
    btn.disabled = true;
    btn.textContent = "Insertando...";

    try {
      const params = new URLSearchParams();
      params.set("idDocSoporte", _idDocSoporteActual);
      const idTerceroKey =
        document.getElementById("ins_tercero").value;

        if (!idTerceroKey) {
        document.getElementById("modalInsertarError").textContent =
            "Debes seleccionar un tercero";
        return;
        }

      params.set("idTerceroKey", idTerceroKey);
      params.set("prefijo", prefijo);

      const resp = await fetch(`https://${host}:9876/abreviatura/doc-soporte-tercero?${params.toString()}`, {
        method: "POST"
      });

      if (!resp.ok) throw new Error(await resp.text());

      cerrarModalInsertar();
      showToast("Éxito", "Tercero insertado correctamente", "success", 4000);
      await abrirModalTerceros(_rowPadreActual);

    } catch (err) {
      document.getElementById("modalInsertarError").textContent = err.message;
    } finally {
      btn.disabled = false;
      btn.textContent = "Insertar";
    }
  }

  // ===================== MODAL EDITAR =====================


// Variable global utilizada para almacenar el item en edición.
  let _itemEditarActual = null;

/**
 * Cierra el modal de edición.
 */
  function cerrarModalEditar() {
    document.getElementById("modalEditar").style.display = "none";
  }

/**
 * Abre el modal de edición cargando los datos del registro seleccionado.
 * 
 * @param {Object} item Registro a editar.
 */
  function abrirModalEditar(item) {
    _itemEditarActual = item;

    document.getElementById("edit_prefijo").value       = item.Prefijo ?? "";
    document.getElementById("edit_obligatorio").checked = item.Obligatorio == 1;
    document.getElementById("edit_inactivo").checked    = item.Inactivo == 1;
    document.getElementById("modalEditarError").textContent = "";
    document.getElementById("modalEditar").style.display = "flex";
  }


/**
 * Actualiza la información de un tercero asociado al documento soporte.
 * 
 * @returns {Promise<void>}
 */
  async function actualizar() {
    const prefijo     = document.getElementById("edit_prefijo").value.trim();
    const obligatorio = document.getElementById("edit_obligatorio").checked ? 1 : 0;
    const inactivo    = document.getElementById("edit_inactivo").checked ? 1 : 0;

    if (!prefijo) {
      document.getElementById("modalEditarError").textContent = "El prefijo es requerido";
      return;
    }

    const btn = document.getElementById("btnActualizar");
    btn.disabled = true;
    btn.textContent = "Guardando...";

    try {
      const params = new URLSearchParams();
      params.set("id",           _itemEditarActual.Id);
      params.set("prefijo",      prefijo);
      params.set("IdTerceroKey", _itemEditarActual.IdTerceroKey);
      params.set("obligatorio",  obligatorio);
      params.set("inactivo",     inactivo);

      const resp = await fetch(`https://${host}:9876/abreviatura/doc-soporte-tercero?${params.toString()}`, {
        method: "PUT"
      });

      if (!resp.ok) throw new Error(await resp.text());

      cerrarModalEditar();
      showToast("Éxito", "Registro actualizado correctamente", "success", 4000);
      await abrirModalTerceros(_rowPadreActual);

    } catch (err) {
      document.getElementById("modalEditarError").textContent = err.message;
    } finally {
      btn.disabled = false;
      btn.textContent = "Guardar";
    }
  }

/**
 * Evento click del botón limpiar.
 * Reinicia filtros, tabla y mensajes visuales.
 */
  document.getElementById("btnLimpiar").addEventListener("click", () => {
    document.getElementById("nombre").value = "";
    document.getElementById("inactivo").checked = false;
    document.getElementById("errorMsg").textContent = "";
    document.getElementById("emptyState").style.display = "none";
    document.getElementById("resultadosTabla").style.display = "none";
    document.getElementById("tablaBody").innerHTML = "";
    document.getElementById("cardTitle").textContent = "Resultados";
  });

/**
 * Evento ejecutado al cargar completamente la página.
 * Dispara automáticamente la búsqueda inicial.
 */
  window.addEventListener("DOMContentLoaded", buscar);

