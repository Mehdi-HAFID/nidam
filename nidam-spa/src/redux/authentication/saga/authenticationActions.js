import * as actionsTypes from "./authenticationActionTypes";


export const isLoggedIn = () => {
	return {
		type: actionsTypes.IS_LOGGED_IN
	}
};

export const logoutBeforeTokenExpires = (expirationTime) => {
    return {
        type: actionsTypes.LOGOUT_BEFORE_TOKEN_EXPIRES,
        expirationTime: expirationTime
    }
};