import {put} from "redux-saga/effects"

import {registerAxios} from "../../axios";
import {registerStart, registerSuccess, registerFail} from '../registerSlice';
import {catchError} from "../../SagaGenericUtil";

export function* registerReCaptcha(action) {
	yield put(registerStart());

	try {
		const response = yield registerAxios.post("registerCaptcha", action.user);
		// console.log("register response: ", response.data);

		yield put(registerSuccess({user: response.data}));

		// 	yield put(push(`/dashboard`));

	} catch (error) {
		yield * catchError(error, registerFail, 'Error Registering, Try Again');
	}
}



