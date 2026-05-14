const STEPS = [
  { id: 'peso_talla',      title: 'Generar Peso y Talla',           campo: 'G2' },
  { id: 'agudeza_visual',  title: 'Generar Agudeza Visual',         campo: 'G3' },
  { id: 'var_embarazada',  title: 'Generar Variables Embarazada',   campo: 'G4' },
  { id: 'valoracion_int',  title: 'Generar Valoración Integral',    campo: 'G5' },
  { id: 'anticonceptivos', title: 'Generar Anticonceptivos',        campo: 'G6' },
  { id: 'laboratorios',    title: 'Generar Laboratorios',           campo: 'G7' },
  { id: 'comodines',       title: 'Generar Comodines',              campo: 'G8' },
];

const EPS_LIST = [
  'Sura EPS', 'Sanitas EPS', 'Compensar EPS', 'Nueva EPS',
  'Coosalud EPS', 'Famisanar', 'Salud Total EPS', 'Aliansalud EPS',
  'Medimás EPS', 'Emssanar', 'Asmet Salud',
];

let currentStep = 0;
let completedSteps = new Set();
let generatedSteps = new Set();
let currentIdRep = null; // IdRep global


/** @type {FileSystemDirectoryHandle} */
let selectedFolderHandle = null;
let selectedFolderPath = null;

document.addEventListener('DOMContentLoaded', () => {
    const fechaDesdeInput = document.getElementById('fecha-desde');
    const today = new Date().toISOString().split('T')[0];
    fechaDesdeInput.value = today;

    const fechaHastaInput = document.getElementById('fecha-hasta');
    fechaHastaInput.value = today;
});

/* ─── INIT ─── */
buildUI();
cargarTerceros();

/* ─── BUILD DOM ─── */
function buildUI() {
  buildProgress();
  buildSteps();
}

function buildProgress() {
  const track = document.getElementById('progressTrack');
  STEPS.forEach((s, i) => {
    const item = document.createElement('div');
    item.className = 'progress-item';
    item.innerHTML = `
      <div class="step-dot" id="dot-${i}">${i + 1}</div>
      ${i < STEPS.length - 1 ? `<div class="step-line" id="line-${i}"></div>` : ''}
    `;
    track.appendChild(item);
  });
  updateProgress();
}

function buildSteps() {
  const container = document.getElementById('stepsContainer');
  
  STEPS.forEach((s, i) => {
    const card = document.createElement('div');
    card.className = 'step-card' + (i === 0 ? ' active' : '');
    card.id = `card-${i}`;

    card.innerHTML = `
      <div class="completed-overlay"></div>
      <div class="card-header">
        <div>
          <div class="step-number">PASO ${i + 1} DE ${STEPS.length}</div>
          <div class="step-title">${s.title}</div>
        </div>
        <div class="status-badge pending" id="badge-${i}">
          <div class="pulse"></div>
          <span id="badge-text-${i}">Pendiente</span>
        </div>
      </div>

      <div class="gen-progress" id="prog-${i}">
        <div class="gen-progress-label" id="prog-label-${i}">Consultando procedimiento almacenado...</div>
        <div class="gen-progress-bar"><div class="gen-progress-fill" id="prog-fill-${i}"></div></div>
      </div>
      <div class="card-actions">
        <button class="btn btn-primary" id="gen-btn-${i}" onclick="handleGenerate(${i})">
           Generar
        </button>
        <button class="btn btn-next" id="next-btn-${i}" onclick="nextStep(${i})" style="display:none;">
          ${i < STEPS.length - 1 ? 'Siguiente →' : 'Finalizar →'}
        </button>
      </div>
    `;
    container.appendChild(card);
  });

  document.getElementById("gen-btn-0").disabled = true;
  
}

async function cargarTerceros() {
    try {
        const response = await fetch('/filtros/terceros');
        const text = await response.text();

        if (!response.ok) {
            showToast("Error", text, "error");
            return;
        }

        const data = JSON.parse(text);

        const select = document.getElementById('eps-select');

        data.forEach(tercero => {
            const option = document.createElement('option');
            option.value = tercero.idTerceroKey;
            option.textContent = tercero.nomTercero;
            select.appendChild(option);
        });
    } catch (error) {
        showToast("Error", "Error al cargar terceros: " + error, 'error'); 
    }
}




function checkFiltros() {
  const eps = document.getElementById("eps-select")?.value;
  const desde = document.getElementById("fecha-desde")?.value;
  const hasta = document.getElementById("fecha-hasta")?.value;
  document.getElementById("consultar-btn").disabled = !(eps && desde && hasta);
}

async function consultarIdRep() {
  const eps = document.getElementById("eps-select").value;
  const desde = document.getElementById("fecha-desde").value;
  const hasta = document.getElementById("fecha-hasta").value;

  const btn = document.getElementById("consultar-btn");
  btn.disabled = true;
  btn.innerHTML = "Consultando...";

  completedSteps.clear();
  generatedSteps.clear();
  currentStep = 0;

  STEPS.forEach((_, i) => {
    const card = document.getElementById(`card-${i}`);
    const badge = document.getElementById(`badge-${i}`);
    const badgeText = document.getElementById(`badge-text-${i}`);
    const nextBtn = document.getElementById(`next-btn-${i}`);
    const genBtn = document.getElementById(`gen-btn-${i}`);
    const dot = document.getElementById(`dot-${i}`); // ← agrega
    const line = document.getElementById(`line-${i}`);

    card.className = "step-card";
    badge.className = "status-badge pending";
    badgeText.textContent = "Pendiente";
    nextBtn.style.display = "none";
    genBtn.disabled = false;

    if (dot) {
      dot.className = "step-dot";
      dot.textContent = i + 1;
    }
    if (line) line.className = "step-line";
  });

  try {
    const response = await fetch(
      `/r202/consultar?FechaIni=${desde}&FechaFin=${hasta}&IdTerceroKey=${eps}`,
    );
    const data = await response.text();

    if (!response.ok) {
      showToast(data, "error");
      btn.disabled = false;
      btn.innerHTML = "Consultar";
      return;
    }

    console.log("Paso 1 Generado Exitosamente");

    const json = JSON.parse(data);
    currentIdRep = json.idRep;

    document.getElementById("idRep-display").textContent =
      `IdRep: ${currentIdRep}`;

    await cargarEstado();

    // Habilitar paso 1
    document.getElementById("gen-btn-0").disabled = false;
    document.getElementById("eliminar-btn").disabled = false;

    showToast(`✅ IdRep obtenido: ${currentIdRep}`, "success");
  } catch (error) {
    showToast("Error al consultar: " + error, "error");
  } finally {
    btn.disabled = false;
    btn.innerHTML = 'Consultar';
  }
}

async function eliminarIdRep() {
  if (!currentIdRep) {
    showToast("No hay IdRep activo para eliminar", "error");
    return;
  }

  const confirmado = confirm("Deseas eliminar este R202?");

  if (!confirmado){
    return
  }

  try {
    const response = await fetch(`/r202/eliminar?idRep=${currentIdRep}`);
    const data = await response.text();

    console.log("IdRep eliminado exitosamente", data);

    if (!response.ok) {
      showToast(data, "error");
      return;
    }

    currentIdRep = null;
    document.getElementById("idRep-display").textContent = "";

    completedSteps.clear();
    generatedSteps.clear();
    currentStep = 0;

    STEPS.forEach((_, i) => {
      const card = document.getElementById(`card-${i}`);
      const badge = document.getElementById(`badge-${i}`);
      const badgeText = document.getElementById(`badge-text-${i}`);
      const nextBtn = document.getElementById(`next-btn-${i}`);
      const genBtn = document.getElementById(`gen-btn-${i}`);
      const dot = document.getElementById(`dot-${i}`);
      const line = document.getElementById(`line-${i}`);

      card.className = "step-card";
      badge.className = "status-badge pending";
      badgeText.textContent = "Pendiente";
      nextBtn.style.display = "none";
      genBtn.disabled = false;

      if (dot) {
        dot.className = "step-dot";
        dot.textContent = i + 1;
      }
      if (line) line.className = "step-line";
    });

    document.getElementById("card-0").classList.add("active");
    document.getElementById("gen-btn-0").disabled = true;
    document.getElementById("exportCard").classList.remove("visible");
    updateProgress();

    showToast("✅ Proceso eliminado correctamente", "success");
  } catch (error) {
    showToast("Error al eliminar: " + error, "error");
  }
}


async function cargarEstado() {
  try {
    const response = await fetch(
      `/r202/consultarProgreso?idRep=${currentIdRep}`,
    );
    const data = await response.json();

    if (!response.ok) {
      showToast("Error al consultar estado: " + data, "error");
    }

    STEPS.forEach((step, i) => {
      if (Number(data[step.campo]) === 1) {
        generatedSteps.add(i);
        completedSteps.add(i);

        const badge = document.getElementById(`badge-${i}`);
        const badgeText = document.getElementById(`badge-text-${i}`);
        const card = document.getElementById(`card-${i}`);
        card.classList.remove("active");
        card.classList.add("completed");

        badge.className = "status-badge done-badge";
        badgeText.textContent = "✓ Listo";
      }
    });

    const next = STEPS.findIndex((s) => Number(data[s.campo]) === 0);

    currentStep = next === -1 ? STEPS.length : next;

    if (currentStep < STEPS.length) {
      document.getElementById(`card-${currentStep}`).classList.add("active");
    }

    if (currentStep >= STEPS.length) {
      showExportCard();
    }

    updateProgress();
  } catch (error) {
    showToast("Error al cargar estado: " + error, "error");
  }
}






/* ─── GENERATE ─── */
async function handleGenerate(i) {
  if (!currentIdRep) {
    showToast('Primero debes consultar los filtros', 'error');
    return;
  }

  const genBtn = document.getElementById(`gen-btn-${i}`);
  const prog = document.getElementById(`prog-${i}`);
  const badge = document.getElementById(`badge-${i}`);
  const badgeText = document.getElementById(`badge-text-${i}`);

  genBtn.disabled = true;
  prog.classList.add('visible');
  badge.className = 'status-badge running';
  badgeText.textContent = 'Ejecutando';

  try {

    const response2 = await fetch(`/r202/ejecutarPasos?idRep=${currentIdRep}&campo=${STEPS[i].campo}`);
    const data2 = await response2.text();


    if (!response2.ok){
      showToast(data2, 'error');
      console.log(data2);
      genBtn.disabled = false;
      prog.classList.remove('visible');
      badge.className = 'status-badge pending';
      badgeText.textContent = 'Pendiente';
      return;
    }

    console.log(data2);

    const response = await fetch(`/r202/generarPaso?idRep=${currentIdRep}&campo=${STEPS[i].campo}`);
    const data = await response.text();

    if (!response.ok) {
      showToast(data, 'error');
      genBtn.disabled = false;
      prog.classList.remove('visible');
      badge.className = 'status-badge pending';
      badgeText.textContent = 'Pendiente';
      return;
    }

    console.log(data);

    prog.classList.remove('visible');
    badge.className = 'status-badge done-badge';
    badgeText.textContent = '✓ Listo';
    generatedSteps.add(i);
    document.getElementById(`next-btn-${i}`).style.display = 'inline-flex';
    showToast(`✅ ${STEPS[i].title} generado correctamente`, 'success');

  } catch (error) {
    showToast('Error: ' + error, 'error');
    genBtn.disabled = false;
    prog.classList.remove('visible');
    badge.className = 'status-badge pending';
    badgeText.textContent = 'Pendiente';
  }
}

/* ─── NEXT STEP ─── */
function nextStep(i) {
  completedSteps.add(i);

  const card = document.getElementById(`card-${i}`);
  card.classList.remove('active');
  card.classList.add('completed');

  currentStep = i + 1;

  if (currentStep < STEPS.length) {
    const nextCard = document.getElementById(`card-${currentStep}`);
    nextCard.classList.add('active');
    nextCard.scrollIntoView({ behavior: 'smooth', block: 'end' });

    if (currentStep === 0) {
      document.getElementById(`gen-btn-0`).disabled = true;
    }
  } else {
    showExportCard();
  }

  updateProgress();
}

function updateProgress() {
  STEPS.forEach((_, i) => {
    const dot = document.getElementById(`dot-${i}`);
    const line = document.getElementById(`line-${i}`);
    if (!dot) return;

    dot.className = 'step-dot';
    dot.textContent = i + 1;

    if (completedSteps.has(i)) {
      dot.className = 'step-dot done';
      dot.textContent = '';
      if (line) line.className = 'step-line done';
    } else if (i === currentStep) {
      dot.className = 'step-dot active';
    }
  });

  const activeDot = document.getElementById(`dot-${currentStep}`);
  if (activeDot) activeDot.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'center' });
}



/* ─── EXPORT CARD ─── */
function showExportCard() {
  const exportCard = document.getElementById('exportCard');
  exportCard.classList.add('visible');
  exportCard.scrollIntoView({ behavior: 'smooth', block: 'start' });

  const row = document.getElementById('summaryRow');
  row.innerHTML = '';
  STEPS.forEach(s => {
    const chip = document.createElement('div');
    chip.className = 'summary-chip';
    chip.innerHTML = `<span>✓</span> ${s.title}`;
    row.appendChild(chip);
  });
}

/* ─── FOLDER PICKER ─── */
async function pickFolder() {
  if ('showDirectoryPicker' in window) {
    try {
      selectedFolderHandle = await window.showDirectoryPicker();
      selectedFolderPath = selectedFolderHandle.name;

      const pathEl = document.getElementById('folderPath');
      pathEl.textContent = `📂 .\\${selectedFolderHandle.name}\\`;
      pathEl.classList.add('visible');
      document.getElementById('exportBtn').disabled = false;
      showToast(`📁 Carpeta seleccionada: ${selectedFolderHandle.name}`, 'success');
    } catch (e) {
      if (e.name !== 'AbortError') showToast('❌ No se pudo abrir el selector', 'error');
    }
  } else {
    document.getElementById('folderInput').click();
  }
}

function handleFolderSelect(input) {
  if (input.files.length > 0) {
    const path = input.files[0].webkitRelativePath.split('/')[0];
    selectedFolderPath = path;
    const pathEl = document.getElementById('folderPath');
    pathEl.textContent = `📂 .\\${path}\\`;
    pathEl.classList.add('visible');
    document.getElementById('exportBtn').disabled = false;
    showToast(`📁 Carpeta seleccionada: ${path}`, 'success');
  }
}

/* ─── EXPORT TXT ─── */
async function exportTXT() {
  console.log("exportando TXT");

  const btn = document.getElementById('exportBtn');

  if (!selectedFolderPath) {
  showToast('⚠️ Primero debes seleccionar una carpeta de destino', 'warning');
  return;
  }

  btn.disabled = true;
  btn.innerHTML = 'Generando...';

  try {
    const response = await fetch(`/r202/generar202?consultaNum=${currentIdRep}&tipo=...&tipoId=...&tipoReg=...&consecArch=...`);

    if (!response.ok) {
      showToast('Error al generar reporte', 'error');
      return;
    }

    const blob = await response.blob();
    const header = response.headers.get("Content-Disposition");
    const match = header?.match(/filename="(.+)"/);
    const filename = match ? match[1] : "archivo.txt";


    const fileHandle = await selectedFolderHandle.getFileHandle(filename, {create: true});

    const writable = await fileHandle.createWritable();
    await writable.write(await blob.arrayBuffer());
    await writable.close();

    showToast('✅ Reporte guardado exitosamente en carpeta seleccionada', 'success');

  } catch (error) {
    showToast('Error: ' + error, 'error');
  } finally {
    btn.disabled = false;
    btn.innerHTML = 'Exportar TXT';
  }
}



/* ─── TOAST ─── */
function showToast(msg, type = 'success') {
  const toast = document.getElementById('toast');
  const toastMsg = document.getElementById('toastMsg');
  toast.className = `toast ${type}`;
  toastMsg.textContent = msg;
  toast.classList.add('show');
  setTimeout(() => toast.classList.remove('show'), 3200);
}

