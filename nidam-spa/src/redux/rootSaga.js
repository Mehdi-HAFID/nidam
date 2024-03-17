import {takeEvery} from "redux-saga/effects";

import * as registerTypes from "./register/saga/registerSagaActionTypes";
import {getSecret, registerReCaptcha} from "./register/saga/registerSaga";

import * as authenticationTypes from "./authentication/saga/authenticationActionTypes";
import {isLoggedIn} from "./authentication/saga/authentication";

export function* watchRegistration() {
	yield takeEvery(registerTypes.REGISTER, registerReCaptcha);
	yield takeEvery("SECRET", getSecret);
}

export function* watchAuthentication() {
	yield takeEvery(authenticationTypes.IS_LOGGED_IN, isLoggedIn);
}