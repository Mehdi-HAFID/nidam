import {configureStore} from '@reduxjs/toolkit';
import createSagaMiddleware from 'redux-saga';

import registerReducer from "./register/registerSlice";
import authenticationSlice from "./authentication/authenticationSlice";
import {watchAuthentication, watchRegistration} from "./rootSaga";

const sagaMiddleware = createSagaMiddleware();
const middleware = [sagaMiddleware]

export const store = configureStore({
	reducer: {
		register: registerReducer,
		authentication: authenticationSlice
	},
	middleware: (getDefaultMiddleware) =>
		getDefaultMiddleware().concat(middleware),
});

sagaMiddleware.run(watchRegistration);
sagaMiddleware.run(watchAuthentication);