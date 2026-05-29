function mostrarPanel(panel, btn) {
    document.getElementById('panel-paciente').style.display  = panel === 'paciente'  ? 'flex' : 'none';
    document.getElementById('panel-consulta').style.display  = panel === 'consulta'  ? 'flex' : 'none';
    document.getElementById('panel-urgencias').style.display = panel === 'urgencias' ? 'flex' : 'none';
    document.getElementById('panel-hospitalizacion').style.display = panel === 'hospitalizacion' ? 'flex' : 'none';

    document.querySelectorAll('.panel-tab').forEach(t => t.classList.remove('active'));
    btn.classList.add('active');
}

// ── ACORDEONES ──
function toggleAccordion(id) {
    const acc = document.getElementById(id);
    const isOpen = acc.classList.contains('open');
    
    document.querySelectorAll('.accordion').forEach(a => a.classList.remove('open'));
    
    if (!isOpen) acc.classList.add('open');
}

// ── TOASTS ──
function showToast(title, msg, type = 'info', duration = 4000) {
    const icons = {
        success: '✓',
        error: '✕',
        info: 'i',
        warning: '!'
    };

    const toast = document.createElement('div');
    toast.className = 'toast';
    toast.innerHTML = `
        <div class="toast-icon ${type}">${icons[type]}</div>
        <div class="toast-content">
            <p class="toast-title">${title}</p>
            ${msg ? `<p class="toast-msg">${msg}</p>` : ''}
        </div>
        <button class="toast-close" onclick="removeToast(this.parentElement)">×</button>
    `;

    document.getElementById('toastContainer').appendChild(toast);

    setTimeout(() => removeToast(toast), duration);
}

function removeToast(toast) {
    if (!toast) return;
    toast.classList.add('hiding');
    setTimeout(() => toast.remove(), 300);
}

// ── CONSULTA ──
async function ejecutarConsulta() {
    const tipo = document.getElementById('tipoDocumento').value;
    const num = document.getElementById('numDocumento').value.trim();
    const admision = document.getElementById('numAdmision');
    const rda = document.getElementById('rda');

    if (!tipo || !num) {
        showToast('Campos requeridos', 'Ingresa el tipo y número de documento', 'warning');
        return;
    }

    showToast('Consultando...', `Paciente ${tipo} ${num}`, 'info', 2500);

    try{

        const response = await fetch(`/ihce/lista-rda?tipoId=${tipo}&numId=${num}`);

        const text = await response.text();
        

        if (!response.ok) {
            showToast('Error', text , 'error');
            return;
        }

        const data = JSON.parse(text);

        console.log("Data recibida:", data);

        const item = Array.isArray(data) ? data[0] : data;

        admision.value = item.IdAdmision;
        rda.value = item.IdSesionEntorno;


        await cargarDatosPaciente(item.IdAdmision, item.IdSesionEntorno);

        await cargarAccordion({ bodyId: "body-urg6",  idAdmision: item.IdAdmision, idSesion: item.IdSesionEntorno, tipo: 6 });
        await cargarAccordion({ bodyId: "body-urg7",  idAdmision: item.IdAdmision, idSesion: item.IdSesionEntorno, tipo: 7 });
        await cargarAccordion({ bodyId: "body-urg8",  idAdmision: item.IdAdmision, idSesion: item.IdSesionEntorno, tipo: 8 });
        await cargarAccordion({ bodyId: "body-urg10", idAdmision: item.IdAdmision, idSesion: item.IdSesionEntorno, tipo: 10 });
        await cargarAccordion({ bodyId: "body-urg11", idAdmision: item.IdAdmision, idSesion: item.IdSesionEntorno, tipo: 11 });
        await cargarAccordion({ bodyId: "body-urg12", idAdmision: item.IdAdmision, idSesion: item.IdSesionEntorno, tipo: 12 });


        setTimeout(() => {
            cargarPdf(tipo, num, admision, rda);
        }, 1500);
    } catch (exception){
        console.log("Ocurrio un error: ", exception);
    }


}




function cargarPdf(tipo, num, admision, rda) {
    const badge = document.getElementById('pdfBadge');
    const empty = document.getElementById('pdfEmpty');
    const frame = document.getElementById('pdfFrame');
    const btnDescargar = document.getElementById('btnDescargar');
    const btnNueva = document.getElementById('btnNueva');

    badge.textContent = `${tipo} · ${num}`;
    empty.style.display = 'none';
    frame.style.display = 'block';
    btnDescargar.style.display = 'flex';
    btnNueva.style.display = 'flex';

    // URL real del PDF del backend (fALTA POR AGREGAR)


    showToast('Documento cargado', `Mostrando resultado para ${tipo} ${num}`, 'success');
}

function descargarPdf() {
    showToast('Descargando...', 'El documento se descargará en breve', 'info', 2000);

}

function nuevaConsulta() {
    document.getElementById('tipoDocumento').value = '';
    document.getElementById('numDocumento').value = '';
    document.getElementById('numAdmision').value = '';
    document.getElementById('rda').value = '';

    const badge = document.getElementById('pdfBadge');
    const empty = document.getElementById('pdfEmpty');
    const frame = document.getElementById('pdfFrame');
    const btnDescargar = document.getElementById('btnDescargar');
    const btnNueva = document.getElementById('btnNueva');

    badge.textContent = 'Sin consulta';
    empty.style.display = 'flex';
    frame.style.display = 'none';
    frame.src = '';
    btnDescargar.style.display = 'none';
    btnNueva.style.display = 'none';

    showToast('Listo', 'Puedes realizar una nueva consulta', 'info', 2000);
}

document.querySelectorAll('.search-bar input').forEach(input => {
    input.addEventListener('keydown', e => {
        if (e.key === 'Enter') ejecutarConsulta();
    });
});


async function cargarDatosPaciente(idAdmision, idSesion) {
    try {
        const response = await fetch(`/ihce/rda-consulta?IdAdmision=${idAdmision}&IdSesion=${idSesion}&Tipo=${1}`);
        const text = await response.text();

        if (!response.ok) {
            showToast('Error', text, 'error');
            return;
        }

        const data = JSON.parse(text);
        const p = Array.isArray(data) ? data[0] : data;

        if (!p) {
            showToast('Sin datos', 'No se encontraron datos del paciente', 'warning');
            return;
        }

        // ── Sección 1: Prestador ──
        setField('Cod_Ips', p.Cod_Ips);

        // ── Sección 2: Paciente ──
        setField('Tipo_Identificacion',      p.Tipo_Identificacion);
        setField('Numero_Identificacion',    p.Numero_Identificacion);
        setField('Primer_Nombre',            p.Primer_Nombre);
        setField('Segundo_Nombre',           p.Segundo_Nombre);
        setField('Primer_Apellido',          p.Primer_Apellido);
        setField('Segundo_Apellido',         p.Segundo_Apellido);
        setField('Fec_Nac',                  p.Fec_Nac);
        setField('Cod_Pais_Nacionalidad',    p.Cod_Pais_Nacionalidad);
        setField('Nombre_Pais_Nacionalidad', p.Nombre_Pais_Nacionalidad);
        setField('Sexo_Biologico',           p.Sexo_Biologico);
        setField('Identidad_Genero',         p.Identidad_Genero);
        setField('Pertenencia_Etnica',       p.Pertenencia_Etnica);
        setField('Nombre_Pertenencia_Etnica',p.Nombre_Pertenencia_Etnica);
        setField('Categoria_Discapacidad',   p.Categoria_Discapacidad);
        setField('Cod_Ocupacion',            p.Cod_Ocupacion);
        setField('Nom_Ocupacion',            p.Nom_Ocupacion);
        setField('Cod_Pais_Residencia',      p.Cod_Pais_Residencia);
        setField('Nombre_Pais_Residencia',   p.Nombre_Pais_Residencia);
        setField('Cod_Mpio_Residencia',      p.Cod_Mpio_Residencia);
        setField('Nombre_Mpio_Residencia',   p.Nombre_Mpio_Residencia);
        setField('CodZona',                  p.CodZona);

        // ── Sección 3: Antecedentes salud ──
        setField('Antecedentes_Alergicos',    p.Antecedentes_Alergicos);
        setField('Antecedentes_NombreAlergico',p.Antecedentes_NombreAlergico);
        setField('Antecedente_Familiar',      p.Antecedente_Familiar);
        setField('Antecedente_Parentesco',    p.Antecedente_Parentesco);

        // ── Sección 4: Antecedentes farmacológicos ──
        setField('Cod_Dx_Princ', p.Cod_Dx_Princ);
        setField('Nom_Dx_Princ', p.Nom_Dx_Princ);

        showToast('Datos cargados', 'Información del paciente cargada correctamente', 'success');

    } catch (e) {
        console.error('Error al cargar datos del paciente:', e);
        showToast('Error', 'No se pudo cargar la información del paciente', 'error');
    }
}

async function cargarDatosUrgencias(idAdmision, idSesion) {
    try {
        const response = await fetch(`/ihce/rda-consulta?IdAdmision=${idAdmision}&IdSesion=${idSesion}&Tipo=${2}`);
        const text = await response.text();

        if (!response.ok) {
            showToast('Error', text, 'error');
            return;
        }

        const data = JSON.parse(text);
        const p = Array.isArray(data) ? data[0] : data;

        if (!p) {
            showToast('Sin datos', 'No se encontraron datos de urgencias', 'warning');
            return;
        }

        // ── Sección 1: Prestador ──
        setField('Cod_Ips', p.Cod_Ips);

        // ── Sección 2: Paciente ──
        setField('Tipo_Identificacion',      p.Tipo_Identificacion);
        setField('Numero_Identificacion',    p.Numero_Identificacion);
        setField('Primer_Nombre',            p.Primer_Nombre);
        setField('Segundo_Nombre',           p.Segundo_Nombre);
        setField('Primer_Apellido',          p.Primer_Apellido);
        setField('Segundo_Apellido',         p.Segundo_Apellido);
        setField('Fec_Nac',                  p.Fec_Nac);
        setField('Cod_Pais_Nacionalidad',    p.Cod_Pais_Nacionalidad);
        setField('Nombre_Pais_Nacionalidad', p.Nombre_Pais_Nacionalidad);
        setField('Sexo_Biologico',           p.Sexo_Biologico);
        setField('Identidad_Genero',         p.Identidad_Genero);
        setField('Pertenencia_Etnica',       p.Pertenencia_Etnica);
        setField('Nombre_Pertenencia_Etnica',p.Nombre_Pertenencia_Etnica);
        setField('Categoria_Discapacidad',   p.Categoria_Discapacidad);
        setField('Cod_Ocupacion',            p.Cod_Ocupacion);
        setField('Nom_Ocupacion',            p.Nom_Ocupacion);
        setField('Cod_Pais_Residencia',      p.Cod_Pais_Residencia);
        setField('Nombre_Pais_Residencia',   p.Nombre_Pais_Residencia);
        setField('Cod_Mpio_Residencia',      p.Cod_Mpio_Residencia);
        setField('Nombre_Mpio_Residencia',   p.Nombre_Mpio_Residencia);
        setField('CodZona',                  p.CodZona);

        // ── Sección 3: Antecedentes salud ──
        setField('Antecedentes_Alergicos',    p.Antecedentes_Alergicos);
        setField('Antecedentes_NombreAlergico',p.Antecedentes_NombreAlergico);
        setField('Antecedente_Familiar',      p.Antecedente_Familiar);
        setField('Antecedente_Parentesco',    p.Antecedente_Parentesco);

        // ── Sección 4: Antecedentes farmacológicos ──
        setField('Cod_Dx_Princ', p.Cod_Dx_Princ);
        setField('Nom_Dx_Princ', p.Nom_Dx_Princ);

        showToast('Datos cargados', 'Información del paciente cargada correctamente', 'success');

    } catch (e) {
        console.error('Error al cargar datos del paciente:', e);
        showToast('Error', 'No se pudo cargar la información del paciente', 'error');
    }
}

// Helper para llenar inputs por name
function setField(name, value) {
    const input = document.querySelector(`input[name="${name}"]`);
    if (input) input.value = value ?? '';
}

async function cargarAccordion({
    bodyId,
    idAdmision,
    idSesion,
    tipo
}) {
    const contenedor = document.getElementById(bodyId);
    contenedor.innerHTML = "<p>Cargando...</p>";

    try {
        const resp = await fetch(
            `/ihce/rda-consulta?IdAdmision=${idAdmision}&IdSesion=${idSesion}&Tipo=${tipo}`
        );

        if (!resp.ok) throw new Error(await resp.text());

        const data = await resp.json();

        contenedor.innerHTML = contenedor.dataset.original;

        if (!data.length) {
            contenedor.innerHTML = '<p class="empty-state">Sin registros</p>';
            return;
        }

        const primerRegistro = data[0];
        Object.entries(primerRegistro).forEach(([key, value]) => {
            const input = contenedor.querySelector(`input[name$="_${key}"]`);
            if (input) input.value = value ?? "";
        });

        if (data.length > 1) {
            data.slice(1).forEach(registro => {
                Object.entries(registro).forEach(([key, value]) => {
                    const inputOriginal = contenedor.querySelector(`input[name$="_${key}"]`);
                    if (!inputOriginal) return;

                    const nuevoInput = document.createElement("input");
                    nuevoInput.type = "text";
                    nuevoInput.readOnly = true;
                    nuevoInput.value = value ?? "";
                    inputOriginal.parentElement.appendChild(nuevoInput);
                });
            });
        }

    } catch (err) {
        contenedor.innerHTML = `<p class="empty-state">Error: ${err.message}</p>`;
        console.error(err);
    }
}

document.addEventListener('DOMContentLoaded', () => {

    document.querySelectorAll('.accordion-body[id^="body-"]').forEach(body => {
        body.dataset.original = body.innerHTML;
    });
});
