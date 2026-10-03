/*
  proyecto-acciones.js — Confirmaciones de las acciones delicadas sobre proyectos,
  con modal propio (SBAI.modal.confirm) en lugar del aviso nativo del navegador,
  y con el motivo que queda en Auditoría.

    SBAI.proyectoAcciones.eliminar({ url, estadoUrl, nombre, estado, estadoLabel })
    SBAI.proyectoAcciones.cambiarEstado({ url, nombre, actual, nuevo, extra })
    SBAI.proyectoAcciones.pedirMotivoEstado({ nombre, nuevo, onConfirm(motivo) })
    SBAI.proyectoAcciones.finalizarAsignacion({ url, nombre, proyecto, extra })

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

  /**
   * Pide el motivo cuando el cambio de estado lo exige (Cancelar / Pausar).
   * Devuelve true si abrió el modal; false si ese estado no necesita confirmación.
   */
  function pedirMotivoEstado(o) {
    var nombre = nombreCorto(o.nombre);
    if (o.nuevo === "cancelado") {
      window.SBAI.modal.confirm({
        variant: "destructive",
        icon: "icon-alert-triangle",
        title: "¿Cancelar “" + nombre + "”?",
        desc: "Pasará a estado Cancelado. Podrás eliminarlo después solo si no tiene equipo, entregables ni hilos de foro.",
        subject: { nombre: nombre, meta: "Cancelado" },
        reason: { label: "Razón de la cancelación", required: true, presets: MOTIVOS.cancelado, placeholder: "Ej.: el cliente retiró el presupuesto." },
        confirmLabel: "Cancelar proyecto",
        cancelLabel: "Volver",
        onConfirm: function (d) { o.onConfirm(d.motivo); }
      });
      return true;
    }
    if (o.nuevo === "en_pausa") {
      window.SBAI.modal.confirm({
        variant: "warning",
        title: "¿Pausar “" + nombre + "”?",
        desc: "Queda en pausa hasta que lo reactives. El motivo se guarda en Auditoría.",
        reason: { label: "Motivo de la pausa", required: true, presets: MOTIVOS.en_pausa, placeholder: "Ej.: esperamos la aprobación del cliente." },
        confirmLabel: "Pausar proyecto",
        cancelLabel: "Volver",
        onConfirm: function (d) { o.onConfirm(d.motivo); }
      });
      return true;
    }
    return false;
  }

  function cambiarEstado(o) {
    if (o.nuevo === o.actual) {
      window.SBAI.toast.show("El proyecto ya está en ese estado.", {});
      return;
    }
    function ir(motivo) {
      var datos = { estado: o.nuevo };
      if (motivo) datos.razonCancelacion = motivo;
      if (o.extra) Object.keys(o.extra).forEach(function (k) { datos[k] = o.extra[k]; });
      enviar(o.url, datos);
    }
    if (pedirMotivoEstado({ nombre: o.nombre, nuevo: o.nuevo, onConfirm: ir })) return;
    if (o.nuevo === "completado") {
      window.SBAI.modal.confirm({
        variant: "primary",
        icon: "icon-check-circle",
        title: "¿Marcar “" + nombreCorto(o.nombre) + "” como completado?",
        desc: "Confirma que el trabajo del proyecto terminó. Puedes volver a cambiar el estado más adelante.",
        confirmLabel: "Marcar como completado",
        cancelLabel: "Volver",
        onConfirm: function () { ir(""); }
      });
      return;
    }
    ir("");
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
    finalizarAsignacion: finalizarAsignacion
  };
})();
