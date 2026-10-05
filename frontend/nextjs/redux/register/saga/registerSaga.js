import {put} from "redux-saga/effects"

import {registerAxios} from "../../axios";
import {registerStart, registerSuccess, registerFail} from '../registerSlice';
import {catchError} from "../../SagaGenericUtil";

export function* registerReCaptcha(action) {
	yield put(registerStart());

	try {
		let response;
		if (action.useRecaptcha === true) {
			response = yield registerAxios.post("registerCaptcha", action.user);
		} else {
			response = yield registerAxios.post("register", action.user);
		}

		// console.log("register response: ", response.data);

		yield put(registerSuccess({user: response.data}));

		// 	yield put(push(`/dashboard`));

	} catch (error) {
		yield * catchError(error, registerFail, 'Error Registering, Try Again');
	}
}



