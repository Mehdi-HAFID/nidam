import * as actionsTypes from "./registerSagaActionTypes";

export const register = (user) => {
	return {
		type: actionsTypes.REGISTER,
		user: user
	}
};