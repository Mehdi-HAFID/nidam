import {put} from "redux-saga/effects"

import {axios} from "../axios";
import * as actions from './demoSlice';
import {catchError} from "../SagaGenericUtil";

export function* getSecret(action) {
    yield put(actions.getSecretStart());

    try {
        const response = yield axios.get("demo");
        console.log("getSecret: ", response.data);

        yield put(actions.getSecretSuccess({secret: response.data}));

    } catch (error) {
        yield * catchError(error, actions.getSecretFail, 'Error Getting Secret, Try Again');
    }
}