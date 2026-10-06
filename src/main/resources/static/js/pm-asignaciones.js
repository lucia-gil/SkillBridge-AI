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
  const pageSize = 10;
  let page = 1;

  function normalize(value) {
    return (value || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim();
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

    modal.querySelectorAll('[data-finalizar-asignacion]').forEach(function (button) {
      button.addEventListener('click', function () {
        const fila = button.closest('tr');
        const nombre = fila && fila.querySelector('.cell-person-name') ? fila.querySelector('.cell-person-name').textContent : 'este colaborador';
        const titulo = modal.querySelector('h2');
        window.SBAI.proyectoAcciones.finalizarAsignacion({
          url: '/project-manager/asignaciones/' + button.dataset.finalizarAsignacion + '/finalizar',
          nombre: nombre,
          proyecto: titulo ? titulo.textContent : '',
          extra: { volver: 'asignaciones' }
        });
      });
    });

    // Propuesta en masa (modelo v7: el PM propone, el RM aprueba). Ver asignacion-masiva.js.
    const assign = modal.querySelector('[data-assign-project]');
    if (assign && panel && window.SBAI.asignacionMasiva) {
      window.SBAI.asignacionMasiva.montar(panel, {
        perfiles: window.SBAI_ASIGNABLES || [],
        limiteCarga: window.SBAI_LIMITE_CARGA || 100,
        url: '/project-manager/proyectos/' + assign.dataset.assignProject + '/proponer-varios',
        extra: { volver: 'asignaciones' },
        proyecto: assign.dataset.proyectoNombre || '',
        modo: 'proponer'
      });
    }
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
