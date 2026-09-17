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
      return normalize(row.dataset.name).includes(term)
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

    // "Finalizar" pide confirmación antes de ejecutar, igual que en la
    // version anterior de esta pantalla, para que un clic accidental no
    // finalice una asignación sin querer.
    modal.querySelectorAll('[data-finalizar-asignacion]').forEach(function (button) {
      button.addEventListener('click', function () {
        const fila = button.closest('tr');
        const nombre = fila ? fila.querySelector('.cell-person-name').textContent : 'este colaborador';
        window.SBAI.modal.confirm({
          title: '¿Finalizar la asignación de ' + nombre + '?',
          desc: 'Dejará de aparecer como parte activa del equipo de este proyecto. Esto no borra su historial.',
          confirmLabel: 'Finalizar',
          variant: 'destructive',
          onConfirm: function () {
            submitForm('/resource-manager/asignaciones/' + button.dataset.finalizarAsignacion + '/finalizar', {});
          }
        });
      });
    });

    const assign = modal.querySelector('[data-assign-project]');
    if (assign && panel) assign.addEventListener('click', function () {
      const profile = panel.querySelector('[data-field="perfilId"]').value;
      const role = panel.querySelector('[data-field="rolEnProyecto"]').value;
      const dedication = Number(panel.querySelector('[data-field="cargaPorcentaje"]').value);
      const startDate = panel.querySelector('[data-field="fechaInicio"]').value;
      if (!profile) return window.SBAI.toast.show('Selecciona un colaborador.', { type: 'error' });
      if (!Number.isInteger(dedication) || dedication < 1 || dedication > 100) return window.SBAI.toast.show('La dedicación debe estar entre 1% y 100%.', { type: 'error' });
      if (!startDate) return window.SBAI.toast.show('Selecciona una fecha de inicio.', { type: 'error' });
      assign.disabled = true;
      submitForm('/resource-manager/proyectos/' + assign.dataset.assignProject + '/asignar', {
        perfilId: profile, rolEnProyecto: role, cargaPorcentaje: dedication, fechaInicio: startDate
      });
    });

    // "(cambiar)" el Project Manager del proyecto — funcionalidad propia
    // del Resource Manager que no existe en la vista del PM.
    const cambiarPm = modal.querySelector('[data-cambiar-pm]');
    if (cambiarPm) cambiarPm.addEventListener('click', function () {
      const proyectoId = cambiarPm.dataset.cambiarPm;
      const proyectoNombre = cambiarPm.dataset.proyectoNombre || '';
      const options = PERFILES.map(function (p) {
        return '<option value="' + p.perfilId + '">' + p.nombre + ' · ' + p.correo + '</option>';
      }).join('');

      const html = '<div class="modal modal-wide" role="dialog" aria-modal="true">' +
        '<div class="modal-header"><div><h2>Cambiar Project Manager</h2><p>' + proyectoNombre + '</p></div>' +
        '<button type="button" class="modal-close" data-modal-close aria-label="Cerrar"><svg class="icon"><use href="/img/icons.svg#icon-x"></use></svg></button></div>' +
        '<div class="modal-body">' +
        '<div class="field"><label class="field-label">Nuevo Project Manager</label><select class="select" id="pm-perfil">' + options + '</select></div>' +
        '</div><div class="modal-footer"><button type="button" class="btn btn-secondary" data-modal-close>Cancelar</button><button type="button" class="btn btn-primary" id="pm-confirm">Confirmar cambio</button></div></div>';

      const backdrop = window.SBAI.modal.open(html);
      backdrop.querySelector('#pm-confirm').addEventListener('click', function () {
        const nuevoPm = backdrop.querySelector('#pm-perfil').value;
        submitForm('/resource-manager/proyectos/' + proyectoId + '/cambiar-pm', { nuevoPmPerfilId: nuevoPm });
      });
    });
  }

  document.querySelectorAll('[data-view-team]').forEach(function (button) {
    button.addEventListener('click', function () {
      const template = document.querySelector('template[data-team-project="' + button.dataset.viewTeam + '"]');
      if (!template) return;
      const backdrop = window.SBAI.modal.open('<div class="modal modal-wide" role="dialog" aria-modal="true">' + template.innerHTML + '</div>');
      bindModal(backdrop);
    });
  });

  search.addEventListener('input', function () { page = 1; render(); });
  state.addEventListener('change', function () { page = 1; render(); });
  previous.addEventListener('click', function () { if (page > 1) { page--; render(); } });
  next.addEventListener('click', function () {
    if (page < Math.max(1, Math.ceil(filteredRows().length / pageSize))) { page++; render(); }
  });
  render();
})();
