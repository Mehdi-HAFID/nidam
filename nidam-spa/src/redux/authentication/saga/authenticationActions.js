import * as actionsTypes from "./authenticationActionTypes";


export const isLoggedIn = () => {
	return {
		type: actionsTypes.IS_LOGGED_IN
	}
};