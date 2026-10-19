/**
 * foto-perfil.js — Selector de foto de "Mi cuenta" (los 4 roles).
 *
 * Reemplaza el botón nativo "Seleccionar archivo / Ningún archivo
 * seleccionado" por una zona para arrastrar o elegir la imagen, con vista
 * previa en el avatar antes de guardar. Solo cambia la presentación: el
 * <input type="file" name="foto"> y el formulario POST /cuenta/foto son los
 * mismos de siempre.
 */
(function () {
  "use strict";
  var MAX = 5 * 1024 * 1024;
  var TIPOS = ["image/jpeg", "image/png"];

  function kb(n) { return n > 1024 * 1024 ? (n / 1024 / 1024).toFixed(1) + " MB" : Math.max(1, Math.round(n / 1024)) + " KB"; }
  function toast(m, t) { if (window.SBAI && window.SBAI.toast) window.SBAI.toast.show(m, { type: t || "error" }); }

  function init() {
    var input = document.getElementById("foto");
    if (!input || input.dataset.fotoLista) return;
    input.dataset.fotoLista = "1";
    var form = input.closest("form");
    var avatar = document.querySelector(".account-photo-large");
    var boton = form ? form.querySelector('button[type="submit"]') : null;

    // Zona de arrastre (label = clic abre el selector nativo).
    var zona = document.createElement("label");
    zona.className = "photo-drop";
    zona.setAttribute("for", "foto");
    zona.innerHTML =
      '<span class="photo-drop-icon"><svg class="icon"><use href="/img/icons.svg#icon-camera"></use></svg></span>' +
      '<span class="photo-drop-text"><strong>Elige una imagen</strong> o arrástrala aquí' +
      '<small>JPG o PNG · máximo 5 MB</small></span>' +
      '<span class="photo-drop-file" hidden></span>';
    input.parentNode.insertBefore(zona, input);
    input.classList.add("photo-input-native");
    var etiquetaArchivo = zona.querySelector(".photo-drop-file");
    if (boton) boton.disabled = true;

    // Botón de cámara sobre el avatar.
    if (avatar && !avatar.querySelector(".photo-cam")) {
      var wrap = document.createElement("span");
      wrap.className = "photo-avatar-wrap";
      avatar.parentNode.insertBefore(wrap, avatar);
      wrap.appendChild(avatar);
      var cam = document.createElement("label");
      cam.className = "photo-cam";
      cam.setAttribute("for", "foto");
      cam.title = "Cambiar fotografía";
      cam.innerHTML = '<svg class="icon icon-sm"><use href="/img/icons.svg#icon-camera"></use></svg>';
      wrap.appendChild(cam);
    }
    var original = avatar ? avatar.innerHTML : "";

    function mostrar(file) {
      if (!file) {
        zona.classList.remove("has-file"); etiquetaArchivo.hidden = true;
        if (avatar) avatar.innerHTML = original;
        if (boton) boton.disabled = true;
        return;
      }
      if (TIPOS.indexOf(file.type) === -1) { input.value = ""; toast("Solo se aceptan imágenes JPG o PNG."); return mostrar(null); }
      if (file.size > MAX) { input.value = ""; toast("La imagen pesa " + kb(file.size) + "; el máximo es 5 MB."); return mostrar(null); }
      zona.classList.add("has-file");
      etiquetaArchivo.hidden = false;
      etiquetaArchivo.innerHTML = '<svg class="icon icon-sm"><use href="/img/icons.svg#icon-check-circle"></use></svg>' +
        '<span></span><button type="button" class="photo-drop-clear" aria-label="Quitar imagen">×</button>';
      etiquetaArchivo.querySelector("span").textContent = file.name + " · " + kb(file.size);
      if (avatar) {
        var url = URL.createObjectURL(file);
        avatar.innerHTML = '<img alt="Vista previa">';
        avatar.querySelector("img").src = url;
        avatar.classList.add("is-preview");
      }
      if (boton) boton.disabled = false;
    }

    input.addEventListener("change", function () { mostrar(input.files && input.files[0]); });
    zona.addEventListener("click", function (e) {
      if (e.target.closest(".photo-drop-clear")) { e.preventDefault(); input.value = ""; mostrar(null); }
    });
    ["dragenter", "dragover"].forEach(function (ev) {
      zona.addEventListener(ev, function (e) { e.preventDefault(); zona.classList.add("is-drag"); });
    });
    ["dragleave", "drop"].forEach(function (ev) {
      zona.addEventListener(ev, function (e) { e.preventDefault(); zona.classList.remove("is-drag"); });
    });
    zona.addEventListener("drop", function (e) {
      var f = e.dataTransfer && e.dataTransfer.files;
      if (!f || !f.length) return;
      try { input.files = f; } catch (err) { /* navegador antiguo */ }
      mostrar(f[0]);
    });
  }

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", init); else init();
})();
