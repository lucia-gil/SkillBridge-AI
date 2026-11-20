/* Interacciones locales: sin nuevos endpoints, dependencias o datos simulados. */
(function () {
  'use strict';
  var page = document.querySelector('.insight-page');
  if (!page) return;
  var dialog = page.querySelector('.insight-dialog'), returnFocus;
  function openDialog(title, content, actions) {
    returnFocus = document.activeElement;
    dialog.querySelector('h2').textContent = title;
    dialog.querySelector('[data-dialog-body]').replaceChildren(content);
    var footer = dialog.querySelector('.insight-dialog-actions');
    footer.replaceChildren();
    (actions || []).forEach(function (a) {
      var button = document.createElement(a.href ? 'a' : 'button');
      button.className = 'btn ' + (a.primary ? 'btn-primary' : 'btn-secondary');
      button.textContent = a.label;
      if (a.href) { button.href = a.href; button.addEventListener('click', function () { dialog.close(); }); }
      else { button.type = 'button'; button.addEventListener('click', function () { dialog.close(); }); }
      footer.appendChild(button);
    });
    dialog.showModal();
  }
  dialog.querySelector('[data-dialog-close]').addEventListener('click', function () { dialog.close(); });
  dialog.addEventListener('close', function () { if (returnFocus) returnFocus.focus(); });
  page.querySelectorAll('[data-export]').forEach(function (link) {
    link.addEventListener('click', function (e) {
      e.preventDefault();
      var text = document.createElement('p');
      text.textContent = 'Descargarás el reporte global en formato ' + link.dataset.export + ', con los indicadores, proyectos por estado, habilidades y ocupación actuales de la plataforma.';
      openDialog('Tu reporte, listo para llevar.', text, [{label:'Volver'},{label:'Descargar '+link.dataset.export,href:link.href,primary:true}]);
    });
  });
  page.querySelectorAll('.insight-detail-btn').forEach(function (button) {
    button.addEventListener('click', function () {
      var cells = button.closest('tr').cells, dl = document.createElement('dl');
      ['Fecha','Usuario','Acción','Detalle','Tipo','Severidad'].forEach(function (label, i) {
        var dt = document.createElement('dt'), dd = document.createElement('dd');
        dt.textContent = label; dd.textContent = cells[i].textContent.trim(); dl.append(dt,dd);
      });
      openDialog('Detalle del evento',dl,[{label:'Cerrar detalle',primary:true}]);
    });
  });
  var filters = page.querySelector('.admin-audit-filters');
  if (filters) {
    var validation = page.querySelector('.insight-validation');
    filters.addEventListener('submit',function(e){
      var desde=filters.elements.desde, hasta=filters.elements.hasta;
      if ((desde.value && hasta.value && desde.value > hasta.value) || !filters.checkValidity()) {
        e.preventDefault();e.stopImmediatePropagation();
        validation.hidden=false;validation.textContent='Revisa el rango: la fecha de inicio debe ser anterior o igual a la fecha final. Usa fechas válidas.';
        desde.setAttribute('aria-invalid','true');desde.focus();
        var submit=filters.querySelector('[type="submit"]'); submit.disabled=false;submit.removeAttribute('aria-busy');delete submit.dataset.adminLoading;
        var spinner=submit.querySelector('.admin-loading-spinner');if(spinner)spinner.remove();
      }
    },true);
    filters.addEventListener('input',function(){validation.hidden=true;filters.elements.desde.removeAttribute('aria-invalid');});
  }
  var states=Array.from(page.querySelectorAll('[data-project-state]')), total=states.reduce(function(n,e){return n+Math.max(0,Number(e.dataset.count)||0);},0);
  if(total){
    var palette=['#218e88','#5476c5','#c79a4b','#8e79b3','#be7381'], offset=0;
    var slices=states.map(function(e,i){e.style.setProperty('--state-color',palette[i%palette.length]);var start=offset;offset+=(Math.max(0,Number(e.dataset.count)||0)/total)*100;return palette[i%palette.length]+' '+start+'% '+offset+'%';});
    var ring=page.querySelector('.insight-donut');ring.style.background='conic-gradient('+slices.join(',')+')';ring.setAttribute('aria-label',states.map(function(e){return e.dataset.label+': '+e.dataset.count;}).join(', '));
    page.querySelector('[data-project-total]').textContent=total;page.querySelector('.insight-donut-wrap').hidden=false;
  }
  var rows=Array.from(page.querySelectorAll('[data-reportes-ocupacion-row]'));
  if(rows.length){
    var index=1, size=8, search=page.querySelector('#insight-occupation-search'), prev=page.querySelector('#reportes-ocupacion-prev'), next=page.querySelector('#reportes-ocupacion-next');
    var sum=0,over=0;rows.forEach(function(row){var load=row.querySelector('[data-load]'), value=Number(load.dataset.load)||0;sum+=value;if(value>Number(load.dataset.limite)){over++;load.classList.add('is-over');}});
    var summary=page.querySelector('.insight-capacity-summary');
    [[rows.length,'colaboradores listados'],[Math.round(sum/rows.length)+'%','ocupación media'],[over,'por encima del límite de '+rows[0].querySelector('[data-load]').dataset.limite+'%']].forEach(function(item){var span=document.createElement('span'),b=document.createElement('b');b.textContent=item[0];span.append(b,document.createTextNode(item[1]));summary.appendChild(span);});
    function normalized(s){return s.toLocaleLowerCase('es').normalize('NFD').replace(/[\u0300-\u036f]/g,'');}
    function render(){var query=normalized(search.value.trim()), matches=rows.filter(function(row){return normalized(row.cells[0].textContent+' '+row.cells[1].textContent).includes(query);});var pages=Math.max(1,Math.ceil(matches.length/size));index=Math.min(index,pages);rows.forEach(function(r){r.hidden=true;});var visible=matches.slice((index-1)*size,index*size);visible.forEach(function(r){r.hidden=false;});page.querySelector('#reportes-ocupacion-visible-count').textContent=visible.length;page.querySelector('#reportes-ocupacion-total-count').textContent=matches.length;page.querySelector('#reportes-ocupacion-page-label').textContent='Página '+index+' de '+pages;prev.disabled=index<=1;next.disabled=index>=pages;page.querySelector('#insight-no-people').hidden=matches.length>0;}
    prev.addEventListener('click',function(){index--;render();});next.addEventListener('click',function(){index++;render();});search.addEventListener('input',function(){index=1;render();});render();
  }
})();
