import {configureStore} from '@reduxjs/toolkit';
import createSagaMiddleware from 'redux-saga';

import registerReducer from "./register/registerSlice";
import authenticationSlice from "./authentication/authenticationSlice";
import demoSlice from "./nidam/demoSlice";
import {watchAuthentication, watchRegistration, watchNidam} from "./rootSaga";

const sagaMiddleware = createSagaMiddleware();
const middleware = [sagaMiddleware]

export const store = configureStore({
	reducer: {
		register: registerReducer,
		authentication: authenticationSlice,
        nidam: demoSlice
	},
	middleware: (getDefaultMiddleware) => getDefaultMiddleware().concat(middleware),
});

sagaMiddleware.run(watchRegistration);
sagaMiddleware.run(watchAuthentication);
sagaMiddleware.run(watchNidam);