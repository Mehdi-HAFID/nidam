import {useEffect, useState} from "react";
import {useDispatch, useSelector} from "react-redux";
import {LayoutSplashScreen} from "../pages/SplashScreen";

import * as authenticationSagas from "../redux/authentication/saga";
import {authenticated} from "../redux/authentication/authenticationSlice";

const AuthenticationStartup = props => {
	const dispatch = useDispatch();

	const isLoggedInLoading = useSelector((state) => state.authentication.isLoggedInLoading);
	const userInfo = useSelector((state) => state.authentication.userInfo);
	const isLoggedInError = useSelector((state) => state.authentication.isLoggedInError);

	const [phase, setPhase] = useState(1);
	const [showSplashScreen, setShowSplashScreen] = useState(true);

	useEffect(() => {
		// call /me phase 2
		console.log("I'm here");
		dispatch(authenticationSagas.isLoggedIn());
		setPhase(2);
		setShowSplashScreen(true); // loading
	}, []);

	useEffect(() => {
		if (phase === 2 && !isLoggedInLoading) {
			if(isLoggedInError !== null){
				console.log("userInfo: ", userInfo);

				if(userInfo?.username && userInfo.username === ""){
					// if empty then unauthenticated phase 3
					setPhase(3);
				} else {
					// if not then authenticated     phase 4
					setPhase(4);
					// userinfo is already loaded in store, add an authenticated flag and set to true
					// and setlocalstorage
					dispatch(authenticated());
				}
			}

			setPhase(3);
			setShowSplashScreen(false);
			disableSplashScreen();
		}

		// always set show splash to false, because registration must be visible
	}, [isLoggedInError, phase, isLoggedInLoading, userInfo]);

	return showSplashScreen ? <LayoutSplashScreen/> : props.children;

}

export default AuthenticationStartup;

const disableSplashScreen = () => {
	const splashScreen = document.getElementById('splash-screen')
	if (splashScreen) {
		splashScreen.style.setProperty('display', 'none')
	}
}