// Super Admin panel — progressive enhancements only.
document.addEventListener('DOMContentLoaded', function () {
  var toggle = document.getElementById('sidebarToggle');
  var sidebar = document.querySelector('.sidebar');
  if (toggle && sidebar) {
    toggle.addEventListener('click', function () { sidebar.classList.toggle('open'); });
  }

  // Confirm destructive actions.
  document.querySelectorAll('form[data-confirm]').forEach(function (form) {
    form.addEventListener('submit', function (e) {
      if (!window.confirm(form.getAttribute('data-confirm'))) e.preventDefault();
    });
  });

  // Live colour preview on the theme editor.
  document.querySelectorAll('input[type=color][data-preview]').forEach(function (input) {
    var target = document.querySelector(input.getAttribute('data-preview'));
    var sync = function () { if (target) target.style.background = input.value; };
    input.addEventListener('input', sync);
    sync();
  });

  // Revenue chart (dashboard / reports).
  var canvas = document.getElementById('revenueChart');
  if (canvas && window.Chart) {
    var labels = JSON.parse(canvas.dataset.labels || '[]');
    var values = JSON.parse(canvas.dataset.values || '[]');
    new Chart(canvas, {
      type: 'line',
      data: {
        labels: labels,
        datasets: [{
          label: 'Revenue',
          data: values,
          borderColor: '#0E9F6E',
          backgroundColor: 'rgba(14,159,110,.18)',
          fill: true,
          tension: 0.35,
          pointRadius: 2
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { display: false } },
        scales: {
          x: { grid: { color: 'rgba(255,255,255,.05)' }, ticks: { color: '#8B98A9' } },
          y: { grid: { color: 'rgba(255,255,255,.05)' }, ticks: { color: '#8B98A9' } }
        }
      }
    });
  }
});
