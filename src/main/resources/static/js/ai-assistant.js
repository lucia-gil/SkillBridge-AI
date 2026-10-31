/**
 * ai-assistant.js — Lógica compartida del Asistente IA (chat de página
 * completa en asistente-ia.html de cada rol, y el mini panel embebido en
 * los dashboards).
 */
(function () {
  "use strict";

  var ROLE_KEY = { colaborador: "colaborador", "project-manager": "pm", "resource-manager": "rm", administrador: "administrador" };
  var PENDING_KEY = "sbai_pending_ai_msg";

  function nowLabel() {
    var d = new Date();
    var hh = String(d.getHours()).padStart(2, "0");
    var mm = String(d.getMinutes()).padStart(2, "0");
    return hh + ":" + mm;
  }

  var AVATAR_HTML = '<span class="avatar avatar-sm chat-msg-avatar"><svg class="icon icon-sm"><use href="/img/icons.svg#icon-sparkles"></use></svg></span>';

  function bubbleHtml(msg) {
    var cardHtml = "";
    if (msg.card) {
      cardHtml = '<div class="chat-msg-card"><div class="chat-msg-card-title">' + msg.card.titulo + '</div>' +
        '<div class="chat-msg-card-sub">' + msg.card.sub + '</div>' +
        '<button type="button" class="btn btn-tertiary btn-sm" data-toast-fact="' + msg.card.titulo.replace(/"/g, "&quot;") + '">' + msg.card.accion + " →</button></div>";
    }
    var body = '<div class="chat-msg ' + msg.role + '">' +
      '<div class="chat-bubble">' + msg.texto + cardHtml + "</div>" +
      '<div class="chat-meta">' + msg.meta + "</div></div>";
    if (msg.role === "assistant") {
      return '<div class="chat-msg-row">' + AVATAR_HTML + body + "</div>";
    }
    return body;
  }

  function renderHistory(role, el) {
    var key = ROLE_KEY[role];
    var items = MOCK.aiHistory[key] || [];
    el.innerHTML = items.map(function (h, i) {
      return '<div class="chat-history-item' + (i === 0 ? " active" : "") + '"><div class="chat-history-title">' + h.titulo + '</div><div class="chat-history-time">' + h.tiempo + "</div></div>";
    }).join("");
  }

  /**
   * Conversacion real con el Asistente IA (backend en /api/asistente/mensaje,
   * ver AsistenteIaController/AsistenteIaService): no hay historial guardado
   * todavia, asi que cada carga de pagina empieza con un saludo nuevo en vez
   * de la conversacion de ejemplo que mostraba el mock.
   */
  var DIAS = ["domingo", "lunes", "martes", "miércoles", "jueves", "viernes", "sábado"];
  var MESES = ["enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"];

  /** Fecha real de hoy en español (no la fecha fija de los datos de ejemplo). */
  function hoyLabel() {
    var d = new Date();
    var dia = DIAS[d.getDay()];
    return dia.charAt(0).toUpperCase() + dia.slice(1) + " " + d.getDate() + " de " + MESES[d.getMonth()] + ", " + d.getFullYear();
  }

  function renderConversation(role, el) {
    el.innerHTML = '<div class="chat-date-sep">' + hoyLabel().toUpperCase() + "</div>" +
      bubbleHtml({ role: "assistant", texto: "Hola, soy el Asistente IA de SkillBridge AI. ¿En qué te ayudo hoy?", meta: nowLabel() + " · ASISTENTE SKILLBRIDGE" });
    el.scrollTop = el.scrollHeight;
  }

  function wireFactButtons(el) {
    el.querySelectorAll("[data-toast-fact]").forEach(function (btn) {
      if (btn.dataset.bound) return;
      btn.dataset.bound = "1";
      btn.addEventListener("click", function () {
        window.SBAI.toast.show("Abriendo <strong>" + btn.dataset.toastFact + "</strong>…", { icon: "icon-folder" });
      });
    });
  }

  /**
   * Manda el mensaje al backend real (POST /api/asistente/mensaje, que a su
   * vez consulta la API de Mistral - ver AsistenteIaService). El token CSRF
   * lo agrega solo window.fetch gracias a csrf.js, no hace falta aqui.
   */
  function sendMessage(role, text, messagesEl) {
    text = text.trim();
    if (!text) return;
    var userMsg = { role: "user", texto: escapeHtml(text), meta: nowLabel() + " · TÚ" };
    messagesEl.insertAdjacentHTML("beforeend", bubbleHtml(userMsg));
    messagesEl.scrollTop = messagesEl.scrollHeight;

    var typingEl = document.createElement("div");
    typingEl.className = "chat-msg-row";
    typingEl.innerHTML = AVATAR_HTML + '<div class="chat-msg assistant"><div class="chat-bubble"><span class="chat-typing"><span></span><span></span><span></span></span></div></div>';
    messagesEl.appendChild(typingEl);
    messagesEl.scrollTop = messagesEl.scrollHeight;

    fetch("/api/asistente/mensaje", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ mensaje: text })
    })
      .then(function (res) { return res.json().then(function (data) { return { ok: res.ok, data: data }; }); })
      .then(function (r) {
        if (typingEl.parentNode) messagesEl.removeChild(typingEl);
        var texto = r.ok ? r.data.respuesta : (r.data && r.data.error) || MOCK.aiFallbackReply;
        var reply = { role: "assistant", texto: escapeHtml(texto), meta: nowLabel() + " · ASISTENTE SKILLBRIDGE" };
        messagesEl.insertAdjacentHTML("beforeend", bubbleHtml(reply));
        wireFactButtons(messagesEl);
        messagesEl.scrollTop = messagesEl.scrollHeight;
      })
      .catch(function () {
        if (typingEl.parentNode) messagesEl.removeChild(typingEl);
        var reply = { role: "assistant", texto: "No pude conectarme al asistente. Revisa tu conexión e intenta de nuevo.", meta: nowLabel() + " · ASISTENTE SKILLBRIDGE" };
        messagesEl.insertAdjacentHTML("beforeend", bubbleHtml(reply));
        messagesEl.scrollTop = messagesEl.scrollHeight;
      });
  }

  function escapeHtml(str) {
    var div = document.createElement("div");
    div.textContent = str;
    return div.innerHTML;
  }

  function wireComposer(role, opts) {
    var input = opts.input, sendBtn = opts.sendBtn, messagesEl = opts.messagesEl, form = opts.form;
    function doSend() {
      if (!input.value.trim()) return;
      sendMessage(role, input.value, messagesEl);
      input.value = "";
      sendBtn.disabled = true;
    }
    input.addEventListener("input", function () { sendBtn.disabled = !input.value.trim(); });
    sendBtn.addEventListener("click", function (e) { e.preventDefault(); doSend(); });
    if (form) form.addEventListener("submit", function (e) { e.preventDefault(); doSend(); });
    input.addEventListener("keydown", function (e) {
      if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); doSend(); }
    });

    var pending = null;
    try { pending = sessionStorage.getItem(PENDING_KEY); } catch (e) {}
    if (pending) {
      try { sessionStorage.removeItem(PENDING_KEY); } catch (e) {}
      setTimeout(function () { sendMessage(role, pending, messagesEl); }, 300);
    }
  }

  function wireSuggestionChips(role, containerEl, sendFn) {
    containerEl.querySelectorAll("[data-ai-suggestion]").forEach(function (chip) {
      chip.addEventListener("click", function () { sendFn(chip.textContent.trim()); });
    });
  }

  function wireMiniPanel(role, opts) {
    var input = opts.input, sendBtn = opts.sendBtn, targetHref = opts.targetHref || "asistente-ia.html";
    function go() {
      var text = input.value.trim();
      if (!text) { window.location.href = targetHref; return; }
      try { sessionStorage.setItem(PENDING_KEY, text); } catch (e) {}
      window.location.href = targetHref;
    }
    if (sendBtn) sendBtn.addEventListener("click", go);
    input.addEventListener("keydown", function (e) { if (e.key === "Enter") go(); });
  }

  window.SBAI = window.SBAI || {};
  window.SBAI.ai = {
    renderHistory: renderHistory,
    renderConversation: renderConversation,
    sendMessage: sendMessage,
    wireComposer: wireComposer,
    wireSuggestionChips: wireSuggestionChips,
    wireMiniPanel: wireMiniPanel
  };
})();
