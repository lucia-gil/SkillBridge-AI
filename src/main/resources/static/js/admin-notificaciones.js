(function () {
  "use strict";

  const bell = document.querySelector("[data-an-bell]");
  if (bell) {
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
    count.textContent = matches.length ? `${start + 1}–${start + visible.size} de ${matches.length} avisos` : "";
    pageLabel.textContent = `Página ${page + 1} de ${pages}`;
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
  window.addEventListener("hashchange", openLinkedNotice);
  render();
  openLinkedNotice();
})();
