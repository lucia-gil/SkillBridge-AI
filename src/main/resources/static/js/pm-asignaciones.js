(function () {
  'use strict';
  const rows = Array.from(document.querySelectorAll('[data-assignment-row]'));
  const params = new URLSearchParams(window.location.search);

  function esc(t) { const d = document.createElement('div'); d.textContent = t == null ? '' : String(t); return d.innerHTML; }

  function abrirProponer(id, nombreProyecto, persona) {
    window.SBAI.selectorPersonas.abrir({
      proyecto: nombreProyecto || '',
      endpoint: '/project-manager/proyectos/' + id + '/candidatos',
      accion: '/project-manager/proyectos/' + id + '/proponer-varios',
      modo: 'proponer',
      extra: { volver: 'asignaciones' },
      q: persona || ''
    });
  }

  // ?proponer=Nombre desde el buscador global: la persona no está en ningún
  // equipo del PM, así que se abre el selector "Proponer" con su nombre ya
  // escrito. Con varios proyectos abiertos, primero se elige a cuál.
  const proponer = params.get('proponer');
  if (proponer && window.SBAI && window.SBAI.selectorPersonas) {
    params.delete('proponer');
    const limpia = window.location.pathname + (params.toString() ? '?' + params.toString() : '');
    window.history.replaceState(null, '', limpia);   // recargar no vuelve a abrirlo
    const abiertos = rows.filter(function (r) { return r.dataset.proponible === 'true'; });
    if (!abiertos.length) {
      if (window.SBAI.toast) window.SBAI.toast.show('Ninguno de tus proyectos tiene vacantes abiertas para proponer a ' + proponer + '.', { type: 'error' });
    } else if (abiertos.length === 1) {
      abrirProponer(abiertos[0].dataset.proyectoId, abiertos[0].dataset.proyectoNombre, proponer);
    } else {
      const opciones = abiertos.map(function (r) {
        return '<button type="button" class="btn btn-secondary btn-block" style="justify-content:space-between;margin-bottom:var(--space-2)" data-elegir-proyecto="' + esc(r.dataset.proyectoId) + '">' +
          '<span>' + esc(r.dataset.proyectoNombre) + '</span><span class="text-tertiary">' + esc(r.dataset.vacantes) + ' vacante(s)</span></button>';
      }).join('');
      const bd = window.SBAI.modal.open(
        '<div class="modal modal-confirm is-info" role="dialog" aria-modal="true" aria-labelledby="pp-titulo">' +
        '<div class="mc-head"><div class="mc-head-text"><h2 class="mc-title" id="pp-titulo">¿A qué proyecto quieres proponer a ' + esc(proponer) + '?</h2>' +
        '<p class="mc-desc">Solo aparecen tus proyectos con vacantes abiertas. La propuesta la revisa el Resource Manager.</p></div>' +
        '<button type="button" class="modal-close" data-modal-close aria-label="Cerrar"><svg class="icon"><use href="/img/icons.svg#icon-x"></use></svg></button></div>' +
        '<div class="modal-body mc-body">' + opciones + '</div></div>');
      bd.querySelectorAll('[data-elegir-proyecto]').forEach(function (b) {
        b.addEventListener('click', function () {
          const fila = abiertos.find(function (r) { return r.dataset.proyectoId === b.dataset.elegirProyecto; });
          window.SBAI.modal.close(bd);
          abrirProponer(fila.dataset.proyectoId, fila.dataset.proyectoNombre, proponer);
        });
      });
    }
  }

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
    // Modelo v7, fase 4: el botón abre el selector de personas (búsqueda y
    // filtros en el servidor, selección múltiple). Ver selector-personas.js.
    const show = modal.querySelector('[data-show-assignment-form]');
    if (show && window.SBAI.selectorPersonas) show.addEventListener('click', function () {
      abrirProponer(show.dataset.proyectoId, show.dataset.proyectoNombre, '');
    });

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
  const buscarParam = params.get('buscar');
  if (buscarParam) search.value = buscarParam;

  search.addEventListener('input', function () { page = 1; render(); });
  state.addEventListener('change', function () { page = 1; render(); });
  previous.addEventListener('click', function () { if (page > 1) { page--; render(); } });
  next.addEventListener('click', function () {
    if (page < Math.max(1, Math.ceil(filteredRows().length / pageSize))) { page++; render(); }
  });
  render();
})();
