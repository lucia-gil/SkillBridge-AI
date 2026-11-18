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
    // Modelo v7, fase 4: el botón abre el selector de personas (búsqueda y
    // filtros en el servidor, selección múltiple). Ver selector-personas.js.
    const show = modal.querySelector('[data-show-assignment-form]');
    if (show && window.SBAI.selectorPersonas) show.addEventListener('click', function () {
      const id = show.dataset.proyectoId;
      window.SBAI.selectorPersonas.abrir({
        proyecto: show.dataset.proyectoNombre || '',
        endpoint: '/resource-manager/proyectos/' + id + '/candidatos',
        accion: '/resource-manager/proyectos/' + id + '/asignar-varios',
        modo: 'asignar',
        extra: {}
      });
    });

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


    // "(cambiar)" el Project Manager — funcionalidad propia del Resource
    // Manager (modelo v7). Candidatos: Miembros activos (el selector ya
    // excluye Admin/RM y cuentas suspendidas). Quien ya está en el equipo
    // asciende conservando su dedicación; quien viene de fuera entra con 20%
    // y necesita capacidad. El PM saliente puede quedarse como colaborador
    // solo si queda un cupo libre; si el equipo está completo, se puede
    // liberar uno eligiendo a qué colaborador finalizar en el mismo paso.
    // El backend vuelve a validar todo.
    const cambiarPm = modal.querySelector('[data-cambiar-pm]');
    if (cambiarPm) cambiarPm.addEventListener('click', function () {
      const proyectoId = cambiarPm.dataset.cambiarPm;
      const proyectoNombre = cambiarPm.dataset.proyectoNombre || '';
      const requeridos = Number(cambiarPm.dataset.requeridos) || 0;
      const limite = Number(window.SBAI_LIMITE_CARGA) || 100;
      const CARGA_PM = 20;

      const equipo = {};
      let pmActual = null;
      modal.querySelectorAll('[data-miembro-perfil]').forEach(function (tr) {
        const m = { perfilId: String(tr.dataset.miembroPerfil), rol: tr.dataset.miembroRol,
          asignacionId: tr.dataset.miembroAsignacion,
          carga: Number(tr.dataset.miembroCarga) || 0, nombre: tr.dataset.miembroNombre || '' };
        equipo[m.perfilId] = m;
        if (m.rol === 'project_manager') pmActual = m;
      });

      const esc = function (t) { const d = document.createElement('div'); d.textContent = t == null ? '' : String(t); return d.innerHTML; };
      let elegido = null; // { perfilId, nombre, enEquipo, ... } desde el selector (modo "pm")

      const salienteHtml = pmActual
        ? '<div class="handover-panel" style="margin-top:var(--space-4);margin-bottom:0">' +
          '<h3>' + esc(pmActual.nombre) + ' (PM actual)</h3>' +
          '<label class="handover-check" id="pm-mantener-wrap"><input type="checkbox" id="pm-mantener">' +
          '<span>Mantener como colaborador en este proyecto<span class="handover-hint" id="pm-mantener-hint" style="display:block"></span></span></label>' +
          '<div class="field" id="pm-liberar-wrap" hidden><label class="field-label" for="pm-liberar">Liberar un cupo finalizando a</label>' +
          '<select class="select" id="pm-liberar"></select>' +
          '<p class="field-hint">Su asignación en este proyecto se finaliza (queda en Auditoría) y ' + esc(pmActual.nombre) + ' ocupa ese cupo.</p></div>' +
          '<div class="field" id="pm-carga-wrap" hidden><label class="field-label">Su dedicación como colaborador (%)</label>' +
          '<input class="input" type="number" min="1" max="' + limite + '" id="pm-carga" value="' + pmActual.carga + '"></div>' +
          '</div>'
        : '<p class="text-tertiary" style="font-size:var(--fs-xs);margin-top:var(--space-3)">Este proyecto no tiene un Project Manager activo.</p>';

      const html = '<div class="modal modal-wide" role="dialog" aria-modal="true">' +
        '<div class="modal-header"><div><h2>Cambiar Project Manager</h2><p>' + esc(proyectoNombre) + '</p></div>' +
        '<button type="button" class="modal-close" data-modal-close aria-label="Cerrar"><svg class="icon"><use href="/img/icons.svg#icon-x"></use></svg></button></div>' +
        '<div class="modal-body">' +
        '<div class="field"><span class="field-label">Nuevo Project Manager</span>' +
        '<div class="pm-picker" id="pm-picker"><span class="pm-picker-vacio">Nadie elegido todavía</span>' +
        '<button type="button" class="btn btn-secondary btn-sm" data-pm-elegir>Elegir…</button></div>' +
        '<p class="field-hint">Solo Miembros activos (nunca Administradores ni Resource Managers). Conserva sus asignaciones en otros proyectos.</p></div>' +
        salienteHtml +
        '</div><div class="modal-footer"><button type="button" class="btn btn-secondary" data-modal-close>Cancelar</button>' +
        '<button type="button" class="btn btn-primary" id="pm-confirm" disabled>Confirmar cambio</button></div></div>';

      const backdrop = window.SBAI.modal.open(html);
      const picker = backdrop.querySelector('#pm-picker');
      const mantener = backdrop.querySelector('#pm-mantener');
      const cargaWrap = backdrop.querySelector('#pm-carga-wrap');
      const hint = backdrop.querySelector('#pm-mantener-hint');
      const cerrado = cambiarPm.dataset.cerrado === 'true';

      const liberarWrap = backdrop.querySelector('#pm-liberar-wrap');
      const liberar = backdrop.querySelector('#pm-liberar');

      /** Colaboradores que seguirían en el equipo después del cambio (sin contar al nuevo PM si asciende). */
      function colaboradoresRestantes() {
        return Object.keys(equipo).map(function (k) { return equipo[k]; }).filter(function (m) {
          return m.rol === 'colaborador' && !(elegido && String(elegido.perfilId) === m.perfilId);
        });
      }
      function refrescarMantener() {
        if (!mantener) { backdrop.querySelector('#pm-confirm').disabled = !elegido; return; }
        // Cupo real: los colaboradores que quedan (el nuevo PM deja el suyo si ascendía)
        // contra los requeridos. Un equipo con más gente de la requerida no tiene cupo.
        const restantes = colaboradoresRestantes();
        const hayCupo = !cerrado && restantes.length < requeridos;
        const puedeLiberar = !cerrado && !hayCupo && !!elegido && restantes.length > 0;
        mantener.disabled = cerrado || (!hayCupo && !puedeLiberar);
        if (mantener.disabled) mantener.checked = false;
        backdrop.querySelector('#pm-mantener-wrap').classList.toggle('is-disabled', mantener.disabled);
        hint.textContent = cerrado ? 'El proyecto está cerrado: su asignación se finalizará.'
          : !elegido ? 'Elige primero al nuevo Project Manager.'
          : hayCupo ? 'Hay un cupo libre. Si no lo marcas, su asignación en este proyecto se finaliza.'
          : puedeLiberar ? 'El equipo está completo (' + restantes.length + ' de ' + requeridos + '): para mantenerlo, elige a qué colaborador finalizar.'
          : 'No hay cupo de colaborador: su asignación en este proyecto se finalizará.';
        // Selector "liberar un cupo": solo cuando hace falta y está marcada la casilla.
        const necesitaLiberar = puedeLiberar && mantener.checked;
        liberarWrap.hidden = !necesitaLiberar;
        if (necesitaLiberar) {
          const previo = liberar.value;
          liberar.innerHTML = '<option value="">Elige a un colaborador…</option>' + restantes.map(function (m) {
            return '<option value="' + esc(m.asignacionId) + '">' + esc(m.nombre) + ' · ' + m.carga + '%</option>';
          }).join('');
          if (previo && liberar.querySelector('option[value="' + previo + '"]')) liberar.value = previo;
        }
        cargaWrap.hidden = !mantener.checked;
        backdrop.querySelector('#pm-confirm').disabled = !elegido || (necesitaLiberar && !liberar.value);
      }
      function abrirSelector() {
        window.SBAI.selectorPersonas.abrir({
          modo: 'pm', proyecto: proyectoNombre,
          endpoint: '/resource-manager/proyectos/' + proyectoId + '/candidatos-pm',
          onElegir: function (c) {
            elegido = c;
            picker.classList.add('has-value');
            picker.innerHTML = '<span class="avatar avatar-sm">' + esc(c.iniciales) + '</span>' +
              '<span class="pm-picker-info"><strong>' + esc(c.nombre) + '</strong><small>' + esc(c.nota || (c.cargo + ' · carga ' + c.cargaActual + '% → ' + (c.cargaActual + CARGA_PM) + '%')) + '</small></span>' +
              '<button type="button" class="btn btn-secondary btn-sm" data-pm-elegir>Cambiar</button>';
            picker.querySelector('[data-pm-elegir]').addEventListener('click', abrirSelector);
            refrescarMantener();
          }
        });
      }
      picker.querySelector('[data-pm-elegir]').addEventListener('click', abrirSelector);
      if (mantener) mantener.addEventListener('change', refrescarMantener);
      if (liberar) liberar.addEventListener('change', refrescarMantener);
      refrescarMantener();

      backdrop.querySelector('#pm-confirm').addEventListener('click', function () {
        if (!elegido) return;
        const valores = { nuevoPmPerfilId: elegido.perfilId, mantener: mantener && mantener.checked ? 'true' : 'false' };
        if (mantener && mantener.checked) valores.cargaSaliente = backdrop.querySelector('#pm-carga').value;
        if (mantener && mantener.checked && !liberarWrap.hidden) {
          if (!liberar.value) return window.SBAI.toast.show('Elige a qué colaborador finalizar para liberar el cupo.', { type: 'error' });
          valores.liberarAsignacionId = liberar.value;
        }
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
