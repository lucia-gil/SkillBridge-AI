(function () {
  "use strict";

  const bell = document.querySelector("[data-an-bell]");
  if (bell) {
    const button = bell.querySelector("summary");
    const badge = bell.querySelector(".an-badge");
    const total = bell.querySelector("[data-an-total]");
    const markAll = bell.querySelector("[data-an-mark-all]");
    const previews = bell.querySelector("[data-an-previews]");
    const status = bell.querySelector("[data-an-status]");
    const inboxUpdate = document.querySelector("[data-an-inbox-update]");
    const inboxNav = Array.from(document.querySelectorAll(".sidebar-nav-item"))
      .find((link) => link.getAttribute("href") === bell.dataset.anInbox);
    const initialTotal = Number(bell.dataset.anUnread) || 0;
    const signature = () => Array.from(previews.querySelectorAll(".an-preview")).map((link) =>
      [link.hash.slice(1), link.classList.contains("an-unread")].join(":")).join(",");
    const initialSignature = signature();
    let loading = false;

    function updateSidebar(count) {
      if (!inboxNav) return;
      let counter = inboxNav.querySelector(".counter-badge");
      if (count === 0) {
        if (counter) counter.remove();
        return;
      }
      if (!counter) {
        counter = document.createElement("span");
        counter.className = "counter-badge";
        inboxNav.append(counter);
      }
      counter.textContent = count > 99 ? "99+" : count;
    }

    function preview(aviso) {
      const link = document.createElement("a");
      link.className = aviso.leida ? "an-preview" : "an-preview an-unread";
      link.href = bell.dataset.anInbox + "#aviso-" + aviso.id;
      const icon = document.createElement("span");
      icon.className = "an-icon";
      icon.dataset.tone = aviso.tono;
      const namespace = "http://www.w3.org/2000/svg";
      const svg = document.createElementNS(namespace, "svg");
      svg.classList.add("icon");
      svg.setAttribute("aria-hidden", "true");
      const use = document.createElementNS(namespace, "use");
      use.setAttribute("href", bell.dataset.anIcons + "#" + aviso.icono);
      svg.append(use);
      icon.append(svg);

      const copy = document.createElement("span");
      copy.className = "an-preview-copy";
      const title = document.createElement("strong");
      title.textContent = aviso.titulo;
      const date = document.createElement("span");
      date.textContent = aviso.fecha;
      copy.append(title, date);
      link.append(icon, copy);
      if (!aviso.leida) {
        const dot = document.createElement("span");
        dot.className = "an-dot";
        dot.setAttribute("aria-label", "Sin leer");
        link.append(dot);
      }
      return link;
    }

    async function refresh() {
      if (loading || document.hidden || !bell.dataset.anSource) return;
      loading = true;
      try {
        const response = await fetch(bell.dataset.anSource, {
          headers: { Accept: "application/json" },
          cache: "no-store"
        });
        if (!response.ok || response.redirected) throw new Error("Bandeja no disponible");
        const { sinLeer, avisos } = await response.json();
        const summary = sinLeer > 0 ? sinLeer + " sin leer" : "Estás al día";
        badge.hidden = sinLeer === 0;
        badge.textContent = sinLeer > 99 ? "99+" : sinLeer;
        button.setAttribute("aria-label", sinLeer > 0 ? "Notificaciones: " + summary : "Notificaciones");
        total.textContent = summary;
        markAll.hidden = sinLeer === 0;
        updateSidebar(sinLeer);
        status.hidden = true;

        const latestSignature = avisos.map((aviso) => "aviso-" + aviso.id + ":" + !aviso.leida).join(",");
        if (signature() !== latestSignature) {
          const focused = previews.contains(document.activeElement) ? document.activeElement.hash : null;
          if (avisos.length) {
            previews.replaceChildren(...avisos.map(preview));
          } else {
            const empty = document.createElement("p");
            empty.className = "an-preview-empty";
            empty.textContent = "Aún no tienes avisos. Puedes consultar la actividad de la plataforma desde la bandeja.";
            previews.replaceChildren(empty);
          }
          if (focused) {
            const restored = Array.from(previews.querySelectorAll("a")).find((link) => link.hash === focused);
            (restored || button).focus();
          }
        }
        if (inboxUpdate) {
          inboxUpdate.hidden = sinLeer === initialTotal && initialSignature === latestSignature;
        }
      } catch {
        status.textContent = "No se pudo actualizar. Cierra y vuelve a abrir la campana para reintentar.";
        status.hidden = false;
      } finally {
        loading = false;
      }
    }

    bell.addEventListener("toggle", () => { if (bell.open) refresh(); });
    window.setInterval(refresh, 45000);
    document.addEventListener("visibilitychange", () => { if (!document.hidden) refresh(); });
    document.addEventListener("click", (event) => {
      if (bell.open && !bell.contains(event.target)) bell.open = false;
    });
    document.addEventListener("keydown", (event) => {
      if (bell.open && event.key === "Escape") {
        bell.open = false;
        bell.querySelector("summary").focus();
      }
    });
  }

  const center = document.querySelector("[data-an-center]");
  if (!center) return;

  const rows = Array.from(center.querySelectorAll("[data-an-row]"));
  if (!rows.length) return;

  const filters = center.querySelector("[data-an-filters]");
  const search = filters.querySelector("[data-an-search]");
  const type = filters.querySelector("[data-an-type]");
  const empty = center.querySelector("[data-an-empty]");
  const pager = center.querySelector("[data-an-pager]");
  const previous = pager.querySelector("[data-an-prev]");
  const next = pager.querySelector("[data-an-next]");
  const count = pager.querySelector("[data-an-count]");
  const pageLabel = pager.querySelector("[data-an-page]");
  const pageSize = 8;
  let page = 0;

  function normalize(value) {
    return value.toLocaleLowerCase("es").normalize("NFD").replace(/[\u0300-\u036f]/g, "").trim();
  }

  const contents = new Map(rows.map((row) => [
    row, normalize(row.querySelector(".an-notice-copy").textContent + " " +
      (row.querySelector(".an-notice-body p")?.textContent || ""))
  ]));

  function render() {
    const query = normalize(search.value);
    const state = filters.querySelector("input:checked").value;
    const matches = rows.filter((row) => {
      const unread = row.dataset.anUnread === "true";
      return (state === "todas" || unread === (state === "nuevas")) &&
        (!type.value || row.dataset.anRow === type.value) &&
        contents.get(row).includes(query);
    });

    const pages = Math.max(1, Math.ceil(matches.length / pageSize));
    page = Math.min(page, pages - 1);
    const start = page * pageSize;
    const visible = new Set(matches.slice(start, start + pageSize));
    rows.forEach((row) => { row.hidden = !visible.has(row); });
    pager.hidden = !matches.length;
    empty.hidden = matches.length > 0;
    count.textContent = matches.length ? `Mostrando ${visible.size} de ${matches.length} avisos` : "";
    // Botones numerados, como en el resto de tablas del sitio.
    pageLabel.replaceChildren(...Array.from({ length: pages }, (_, i) => {
      const b = document.createElement("button");
      b.type = "button";
      b.className = "page-btn page-num" + (i === page ? " active" : "");
      b.textContent = String(i + 1);
      if (i === page) b.setAttribute("aria-current", "page");
      b.addEventListener("click", () => { page = i; render(); });
      return b;
    }));
    previous.disabled = page === 0;
    next.disabled = page === pages - 1;
  }

  function reset() {
    filters.querySelector('input[value="todas"]').checked = true;
    search.value = "";
    type.value = "";
    page = 0;
  }

  function openLinkedNotice() {
    const id = window.location.hash.slice(1);
    const index = rows.findIndex((row) => row.id === id);
    if (index < 0) return;
    reset();
    page = Math.floor(index / pageSize);
    render();
    const notice = rows[index].querySelector("details");
    if (bell) bell.open = false;
    notice.open = true;
    notice.querySelector("summary").focus();
  }

  filters.hidden = false;
  filters.addEventListener("input", () => { page = 0; render(); });
  filters.addEventListener("change", () => { page = 0; render(); });
  previous.addEventListener("click", () => { page--; render(); });
  next.addEventListener("click", () => { page++; render(); });
  center.querySelector("[data-an-reset]").addEventListener("click", () => {
    reset();
    render();
    search.focus();
  });
  if (bell) bell.addEventListener("click", (event) => {
    const link = event.target.closest(".an-preview");
    if (link && link.hash === window.location.hash && link.pathname === window.location.pathname) {
      event.preventDefault();
      openLinkedNotice();
    }
  });
  window.addEventListener("hashchange", openLinkedNotice);
  render();
  openLinkedNotice();
})();
