(() => {
  const dialogo = document.getElementById("identidad-admin-dialogo");
  const invitacion = document.getElementById("identidad-invitar-dialogo");
  if (!dialogo || !invitacion) return;

  function consultaEn(ventana, habilitarVacio) {
    const formulario = ventana.querySelector("form");
    const entrada = formulario.querySelector("[data-dni-input]");
    const consultar = formulario.querySelector("[data-dni-consultar]");
    const salida = formulario.querySelector("[data-dni-estado]");
    const confirmar = formulario.querySelector("[data-dni-confirmar]");
    if (!entrada) {
      ventana.addEventListener("close", () => formulario.reset());
      return () => {};
    }

    let solicitud;
    let autorizado = false;

    function actualizar() {
      if (confirmar) confirmar.disabled = !autorizado && !(habilitarVacio && !entrada.value);
    }

    function limpiar() {
      solicitud?.abort();
      solicitud = null;
      autorizado = false;
      consultar.disabled = false;
      salida.hidden = true;
      salida.textContent = "";
      entrada.removeAttribute("aria-invalid");
      actualizar();
    }

    function pintar(mensaje, estado) {
      salida.textContent = mensaje;
      salida.dataset.estado = estado;
      salida.hidden = false;
    }

    async function buscar() {
      if (!/^[0-9]{8}$/.test(entrada.value)) {
        entrada.setAttribute("aria-invalid", "true");
        pintar("Escribe un DNI de 8 dígitos.", "error");
        entrada.focus();
        return;
      }
      limpiar();
      const consulta = new AbortController();
      solicitud = consulta;
      consultar.disabled = true;
      pintar("Consultando el DNI…", "cargando");
      const datos = new URLSearchParams({ dni: entrada.value });
      const csrf = formulario.elements[formulario.dataset.csrf];
      if (csrf) datos.set(csrf.name, csrf.value);

      try {
        const respuesta = await fetch(formulario.dataset.consulta, {
          method: "POST", body: datos, signal: consulta.signal, credentials: "same-origin",
          headers: { Accept: "application/json" }
        });
        const resultado = await respuesta.json();
        if (consulta.signal.aborted) return;
        if (!respuesta.ok) {
          pintar(resultado.mensaje || "No se pudo consultar el DNI.", "error");
          return;
        }
        const estado = {
          disponible: "Disponible para vincular.",
          reservado: "Ya está asociado a una invitación.",
          vinculado: "Ya está asociado a una cuenta."
        };
        autorizado = resultado.estado === "disponible";
        pintar(`${resultado.nombres} ${resultado.apellidos} · ${estado[resultado.estado] || ""}`, "exito");
      } catch (error) {
        if (!consulta.signal.aborted) pintar("No se pudo consultar el DNI. Vuelve a intentarlo.", "error");
      } finally {
        if (solicitud === consulta) {
          solicitud = null;
          consultar.disabled = false;
          actualizar();
        }
      }
    }

    entrada.addEventListener("input", limpiar);
    consultar.addEventListener("click", buscar);
    entrada.addEventListener("keydown", evento => {
      if (evento.key === "Enter" && entrada.value && !autorizado) {
        evento.preventDefault();
        buscar();
      }
    });
    formulario.addEventListener("submit", evento => {
      if (confirmar?.hidden || (!autorizado && !(habilitarVacio && !entrada.value))) {
        evento.preventDefault();
        if (!confirmar?.hidden) buscar();
      }
    });
    ventana.addEventListener("close", () => {
      formulario.reset();
      limpiar();
    });
    return limpiar;
  }

  const limpiarDialogo = consultaEn(dialogo, false);
  const limpiarInvitacion = consultaEn(invitacion, true);
  const formulario = dialogo.querySelector("form");
  const titulo = dialogo.querySelector("h2");
  const descripcion = dialogo.querySelector("[data-dni-descripcion]");
  const confirmar = dialogo.querySelector("[data-dni-confirmar]");
  const entrada = dialogo.querySelector("[data-dni-input]");

  document.querySelectorAll("[data-identidad-abrir]").forEach(boton => {
    boton.addEventListener("click", () => {
      const modo = boton.dataset.identidadAbrir;
      if (modo === "invitar") {
        limpiarInvitacion();
        invitacion.showModal();
        return;
      }
      formulario.action = boton.dataset.identidadAccion || formulario.dataset.consulta;
      confirmar.hidden = modo === "consultar";
      entrada.required = modo !== "consultar";
      titulo.textContent = { consultar: "Consultar DNI", cuenta: "Vincular DNI a cuenta", reserva: "Asociar DNI a invitación" }[modo];
      descripcion.textContent = boton.dataset.identidadEtiqueta || "Escribe el DNI de la persona para consultar la coincidencia.";
      limpiarDialogo();
      dialogo.showModal();
    });
  });

  [dialogo, invitacion].forEach(ventana => {
    ventana.querySelector("[data-identidad-cerrar]").addEventListener("click", () => ventana.close());
  });
})();
