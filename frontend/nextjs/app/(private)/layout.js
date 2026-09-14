'use client';

import {useEffect} from 'react';
import {useRouter} from 'next/navigation';
import {useSelector} from 'react-redux';

export default function PrivateLayout({children}) {
	const router = useRouter();

	const authenticated = useSelector((state) => state.authentication.authenticated);

	const isLoggedInLoading = useSelector((state) => state.authentication.isLoggedInLoading);

	useEffect(() => {
		if (isLoggedInLoading) {
			return;
		}

		if (!authenticated) {
			router.replace('/signup');
		}
	}, [authenticated, isLoggedInLoading, router]);

	if (isLoggedInLoading) {
		return null;
	}

	if (!authenticated) {
		return null;
	}

	return children;
}