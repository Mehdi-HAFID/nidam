'use client';

import {useEffect} from 'react';
import {useRouter} from 'next/navigation';
import {useSelector} from 'react-redux';

export default function HomePage() {
	const router = useRouter();

	const authenticated = useSelector((state) => state.authentication.authenticated);

	const isLoggedInLoading = useSelector((state) => state.authentication.isLoggedInLoading);

	useEffect(() => {
		if (isLoggedInLoading) {
			return;
		}

		if (authenticated) {
			router.replace('/secret');
		} else {
			router.replace('/signup');
		}
	}, [authenticated, isLoggedInLoading, router]);

	return null;
}