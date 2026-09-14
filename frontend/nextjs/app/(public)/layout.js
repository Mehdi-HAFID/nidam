'use client';

import {useEffect} from 'react';
import {useRouter} from 'next/navigation';
import {useSelector} from 'react-redux';

export default function PublicLayout({children}) {
	const router = useRouter();

	const authenticated = useSelector((state) => state.authentication.authenticated);

	const isLoggedInLoading = useSelector((state) => state.authentication.isLoggedInLoading);

	useEffect(() => {
		if (isLoggedInLoading) {
			return;
		}

		if (authenticated) {
			router.replace('/secret');
		}
	}, [authenticated, isLoggedInLoading, router]);

	if (isLoggedInLoading) {
		return null;
	}

	if (authenticated) {
		return null;
	}

	return children;
}