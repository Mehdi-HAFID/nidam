import * as actionsTypes from "./demoActionTypes";

export const GetSecret = () => {
	return {
        type: actionsTypes.GET_SECRET,
	}
};

export const GetTopSecret = () => {
	return {
		type: actionsTypes.GET_TOP_SECRET,
	}
};
