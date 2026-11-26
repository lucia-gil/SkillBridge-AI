(function () {
  "use strict";

  const button = document.getElementById("login-show");
  const password = document.getElementById("login-password");
  button.hidden = false;
  button.addEventListener("click", function () {
    const visible = password.type === "password";
    password.type = visible ? "text" : "password";
    button.setAttribute("aria-pressed", String(visible));
    button.setAttribute("aria-label", visible ? "Ocultar contraseña" : "Mostrar contraseña");
  });
})();
