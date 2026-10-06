(function () {
  'use strict';
  const rows = Array.from(document.querySelectorAll('[data-assignment-row]'));
  if (!rows.length) return;

  const search = document.getElementById('assignment-search');
  const state = document.getElementById('assignment-state');
  const empty = document.getElementById('assignment-empty');
  const visibleCount = document.getElementById('assignment-visible-count');
  const filteredCount = document.getElementById('assignment-filtered-count');
  const pageLabel = document.getElementById('assignment-page-label');
  const previous = document.getElementById('assignment-prev');
  const next = document.getElementById('assignment-next');
  const pageSize = 6;
  let page = 1;

  function normalize(value) {
    return (value || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim();
  }

  function filteredRows() {
    const term = normalize(search.value);
    return rows.filter(function (row) {
      // Busca por nombre del proyecto o de cualquiera de sus integrantes
      // (asi el buscador global puede llevar aqui a una persona).
      return (normalize(row.dataset.name).includes(term) || normalize(row.dataset.miembros).includes(term))
        && (state.value === 'todos' || row.dataset.state === state.value);
    });
  }

  function render() {
    const filtered = filteredRows();
    const pages = Math.max(1, Math.ceil(filtered.length / pageSize));
    page = Math.min(page, pages);
    const visible = filtered.slice((page - 1) * pageSize, page * pageSize);
    rows.forEach(function (row) { row.hidden = true; });
    visible.forEach(function (row) { row.hidden = false; });
    empty.hidden = filtered.length !== 0;
    visibleCount.textContent = visible.length;
    filteredCount.textContent = filtered.length;
    pageLabel.textContent = 'Página ' + page + ' de ' + pages;
    previous.disabled = page === 1;
    next.disabled = page === pages;
  }

  function submitForm(url, values) {
    const form = document.createElement('form');
    form.method = 'post';
    form.action = url;
    Object.keys(values).forEach(function (name) {
      const input = document.createElement('input');
      input.type = 'hidden'; input.name = name; input.value = values[name];
      form.appendChild(input);
    });
    document.body.appendChild(form);
    form.submit();
  }

  function bindModal(modal) {
    const panel = modal.querySelector('[data-assignment-form]');
    const show = modal.querySelector('[data-show-assignment-form]');
    const hide = modal.querySelector('[data-hide-assignment-form]');
    if (show && panel) show.addEventListener('click', function () {
      panel.hidden = false; show.hidden = true;
      const date = panel.querySelector('[data-field="fechaInicio"]');
      if (date && !date.value) date.value = new Date().toISOString().slice(0, 10);
    });
    if (hide && panel) hide.addEventListener('click', function () { panel.hidden = true; show.hidden = false; });

    // "Finalizar" pide confirmación (y un motivo opcional para Auditoría) antes de ejecutar,
    // para que un clic accidental no finalice una asignación sin querer.
    modal.querySelectorAll('[data-finalizar-asignacion]').forEach(function (button) {
      button.addEventListener('click', function () {
        const fila = button.closest('tr');
        const nombre = fila ? fila.querySelector('.cell-person-name').textContent : 'este colaborador';
        const titulo = modal.querySelector('h2');
        window.SBAI.proyectoAcciones.finalizarAsignacion({
          url: '/resource-manager/asignaciones/' + button.dataset.finalizarAsignacion + '/finalizar',
          nombre: nombre,
          proyecto: titulo ? titulo.textContent : ''
        });
      });
    });

    // Asignacion en masa: lista con checkboxes (ver asignacion-masiva.js).
    const assign = modal.querySelector('[data-assign-project]');
    if (assign && panel && window.SBAI.asignacionMasiva) {
      window.SBAI.asignacionMasiva.montar(panel, {
        perfiles: window.SBAI_ASIGNABLES || [],
        limiteCarga: window.SBAI_LIMITE_CARGA || 100,
        url: '/resource-manager/proyectos/' + assign.dataset.assignProject + '/asignar-varios',
        extra: {},
        proyecto: assign.dataset.proyectoNombre || ''
      });
    }

    // "(cambiar)" el Project Manager — funcionalidad propia del Resource
    // Manager (modelo v7). Candidatos: Miembros activos (SBAI_ASIGNABLES ya
    // excluye Admin/RM y cuentas suspendidas). Quien ya está en el equipo
    // asciende conservando su dedicación; quien viene de fuera entra con 20%
    // y necesita capacidad. El PM saliente puede quedarse como colaborador
    // solo si hay vacante. El backend vuelve a validar todo.
    const cambiarPm = modal.querySelector('[data-cambiar-pm]');
    if (cambiarPm) cambiarPm.addEventListener('click', function () {
      const proyectoId = cambiarPm.dataset.cambiarPm;
      const proyectoNombre = cambiarPm.dataset.proyectoNombre || '';
      const vacantes = Number(cambiarPm.dataset.vacantes) || 0;
      const limite = Number(window.SBAI_LIMITE_CARGA) || 100;
      const CARGA_PM = 20;

      const equipo = {};
      let pmActual = null;
      modal.querySelectorAll('[data-miembro-perfil]').forEach(function (tr) {
        const m = { perfilId: String(tr.dataset.miembroPerfil), rol: tr.dataset.miembroRol,
          carga: Number(tr.dataset.miembroCarga) || 0, nombre: tr.dataset.miembroNombre || '' };
        equipo[m.perfilId] = m;
        if (m.rol === 'project_manager') pmActual = m;
      });

      const opciones = (window.SBAI_ASIGNABLES || [])
        .filter(function (p) { return !pmActual || String(p.perfilId) !== pmActual.perfilId; })
        .map(function (p) {
          const enEquipo = equipo[String(p.perfilId)];
          const sinCapacidad = !enEquipo && (p.cargaActual + CARGA_PM > limite);
          const detalle = enEquipo
            ? 'ya en el equipo · conserva su ' + enEquipo.carga + '%'
            : 'carga ' + p.cargaActual + '%' + (sinCapacidad ? ' · sin capacidad para 20%' : ' → ' + (p.cargaActual + CARGA_PM) + '%');
          return { p: p, enEquipo: !!enEquipo, sinCapacidad: sinCapacidad, detalle: detalle };
        })
        .sort(function (a, b) { return (b.enEquipo - a.enEquipo) || (a.sinCapacidad - b.sinCapacidad) || a.p.nombre.localeCompare(b.p.nombre); });

      const esc = function (t) { const d = document.createElement('div'); d.textContent = t == null ? '' : String(t); return d.innerHTML; };
      const options = opciones.map(function (o) {
        return '<option value="' + o.p.perfilId + '"' + (o.sinCapacidad ? ' disabled' : '') + ' data-en-equipo="' + (o.enEquipo ? '1' : '0') + '">' +
          esc(o.p.nombre) + ' · ' + esc(o.detalle) + '</option>';
      }).join('');

      const salienteHtml = pmActual
        ? '<div class="handover-panel" style="margin-top:var(--space-4);margin-bottom:0">' +
          '<h3>' + esc(pmActual.nombre) + ' (PM actual)</h3>' +
          '<label class="handover-check" id="pm-mantener-wrap"><input type="checkbox" id="pm-mantener">' +
          '<span>Mantener como colaborador en este proyecto<span class="handover-hint" id="pm-mantener-hint" style="display:block"></span></span></label>' +
          '<div class="field" id="pm-carga-wrap" hidden><label class="field-label">Su dedicación como colaborador (%)</label>' +
          '<input class="input" type="number" min="1" max="' + limite + '" id="pm-carga" value="' + pmActual.carga + '"></div>' +
          '</div>'
        : '<p class="text-tertiary" style="font-size:var(--fs-xs);margin-top:var(--space-3)">Este proyecto no tiene un Project Manager activo.</p>';

      const html = '<div class="modal modal-wide" role="dialog" aria-modal="true">' +
        '<div class="modal-header"><div><h2>Cambiar Project Manager</h2><p>' + esc(proyectoNombre) + '</p></div>' +
        '<button type="button" class="modal-close" data-modal-close aria-label="Cerrar"><svg class="icon"><use href="/img/icons.svg#icon-x"></use></svg></button></div>' +
        '<div class="modal-body">' +
        '<div class="field"><label class="field-label">Nuevo Project Manager</label>' +
        (options ? '<select class="select" id="pm-perfil">' + options + '</select>' : '<p class="text-tertiary">No hay Miembros disponibles.</p>') +
        '<p class="field-hint">Solo Miembros activos (nunca Administradores ni Resource Managers). Conserva sus asignaciones en otros proyectos.</p></div>' +
        salienteHtml +
        '</div><div class="modal-footer"><button type="button" class="btn btn-secondary" data-modal-close>Cancelar</button>' +
        '<button type="button" class="btn btn-primary" id="pm-confirm"' + (options ? '' : ' disabled') + '>Confirmar cambio</button></div></div>';

      const backdrop = window.SBAI.modal.open(html);
      const sel = backdrop.querySelector('#pm-perfil');
      const mantener = backdrop.querySelector('#pm-mantener');
      const cargaWrap = backdrop.querySelector('#pm-carga-wrap');
      const hint = backdrop.querySelector('#pm-mantener-hint');

      function refrescarMantener() {
        if (!mantener || !sel) return;
        const opt = sel.options[sel.selectedIndex];
        // Si el nuevo PM ya era colaborador del equipo, su cupo se libera al ascender.
        const hayCupo = vacantes > 0 || (opt && opt.dataset.enEquipo === '1');
        mantener.disabled = !hayCupo;
        if (!hayCupo) mantener.checked = false;
        backdrop.querySelector('#pm-mantener-wrap').classList.toggle('is-disabled', !hayCupo);
        hint.textContent = hayCupo
          ? 'Si no lo marcas, su asignación en este proyecto se finaliza.'
          : 'No hay vacante de colaborador: su asignación en este proyecto se finalizará.';
        cargaWrap.hidden = !mantener.checked;
      }
      if (sel) {
        const primeraValida = Array.prototype.find.call(sel.options, function (o) { return !o.disabled; });
        if (primeraValida) sel.value = primeraValida.value;
        sel.addEventListener('change', refrescarMantener);
      }
      if (mantener) mantener.addEventListener('change', refrescarMantener);
      refrescarMantener();

      backdrop.querySelector('#pm-confirm').addEventListener('click', function () {
        if (!sel || !sel.value) return;
        const valores = { nuevoPmPerfilId: sel.value, mantener: mantener && mantener.checked ? 'true' : 'false' };
        if (mantener && mantener.checked) valores.cargaSaliente = backdrop.querySelector('#pm-carga').value;
        submitForm('/resource-manager/proyectos/' + proyectoId + '/cambiar-pm', valores);
      });
    });
  }

  document.querySelectorAll('[data-view-team]').forEach(function (button) {
    button.addEventListener('click', function () {
      const template = document.querySelector('template[data-team-project="' + button.dataset.viewTeam + '"]');
      if (!template) return;
      const backdrop = window.SBAI.modal.open('<div class="modal modal-wide modal-assign" role="dialog" aria-modal="true">' + template.innerHTML + '</div>');
      bindModal(backdrop);
    });
  });

  // ?buscar= desde el buscador global del topbar (proyecto o persona).
  const buscarParam = new URLSearchParams(window.location.search).get('buscar');
  if (buscarParam) search.value = buscarParam;

  search.addEventListener('input', function () { page = 1; render(); });
  state.addEventListener('change', function () { page = 1; render(); });
  previous.addEventListener('click', function () { if (page > 1) { page--; render(); } });
  next.addEventListener('click', function () {
    if (page < Math.max(1, Math.ceil(filteredRows().length / pageSize))) { page++; render(); }
  });
  render();
})();
