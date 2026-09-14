'use client';

import {useEffect, useRef, useState} from 'react';
import {useDispatch, useSelector} from 'react-redux';

import {LayoutSplashScreen} from '../app/components/SplashScreen';
import {authenticated} from '../redux/authentication/authenticationSlice';
import * as authenticationActions from '../redux/authentication/saga';

const AuthenticationStartup = ({children}) => {
	const dispatch = useDispatch();

	const isLoggedInLoading = useSelector(
		(state) => state.authentication.isLoggedInLoading
	);

	const userInfo = useSelector(
		(state) => state.authentication.userInfo
	);

	const isLoggedInError = useSelector(
		(state) => state.authentication.isLoggedInError
	);

	const [phase, setPhase] = useState(1);
	const [showSplashScreen, setShowSplashScreen] = useState(true);

	const initialized = useRef(false);

	useEffect(() => {
		if (!initialized.current) {
			initialized.current = true;
			dispatch(authenticationActions.isLoggedIn());
			setPhase(2);
			setShowSplashScreen(true); // loading
		}
	}, []);

	useEffect(() => {
		if (phase === 2 && !isLoggedInLoading) {
			if(isLoggedInError === null){
				console.log("userInfo: ", userInfo);

				if(userInfo?.username === ""){
					// if empty then unauthenticated phase 3
					setPhase(3);
				} else {
					// if not then authenticated     phase 4
					setPhase(4);
					// userinfo is already loaded in store, add an authenticated flag and set to true
					dispatch(authenticated());

					// this is the best place to fire logoutBeforeTokenExpires
					// logoutBeforeTokenExpires();
				}
			}
			// setPhase(3);
			setShowSplashScreen(false);
			disableSplashScreen();
		}
	}, [isLoggedInError, phase, isLoggedInLoading, userInfo]);

	return showSplashScreen ? <LayoutSplashScreen/> : children;
};

export default AuthenticationStartup;

const disableSplashScreen = () => {
	const splashScreen = document.getElementById('splash-screen');

	if (splashScreen) {
		splashScreen.style.setProperty('display', 'none');
	}
};