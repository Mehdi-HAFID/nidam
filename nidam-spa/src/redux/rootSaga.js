import {takeEvery} from "redux-saga/effects";

import * as registerTypes from "./register/saga/registerSagaActionTypes";
import {registerReCaptcha} from "./register/saga/registerSaga";

import * as authenticationTypes from "./authentication/saga/authenticationActionTypes";
import {isLoggedIn, logoutBeforeTokenExpires} from "./authentication/saga/authentication";

import * as nidamTypes from "./nidam/demoActionTypes";
import {getSecret} from "./nidam/demoSaga";


export function* watchRegistration() {
	yield takeEvery(registerTypes.REGISTER, registerReCaptcha);
}

export function* watchAuthentication() {
	yield takeEvery(authenticationTypes.IS_LOGGED_IN, isLoggedIn);
    yield takeEvery(authenticationTypes.LOGOUT_BEFORE_TOKEN_EXPIRES, logoutBeforeTokenExpires);
}

export function* watchNidam() {
    yield takeEvery(nidamTypes.GET_SECRET, getSecret);
}