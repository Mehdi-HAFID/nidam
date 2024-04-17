import {put} from "redux-saga/effects"

import {axios, registerAxios} from "../../axios";
import {registerStart, registerSuccess, registerFail, saveSecret} from '../registerSlice';
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

// TODO move from this file
export function* getSecret(action) {
	// yield put(registerStart());

	const rsAxios = axios.create({
		baseURL: "http://localhost:7080/bff/api",
		timeout: 15000,
	});
	try {
		const response = yield rsAxios.get("demo");
		console.log("getSecret: ", response.data);

		yield put(saveSecret({secret: response.data}));

		// 	yield put(push(`/dashboard`));

	} catch (error) {
		if (error.response) {
			console.log(error.response.data.message);
			yield put(registerFail({error: error.response.data.message}));
		} else if (error.request) {
			console.log(error.request)
			yield put(registerFail({error: 'Error Registering, Try Again'}));
		} else {
			console.log('Error', error.message);
			yield put(registerFail({error: error.message}));
		}
	}
}

