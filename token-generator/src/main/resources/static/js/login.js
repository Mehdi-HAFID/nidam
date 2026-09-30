(function () {
  'use strict';

  var toggleButton = document.getElementById('togglePassword');
  var passwordInput = document.getElementById('password');

  if (!toggleButton || !passwordInput) {
    return;
  }

  var eyeIcon = toggleButton.querySelector('.icon-eye');
  var eyeOffIcon = toggleButton.querySelector('.icon-eye-off');

  // Set the initial state explicitly on load instead of trusting the
  // markup alone — guarantees exactly one icon is visible regardless
  // of whether the stylesheet has finished loading yet.
  eyeIcon.style.display = 'inline-block';
  eyeOffIcon.style.display = 'none';

  toggleButton.addEventListener('click', function () {
    var showing = passwordInput.type === 'password';

    passwordInput.type = showing ? 'text' : 'password';
    toggleButton.setAttribute('aria-pressed', String(showing));
    toggleButton.setAttribute('aria-label', showing ? 'Hide password' : 'Show password');

    eyeIcon.style.display = showing ? 'none' : 'inline-block';
    eyeOffIcon.style.display = showing ? 'inline-block' : 'none';
  });
})();
