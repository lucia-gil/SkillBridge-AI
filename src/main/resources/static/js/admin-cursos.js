document.addEventListener('DOMContentLoaded', () => {
  const dialog = document.getElementById('curso-dialog');
  const form = document.getElementById('curso-form');
  const title = document.getElementById('curso-dialog-title');
  const open = (button) => {
    form.reset();
    form.action = '/administrador/cursos';
    title.textContent = 'Nuevo curso';
    if (button && button.dataset.editarCurso !== undefined) {
      const data = button.dataset;
      form.action = `/administrador/cursos/${encodeURIComponent(data.id)}/editar`;
      title.textContent = 'Editar curso';
      for (const [field, value] of Object.entries({titulo:data.titulo, descripcion:data.descripcion, url:data.url,
        categoriaId:data.categoria, habilidadId:data.habilidad, nivel:data.nivel, duracionHoras:data.duracion})) {
        form.elements[field].value = value || '';
      }
    }
    dialog.showModal();
    form.elements.titulo.focus();
  };
  document.getElementById('nuevo-curso').addEventListener('click', () => open());
  document.getElementById('curso-vacio-nuevo')?.addEventListener('click', () => open());
  document.querySelectorAll('[data-editar-curso]').forEach(button => button.addEventListener('click', () => open(button)));
  document.getElementById('cerrar-curso').addEventListener('click', () => dialog.close());
  document.getElementById('cancelar-curso').addEventListener('click', () => dialog.close());
  dialog.addEventListener('click', event => { if (event.target === dialog) dialog.close(); });
  const feedback = window.adminCursoFeedback;
  if (feedback?.exito && window.SBAI?.toast) window.SBAI.toast.show(feedback.exito, {type:'success'});
  if (feedback?.error && window.SBAI?.toast) window.SBAI.toast.show(feedback.error, {type:'error'});
});
