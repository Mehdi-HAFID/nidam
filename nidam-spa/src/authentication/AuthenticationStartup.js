import {useEffect, useState} from "react";
import {useDispatch, useSelector} from "react-redux";
import {LayoutSplashScreen} from "../pages/SplashScreen";

import {authenticated} from "../redux/authentication/authenticationSlice";
import * as authenticationActions from "../redux/authentication/saga";

const AuthenticationStartup = props => {
	const dispatch = useDispatch();

	const isLoggedInLoading = useSelector((state) => state.authentication.isLoggedInLoading);
	const userInfo = useSelector((state) => state.authentication.userInfo);
	const isLoggedInError = useSelector((state) => state.authentication.isLoggedInError);

	const [phase, setPhase] = useState(1);
	const [showSplashScreen, setShowSplashScreen] = useState(true);


	useEffect(() => {
		// call /me phase 2
		dispatch(authenticationActions.isLoggedIn());
		setPhase(2);
		setShowSplashScreen(true); // loading
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
                    logoutBeforeTokenExpires();
				}
			}
			setPhase(3);
			setShowSplashScreen(false);
			disableSplashScreen();
		}

		// always set show splash to false, because registration must be visible
	}, [isLoggedInError, phase, isLoggedInLoading, userInfo]);

    const logoutBeforeTokenExpires = () => {
        const expireAt = ((userInfo.exp * 1000) - (new Date().getTime())) - 30_000;
        dispatch(authenticationActions.logoutBeforeTokenExpires(expireAt));
    }

	return showSplashScreen ? <LayoutSplashScreen/> : props.children;

}

export default AuthenticationStartup;

const disableSplashScreen = () => {
	const splashScreen = document.getElementById('splash-screen')
	if (splashScreen) {
		splashScreen.style.setProperty('display', 'none')
	}
}