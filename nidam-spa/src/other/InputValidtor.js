export const checkValidity = (element, name) => {
	const rules = element.validation;
	let value = element.value;
	let isValid = true;
	let validationMessage = null;

	if (!rules) {
		return true;
	}

	if (rules.required) {
		value = typeof value === 'string' ? value.trim(): value;
		isValid = value !== '' && isValid;
		validationMessage = !isValid ? `${name} is required!` : null;
		if(!isValid){
			return [isValid, validationMessage];
		}
	}

	if (rules.minLength) {
		isValid = (value.length >= rules.minLength) && isValid;
		validationMessage = !isValid ? `${name} length should be at least ${rules.minLength} !` : null;
		if(!isValid){
			return [isValid, validationMessage];
		}
	}

	if (rules.maxLength) {
		isValid = (value.length <= rules.maxLength) && isValid;
		validationMessage = !isValid ? `${name} length should be less than ${rules.maxLength} !` : null;
		if(!isValid){
			return [isValid, validationMessage];
		}
	}

	if (rules.isEmail) {
		const pattern = /[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?/;
		isValid = pattern.test(value) && isValid;
		validationMessage = !isValid ? `${name} is not a valid email!` : null;
		if(!isValid){
			return [isValid, validationMessage];
		}
	}

	return [isValid, validationMessage];
};