/*
  proyecto-acciones.js — Confirmaciones de las acciones delicadas sobre proyectos,
  con modal propio (SBAI.modal.confirm) en lugar del aviso nativo del navegador,
  y con el motivo que queda en Auditoría.

    SBAI.proyectoAcciones.eliminar({ url, estadoUrl, nombre, estado, estadoLabel })
    SBAI.proyectoAcciones.cambiarEstado({ url, nombre, actual, nuevo, extra, colaboradoresActivos, equipoAnterior })
    SBAI.proyectoAcciones.datosDeModal(elementoDelModal) -> { colaboradoresActivos, equipoAnterior }
    SBAI.proyectoAcciones.pedirMotivoEstado({ nombre, nuevo, onConfirm(motivo) })
    SBAI.proyectoAcciones.finalizarAsignacion({ url, nombre, proyecto, extra, alConfirmar(motivo) })

  Se usa en: project-manager/proyectos, administrador/proyectos,
  project-manager/asignaciones y resource-manager/asignaciones.
*/
(function () {
  "use strict";
  window.SBAI = window.SBAI || {};

  var MOTIVOS = {
    cancelado: ["El cliente canceló el proyecto", "Sin presupuesto", "Cambio de prioridades", "Alcance no viable"],
    en_pausa: ["Falta de presupuesto", "Esperando respuesta del cliente", "Recursos no disponibles", "Reprogramación"],
    eliminar: ["Creado por error", "Proyecto duplicado", "Ya no se necesita"],
    asignacion: ["Terminó su parte del proyecto", "Cambio de prioridades", "Reasignación a otro proyecto", "A solicitud del colaborador"]
  };

  function enviar(url, datos) {
    window.SBAI.submitForm(url, datos);
  }

  function nombreCorto(nombre) {
    return nombre || "este proyecto";
  }

  function esc(t) {
    var d = document.createElement("div");
    d.textContent = t == null ? "" : String(t);
    return d.innerHTML;
  }
  function pad2(n) { return n < 10 ? "0" + n : "" + n; }
  function isoEnDias(dias) {
    var d = new Date(); d.setDate(d.getDate() + dias);
    return d.getFullYear() + "-" + pad2(d.getMonth() + 1) + "-" + pad2(d.getDate());
  }
  var ETIQUETA = { planificacion: "Planificación", activo: "Activo", en_pausa: "En pausa", completado: "Completado", cancelado: "Cancelado" };
  function plural(n, uno, varios) { return n + " " + (n === 1 ? uno : varios); }

  /**
   * Pide el motivo cuando el cambio de estado lo exige (Cancelar / Pausar).
   * Modelo v7: pausar también pide la fecha estimada de reanudación y avisa
   * que el equipo sale del proyecto. Devuelve true si abrió el modal.
   *  o: { nombre, nuevo, colaboradoresActivos, onConfirm(motivo, fechaReanudacion) }
   */
  function pedirMotivoEstado(o) {
    var nombre = nombreCorto(o.nombre);
    var n = Number(o.colaboradoresActivos) || 0;
    if (o.nuevo === "cancelado") {
      window.SBAI.modal.confirm({
        variant: "destructive",
        icon: "icon-alert-triangle",
        title: "¿Cancelar “" + nombre + "”?",
        desc: "Pasará a estado Cancelado. Podrás reabrirlo más adelante (vuelve a Planificación).",
        subject: { nombre: nombre, meta: "Cancelado" },
        consequences: [
          n ? plural(n, "colaborador sale", "colaboradores salen") + " del proyecto: su asignación queda Cancelada en su historial y su carga se libera." : "No hay colaboradores activos.",
          "El Project Manager se mantiene (para conservar el historial), pero su carga deja de contar.",
          "Las propuestas de asignación pendientes se rechazan automáticamente."
        ],
        reason: { label: "Razón de la cancelación", required: true, presets: MOTIVOS.cancelado, placeholder: "Ej.: el cliente retiró el presupuesto." },
        confirmLabel: "Cancelar proyecto",
        cancelLabel: "Volver",
        onConfirm: function (d) { o.onConfirm(d.motivo, ""); }
      });
      return true;
    }
    if (o.nuevo === "en_pausa") {
      var bd = null;
      var min = isoEnDias(1);
      bd = window.SBAI.modal.confirm({
        variant: "warning",
        title: "¿Pausar “" + nombre + "”?",
        desc: "Al pausar, el equipo sale del proyecto para que su carga quede libre de verdad. Al reanudar podrás proponer su reincorporación.",
        consequences: [
          n ? plural(n, "colaborador sale", "colaboradores salen") + " del proyecto (su asignación finaliza hoy y queda en su historial)." : "No hay colaboradores activos.",
          "El Project Manager se queda para planificar la reanudación; su carga sigue contando.",
          "Las propuestas de asignación pendientes se rechazan automáticamente."
        ],
        bodyHtml: '<div class="field"><label class="field-label" for="pa-reanudacion">Fecha estimada de reanudación <span class="mc-tag is-req">obligatorio</span></label>' +
          '<input class="input" type="date" id="pa-reanudacion" min="' + min + '" value="' + isoEnDias(14) + '">' +
          '<div class="field-error" id="pa-reanudacion-error"></div></div>',
        reason: { label: "Motivo de la pausa", required: true, presets: MOTIVOS.en_pausa, placeholder: "Ej.: esperamos la aprobación del cliente." },
        confirmLabel: "Pausar proyecto",
        cancelLabel: "Volver",
        validate: function (backdrop) {
          var f = backdrop.querySelector("#pa-reanudacion");
          var err = backdrop.querySelector("#pa-reanudacion-error");
          var ok = f.value && f.value >= min;
          err.textContent = ok ? "" : "Elige una fecha posterior a hoy.";
          err.style.display = ok ? "none" : "flex";
          f.closest(".field").classList.toggle("has-error", !ok);
          if (!ok) f.focus();
          return ok;
        },
        onConfirm: function (d) { o.onConfirm(d.motivo, bd.querySelector("#pa-reanudacion").value); }
      });
      return true;
    }
    return false;
  }

  /**
   * Reanudar (en pausa -> activo/planificación) o reabrir (completado/
   * cancelado -> planificación). Muestra el equipo anterior para marcar a
   * quién reincorporar: cada marcado genera una propuesta que aprueba el
   * Resource Manager. Quien ya no tiene capacidad aparece deshabilitado.
   */
  function pedirReincorporacion(o, onConfirm) {
    var nombre = nombreCorto(o.nombre);
    var reabrir = o.actual === "completado" || o.actual === "cancelado";
    var equipo = o.equipoAnterior || [];
    var lista = equipo.length
      ? '<div class="reinc-list">' + equipo.map(function (m) {
          var ok = m.conCapacidad;
          return '<label class="reinc-row' + (ok ? "" : " is-blocked") + '">' +
            '<input type="checkbox" class="reinc-check" value="' + esc(m.perfilId) + '"' + (ok ? " checked" : " disabled") + ">" +
            '<span class="reinc-main"><strong>' + esc(m.nombre) + "</strong>" +
            '<span class="reinc-sub">' + esc(m.cargo || "Colaborador") + " · tenía " + esc(m.cargaAnterior) + "% · hoy " + esc(m.cargaActual) + "% de carga</span>" +
            (ok ? "" : '<span class="reinc-why">' + esc(m.motivo) + "</span>") + "</span></label>";
        }).join("") + "</div>"
      : '<p class="text-tertiary" style="font-size:var(--fs-xs)">No hay equipo anterior que reincorporar.</p>';
    var bd = window.SBAI.modal.confirm({
      variant: "primary",
      icon: reabrir ? "icon-arrow-left" : "icon-check-circle",
      title: (reabrir ? "¿Reabrir “" : "¿Reanudar “") + nombre + "”?",
      desc: (reabrir ? "Vuelve a Planificación con su Project Manager. " : "Pasa a " + (ETIQUETA[o.nuevo] || o.nuevo) + " con su Project Manager. ") +
        "Marca a quién del equipo anterior quieres reincorporar: se enviará una propuesta al Resource Manager por cada persona, con la misma dedicación que tenía.",
      bodyHtml: '<div class="field"><span class="field-label">Equipo anterior</span>' + lista + "</div>",
      confirmLabel: reabrir ? "Reabrir proyecto" : "Reanudar proyecto",
      cancelLabel: "Volver",
      onConfirm: function () {
        var ids = Array.prototype.map.call(bd.querySelectorAll(".reinc-check:checked"), function (c) { return c.value; });
        onConfirm(ids);
      }
    });
  }

  /**
   * o: { url, nombre, actual, nuevo, extra, colaboradoresActivos, equipoAnterior }
   */
  function cambiarEstado(o) {
    if (o.nuevo === o.actual) {
      window.SBAI.toast.show("El proyecto ya está en ese estado.", {});
      return;
    }
    function ir(motivo, fechaReanudacion, reincorporar) {
      var datos = { estado: o.nuevo };
      if (motivo) datos.razonCancelacion = motivo;
      if (fechaReanudacion) datos.fechaReanudacion = fechaReanudacion;
      if (reincorporar && reincorporar.length) datos.reincorporar = reincorporar;
      if (o.extra) Object.keys(o.extra).forEach(function (k) { datos[k] = o.extra[k]; });
      enviar(o.url, datos);
    }
    var reanuda = o.actual === "en_pausa" && (o.nuevo === "activo" || o.nuevo === "planificacion");
    var reabre = (o.actual === "completado" || o.actual === "cancelado") && o.nuevo === "planificacion";
    if (reanuda || reabre) {
      pedirReincorporacion(o, function (ids) { ir("", "", ids); });
      return;
    }
    if (pedirMotivoEstado({ nombre: o.nombre, nuevo: o.nuevo, colaboradoresActivos: o.colaboradoresActivos, onConfirm: ir })) return;
    if (o.nuevo === "completado") {
      var n = Number(o.colaboradoresActivos) || 0;
      window.SBAI.modal.confirm({
        variant: "primary",
        icon: "icon-check-circle",
        title: "¿Marcar “" + nombreCorto(o.nombre) + "” como completado?",
        desc: "Confirma que el trabajo del proyecto terminó. Si hiciera falta, podrás reabrirlo (vuelve a Planificación).",
        consequences: [
          n ? plural(n, "colaborador sale", "colaboradores salen") + " del proyecto: su asignación finaliza hoy y queda en su historial." : "No hay colaboradores activos.",
          "El Project Manager se mantiene para conservar el historial; su carga deja de contar.",
          "Las propuestas de asignación pendientes se rechazan automáticamente."
        ],
        confirmLabel: "Marcar como completado",
        cancelLabel: "Volver",
        onConfirm: function () { ir(""); }
      });
      return;
    }
    ir("");
  }

  /**
   * Lee del modal de detalle (plantilla Thymeleaf) lo que necesita
   * cambiarEstado: colaboradores activos y equipo anterior.
   */
  function datosDeModal(raiz) {
    var anterior = Array.prototype.map.call(raiz.querySelectorAll("[data-equipo-anterior] [data-perfil]"), function (el) {
      return {
        perfilId: el.dataset.perfil, nombre: el.dataset.nombre, cargo: el.dataset.cargo,
        cargaAnterior: Number(el.dataset.cargaAnterior) || 0, cargaActual: Number(el.dataset.cargaActual) || 0,
        conCapacidad: el.dataset.conCapacidad === "true", motivo: el.dataset.motivo || ""
      };
    });
    var activos = raiz.querySelector("[data-colaboradores-activos]");
    return { equipoAnterior: anterior, colaboradoresActivos: activos ? Number(activos.dataset.colaboradoresActivos) || 0 : 0 };
  }

  function eliminar(o) {
    var nombre = nombreCorto(o.nombre);
    // El servidor solo deja eliminar proyectos cancelados: se lo explicamos y le ofrecemos el camino.
    if (o.estado !== "cancelado") {
      window.SBAI.modal.confirm({
        variant: "warning",
        title: "Primero hay que cancelar el proyecto",
        desc: "“" + nombre + "” está en " + (o.estadoLabel || "otro estado") + ". Por seguridad solo se pueden eliminar proyectos Cancelados que no tengan equipo, entregables ni hilos de foro.",
        confirmLabel: o.estadoUrl ? "Cancelar el proyecto…" : "Entendido",
        cancelLabel: "Cerrar",
        onConfirm: function () {
          if (!o.estadoUrl) return;
          pedirMotivoEstado({
            nombre: nombre, nuevo: "cancelado",
            colaboradoresActivos: 0,
            onConfirm: function (motivo) { enviar(o.estadoUrl, { estado: "cancelado", razonCancelacion: motivo }); }
          });
        }
      });
      return;
    }
    window.SBAI.modal.confirm({
      variant: "destructive",
      title: "¿Eliminar “" + nombre + "”?",
      desc: "Esta acción no se puede deshacer.",
      subject: { nombre: nombre, meta: "Cancelado" },
      consequences: [
        "Se borra el proyecto junto con sus asignaciones históricas.",
        "Solo es posible si ya no tiene colaboradores activos, entregables ni hilos de foro."
      ],
      reason: { label: "Motivo de la eliminación", required: true, presets: MOTIVOS.eliminar, placeholder: "Ej.: se creó por error." },
      typeToConfirm: nombre,
      confirmLabel: "Eliminar definitivamente",
      cancelLabel: "Volver",
      onConfirm: function (d) { enviar(o.url, Object.assign({ motivo: d.motivo }, o.extra || {})); }
    });
  }

  function finalizarAsignacion(o) {
    var nombre = o.nombre || "este colaborador";
    window.SBAI.modal.confirm({
      variant: "destructive",
      icon: "icon-user-x",
      title: "¿Finalizar la asignación de " + nombre + "?",
      desc: "Dejará de aparecer como parte activa del equipo" + (o.proyecto ? " de “" + o.proyecto + "”" : "") + ". Su historial se conserva.",
      reason: { label: "Motivo", required: false, presets: MOTIVOS.asignacion, placeholder: "Opcional: queda registrado en Auditoría." },
      confirmLabel: "Finalizar asignación",
      cancelLabel: "Volver",
      onConfirm: function (d) {
        // Con o.alConfirmar quien llama hace el envío (p. ej. por AJAX, sin recargar la página).
        if (typeof o.alConfirmar === "function") { o.alConfirmar(d.motivo || ""); return; }
        var datos = Object.assign({}, o.extra || {});
        if (d.motivo) datos.motivo = d.motivo;
        enviar(o.url, datos);
      }
    });
  }

  window.SBAI.proyectoAcciones = {
    eliminar: eliminar,
    cambiarEstado: cambiarEstado,
    pedirMotivoEstado: pedirMotivoEstado,
    datosDeModal: datosDeModal,
    finalizarAsignacion: finalizarAsignacion
  };
})();
