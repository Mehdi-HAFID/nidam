(() => {
    'use strict';

    const passwordInput = document.getElementById('password');
    const toggleButton = document.getElementById('toggle-password');

    if (!passwordInput || !toggleButton) {
        return;
    }

    toggleButton.addEventListener('click', () => {
        const isPasswordVisible = passwordInput.type === 'text';

        passwordInput.type = isPasswordVisible ? 'password' : 'text';
        toggleButton.classList.toggle('is-visible', !isPasswordVisible);
        toggleButton.setAttribute(
            'aria-label',
            isPasswordVisible ? 'Show password' : 'Hide password'
        );
        toggleButton.setAttribute(
            'title',
            isPasswordVisible ? 'Show password' : 'Hide password'
        );
        passwordInput.focus();
    });
})();
