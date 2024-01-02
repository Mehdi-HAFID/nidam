import {configureStore} from '@reduxjs/toolkit';
import createSagaMiddleware from 'redux-saga';

import registerReducer from "./register/registerSlice";
import {watchRegistration} from "./rootSaga";

const sagaMiddleware = createSagaMiddleware();
const middleware = [sagaMiddleware]

export const store = configureStore({
	reducer: {
		register: registerReducer
	},
	middleware: (getDefaultMiddleware) =>
		getDefaultMiddleware().concat(middleware),
});

sagaMiddleware.run(watchRegistration);