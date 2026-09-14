'use client';

import {Provider} from 'react-redux';

import {store} from '../redux/store';
import AuthenticationStartup from '../authentication/AuthenticationStartup';

export default function Providers({children}) {
	return (
		<Provider store={store}>
			<AuthenticationStartup>
				{children}
			</AuthenticationStartup>
		</Provider>
	);
}