document.addEventListener("DOMContentLoaded", function () {
  const archivo = document.getElementById("archivo-invitaciones");
  if (!archivo) return;

  function validarArchivo() {
    const seleccionado = archivo.files[0];
    let mensaje = "";
    if (seleccionado && !/\.(csv|xlsx)$/i.test(seleccionado.name)) {
      mensaje = "Selecciona un archivo CSV (.csv) o Excel (.xlsx).";
    } else if (seleccionado && seleccionado.size > 1024 * 1024) {
      mensaje = "El archivo debe pesar como máximo 1 MB.";
    }
    archivo.setCustomValidity(mensaje);
  }

  archivo.addEventListener("change", function () {
    validarArchivo();
    archivo.reportValidity();
  });
  validarArchivo();
});
