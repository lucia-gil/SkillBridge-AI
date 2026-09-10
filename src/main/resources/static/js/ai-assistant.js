/**
 * ai-assistant.js — Lógica compartida del Asistente IA (chat de página
 * completa en asistente-ia.html de cada rol, y el mini panel embebido en
 * los dashboards).
 */
(function () {
  "use strict";

  var ROLE_KEY = { colaborador: "colaborador", "project-manager": "pm", "resource-manager": "rm", administrador: "administrador" };
  var PENDING_KEY = "sbai_pending_ai_msg";

  function normalize(str) { return window.SBAI.tableFilter.normalize(str); }
  function nowLabel() {
    var d = new Date();
    var hh = String(d.getHours()).padStart(2, "0");
    var mm = String(d.getMinutes()).padStart(2, "0");
    return hh + ":" + mm;
  }

  function bubbleHtml(msg) {
    var cardHtml = "";
    if (msg.card) {
      cardHtml = '<div class="chat-msg-card"><div class="chat-msg-card-title">' + msg.card.titulo + '</div>' +
        '<div class="chat-msg-card-sub">' + msg.card.sub + '</div>' +
        '<button type="button" class="btn btn-tertiary btn-sm" data-toast-fact="' + msg.card.titulo.replace(/"/g, "&quot;") + '">' + msg.card.accion + " →</button></div>";
    }
    var bubble = '<div class="chat-bubble">' + msg.texto + cardHtml + "</div>";
    if (msg.role === "assistant") {
      return '<div class="chat-msg assistant"><div class="chat-msg-row">' +
        '<img class="chat-ai-avatar" src="' + window.SBAI.state.STATIC + 'img/logo-ai-icon.png" alt="">' + bubble + "</div>" +
        '<div class="chat-meta">' + msg.meta + "</div></div>";
    }
    return '<div class="chat-msg ' + msg.role + '">' + bubble + '<div class="chat-meta">' + msg.meta + "</div></div>";
  }

  function renderHistory(role, el) {
    var key = ROLE_KEY[role];
    var items = MOCK.aiHistory[key] || [];
    el.innerHTML = items.map(function (h, i) {
      return '<div class="chat-history-item' + (i === 0 ? " active" : "") + '"><div class="chat-history-title">' + h.titulo + '</div><div class="chat-history-time">' + h.tiempo + "</div></div>";
    }).join("");
  }

  function renderConversation(role, el) {
    var key = ROLE_KEY[role];
    var items = (MOCK.aiConversations[key] || []).slice();
    el.innerHTML = '<div class="chat-date-sep">' + MOCK.meta.todayLabel.toUpperCase() + "</div>" + items.map(bubbleHtml).join("");
    wireFactButtons(el);
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

  function findScriptedReply(role, text) {
    var key = ROLE_KEY[role];
    var convo = MOCK.aiConversations[key] || [];
    var norm = normalize(text);
    for (var i = 0; i < convo.length; i++) {
      if (convo[i].role !== "user") continue;
      var convoNorm = normalize(convo[i].texto);
      if (convoNorm === norm || convoNorm.indexOf(norm) !== -1 || norm.indexOf(convoNorm) !== -1) {
        var reply = convo[i + 1];
        if (reply && reply.role === "assistant") return reply;
      }
    }
    var suggestions = MOCK.aiSuggestions[key] || [];
    for (var j = 0; j < suggestions.length; j++) {
      if (normalize(suggestions[j]) === norm) {
        for (var k = 0; k < convo.length; k++) {
          if (convo[k].role === "user" && normalize(convo[k].texto) === normalize(suggestions[j])) {
            return convo[k + 1];
          }
        }
      }
    }
    return null;
  }

  function sendMessage(role, text, messagesEl) {
    text = text.trim();
    if (!text) return;
    var userMsg = { role: "user", texto: escapeHtml(text), meta: nowLabel() + " · TÚ" };
    messagesEl.insertAdjacentHTML("beforeend", bubbleHtml(userMsg));
    messagesEl.scrollTop = messagesEl.scrollHeight;

    var typingEl = document.createElement("div");
    typingEl.className = "chat-msg assistant";
    typingEl.innerHTML = '<div class="chat-msg-row"><img class="chat-ai-avatar" src="' + window.SBAI.state.STATIC + 'img/logo-ai-icon.png" alt=""><div class="chat-bubble"><span class="chat-typing"><span></span><span></span><span></span></span></div></div>';
    messagesEl.appendChild(typingEl);
    messagesEl.scrollTop = messagesEl.scrollHeight;

    setTimeout(function () {
      messagesEl.removeChild(typingEl);
      var scripted = findScriptedReply(role, text);
      var reply = scripted || { role: "assistant", texto: MOCK.aiFallbackReply, meta: nowLabel() + " · ASISTENTE SKILLBRIDGE" };
      messagesEl.insertAdjacentHTML("beforeend", bubbleHtml(reply));
      wireFactButtons(messagesEl);
      messagesEl.scrollTop = messagesEl.scrollHeight;
    }, 900 + Math.random() * 500);
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