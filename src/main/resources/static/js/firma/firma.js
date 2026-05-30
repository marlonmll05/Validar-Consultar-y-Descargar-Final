const host = window.location.hostname;

let _idAtencionActual    = null;
let _idAdmisionActual    = null;
let _idPacienteKeyActual = null;
let _firmaIniciadaCorrectamente = false;

document.addEventListener("DOMContentLoaded", () => {
    poblarSelect();
});

async function consultarPaciente() {
    const tipoDoc        = document.getElementById("tipoDoc").value.trim();
    const identificacion = document.getElementById("identificacion").value.trim();
    const idAtencion     = document.getElementById("idAtencion").value.trim();
    const errorEl        = document.getElementById("errorConsulta");

    errorEl.textContent = "";

    if (!tipoDoc || !identificacion || !idAtencion) {
        errorEl.textContent = "Todos los campos son requeridos";
        return;
    }

    const btn = document.getElementById("btnConsultar");
    const originalHTML = btn.innerHTML;
    btn.disabled = true;
    btn.innerHTML = "Consultando...";
    btn.style.opacity = "0.6";

    document.getElementById("firmaSection").classList.add("firma-disabled");
    document.getElementById("resultadoBox").style.display = "none";
    document.getElementById("resultadoBody").innerHTML = "";
    document.getElementById("resultadoHead").innerHTML = "";
    _idAtencionActual    = null;
    _idAdmisionActual    = null;
    _idPacienteKeyActual = null;
    _firmaIniciadaCorrectamente = false;  
    deshabilitarBotonesGuardado();      

    try {
        const params = new URLSearchParams();
        params.set("TipoDoc", tipoDoc);
        params.set("Identificacion", identificacion);
        params.set("IdAtencion", idAtencion);

        const resp = await fetch(`https://${host}:9876/firma/consultar-firma?${params.toString()}`);
        if (!resp.ok) throw new Error(await resp.text());

        const data = await resp.json();

        if (!Array.isArray(data) || data.length === 0) {
            errorEl.textContent = "No se encontraron resultados para los datos ingresados";
            return;
        }

        const row = data[0];
        _idAtencionActual    = row.IdAtencion    ?? row.idAtencion    ?? null;
        _idAdmisionActual    = row.IdAdmision    ?? row.idAdmision    ?? null;
        _idPacienteKeyActual = row.IdPacienteKey ?? row.idPacienteKey ?? null;

        const keys = Object.keys(row);
        keys.forEach(k => {
            const th = document.createElement("th");
            th.textContent = k;
            th.title = k;
            document.getElementById("resultadoHead").appendChild(th);
        });

        data.forEach(item => {
            const tr = document.createElement("tr");
            keys.forEach(k => {
                const td = document.createElement("td");
                td.textContent = item[k] ?? "";
                td.title       = item[k] ?? "";
                tr.appendChild(td);
            });
            document.getElementById("resultadoBody").appendChild(tr);
        });

        document.getElementById("resultadoBox").style.display = "block";
        document.getElementById("firmaSection").classList.remove("firma-disabled");
        document.getElementById("estado").textContent = "Tablet lista para firmar";

    } catch (err) {
        errorEl.textContent = err.message;
        document.getElementById("firmaSection").classList.add("firma-disabled");
    } finally {
        btn.disabled = false;
        btn.innerHTML = originalHTML;
        btn.style.opacity = "1";
    }
}

function limpiarFormulario() {
    document.getElementById("tipoDoc").value = "";
    document.getElementById("identificacion").value = "";
    document.getElementById("idAtencion").value = "";
    document.getElementById("errorConsulta").textContent = "";
    document.getElementById("resultadoBox").style.display = "none";
    document.getElementById("resultadoBody").innerHTML = "";
    document.getElementById("resultadoHead").innerHTML = "";
    document.getElementById("firmaSection").classList.add("firma-disabled");
    document.getElementById("firmaImg").src = "";
    document.getElementById("estado").textContent = "Tablet lista para firmar";
    document.getElementById("estado").className = "status";
    _idAtencionActual    = null;
    _idAdmisionActual    = null;
    _idPacienteKeyActual = null;
    _firmaIniciadaCorrectamente = false;  
    
    deshabilitarBotonesGuardado();  // ← Desactiva los botones
}


async function iniciarFirma() {
    const btn = document.getElementById("btnIniciarFirma");
    btn.disabled = true;
    btn.innerText = "Conectando...";

    const estado = document.getElementById("estado");
    _firmaIniciadaCorrectamente = false;

    try {
        estado.textContent = "Preparando tablet...";
        estado.className = "status";

        await Promise.race([
            BasicDemoStartSignEx(),
            new Promise((_, reject) => 
                setTimeout(() => reject(new Error("Timeout: La tablet tardó demasiado en conectar")), 5000)
            )
        ]);
        
        await new Promise(resolve => setTimeout(resolve, 500));

        estado.textContent = "✔ Tablet lista para firmar";
        estado.className = "status";
        
        _firmaIniciadaCorrectamente = true;
        habilitarBotonesGuardado();

    } catch (err) {
        console.error("Error en iniciarFirma:", err);
        estado.textContent = "⚠ Error: " + err.message;
        estado.className = "status error";
        _firmaIniciadaCorrectamente = false;
        deshabilitarBotonesGuardado();

    } finally {
        btn.disabled = false;
        btn.innerText = "Iniciar Firma";
    }
}

function habilitarBotonesGuardado() {
    document.getElementById("btnGuardarFirma").disabled = false;
    document.getElementById("btnGuardarFirma").classList.remove("btn-disabled");
    document.getElementById("btnLimpiarFirma").disabled = false; 
    document.getElementById("btnLimpiarFirma").classList.remove("btn-disabled");  
}

function deshabilitarBotonesGuardado() {
    document.getElementById("btnGuardarFirma").disabled = true;
    document.getElementById("btnGuardarFirma").classList.add("btn-disabled");
    document.getElementById("btnLimpiarFirma").disabled = true;
    document.getElementById("btnLimpiarFirma").classList.add("btn-disabled");  
}


async function guardarFirma() {
    if (!_firmaIniciadaCorrectamente) {
      alert("Debes iniciar la firma primero");
      return;
  }

  const btn = document.getElementById("btnGuardarFirma");
  const originalHTML = btn.innerHTML;

  btn.innerHTML = "Procesando...";

  try {

    // =========================
    // CAPTURAR FIRMA DESDE TOPAZ
    // =========================

    await BasicDemoDone();

    const raw = document.MainForm.sigRawData.value
      .replace("Base64String:", "")
      .trim();

    const sigString = document.MainForm.sigStringData.value
      .replace("SigString:", "")
      .trim();

    console.log("RAW:", raw.substring(0, 50));
    console.log("SIGSTRING:", sigString.substring(0, 50));

    if (!raw) {
      throw new Error("No hay firma capturada");
    }

    // =========================
    // DETECTAR MIME
    // =========================

    let imagenBase64 = raw.replace(/^data:image\/\w+;base64,/, "").trim();

    let mimeType = "image/jpeg";

    if (imagenBase64.startsWith("iVBOR")) {
      mimeType = "image/png";
    } else if (imagenBase64.startsWith("Qk")) {
      mimeType = "image/bmp";
    } else if (
      imagenBase64.startsWith("/9j/") ||
      imagenBase64.startsWith("AQSkZJRg")
    ) {
      mimeType = "image/jpeg";
    }

    // =========================
    // VALIDAR BASE64
    // =========================

    try {
      atob(imagenBase64);
    } catch (e) {
      throw new Error("Base64 inválido");
    }

    // =========================
    // MOSTRAR IMAGEN
    // =========================

    const imgEl = document.getElementById("firmaImg");

    if (!imgEl) {
      throw new Error("No existe #firmaImg");
    }

    const imageUrl = `data:${mimeType};base64,${imagenBase64}`;

    imgEl.src = imageUrl;
    imgEl.style.display = "block";

    const placeholder = document.getElementById("firmaPlaceholder");

    if (placeholder) {
      placeholder.style.display = "none";
    }

    // =========================
    // 1. GENERAR / INSERTAR PDF
    // =========================

    document.getElementById("estado").textContent = "Generando PDF...";
    document.getElementById("estado").className = "status";

    const resultadoPDF = await timeoutPromise(
        manejarDescargaPDF(_idAdmisionActual, _idPacienteKeyActual),
        30000
    );

    if (resultadoPDF === null) {
        document.getElementById("estado").textContent = "Tablet lista para firmar";
        document.getElementById("estado").className = "status";
        return;
    }

    // =========================
    // 2. INSERTAR FIRMA
    // =========================

    document.getElementById("estado").textContent =
      "Guardando firma...";

    const resp = await fetch(
      `https://${host}:9876/firma/insertar-firma`,
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          IdAtencion:    _idAtencionActual,
          IdAdmision:    _idAdmisionActual,
          IdPacienteKey: _idPacienteKeyActual,
          FirmaPad:      raw,
          Encryp_Pad:    sigString,
        }),
      }
    );

    if (!resp.ok) {
      throw new Error(await resp.text());
    }

    const result = await resp.json();

    if (!result.success) {
      throw new Error(result.message);
    }

    document.getElementById("estado").textContent =
      "✔ Firma y PDF guardados correctamente";

    document.getElementById("estado").className =
      "status";


    await BasicDemoClearSign();
 
    limpiarFormulario();

  } catch (err) {

    console.error(err);

    document.getElementById("estado").textContent =
      "⚠ Error: " + err.message;

    document.getElementById("estado").className =
      "status error";

    btn.disabled = false;
    btn.innerHTML = originalHTML;

  } finally {

    btn.disabled = false;
    btn.innerHTML = originalHTML;
  }
}

// =====================================================
// TIMEOUT PARA EVITAR BLOQUEOS
// =====================================================

function timeoutPromise(promise, ms) {

  return Promise.race([
    promise,
    new Promise((_, reject) =>
      setTimeout(() => reject(new Error("Tiempo agotado")), ms)
    )
  ]);
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

async function manejarDescargaPDF(idAdmision, idPacienteKey) {
    const idSoporteKey = 6;
    const tipoDocumento = 1;

    // 1. Contar registros existentes
    const countResp = await fetch(
        `https://${host}:9876/firma/contar-pdf?idAdmision=${idAdmision}&idSoporteKey=${idSoporteKey}`
    );
    if (!countResp.ok) throw new Error(await countResp.text());
    const { count } = await countResp.json();

    // 2. Si existe, preguntar al usuario
    let anexar = false;
    if (count > 0) {
        const opcion = await showModalGuardar(
            "Ya existe un PDF para esta admisión. ¿Qué deseas hacer?"
        );

         if (opcion === false) return null;
        anexar = opcion === "anexar";
    }

    // 3. Descargar e insertar
    const params = new URLSearchParams({
        idAdmision,
        idPacienteKey,
        idSoporteKey,
        tipoDocumento,
        anexar
    });

    const resp = await fetch(
        `https://${host}:9876/firma/descargar-insertar-pdf?${params}`,
        { method: "POST" }
    );

    if (!resp.ok) throw new Error(await resp.text());
    return await resp.json();
}


async function poblarSelect() {
    const select = document.getElementById("tipoDoc");
    if (!select) return;

    try {
    const url = `https://${host}:9876/firma/obtener-tipos-identificacion`;
    const r = await fetch(url);

    if (!r.ok) throw new Error(await r.text());
    const data = await r.json();
    console.log(data);
    data.forEach(item => {
        const opt = document.createElement("option");
        opt.value = item["IdTipoDocFE"];      
        opt.textContent = item["IdTipoDocFE"] + " - " + item["DescTipoIdenFE"];
        select.appendChild(opt);
    });
    } catch (err) {
    console.error(`Error cargando ${select}:`, err);

    select.querySelectorAll("option:not([value=''])").forEach(opt => opt.remove());
    const errorOpt = document.createElement("option");
    errorOpt.value = "";
    errorOpt.textContent = "⚠ Error al cargar";
    select.appendChild(errorOpt);
    }
}