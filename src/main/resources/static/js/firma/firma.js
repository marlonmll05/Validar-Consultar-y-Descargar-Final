const host = window.location.hostname;

let _idAtencionActual    = null;
let _idAdmisionActual    = null;
let _idPacienteKeyActual = null;

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
}




async function guardarFirma() {
  await BasicDemoDone();

  // Obtener datos Topaz
  const raw = document.MainForm.sigRawData.value
    .replace("Base64String:", "")
    .trim();
  const sigString = document.MainForm.sigStringData.value
    .replace("SigString:", "")
    .trim();

  console.log("RAW:", raw.substring(0, 50));
  console.log("SIGSTRING:", sigString.substring(0, 50));

  if (!raw) {
    document.getElementById("estado").textContent = "⚠ No hay firma capturada";
    document.getElementById("estado").className = "status error";
    return;
  }

  // Base64 limpio
  let imagenBase64 = raw.replace(/^data:image\/\w+;base64,/, "").trim();

  // Detectar MIME type
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

  console.log("MIME:", mimeType);

  // Preview
  const imgEl = document.getElementById("firmaImg");
  if (!imgEl) {
    console.error("No existe #firmaImg");
    return;
  }

  try {
    // Validar Base64
    try {
      atob(imagenBase64);
    } catch (e) {
      throw new Error("Base64 inválido");
    }

    // Crear data URL y mostrar imagen
    const imageUrl = `data:${mimeType};base64,${imagenBase64}`;
    console.log("URL:", imageUrl.substring(0, 80));

    imgEl.onload = () => {
      console.log("✔ Imagen cargada correctamente");
      document.getElementById("estado").textContent =
        "✔ Firma capturada correctamente";
      document.getElementById("estado").className = "status";
    };

    imgEl.onerror = () => {
      console.log("❌ Error cargando imagen");
      document.getElementById("estado").textContent =
        "⚠ Error mostrando la firma";
      document.getElementById("estado").className = "status error";
    };

    imgEl.src = imageUrl;
    imgEl.style.display = "block";

    const placeholder = document.getElementById("firmaPlaceholder");
    if (placeholder) placeholder.style.display = "none";
  } catch (e) {
    console.error(e);
    document.getElementById("estado").textContent = "⚠ Error procesando firma";
    document.getElementById("estado").className = "status error";
  }

  // Botón loading
  const btn = document.getElementById("btnGuardarFirma");
  const originalHTML = btn.innerHTML;
  btn.disabled = true;
  btn.innerHTML = "Guardando...";

  try {
    const body = {
      IdAtencion: _idAtencionActual,
      IdAdmision: _idAdmisionActual,
      IdPacienteKey: _idPacienteKeyActual,
      FirmaPad: raw,
      Encryp_Pad: sigString,
    };

    const resp = await fetch(`https://${host}:9876/firma/insertar-firma`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });

    if (!resp.ok) throw new Error(await resp.text());

    const result = await resp.json();

    if (result.success) {
      document.getElementById("estado").textContent =
        "✔ Firma guardada correctamente";
      document.getElementById("estado").className = "status";
    } else {
      throw new Error(result.message);
    }
  } catch (err) {
    document.getElementById("estado").textContent =
      "⚠ Error al guardar: " + err.message;
    document.getElementById("estado").className = "status error";
  } finally {
    btn.disabled = false;
    btn.innerHTML = originalHTML;
  }
}


async function consultarEInsertarPdf(idPdfKey, datosInsertar) {
  // 1. Consultar el PDF del backend
  const respConsulta = await fetch(`/pdf/consultar-pdf/${idPdfKey}`);

  if (!respConsulta.ok) throw new Error(await respConsulta.text());

  // 2. Convertir bytes a Base64
  const blob = await respConsulta.blob();
  const base64 = await new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result.split(",")[1]);
    reader.onerror = reject;
    reader.readAsDataURL(blob);
  });

  // 3. Insertar el PDF
  const respInsertar = await fetch("/pdf/insertar-pdf", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      ...datosInsertar,
      pdf: base64,
    }),
  });

  if (!respInsertar.ok) throw new Error(await respInsertar.text());

  const result = await respInsertar.json();

  if (!result.success) throw new Error(result.message);

  return result.idPdfKey;
}
