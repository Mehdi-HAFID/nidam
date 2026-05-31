import {put} from "redux-saga/effects";

function* catchError(error, failAction, requestError) {
	if (error.response) {
		console.log('error.response', error.response.data.message);
		// console.log("error.response at runtime:", !!error.response);
		// console.log("error.response value:", JSON.stringify(error.response));
		yield put(failAction({error: error.response.data.message}));
	} else if (error.request) {
		console.log('error.request', error.request)
		yield put(failAction({error: requestError}));
	} else {
		console.log('Error', error.message);
		yield put(failAction({error: error.message}));
	}
}

export {catchError};