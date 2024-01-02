import {put} from "redux-saga/effects"

import axios from "../../axios";
import { registerStart, registerSuccess, registerFail } from '../registerSlice';

export function* register(action) {
	yield put(registerStart());

	try {
		const response = yield axios.post("new", action.user);
		// console.log("register response: ", response.data);

		yield put(registerSuccess({user: response.data}));

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