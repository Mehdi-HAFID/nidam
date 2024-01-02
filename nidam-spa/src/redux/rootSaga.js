import {takeEvery} from "redux-saga/effects";

import {register} from "./register/saga/registerSaga";
import * as actionsTypes from "./register/saga/registerSagaActionTypes";

export function* watchRegistration() {
	yield takeEvery(actionsTypes.REGISTER, register);
}