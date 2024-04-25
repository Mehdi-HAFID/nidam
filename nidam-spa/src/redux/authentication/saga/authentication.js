import {put} from "redux-saga/effects";

import {axios} from "../../axios";
import {isLoggedInFail, isLoggedInStart, isLoggedInSuccess} from '../authenticationSlice';
import {catchError} from "../../SagaGenericUtil";


export function* isLoggedIn(action) {
	yield put(isLoggedInStart());

	try {
		const response = yield axios.get("me");
		console.log("isLoggedIn userInfo: ", response.data);

		yield put(isLoggedInSuccess({userInfo: response.data}));

	} catch (error) {
		yield * catchError(error, isLoggedInFail, 'Error getting user info on startup, Try Again');
	}
}

