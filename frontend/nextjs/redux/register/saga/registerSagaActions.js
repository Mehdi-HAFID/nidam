import * as actionsTypes from "./registerSagaActionTypes";

export const register = (useRecaptcha= false, user) => {
	return {
		type: actionsTypes.REGISTER,
		useRecaptcha: useRecaptcha,
		user: user
	}
};
