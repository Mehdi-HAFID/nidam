import {put, delay} from "redux-saga/effects";
import rawAxios from "axios";

import {axios} from "../../axios";
import * as actions from '../authenticationSlice';
import {catchError} from "../../SagaGenericUtil";


export function* isLoggedIn(action) {
	yield put(actions.isLoggedInStart());

	try {
		const response = yield axios.get("me");
		console.log("isLoggedIn userInfo: ", response.data);

		yield put(actions.isLoggedInSuccess({userInfo: response.data}));

	} catch (error) {
		yield * catchError(error, actions.isLoggedInFail, 'Error getting user info on startup, Try Again');
	}
}

// integrate this when: 1/login with email and password 2/ any refresh to tab (this includes logged in, close browser, open browser, visited page and the token
//  is still valid). Implemented in AuthenticationStartup phase = 2
export function* logoutBeforeTokenExpires(action) {
    console.log("saga logoutBeforeTokenExpires delay in minutes: ", (action.expirationTime / 1000) / 60);
    yield delay(action.expirationTime);

    // call bff logout, this has the benefit of clearing the bff memory
    const response = yield rawAxios.post(
        "/bff/logout",
        {},
        {
            headers: {
                "X-POST-LOGOUT-SUCCESS-URI": process.env.REACT_APP_BASE_URI,
            },
        }
    );
    // console.log("logout response: ", JSON.stringify(response.headers["location"]));
    window.location.href = response.headers["location"];
}

