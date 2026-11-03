document.addEventListener('DOMContentLoaded', () => {
  const dialog = document.getElementById('curso-dialog');
  const form = document.getElementById('curso-form');
  const title = document.getElementById('curso-dialog-title');
  const category = form.elements.categoriaId;
  const skill = form.elements.habilidadId;
  const allSkills = [...skill.options].map(option => ({value: option.value, label: option.textContent, category: option.dataset.categoriaId || ''}));
  const error = document.getElementById('curso-form-error');
  const save = form.querySelector('[type="submit"]');

  function setError(message, field) {
    error.textContent = message || '';
    error.hidden = !message;
    if (field) field.focus();
  }

  function filterSkills(selected = '') {
    const current = selected || skill.value;
    skill.replaceChildren();
    allSkills.filter(item => !item.value || !category.value || item.category === category.value).forEach(item => {
      const option = new Option(item.label, item.value);
      option.dataset.categoriaId = item.category;
      skill.add(option);
    });
    skill.value = [...skill.options].some(option => option.value === current) ? current : '';
    setError('');
  }

  function open(button) {
    form.reset();
    save.disabled = false;
    save.textContent = 'Guardar curso';
    form.action = '/administrador/cursos';
    title.textContent = 'Nuevo curso';
    setError('');
    const data = button?.dataset;
    if (data?.editarCurso !== undefined) {
      form.action = `/administrador/cursos/${encodeURIComponent(data.id)}/editar`;
      title.textContent = 'Editar curso';
      form.elements.titulo.value = data.titulo || '';
      form.elements.descripcion.value = data.descripcion || '';
      form.elements.url.value = data.url || '';
      form.elements.duracionHoras.value = data.duracion || '1';
      category.value = data.categoria || '';
      form.elements.nivel.value = data.nivel || 'BASICO';
      filterSkills(data.habilidad || '');
    } else filterSkills();
    dialog.showModal();
    form.elements.titulo.focus();
  }

  category.addEventListener('change', () => filterSkills());
  form.addEventListener('input', () => { if (!error.hidden) setError(''); });
  form.addEventListener('submit', event => {
    const name = form.elements.titulo;
    const url = form.elements.url;
    const duration = form.elements.duracionHoras;
    if (name.value.trim().length < 3) {
      event.preventDefault(); setError('Escribe un título de al menos 3 caracteres.', name); return;
    }
    if (!Number.isInteger(Number(duration.value)) || Number(duration.value) < 1 || Number(duration.value) > 10000) {
      event.preventDefault(); setError('La duración debe estar entre 1 y 10 000 horas.', duration); return;
    }
    if (url.value && !/^https?:\/\//i.test(url.value)) {
      event.preventDefault(); setError('El enlace debe comenzar con https:// o http://.', url); return;
    }
    const chosen = skill.selectedOptions[0];
    if (category.value && chosen?.value && chosen.dataset.categoriaId !== category.value) {
      event.preventDefault(); setError('La habilidad debe pertenecer a la categoría seleccionada.', skill); return;
    }
    save.textContent = 'Guardando…';
  });

  document.getElementById('nuevo-curso').addEventListener('click', () => open());
  document.getElementById('curso-vacio-nuevo')?.addEventListener('click', () => open());
  document.querySelectorAll('[data-editar-curso]').forEach(button => button.addEventListener('click', () => open(button)));
  document.getElementById('cerrar-curso').addEventListener('click', () => dialog.close());
  document.getElementById('cancelar-curso').addEventListener('click', () => dialog.close());
  dialog.addEventListener('click', event => { if (event.target === dialog) dialog.close(); });

  const confirm = document.getElementById('curso-confirm-dialog');
  document.querySelectorAll('[data-course-state]').forEach(button => button.addEventListener('click', () => {
    const targetForm = button.closest('form');
    const state = targetForm.elements.estado.value;
    confirm.querySelector('[data-confirm-title]').textContent = state === 'ARCHIVADO' ? '¿Archivar curso?' : '¿Publicar curso?';
    confirm.querySelector('[data-confirm-copy]').textContent = state === 'ARCHIVADO'
      ? 'El curso dejará de estar visible como publicado. Podrás volver a publicarlo.'
      : 'El curso volverá a estar disponible en el catálogo.';
    confirm.querySelector('[data-confirm-accept]').onclick = () => { confirm.close(); targetForm.requestSubmit(); };
    confirm.showModal();
  }));
  confirm.querySelector('[data-confirm-cancel]').addEventListener('click', () => confirm.close());

  const feedback = window.adminCursoFeedback;
  window.addEventListener('pageshow', () => { save.disabled = false; save.textContent = 'Guardar curso'; });
  if (feedback?.exito && window.SBAI?.toast) window.SBAI.toast.show(feedback.exito, {type:'success'});
  if (feedback?.error && window.SBAI?.toast) window.SBAI.toast.show(feedback.error, {type:'error'});
});
